package com.febrie.demo_bk.service;

import com.febrie.demo_bk.dto.ArticleListDTO;
import com.febrie.demo_bk.dto.ArticlePageIndex;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 文章列表索引与浏览量排行榜的 Redis 访问入口。
 *
 * <p>最新列表缓存页面 ID，浏览量榜缓存排序 ID；两者都通过文章 DTO 缓存补全展示数据。</p>
 */
@Service
@AllArgsConstructor
public class ArticleIndexRedisService {

    private static final String LATEST_VERSION_KEY =
            "blog:article:index:latest:version";
    private static final String LATEST_PAGE_KEY_PREFIX =
            "blog:article:index:latest:";
    private static final String ARTICLE_LIST_KEY_PREFIX =
            "blog:article:list:";
    private static final String VIEW_RANK_KEY =
            "blog:article:rank:view:all";

    private static final long LATEST_PAGE_TTL_HOURS = 1L;
    private static final long ARTICLE_LIST_TTL_HOURS = 24L;
    private static final long DAILY_VIEW_TTL_SECONDS =
            TimeUnit.DAYS.toSeconds(2L);

    /**
     * 同一 Redis 内完成每日 PV 与排行榜累计值更新，避免两个命令部分成功。
     */
    private static final DefaultRedisScript<Long> INCREMENT_VIEW_SCRIPT =
            new DefaultRedisScript<>("""
                    local currentScore = redis.call('ZSCORE', KEYS[2], ARGV[2])
                    if not currentScore then
                      redis.call('ZADD', KEYS[2], tonumber(ARGV[3]), ARGV[2])
                    end

                    redis.call('HINCRBY', KEYS[1], ARGV[1], 1)

                    if redis.call('TTL', KEYS[1]) < 0 then
                      redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
                    end

                    return tonumber(redis.call('ZINCRBY', KEYS[2], 1, ARGV[2]))
                    """, Long.class);

    /**
     * 只把异常偏低的分数提高到 MySQL 已持久化值，绝不覆盖更高的实时值。
     */
    private static final DefaultRedisScript<Long> PROMOTE_RANK_SCORE_SCRIPT =
            new DefaultRedisScript<>("""
                    local currentScore = redis.call('ZSCORE', KEYS[1], ARGV[1])
                    if not currentScore or tonumber(currentScore) < tonumber(ARGV[2]) then
                      redis.call('ZADD', KEYS[1], tonumber(ARGV[2]), ARGV[1])
                      return 1
                    end
                    return 0
                    """, Long.class);

    private final RedisService redisService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Redis 并列分数按 member 字典序排序，十位补零后可得到数值 ID 倒序。
     */
    public static String member(int articleId) {
        if (articleId <= 0) {
            throw new IllegalArgumentException("文章ID不合法");
        }
        return String.format(Locale.ROOT, "%010d", articleId);
    }

    public static int articleId(String member) {
        return Integer.parseInt(member);
    }

    public long getLatestVersion() {
        redisService.setIfAbsent(LATEST_VERSION_KEY, 1L);
        Long version = redisService.getObject(LATEST_VERSION_KEY, Long.class);
        return version == null ? 1L : version;
    }

    public void increaseLatestVersion() {
        redisService.ValueIncrease(LATEST_VERSION_KEY);
    }

    public String latestPageKey(long version, int page, int size) {
        return LATEST_PAGE_KEY_PREFIX + version + ":" + page + ":" + size;
    }

    public ArticlePageIndex getLatestPageIndex(String key) {
        return redisService.getObject(key, ArticlePageIndex.class);
    }

    public void cacheLatestPageIndex(String key, ArticlePageIndex pageIndex) {
        redisService.setObject(key, pageIndex, LATEST_PAGE_TTL_HOURS, TimeUnit.HOURS);
    }

    public void evictLatestPageIndex(String key) {
        redisService.delete(key);
    }

