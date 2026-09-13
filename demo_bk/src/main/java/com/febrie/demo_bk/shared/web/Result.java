package com.febrie.demo_bk.shared.web;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Result {
    //code可以使用枚举类型
    private int code;
    private String token;

    public Result(int code) {
        this.code = code;
    }

    public Result(int code, String token) {
        this.code = code;
        this.token = token;
    }
}
