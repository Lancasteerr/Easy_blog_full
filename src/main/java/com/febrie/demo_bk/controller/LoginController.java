package com.febrie.demo_bk.controller;

import com.febrie.demo_bk.annotation.OperationLoger;
import com.febrie.demo_bk.pojo.User;
import com.febrie.demo_bk.result.Result;
import com.febrie.demo_bk.service.LoginAttemptService;
import com.febrie.demo_bk.service.UserService;
import com.febrie.demo_bk.util.JwtUtil;
import com.febrie.demo_bk.util.RequestUtil;
import lombok.extern.slf4j.Slf4j;
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

    public LoginController(
            UserService userService,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService
    ){
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    //跨域支持
    @CrossOrigin
    //post请求为api/login将转发到该方法
    @PostMapping(value = "api/public/login")
    //返回值为响应，转换为json
    @ResponseBody
    @OperationLoger(module = "登录")
    public Result login(
            @RequestBody User requestUser
    ) {
        String ip = RequestUtil.getIpAddress();
        String userName = requestUser.getUserName();
        String password = requestUser.getPassword();
        //转义防xss攻击
        userName = HtmlUtils.htmlEscape(userName);
        password = HtmlUtils.htmlEscape(password);

        //账号或ip锁定
        if(!loginAttemptService.allowLogin(userName, ip)){
            return new Result(400);
        }

        User user = userService.getByName(userName);

        //用户不存在
        if(null == user){
            loginAttemptService.recordFail(userName,
                    ip
            );
            return new Result(400);
        }

        //校验密码
        if(!passwordEncoder.matches(password,user.getPassword())){
            loginAttemptService.recordFail(
                    userName,
                    ip
            );
            return new Result(400);
        }

        loginAttemptService.clear(
                userName,
                ip
        );

        //生成JWT
        String token = JwtUtil.generateToken(userName);

        return new Result(200,token);
    }
}
