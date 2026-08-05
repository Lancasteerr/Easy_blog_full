package com.febrie.demo_bk.controller;

import com.febrie.demo_bk.annotation.OperationLoger;
import com.febrie.demo_bk.pojo.User;
import com.febrie.demo_bk.result.Result;
import com.febrie.demo_bk.service.LoginAttemptService;
import com.febrie.demo_bk.service.TokenBlacklistService;
import com.febrie.demo_bk.service.UserService;
import com.febrie.demo_bk.util.JwtUtil;
import com.febrie.demo_bk.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Controller
public class LoginController {

    private final UserService userService;

    private final PasswordEncoder passwordEncoder;// 密码加密比较

    private final LoginAttemptService loginAttemptService;

    private final TokenBlacklistService tokenBlacklistService;

    private final JwtUtil jwtUtil;

    public LoginController(
            UserService userService,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            TokenBlacklistService tokenBlacklistService,
            JwtUtil jwtUtil
    ){
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.jwtUtil = jwtUtil;
    }

    //post请求为api/login将转发到该方法
    @PostMapping(value = "api/public/login")
    //返回值为响应，转换为json
    @ResponseBody
    @OperationLoger(module = "登录")
    public ResponseEntity<Result> login(
            @RequestBody User requestUser
    ) {
        if (requestUser == null) {
            return ResponseEntity
                    .badRequest()
                    .body(new Result(400));
        }

        String ip = RequestUtil.getIpAddress();
        String userName = requestUser.getUserName();
        String password = requestUser.getPassword();

        if (userName == null || password == null) {
            return badLoginResult();
        }

        //转义防xss攻击
        userName = HtmlUtils.htmlEscape(userName);
        password = HtmlUtils.htmlEscape(password);

        //账号或ip锁定
        if(!loginAttemptService.allowLogin(userName, ip)){
            return badLoginResult();
        }

        User user = userService.getByName(userName);

        //用户不存在
        if(null == user){
            loginAttemptService.recordFail(userName,
                    ip
            );
            return badLoginResult();
        }

        //校验密码
        if(!passwordEncoder.matches(password,user.getPassword())){
            loginAttemptService.recordFail(
                    userName,
                    ip
            );
            return badLoginResult();
        }

        loginAttemptService.clear(
                userName,
                ip
        );

        //生成JWT
        String token = jwtUtil.generateToken(userName);

        return ResponseEntity.ok(new Result(200,token));
    }

    //退出登录时废除当前请求携带的JWT，避免旧token在过期前继续访问后台接口。
    @PostMapping(value = "api/admin/logout")
    @ResponseBody
    @OperationLoger(module = "退出登录")
    public ResponseEntity<Result> logout(HttpServletRequest request) {
        String token = extractBearerToken(request);

        if (token == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new Result(401));
        }

        tokenBlacklistService.revoke(token);

        return ResponseEntity.ok(new Result(200));
    }

    private ResponseEntity<Result> badLoginResult() {
        // 登录失败仍保留旧的 code 字段，前端只需要从 HTTP 400 分支读取即可。
        return ResponseEntity
                .badRequest()
                .body(new Result(400));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        return authorization.substring(7);
    }
}
