package com.febrie.demo_bk.controller;

import com.febrie.demo_bk.dto.FileDTO;
import com.febrie.demo_bk.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


@RestController
@RequestMapping("api/admin/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public FileDTO upload(
            MultipartFile file
    ) throws IOException {

        return fileService.upload(file);
    }

    /**
     * 判断是否存在
     */
    @GetMapping("{id}/exists")
    public boolean exists(
            @PathVariable Long id
    ){
        return fileService.exists(id);
    }

    /**
     * 删除文件
     */
    @DeleteMapping("{id}")
    public void delete(
            @PathVariable Long id
    ){
        fileService.delete(id);
    }

}
