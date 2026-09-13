package com.febrie.demo_bk.audit.internal;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OperationLogServiceTest {

    @Test
    void saveShouldUseMapperAndRemainAsync() throws NoSuchMethodException {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        OperationLogService operationLogService =
                new OperationLogService(operationLogMapper);
        OperationLog operationLog = new OperationLog();

        operationLogService.save(operationLog);

        verify(operationLogMapper).insert(operationLog);
        // 防止后续整理时误删上一提交引入的异步边界。
        assertThat(OperationLogService.class
                .getDeclaredMethod("save", OperationLog.class)
                .isAnnotationPresent(Async.class)).isTrue();
    }
}
