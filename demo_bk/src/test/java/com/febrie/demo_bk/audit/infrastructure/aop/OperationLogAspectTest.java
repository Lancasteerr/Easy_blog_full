package com.febrie.demo_bk.audit.infrastructure.aop;

import com.febrie.demo_bk.audit.application.OperationLogService;
import com.febrie.demo_bk.audit.application.OperationLoger;
import com.febrie.demo_bk.audit.infrastructure.persistence.OperationLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogAspectTest {

    private OperationLogService operationLogService;
    private ProceedingJoinPoint joinPoint;
    private OperationLogAspect aspect;

    @BeforeEach
    void setUp() throws Exception {
        operationLogService = mock(OperationLogService.class);
        LogParamUtil logParamUtil = mock(LogParamUtil.class);
        joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = LoggedTarget.class.getDeclaredMethod("execute");

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new LoggedTarget());
        when(joinPoint.getArgs()).thenReturn(new Object[]{"parameter"});
        when(logParamUtil.parse(joinPoint.getArgs())).thenReturn("[]");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        aspect = new OperationLogAspect(operationLogService, logParamUtil);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void successfulRequestShouldDelegateCompletedLogToAsyncService() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.around(joinPoint)).isEqualTo("ok");

        OperationLog operationLog = captureSavedLog();
        assertThat(operationLog.getStatus()).isEqualTo(1);
        assertThat(operationLog.getModule()).isEqualTo("测试模块");
        assertThat(operationLog.getOperation()).isEqualTo("执行");
        assertThat(operationLog.getRequestUri()).isEqualTo("/api/test");
        assertThat(operationLog.getRequestMethod()).isEqualTo("POST");
    }

    @Test
    void failedRequestShouldSaveFailureLogAndRethrowOriginalException() throws Throwable {
        IllegalStateException failure = new IllegalStateException("业务失败");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.around(joinPoint)).isSameAs(failure);

        OperationLog operationLog = captureSavedLog();
        assertThat(operationLog.getStatus()).isZero();
        assertThat(operationLog.getErrorMsg()).isEqualTo("业务失败");
    }

    private OperationLog captureSavedLog() {
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(logCaptor.capture());
        return logCaptor.getValue();
    }

    private static class LoggedTarget {

        @OperationLoger(module = "测试模块", type = "执行")
        public String execute() {
            return "ok";
        }
    }
}
