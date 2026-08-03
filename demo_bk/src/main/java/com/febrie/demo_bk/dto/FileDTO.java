package com.febrie.demo_bk.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileDTO {

    /**
     * 文件ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
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
