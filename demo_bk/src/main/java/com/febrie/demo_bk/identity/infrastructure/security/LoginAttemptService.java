package com.febrie.demo_bk.identity.infrastructure.security;

import com.febrie.demo_bk.shared.infrastructure.redis.RedisStore;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class LoginAttemptService {

    private final RedisStore redisStore;

     public LoginAttemptService(RedisStore redisStore){
         this.redisStore = redisStore;
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
                redisStore.getObject(userKey, Integer.class);

        Integer ipCount =
                redisStore.getObject(ipKey, Integer.class);

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

        Long userCount = redisStore.increment(userKey);

        Long ipCount = redisStore.increment(ipKey);

        if(userCount == 1) {
            redisStore.expire(
                    userKey,
                    10,
                    TimeUnit.MINUTES
            );
        }

        if(ipCount == 1){
            redisStore.expire(
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

        redisStore.delete("login:fail:user:" + username);

        redisStore.delete("login:fail:ip:" + ip);

    }

}
