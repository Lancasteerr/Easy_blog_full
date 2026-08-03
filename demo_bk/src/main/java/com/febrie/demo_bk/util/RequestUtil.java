package com.febrie.demo_bk.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestUtil {

    public static HttpServletRequest getRequest(){

        ServletRequestAttributes attributes =
                (ServletRequestAttributes)
                        RequestContextHolder
                                .getRequestAttributes();

        return attributes.getRequest();
    }

    public static String getIpAddress() {

        HttpServletRequest request = getRequest();

        String ip = request.getHeader("X-Forwarded-For");

        if(ip == null || ip.isEmpty()
                || "unknown".equalsIgnoreCase(ip)){

            ip = request.getHeader("X-Real-IP");
        }

        if(ip == null || ip.isEmpty()
                || "unknown".equalsIgnoreCase(ip)){

            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For可能有多个IP
        // 格式:
        // clientIP, proxy1IP, proxy2IP

        if(ip.contains(",")){
            ip = ip.split(",")[0].trim();
        }

        return ip;

    }

}
