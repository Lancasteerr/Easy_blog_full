package com.febrie.demo_bk.result;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Setter
public class ApiError {
    private int status;

    private String message;

    private String path;

    private LocalDateTime timestamp;

    public ApiError() {
    }

    public ApiError(int status,
                    String message,
                    String path,
                    LocalDateTime timestamp) {
        this.status = status;
        this.message = message;
        this.path = path;
        this.timestamp = timestamp;
    }

    public static ApiError of(HttpStatus status,
                              String message,
                              String path) {
        // 错误时间统一按项目部署所在时区记录，方便排查线上访问日志。
        return new ApiError(
                status.value(),
                message,
                path,
                LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        );
    }
}
