package com.febrie.demo_bk.article.infrastructure.cache;

/**
 * 显式表达缓存正常命中、负缓存命中和真正未命中三种状态。
 */
public record CacheValue<T>(CacheState state, T value) {

    public static <T> CacheValue<T> hit(T value) {
        return new CacheValue<>(CacheState.HIT, value);
    }

    public static <T> CacheValue<T> negative() {
        return new CacheValue<>(CacheState.NEGATIVE, null);
    }

    public static <T> CacheValue<T> miss() {
        return new CacheValue<>(CacheState.MISS, null);
    }

    /**
     * 缓存读取状态。
     */
    public enum CacheState {
        HIT,
        NEGATIVE,
        MISS
    }
}
