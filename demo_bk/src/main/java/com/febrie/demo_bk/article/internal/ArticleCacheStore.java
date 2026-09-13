package com.febrie.demo_bk.article.internal;

import com.febrie.demo_bk.article.ArticleDTO;
import com.febrie.demo_bk.article.ArticleListDTO;
import com.febrie.demo_bk.shared.infrastructure.RedisStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 文章详情、列表索引和列表展示对象的 Redis 访问入口。
 */
@Component
@RequiredArgsConstructor
public class ArticleCacheStore {

    private static final String ARTICLE_DETAIL_KEY_PREFIX = "blog:article:detail:";
    private static final String LATEST_VERSION_KEY = "blog:article:index:latest:version";
    private static final String LATEST_PAGE_KEY_PREFIX = "blog:article:index:latest:";
    private static final String ARTICLE_LIST_KEY_PREFIX = "blog:article:list:";

    private static final long ARTICLE_DETAIL_TTL_DAYS = 30L;
    private static final long LATEST_PAGE_TTL_HOURS = 1L;
    private static final long ARTICLE_LIST_TTL_HOURS = 24L;

    private final RedisStore redisStore;

    public ArticleDTO getArticleDetail(int articleId) {
        return redisStore.getObject(articleDetailKey(articleId), ArticleDTO.class);
    }

    public void cacheArticleDetail(int articleId, ArticleDTO article) {
        redisStore.setObject(
                articleDetailKey(articleId),
                article,
                ARTICLE_DETAIL_TTL_DAYS,
                TimeUnit.DAYS
        );
    }

    public void evictArticleDetail(int articleId) {
        redisStore.delete(articleDetailKey(articleId));
    }

    public long getLatestVersion() {
        redisStore.setIfAbsent(LATEST_VERSION_KEY, 1L);
        Long version = redisStore.getObject(LATEST_VERSION_KEY, Long.class);
        return version == null ? 1L : version;
    }

    public void increaseLatestVersion() {
        redisStore.increment(LATEST_VERSION_KEY);
    }

    public String latestPageKey(long version, int page, int size) {
        return LATEST_PAGE_KEY_PREFIX + version + ":" + page + ":" + size;
    }

    public ArticlePageIndex getLatestPageIndex(String key) {
        return redisStore.getObject(key, ArticlePageIndex.class);
    }

    public void cacheLatestPageIndex(String key, ArticlePageIndex pageIndex) {
        redisStore.setObject(key, pageIndex, LATEST_PAGE_TTL_HOURS, TimeUnit.HOURS);
    }

    public void evictLatestPageIndex(String key) {
        redisStore.delete(key);
    }

    public Map<Integer, ArticleListDTO> getArticleListDtos(List<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = articleIds.stream()
                .map(this::articleListKey)
                .toList();
        List<ArticleListDTO> cachedDtos = redisStore.getObjects(keys, ArticleListDTO.class);
        Map<Integer, ArticleListDTO> dtoById = new LinkedHashMap<>();

        for (int index = 0; index < articleIds.size(); index++) {
            ArticleListDTO dto = cachedDtos.get(index);
            if (dto != null && dto.getId() != null) {
                dtoById.put(dto.getId(), dto);
            }
        }
        return dtoById;
    }

    public void cacheArticleListDtos(Collection<ArticleListDTO> articleListDtos) {
        if (articleListDtos == null || articleListDtos.isEmpty()) {
            return;
        }

        for (ArticleListDTO dto : articleListDtos) {
            if (dto != null && dto.getId() != null) {
                redisStore.setObject(
                        articleListKey(dto.getId()),
                        dto,
                        ARTICLE_LIST_TTL_HOURS,
                        TimeUnit.HOURS
                );
            }
        }
    }

    public void evictArticleListDto(int articleId) {
        redisStore.delete(articleListKey(articleId));
    }

    private String articleDetailKey(int articleId) {
        return ARTICLE_DETAIL_KEY_PREFIX + articleId;
    }

    private String articleListKey(int articleId) {
        return ARTICLE_LIST_KEY_PREFIX + articleId;
    }
}
