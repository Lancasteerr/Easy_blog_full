package com.febrie.demo_bk.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.febrie.demo_bk.dao.OperationLogDAO;
import com.febrie.demo_bk.pojo.OperationLog;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时清理过期操作日志，防止 sys_operation_log 表无限增长。
 */
@Slf4j
@Component
@AllArgsConstructor
public class OperationLogCleanupTask {

    //日志保留180天，超过保留期的日志会被定时清理
    private static final long LOG_RETENTION_DAYS = 180;

    //每批最多删除1000条，避免单次删除过多数据影响数据库
    private static final int BATCH_SIZE = 1000;

    //单次任务最多执行100批，避免历史日志过多时任务长时间占用线程
    private static final int MAX_BATCH_COUNT = 100;

    private final OperationLogDAO operationLogDAO;

    //每天凌晨3点清理一次过期日志
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredOperationLogs() {
        LocalDateTime expireBefore =
                LocalDateTime.now()
                        .minusDays(LOG_RETENTION_DAYS);

        int totalDeleted = 0;

        for (int i = 0; i < MAX_BATCH_COUNT; i++) {
            int deleted =
                    operationLogDAO.delete(
                            new LambdaQueryWrapper<OperationLog>()
                                    .lt(OperationLog::getCreateTime, expireBefore)
                                    .orderByAsc(OperationLog::getCreateTime)
                                    .last("LIMIT " + BATCH_SIZE)
                    );

            totalDeleted += deleted;

            if (deleted < BATCH_SIZE) {
                break;
            }
        }

        if (totalDeleted > 0) {
            log.info(
                    "Expired operation logs cleaned, expireBefore={}, totalDeleted={}",
                    expireBefore,
                    totalDeleted
            );
        }
    }
}
