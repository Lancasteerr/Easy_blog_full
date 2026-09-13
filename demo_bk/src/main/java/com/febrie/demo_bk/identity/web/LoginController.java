package com.febrie.demo_bk.identity.web;

import com.febrie.demo_bk.audit.OperationLoger;
import com.febrie.demo_bk.identity.AuthService;
import com.febrie.demo_bk.identity.internal.User;
import com.febrie.demo_bk.shared.web.Result;
import com.febrie.demo_bk.shared.web.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final AuthService authService;

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
        return authService.authenticate(
                        requestUser.getUserName(),
                        requestUser.getPassword(),
                        ip
                )
                .map(token -> ResponseEntity.ok(new Result(200, token)))
                .orElseGet(this::badLoginResult);
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

        authService.logout(token);

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
