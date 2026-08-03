package com.febrie.demo_bk.result;

public class Result {
    //code可以使用枚举类型
    private int code;
    private String token;

    public Result(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getToken() {return token;}

    public void setToken(String token) {this.token = token;}

    public Result(int code, String token) {
        this.code = code;
        this.token = token;
    }
}
