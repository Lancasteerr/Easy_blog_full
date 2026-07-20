package com.febrie.demo_bk.service;

import com.febrie.demo_bk.dao.OperationLogDAO;
import com.febrie.demo_bk.pojo.OperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

public class OperationLogService {

    private final OperationLogDAO operationLogDAO;

    @Autowired
    public OperationLogService(OperationLogDAO operationLogDAO){
        this.operationLogDAO = operationLogDAO;
    }

    @Async
    public void save(OperationLog log){
        operationLogDAO.insert(log);
    }
}
