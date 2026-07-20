package com.febrie.demo_bk.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("sys_operation_log")
@Getter
@Setter
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String module;

    private String operation;

    private String method;

    private String requestUri;

    private String requestMethod;

    private String requestParam;

    private String ip;

    private Integer status;

    private String errorMsg;

    private Long costTime;

    private LocalDateTime createTime;
}
