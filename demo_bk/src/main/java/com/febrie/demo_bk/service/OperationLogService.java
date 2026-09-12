package com.febrie.demo_bk.service;

import com.febrie.demo_bk.dao.OperationLogDAO;
import com.febrie.demo_bk.pojo.OperationLog;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class OperationLogService {

    private final OperationLogDAO operationLogDAO;

    @Async
    public void save(OperationLog log){
        operationLogDAO.insert(log);
    }
}
