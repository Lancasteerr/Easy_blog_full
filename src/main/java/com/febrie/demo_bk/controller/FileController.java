package com.febrie.demo_bk.controller;

import com.febrie.demo_bk.dto.FileDTO;
import com.febrie.demo_bk.service.FileService;
import com.febrie.demo_bk.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

}
