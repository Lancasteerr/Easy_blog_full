package com.febrie.demo_bk.service.storage;

import com.febrie.demo_bk.pojo.FileUploadRequest;
import com.febrie.demo_bk.pojo.FileUploadResult;

import java.io.InputStream;

public interface FileStorageService {

    /**
     * 上传文件
     */
    FileUploadResult upload(
            InputStream inputStream,
            FileUploadRequest request
    );


    /**
     * 下载
     */
    InputStream download(String objectKey);


    /**
     * 删除
     */
    void delete(String objectKey);


    /**
     * 获取访问地址
     */
    String getUrl(String objectKey);


    /**
     * 判断存在
     */
    boolean exists(String objectKey);

}
