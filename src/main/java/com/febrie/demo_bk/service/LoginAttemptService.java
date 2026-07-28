package com.febrie.demo_bk.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class LoginAttemptService {

    private final RedisService redisService;

     public LoginAttemptService(RedisService redisService){
         this.redisService = redisService;
     }

     //最大错误次数
     private static final int MAX_FAIL = 5;

    /**
     * 是否允许登录
     */
    public boolean allowLogin(
            String username,
            String ip
    ){
        String userKey =
                "login:fail:user:" + username;

        String ipKey =
                "login:fail:ip:" + ip;

        Integer userCount =
                redisService.getObject(userKey, Integer.class);

        Integer ipCount =
                redisService.getObject(ipKey, Integer.class);

        return (userCount == null || userCount <= MAX_FAIL)
                &&
                (ipCount == null || ipCount <= MAX_FAIL);
    }

    /**
     * 登录失败
     */

    public void recordFail(
            String username,
            String ip
    ){
        String userKey =
                "login:fail:user:" + username;

        String ipKey =
                "login:fail:ip:" + ip;

        Long userCount = redisService.ValueIncrease(userKey);

        Long ipCount = redisService.ValueIncrease(ipKey);

        if(userCount == 1) {
            redisService.redisSetExpire(
                    userKey,
                    10,
                    TimeUnit.MINUTES
            );
        }

        if(ipCount == 1){
            redisService.redisSetExpire(
                    ipKey,
                    10,
                    TimeUnit.MINUTES
            );
        }
    }

    /**
     * 登录成功
     */
    public void clear(
            String username,
            String ip
    ){

        redisService.delete("login:fail:user:" + username);

        redisService.delete("login:fail:ip:" + ip);

    }

}