    public Map<Integer, ArticleListDTO> getArticleListDtos(List<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = articleIds.stream()
                .map(this::articleListKey)
                .toList();
        List<ArticleListDTO> cachedDtos = redisService.getObjects(keys, ArticleListDTO.class);
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
                redisService.setObject(
                        articleListKey(dto.getId()),
                        dto,
                        ARTICLE_LIST_TTL_HOURS,
                        TimeUnit.HOURS
                );
            }
        }
    }

    public void evictArticleListDto(int articleId) {
        redisService.delete(articleListKey(articleId));
    }

    public Long incrementView(int articleId,
                              String dailyViewKey,
                              long persistedViewCount) {
        long baseScore = Math.max(0L, persistedViewCount);
        return stringRedisTemplate.execute(
                INCREMENT_VIEW_SCRIPT,
                List.of(dailyViewKey, VIEW_RANK_KEY),
                String.valueOf(articleId),
                member(articleId),
                String.valueOf(baseScore),
                String.valueOf(DAILY_VIEW_TTL_SECONDS)
        );
    }

    public void addRankMember(int articleId, long viewCount) {
        stringRedisTemplate.opsForZSet().add(
                VIEW_RANK_KEY,
                member(articleId),
                Math.max(0L, viewCount)
        );
    }

    public void removeRankMember(int articleId) {
        stringRedisTemplate.opsForZSet().remove(VIEW_RANK_KEY, member(articleId));
    }

    public void removeRankMembers(Collection<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return;
        }
        Object[] members = articleIds.stream()
                .map(ArticleIndexRedisService::member)
                .toArray();
        stringRedisTemplate.opsForZSet().remove(VIEW_RANK_KEY, members);
    }

    public List<Long> getViewScores(List<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Object[] members = articleIds.stream()
                .map(ArticleIndexRedisService::member)
                .toArray();
        List<Double> scores = stringRedisTemplate.opsForZSet().score(VIEW_RANK_KEY, members);
        if (scores == null) {
            return Collections.nCopies(articleIds.size(), null);
        }

        List<Long> result = new ArrayList<>(scores.size());
        for (Double score : scores) {
            result.add(score == null ? null : score.longValue());
        }
        return result;
    }

    public RankPage getRankPage(int page, int size) {
        long start = (long) (page - 1) * size;
        long end = start + size - 1;
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(
                        VIEW_RANK_KEY,
                        start,
                        end
                );
        Long total = stringRedisTemplate.opsForZSet().zCard(VIEW_RANK_KEY);
        if (tuples == null || tuples.isEmpty()) {
            return new RankPage(Collections.emptyList(), Collections.emptyMap(), total == null ? 0L : total);
        }

        List<Integer> ids = new ArrayList<>(tuples.size());
        Map<Integer, Long> scores = new LinkedHashMap<>();
        for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() == null) {
                continue;
            }
            int id = articleId(tuple.getValue());
            ids.add(id);
            scores.put(id, tuple.getScore() == null ? 0L : tuple.getScore().longValue());
        }
        return new RankPage(ids, scores, total == null ? 0L : total);
    }

    public boolean hasRankIndex() {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(VIEW_RANK_KEY));
    }

    public Set<Integer> getAllRankArticleIds() {
        Set<String> members = stringRedisTemplate.opsForZSet().range(VIEW_RANK_KEY, 0, -1);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> articleIds = new LinkedHashSet<>();
        for (String rankMember : members) {
            articleIds.add(articleId(rankMember));
        }
        return articleIds;
    }

    public void promoteRankScore(int articleId, long persistedViewCount) {
        stringRedisTemplate.execute(
                PROMOTE_RANK_SCORE_SCRIPT,
                List.of(VIEW_RANK_KEY),
                member(articleId),
                String.valueOf(Math.max(0L, persistedViewCount))
        );
    }

    private String articleListKey(int articleId) {
        return ARTICLE_LIST_KEY_PREFIX + articleId;
    }

    /**
     * ZSet 分页结果，score 即可直接用于响应中的实时浏览量。
     */
    public record RankPage(List<Integer> ids,
                           Map<Integer, Long> scores,
                           long totalElements) {
    }
}
