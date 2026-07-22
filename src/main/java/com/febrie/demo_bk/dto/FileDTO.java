package com.febrie.demo_bk.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileDTO {

    /**
     * 文件ID
     */
    private Long id;


    /**
     * 文件访问地址
     */
    private String url;


    /**
     * S3 Object Key
     */
    private String objectKey;


    /**
     * MIME类型
     */
    private String contentType;


    /**
     * 文件大小
     */
    private Long size;

}
