package com.febrie.demo_bk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${storage.local.root-path}")
    private String localStorageRootPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path storagePath = Paths.get(localStorageRootPath)
                .toAbsolutePath()
                .normalize();

        String storageLocation = storagePath.toUri().toString();
        if (!storageLocation.endsWith("/")) {
            storageLocation = storageLocation + "/";
        }

        registry.addResourceHandler("/files/**")
                .addResourceLocations(storageLocation);
    }
}
