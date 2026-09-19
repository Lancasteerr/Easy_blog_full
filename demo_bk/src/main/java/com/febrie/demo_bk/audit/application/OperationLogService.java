package com.febrie.demo_bk.audit.application;

import com.febrie.demo_bk.audit.infrastructure.persistence.OperationLog;
import com.febrie.demo_bk.audit.infrastructure.persistence.OperationLogMapper;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步保存操作日志，避免日志数据库写入阻塞业务请求。
 */
@Service
@AllArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Async
    public void save(OperationLog log){
        operationLogMapper.insert(log);
    }
}
