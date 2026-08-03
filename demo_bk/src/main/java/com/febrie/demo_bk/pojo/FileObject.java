package com.febrie.demo_bk.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_file_object")
public class FileObject {


    private Long id;


    private String bucketName;


    private String objectKey;


    private String originalName;


    private String fileName;


    private String suffix;


    private String contentType;


    private Long fileSize;


    private String storageType;


    private String url;

    private Integer status;


    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

}
