package com.febrie.demo_bk.aop;

import com.febrie.demo_bk.dao.OperationLogDAO;
import com.febrie.demo_bk.annotation.OperationLoger;
import com.febrie.demo_bk.pojo.OperationLog;
import com.febrie.demo_bk.util.LogParamUtil;
import com.febrie.demo_bk.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
public class OperationLogAspect {

    private final OperationLogDAO operationLogDAO;

    @Autowired
    public OperationLogAspect(OperationLogDAO operationLogDAO){
        this.operationLogDAO = operationLogDAO;
    }

    //定义切点(哪个自定义注解需要注入代码)
    @Pointcut("@annotation(com.febrie.demo_bk.annotation.OperationLoger)")
    public void pointcut(){}

    //定义注解执行代码
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

            operationLogDAO.insert(log);
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
                LogParamUtil.parse(joinPoint.getArgs())
        );

        log.setCreateTime(LocalDateTime.now());

    }
}
