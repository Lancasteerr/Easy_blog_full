package com.febrie.demo_bk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class RedisService {

    private final RedisTemplate redisTemplate;
    private  final StringRedisTemplate stringRedisTemplate;

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

    //指定value自增 原子自增避免并发问题
    public Long ValueIncrease(String key){
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 序列方法：stringRedisTemplate,opsForHash
     */
    public Long hashStringValueIncrease(String key, String id, Long value) {
        return  stringRedisTemplate.opsForHash()
                .increment(key, id, value);
    }

    public Map<Object, Object> getHashEntries(String key) {
        return stringRedisTemplate.opsForHash().entries(key);
    }

    //指定key设定过期时间
    public void redisSetExpire(String key, long time, TimeUnit unit){
        redisTemplate.expire(key, time, unit);
    }

    public void stringSetExpire(String key, long time, TimeUnit unit) {
        stringRedisTemplate.expire(key, time, unit);
    }

    /**
     * 原子操作 返回之前是否存在
     */
    public boolean setIfAbsent(String key, Object value) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(key, value);

        return Boolean.TRUE.equals(result);
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
