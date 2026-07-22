package com.febrie.demo_bk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private final RedisTemplate redisTemplate;
    private  final StringRedisTemplate stringRedisTemplate;

    //@Autowired
    public RedisService(RedisTemplate redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, String value, long timeout, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key,value,timeout,unit);
    }

    public void set(String key,String value){
        stringRedisTemplate.opsForValue().set(key,value);
    }

    public String get(String key){
        return stringRedisTemplate.opsForValue().get(key);
    }

    //设置带过期时间的缓存
    public <T> void setObject(String key,T value, long timeout,TimeUnit unit){
        redisTemplate.opsForValue().set(key, value,timeout, unit);
    }

    //设置缓存
    public <T> void setObject(String key,T value){
        redisTemplate.opsForValue().set(key, value);
    }

    //根据key获得缓存
    public <T> T getObject(String key,Class<T> clazz){
        Object object = redisTemplate.opsForValue().get(key);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return object==null?null: mapper.convertValue(object,clazz);
    }

    //根据key删除缓存
    public boolean delete(String key){
        return redisTemplate.delete(key);
    }

    //根据keys集合批量删除缓存
    public Long delete(Set<String> keys){
        return redisTemplate.delete(keys);
    }

    //指定value自增
    public Long valueIncrease(String key){
        return redisTemplate.opsForValue().increment(key);
    }

    //指定key设定过期时间
    public void setExpire(String key, long time, TimeUnit unit){
        redisTemplate.expire(key, time, unit);
    }

    /**
     * @param pattern
     * KEYS是O(N)会阻塞Redis主线程，生产环境禁用
     */
    //根据正则表达式匹配keys获取缓存
    public Set<String> getKeysByPattern(String pattern) {
        return redisTemplate.keys(pattern);
    }
}
