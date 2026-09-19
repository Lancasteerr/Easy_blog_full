package com.febrie.demo_bk.file.application.storage;

import lombok.Data;

@Data
public class FileUploadRequest {
    /**
     * bucket
     */
    private String bucket;


    /**
     * S3 key
     */
    private String objectKey;


    /**
     * 原文件名
     */
    private String originalName;


    /**
     * MIME类型
     */
    private String contentType;


    /**
     * 文件大小
     */
    private Long size;
}
