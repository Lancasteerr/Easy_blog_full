package com.febrie.demo_bk.identity;

import com.febrie.demo_bk.identity.internal.JwtUtil;
import com.febrie.demo_bk.identity.internal.LoginAttemptService;
import com.febrie.demo_bk.identity.internal.TokenBlacklistService;
import com.febrie.demo_bk.identity.internal.User;
import com.febrie.demo_bk.identity.internal.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private LoginAttemptService loginAttemptService;
    private TokenBlacklistService tokenBlacklistService;
    private JwtUtil jwtUtil;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        loginAttemptService = mock(LoginAttemptService.class);
        tokenBlacklistService = mock(TokenBlacklistService.class);
        jwtUtil = mock(JwtUtil.class);
        authService = new AuthService(
                userMapper,
                passwordEncoder,
                loginAttemptService,
                tokenBlacklistService,
                jwtUtil
        );
    }

    @Test
    void authenticateShouldReturnTokenAndClearFailureCounters() {
        User user = new User(1, "admin", "encoded", "ROLE_ADMIN");
        when(loginAttemptService.allowLogin("admin", "127.0.0.1")).thenReturn(true);
        when(userMapper.selectByUserName("admin")).thenReturn(user);
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken("admin")).thenReturn("jwt-token");

        Optional<String> result =
                authService.authenticate("admin", "password", "127.0.0.1");

        assertThat(result).contains("jwt-token");
        verify(loginAttemptService).clear("admin", "127.0.0.1");
    }

    @Test
    void authenticateShouldRecordPasswordFailure() {
        User user = new User(1, "admin", "encoded", "ROLE_ADMIN");
        when(loginAttemptService.allowLogin("admin", "127.0.0.1")).thenReturn(true);
        when(userMapper.selectByUserName("admin")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        Optional<String> result =
                authService.authenticate("admin", "wrong", "127.0.0.1");

        assertThat(result).isEmpty();
        verify(loginAttemptService).recordFail("admin", "127.0.0.1");
    }

    @Test
    void authenticateShouldStopBeforeDatabaseWhenAccountOrIpIsLocked() {
        when(loginAttemptService.allowLogin("admin", "127.0.0.1")).thenReturn(false);

        Optional<String> result =
                authService.authenticate("admin", "password", "127.0.0.1");

        assertThat(result).isEmpty();
        verify(userMapper, never()).selectByUserName("admin");
    }

    @Test
    void logoutShouldRevokeCurrentToken() {
        authService.logout("jwt-token");

        verify(tokenBlacklistService).revoke("jwt-token");
    }
}
