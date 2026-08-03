package com.febrie.demo_bk.util;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public class FileUtil {

    public static String getSuffix(MultipartFile file) {

        String filename = file.getOriginalFilename();

        if(filename == null) {
            return "";
        }

        String ext =
                StringUtils.getFilenameExtension(filename);

        return ext == null ? "" : "." + ext;

    }

}
