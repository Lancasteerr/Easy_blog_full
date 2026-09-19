package com.febrie.demo_bk.audit.infrastructure.aop;

import com.febrie.demo_bk.audit.application.OperationLogService;
import com.febrie.demo_bk.audit.application.OperationLoger;
import com.febrie.demo_bk.audit.infrastructure.persistence.OperationLog;
import com.febrie.demo_bk.shared.web.request.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
@AllArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final LogParamUtil logParamUtil;

    // 标记需要记录操作日志的方法。
    @Pointcut("@annotation(com.febrie.demo_bk.audit.application.OperationLoger)")
    public void pointcut(){}

    // 在不改变业务返回值和异常的前提下收集操作日志。
    @Around("pointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        long start = System.currentTimeMillis();

        Object result = null;

        OperationLog log = new OperationLog();

        try {

            result = joinPoint.proceed();

            log.setStatus(1);

        } catch (Exception e) {

            log.setStatus(0);
            log.setErrorMsg(e.getMessage());
            throw e;

        } finally {

            long cost = System.currentTimeMillis() - start;
            log.setCostTime(cost);

            handleLog(joinPoint, log);

            operationLogService.save(log);

        }

        return result;
    }

    //解析注解与请求信息
    private void handleLog(ProceedingJoinPoint joinPoint, OperationLog log){
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        Method method = signature.getMethod();

        OperationLoger annotation = method.getAnnotation(OperationLoger.class);

        log.setModule(annotation.module());
        log.setOperation(annotation.type());

        log.setMethod(
                joinPoint.getTarget().getClass().getName()
                        + "." + method.getName()
        );

        HttpServletRequest request = RequestUtil.getRequest();

        log.setRequestUri(request.getRequestURI());

        log.setRequestMethod(request.getMethod());

        log.setIp(RequestUtil.getIpAddress());

        log.setRequestParam(
                logParamUtil.parse(joinPoint.getArgs())
        );

        log.setCreateTime(LocalDateTime.now());

    }
}
