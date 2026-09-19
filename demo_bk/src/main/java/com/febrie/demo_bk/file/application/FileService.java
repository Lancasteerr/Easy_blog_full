package com.febrie.demo_bk.file.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.febrie.demo_bk.file.application.dto.FileDTO;
import com.febrie.demo_bk.file.application.storage.FileStorage;
import com.febrie.demo_bk.file.application.storage.FileUploadRequest;
import com.febrie.demo_bk.file.application.storage.FileUploadResult;
import com.febrie.demo_bk.file.infrastructure.persistence.FileMapper;
import com.febrie.demo_bk.file.infrastructure.persistence.FileObject;
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

/**
 * 文件模块应用服务，统一管理上传、图片校验、绑定状态和临时文件清理。
 *
 * <p>这些操作共享同一套文件生命周期规则，当前不再按单个方法拆分服务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    public static final int STATUS_TEMP = 0;
    public static final int STATUS_BOUND = 1;

    private final FileStorage fileStorage;
    private final FileMapper fileMapper;

    @Value("${storage.type}")
    private String storageType;

    /**
     * 保存物理文件和对应元数据，新文件默认处于临时状态。
     */
    public FileDTO upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String suffix = FileUtil.getSuffix(file);
        String uuid = UUID.randomUUID().toString();
        String objectKey = "article/" + LocalDate.now() + "/" + uuid + suffix;

        FileUploadRequest request = new FileUploadRequest();
        request.setBucket("blog");
        request.setObjectKey(objectKey);
        request.setOriginalName(file.getOriginalFilename());
        request.setContentType(file.getContentType());
        request.setSize(file.getSize());

        FileUploadResult result = fileStorage.upload(file.getInputStream(), request);

        LocalDateTime now = LocalDateTime.now();
        FileObject object = new FileObject();
        object.setBucketName(result.getBucket());
        object.setObjectKey(result.getObjectKey());
        object.setOriginalName(file.getOriginalFilename());
        object.setFileName(uuid + suffix);
        object.setSuffix(suffix);
        object.setContentType(file.getContentType());
        object.setFileSize(file.getSize());
        object.setStorageType(storageType);
        object.setUrl(result.getUrl());
        object.setStatus(STATUS_TEMP);
        object.setCreatedTime(now);
        object.setUpdatedTime(now);
        fileMapper.insert(object);

        return FileDTO.builder()
                .id(object.getId())
                .url(object.getUrl())
                .objectKey(object.getObjectKey())
                .build();
    }

    /**
     * 同时检查元数据与物理文件是否存在。
     */
    public boolean exists(Long id) {
        FileObject object = fileMapper.selectById(id);
        return object != null && fileStorage.exists(getObjectPath(object));
    }

    /**
     * 后台主动删除也必须遵守临时状态约束，防止误删文章正在引用的文件。
     */
    public void delete(Long id) {
        if (id != null) {
            deleteTempFiles(Collections.singleton(id));
        }
    }

    /**
     * 校验图片存在后返回访问地址，避免其他业务模块依赖文件持久化对象。
     */
    public String getImageUrl(Long id) {
        return getImageObject(id).getUrl();
    }

    /**
     * 批量校验文章引用的全部文件，任一文件无效时终止文章事务。
     */
    public void validateImageFiles(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        List<FileObject> objects = fileMapper.selectByIds(ids);
        Map<Long, FileObject> objectMap = objects.stream()
                .collect(Collectors.toMap(FileObject::getId, Function.identity()));

        for (Long id : ids) {
            FileObject object = objectMap.get(id);
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

    /**
     * 只物理删除仍处于临时状态的文件，绑定文件会被忽略。
     */
    public void deleteTempFiles(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        fileMapper.selectByIds(ids).stream()
                .filter(object -> object.getStatus() != null
                        && object.getStatus() == STATUS_TEMP)
                .forEach(this::deleteTempFile);
    }

    /**
     * 分批清理超过保留时间的临时文件，失败项留给下一轮任务补偿。
     */
    public void deleteExpiredTempFiles(LocalDateTime expireBefore, int limit) {
        if (expireBefore == null || limit <= 0) {
            return;
        }

        List<FileObject> objects = fileMapper.selectList(
                new LambdaQueryWrapper<FileObject>()
                        .eq(FileObject::getStatus, STATUS_TEMP)
                        .and(wrapper -> wrapper
                                .lt(FileObject::getUpdatedTime, expireBefore)
                                .or()
                                .isNull(FileObject::getUpdatedTime))
                        .orderByAsc(FileObject::getUpdatedTime)
                        .last("LIMIT " + limit)
        );
        objects.forEach(this::deleteTempFile);
    }

    public String getUrl(String objectKey) {
        return fileStorage.getUrl(objectKey);
    }

    private FileObject getImageObject(Long id) {
        FileObject object = fileMapper.selectById(id);
        if (object == null) {
            throw new IllegalArgumentException("图片文件不存在");
        }
        validateImageObject(object);
        return object;
    }

    private String getObjectPath(FileObject object) {
        return object.getBucketName() + "/" + object.getObjectKey();
    }

    private void updateStatus(Set<Long> ids, int status) {
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
                || object.getStatus() == null
                || object.getStatus() != STATUS_TEMP) {
            return;
        }

        try {
            fileStorage.delete(getObjectPath(object));
            fileMapper.deleteById(object.getId());
        } catch (Exception exception) {
            log.warn(
                    "Delete temp file failed, fileId={}, objectPath={}",
                    object.getId(),
                    getObjectPath(object),
                    exception
            );
        }
    }

    private void validateImageObject(FileObject object) {
        String contentType = object.getContentType();
        boolean image = contentType != null
                && contentType.toLowerCase().startsWith("image/");
        if (!image) {
            throw new IllegalArgumentException("文件不是图片");
        }
        if (!fileStorage.exists(getObjectPath(object))) {
            throw new IllegalArgumentException("图片文件不存在");
        }
    }
}
