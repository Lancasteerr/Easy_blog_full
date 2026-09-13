package com.febrie.demo_bk.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class RedisStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private  final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

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

        // 复用Spring统一配置的ObjectMapper，避免不同缓存读取路径出现序列化差异。
        return object==null?null: objectMapper.convertValue(object,clazz);
    }

    /**
     * 批量读取同类型对象缓存，并保持传入 Key 的顺序。
     */
    public <T> List<T> getObjects(List<String> keys, Class<T> clazz) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> objects = redisTemplate.opsForValue().multiGet(keys);
        if (objects == null) {
            return Collections.nCopies(keys.size(), null);
        }

        List<T> results = new ArrayList<>(objects.size());
        for (Object object : objects) {
            results.add(object == null ? null : objectMapper.convertValue(object, clazz));
        }
        return results;
    }

    //根据key删除缓存
    public boolean delete(String key){
        return redisTemplate.delete(key);
    }

    //根据keys集合批量删除缓存
    public Long delete(Set<String> keys){
        return redisTemplate.delete(keys);
    }

    // 指定 value 原子自增，避免并发请求丢失计数。
    public Long increment(String key){
        return redisTemplate.opsForValue().increment(key);
    }

    public Map<Object, Object> getHashEntries(String key) {
        return stringRedisTemplate.opsForHash().entries(key);
    }

    // 为指定 Key 设置过期时间。
    public void expire(String key, long time, TimeUnit unit){
        redisTemplate.expire(key, time, unit);
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
