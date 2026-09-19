package com.febrie.demo_bk.identity.web.request;

/**
 * 登录接口请求参数，只承载 HTTP 边界需要的用户名和密码。
 */
public record LoginRequest(String userName, String password) {
}
