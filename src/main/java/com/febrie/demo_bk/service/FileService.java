package com.febrie.demo_bk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.febrie.demo_bk.dao.FileObjectMapper;
import com.febrie.demo_bk.dto.FileDTO;
import com.febrie.demo_bk.pojo.FileObject;
import com.febrie.demo_bk.pojo.FileUploadRequest;
import com.febrie.demo_bk.pojo.FileUploadResult;
import com.febrie.demo_bk.service.storage.FileStorageService;
import com.febrie.demo_bk.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    public static final int STATUS_TEMP = 0;

    public static final int STATUS_BOUND = 1;

    private final FileStorageService storageService;

    private final FileObjectMapper fileMapper;

    @Value("${storage.type}")
    private String storageType;

    public FileDTO upload(MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String suffix =
                FileUtil.getSuffix(file);

        String uuid = String.valueOf(UUID.randomUUID());

        String objectKey =
                "article/"
                        +
                        LocalDate.now()
                        +
                        "/"
                        +
                        uuid
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

        LocalDateTime now =
                LocalDateTime.now();


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
                uuid + suffix
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
                storageType
        );


        object.setUrl(
                result.getUrl()
        );

        object.setStatus(
                STATUS_TEMP
        );

        object.setCreatedTime(
                now
        );

        object.setUpdatedTime(
                now
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
                object.getBucketName()
                        +
                        "/"
                        +
                        object.getObjectKey()
        );

    }

    /**
     * 删除单个文件
     */
    public void delete(Long id){
        if (id == null) {
            return;
        }

        deleteTempFiles(Collections.singleton(id));
    }

    public FileObject getImageObject(Long id) {
        FileObject object =
                fileMapper.selectById(id);

        if (object == null) {
            throw new IllegalArgumentException("图片文件不存在");
        }

        validateImageObject(object);

        return object;
    }

    public void validateImageFiles(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        List<FileObject> objects =
                fileMapper.selectBatchIds(ids);

        Map<Long, FileObject> objectMap =
                objects.stream()
                        .collect(Collectors.toMap(
                                FileObject::getId,
                                Function.identity()
                        ));

        for (Long id : ids) {
            FileObject object =
                    objectMap.get(id);

            if (object == null) {
                throw new IllegalArgumentException("图片文件不存在");
            }

            validateImageObject(object);
        }
    }

    public void markBound(Set<Long> ids) {
        updateStatus(ids, STATUS_BOUND);
    }

    public void markTemp(Set<Long> ids) {
        updateStatus(ids, STATUS_TEMP);
    }

    public void deleteTempFiles(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        List<FileObject> objects =
                fileMapper.selectBatchIds(ids);

        objects.stream()
                .filter(object ->
                        object.getStatus() != null
                                &&
                                object.getStatus() == STATUS_TEMP
                )
                .forEach(this::deleteTempFile);
    }

    public void deleteExpiredTempFiles(LocalDateTime expireBefore,
                                       int limit) {
        if (expireBefore == null || limit <= 0) {
            return;
        }

        List<FileObject> objects =
                fileMapper.selectList(
                        new LambdaQueryWrapper<FileObject>()
                                .eq(FileObject::getStatus, STATUS_TEMP)
                                .and(wrapper ->
                                        wrapper
                                                .lt(FileObject::getUpdatedTime, expireBefore)
                                                .or()
                                                .isNull(FileObject::getUpdatedTime)
                                )
                                .orderByAsc(FileObject::getUpdatedTime)
                                .last("LIMIT " + limit)
                );

        objects.forEach(this::deleteTempFile);
    }

    /**
     * 将objectKey拼接为url
     */
    public String getUrl(String objectKey){

        return storageService.getUrl(objectKey);

    }

    public String getObjectPath(FileObject object) {
        return object.getBucketName()
                +
                "/"
                +
                object.getObjectKey();

    }

    private void updateStatus(Set<Long> ids,
                              int status) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        fileMapper.update(
                null,
                new LambdaUpdateWrapper<FileObject>()
                        .in(FileObject::getId, ids)
                        .set(FileObject::getStatus, status)
                        .set(FileObject::getUpdatedTime, LocalDateTime.now())
        );
    }

    private void deleteTempFile(FileObject object) {
        if (object == null
                ||
                object.getStatus() == null
                ||
                object.getStatus() != STATUS_TEMP) {
            return;
        }

        try {
            //删除存储文件
            storageService.delete(
                    getObjectPath(object)
            );

            //删除数据库记录
            fileMapper.deleteById(object.getId());
        } catch (Exception e) {
            log.warn(
                    "Delete temp file failed, fileId={}, objectPath={}",
                    object.getId(),
                    getObjectPath(object),
                    e
            );
        }

    }

    private boolean isImage(FileObject object) {
        String contentType =
                object.getContentType();

        return contentType != null
                &&
                contentType.toLowerCase()
                        .startsWith("image/");
    }

    private void validateImageObject(FileObject object) {
        if (!isImage(object)) {
            throw new IllegalArgumentException("文件不是图片");
        }

        if (!storageService.exists(getObjectPath(object))) {
            throw new IllegalArgumentException("图片文件不存在");
        }
    }


}
