package com.febrie.demo_bk.file.application.storage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileUploadResult {

    private String bucket;

    private String objectKey;

    private String url;

    private  Long size;

}
