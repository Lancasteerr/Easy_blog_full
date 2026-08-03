package com.febrie.demo_bk.controller;

import com.febrie.demo_bk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;


@Controller
public class RegisterController{
    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /*
     * 注册功能已关闭：取消接口映射，避免前端或外部请求继续创建新账号。
     *
     * @CrossOrigin
     * @PostMapping(value = "api/public/register")
     * @ResponseBody
     * public Result register(@RequestBody User requestUser) {
     *     //不hash密码写法:
     *     requestUser.setUserName(HtmlUtils.htmlEscape(requestUser.getUserName()));
     *     requestUser.setPassword(HtmlUtils.htmlEscape(requestUser.getPassword()));
     *
     *     if(userService.isExist(requestUser.getUserName())) {
     *         return new Result(400);
     *     }
     *     else {
     *         //加密密码
     *         String encodePassword = passwordEncoder.encode(requestUser.getPassword());
     *         requestUser.setPassword(encodePassword);
     *
     *         //默认用户组
     *         requestUser.setRole("ROLE_ADMIN");
     *
     *         userService.add(requestUser);
     *         return new Result(200);
     *     }
     * }
     */
}
