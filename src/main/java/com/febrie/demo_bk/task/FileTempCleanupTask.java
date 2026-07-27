package com.febrie.demo_bk.task;

import com.febrie.demo_bk.service.FileService;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@AllArgsConstructor
public class FileTempCleanupTask {

    //每次只删200个
    private static final int BATCH_SIZE = 200;

    private static final long TEMP_FILE_TTL_HOURS = 24;

    private final FileService fileService;

    //每1h执行一次
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void cleanupExpiredTempFiles() {
        fileService.deleteExpiredTempFiles(
                LocalDateTime.now().minusHours(TEMP_FILE_TTL_HOURS),
                BATCH_SIZE
        );
    }
}
