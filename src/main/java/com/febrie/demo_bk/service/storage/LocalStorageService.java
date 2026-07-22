package com.febrie.demo_bk.service.storage;

import com.febrie.demo_bk.pojo.FileUploadRequest;
import com.febrie.demo_bk.pojo.FileUploadResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


@Service
public class LocalStorageService
        implements FileStorageService {

    @Value("${storage.local.root-path}")
    private String rootPath;

    @Value("${storage.local.domain}")
    private String domain;

    @Override
    public FileUploadResult upload(InputStream inputStream,
                                    FileUploadRequest request) {

        try {

            Path filePath =
                    Paths.get(
                            rootPath,
                            request.getBucket(),
                            request.getObjectKey()
                    );

            Files.createDirectories(
                    filePath.getParent()
            );

            Files.copy(
                    inputStream,
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return FileUploadResult.builder()
                    .bucket(request.getBucket())
                    .objectKey(request.getObjectKey())
                    .size(request.getSize())
                    .url(
                            domain + "/"
                            + request.getBucket()
                            + "/"
                            + request.getObjectKey()
                    )
                    .build();

        }catch (IOException e) {

            throw new RuntimeException(
                    "文件上传失败", e
            );

        }

    }

    @Override
    public InputStream download(String objectKey) {

        try {

            return Files.newInputStream(
                    Paths.get(
                            rootPath,
                            objectKey
                    )
            );

        } catch (Exception e) {

            throw new RuntimeException(e);

        }

    }

    @Override
    public void delete(String objectKey) {

        try {

            Files.deleteIfExists(
                    Paths.get(
                            rootPath,
                            objectKey
                    )
            );

        } catch (Exception e) {

            throw new RuntimeException(e);

        }

    }

    @Override
    public String getUrl(String objectKey) {

        return domain + "/" + objectKey;

    }

    @Override
    public boolean exists(String objectKey) {

        return Files.exists(
                Paths.get(rootPath, objectKey)
        );

    }

}
