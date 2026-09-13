package com.febrie.demo_bk.identity;

import com.febrie.demo_bk.identity.internal.JwtUtil;
import com.febrie.demo_bk.identity.internal.LoginAttemptService;
import com.febrie.demo_bk.identity.internal.TokenBlacklistService;
import com.febrie.demo_bk.identity.internal.User;
import com.febrie.demo_bk.identity.internal.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Optional;

/**
 * 身份认证模块的应用服务，统一编排登录限制、密码校验和 JWT 生命周期。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtUtil jwtUtil;

    /**
     * 校验登录凭据。失败原因统一折叠为空结果，避免泄露用户是否存在。
     */
    public Optional<String> authenticate(String userName,
                                         String password,
                                         String ip) {
        if (userName == null || password == null) {
            return Optional.empty();
        }

        // 保留原有输入处理行为，避免重构改变已有账号的登录结果。
        String escapedUserName = HtmlUtils.htmlEscape(userName);
        String escapedPassword = HtmlUtils.htmlEscape(password);

        if (!loginAttemptService.allowLogin(escapedUserName, ip)) {
            return Optional.empty();
        }

        User user = userMapper.selectByUserName(escapedUserName);
        if (user == null || !passwordEncoder.matches(escapedPassword, user.getPassword())) {
            loginAttemptService.recordFail(escapedUserName, ip);
            return Optional.empty();
        }

        loginAttemptService.clear(escapedUserName, ip);
        return Optional.of(jwtUtil.generateToken(escapedUserName));
    }

    /**
     * 废除当前 JWT，使其在原过期时间之前不再可用。
     */
    public void logout(String token) {
        tokenBlacklistService.revoke(token);
    }
}
