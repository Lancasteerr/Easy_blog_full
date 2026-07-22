package com.febrie.demo_bk.service;

import com.febrie.demo_bk.dao.FileObjectMapper;
import com.febrie.demo_bk.dto.FileDTO;
import com.febrie.demo_bk.pojo.FileObject;
import com.febrie.demo_bk.pojo.FileUploadRequest;
import com.febrie.demo_bk.pojo.FileUploadResult;
import com.febrie.demo_bk.service.storage.FileStorageService;
import com.febrie.demo_bk.util.FileUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class FileService {

    private final FileStorageService storageService;

    private final FileObjectMapper fileMapper;

    public FileDTO upload(MultipartFile file)
            throws IOException {
        String suffix =
                FileUtil.getSuffix(file);



        String objectKey =
                "article/"
                        +
                        LocalDate.now()
                        +
                        "/"
                        +
                        UUID.randomUUID()
                        +
                        suffix;



        FileUploadRequest request =
                new FileUploadRequest();


        request.setBucket("blog");

        request.setObjectKey(objectKey);

        request.setOriginalName(
                file.getOriginalFilename()
        );

        request.setContentType(
                file.getContentType()
        );

        request.setSize(
                file.getSize()
        );



        // 1. 保存文件
        FileUploadResult result =
                storageService.upload(
                        file.getInputStream(),
                        request
                );



        // 2. 保存元数据
        FileObject object =
                new FileObject();


        object.setBucketName(
                result.getBucket()
        );


        object.setObjectKey(
                result.getObjectKey()
        );


        object.setOriginalName(
                file.getOriginalFilename()
        );


        object.setFileName(
                UUID.randomUUID()+suffix
        );


        object.setSuffix(
                suffix
        );


        object.setContentType(
                file.getContentType()
        );


        object.setFileSize(
                file.getSize()
        );


        object.setStorageType(
                "LOCAL"
        );


        object.setUrl(
                result.getUrl()
        );


        fileMapper.insert(object);



        return FileDTO.builder()
                .id(object.getId())
                .url(object.getUrl())
                .objectKey(object.getObjectKey())
                .build();
    }

    /**
     * 判断文件是否存在
     */
    public boolean exists(Long id){
        FileObject object =
                fileMapper.selectById(id);

        if(object == null){
            return false;
        }

        return storageService.exists(
                object.getObjectKey()
        );

    }

    /**
     * 删除文件
     */
    public void delete(Long id){

        FileObject object =
                fileMapper.selectById(id);

        if(object == null){
            return;
        }

        //删除存储文件
        storageService.delete(
                object.getObjectKey()
        );

        //删除数据库记录
        fileMapper.deleteById(id);

    }


}
