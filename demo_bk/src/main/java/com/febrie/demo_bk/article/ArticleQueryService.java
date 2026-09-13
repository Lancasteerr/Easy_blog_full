package com.febrie.demo_bk.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.article.internal.ArticleCacheStore;
import com.febrie.demo_bk.article.internal.ArticleMapper;
import com.febrie.demo_bk.article.internal.ArticlePageIndex;
import com.febrie.demo_bk.article.internal.ArticleViewService;
import com.febrie.demo_bk.article.internal.ArticleViewStore;
import com.febrie.demo_bk.article.internal.BlogArticle;
import com.febrie.demo_bk.shared.web.PageResult;
import com.febrie.demo_bk.shared.web.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章读取用例入口，统一处理缓存旁路、分页和 Redis 故障降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ArticleMapper articleMapper;
    private final ArticleCacheStore articleCacheStore;
    private final ArticleViewStore articleViewStore;
    private final ArticleViewService articleViewService;

    /**
     * 优先读取详情缓存；缓存不可用时回退到 MySQL。
     */
    public ArticleDTO findById(int articleId) {
        validateArticleId(articleId);

        ArticleDTO cachedArticle = null;
        try {
            cachedArticle = articleCacheStore.getArticleDetail(articleId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Read article detail cache failed, fallback to MySQL, articleId={}",
                    articleId,
                    exception
            );
        }

        if (cachedArticle == null) {
            BlogArticle article = articleMapper.selectById(articleId);
            if (article == null) {
                throw new ResourceNotFoundException("文章不存在");
            }

            ArticleDTO articleDTO = BlogArticle.toDTO(article);
            try {
                articleCacheStore.cacheArticleDetail(articleId, articleDTO);
            } catch (RuntimeException exception) {
                log.warn("Cache article detail failed, articleId={}", articleId, exception);
            }
            applyRealtimeViewCount(
                    articleDTO,
                    articleViewService.recordView((long) articleId, articleDTO.getViewCount())
            );
            return articleDTO;
        }

        applyRealtimeViewCount(
                cachedArticle,
                articleViewService.recordView((long) articleId, cachedArticle.getViewCount())
        );
        return cachedArticle;
    }

    public PageResult<ArticleListDTO> findPage(int page,
                                               int size,
                                               String sort) {
        validatePageParams(page, size);
        return "viewCountDesc".equals(normalizeSort(sort))
                ? getViewRankArticleList(page, size, true)
                : getLatestArticleList(page, size);
    }

    /**
     * 最新列表只缓存分页 ID，排序、总数与分页仍由 MySQL 完成。
     */
    private PageResult<ArticleListDTO> getLatestArticleList(int page, int size) {
        ArticlePageIndex pageIndex = null;
        String cacheKey = null;

        try {
            long version = articleCacheStore.getLatestVersion();
            cacheKey = articleCacheStore.latestPageKey(version, page, size);
            pageIndex = articleCacheStore.getLatestPageIndex(cacheKey);
        } catch (RuntimeException exception) {
            log.warn("Read latest article index cache failed, fallback to MySQL", exception);
        }

        if (pageIndex == null) {
            pageIndex = queryLatestPageIndex(page, size);
            if (cacheKey != null) {
                try {
                    articleCacheStore.cacheLatestPageIndex(cacheKey, pageIndex);
                } catch (RuntimeException exception) {
                    log.warn("Cache latest article index failed", exception);
                }
            }
        }

        validatePageBounds(pageIndex.getTotalElements(), page, size);
        HydratedArticleList hydrated = hydrateArticleListDtos(pageIndex.getIds(), null);

        // 删除操作与旧分页索引并发时，回库重建一次失效页面。
        if (!hydrated.missingIds().isEmpty() && cacheKey != null) {
            try {
                articleCacheStore.evictLatestPageIndex(cacheKey);
            } catch (RuntimeException exception) {
                log.warn("Evict stale latest article index failed", exception);
            }
            pageIndex = queryLatestPageIndex(page, size);
            hydrated = hydrateArticleListDtos(pageIndex.getIds(), null);
        }

        return new PageResult<>(
                hydrated.dtos(),
                pageIndex.getTotalElements(),
                pageIndex.getNumber()
        );
    }

    /**
     * 浏览量榜由 ZSet 排序；Redis 不可用时使用 MySQL 快照降级。
     */
    private PageResult<ArticleListDTO> getViewRankArticleList(int page,
                                                               int size,
                                                               boolean allowReadRepair) {
        try {
            if (articleViewStore.hasRankIndex()) {
                ArticleViewStore.RankPage rankPage = articleViewStore.getRankPage(page, size);
                validatePageBounds(rankPage.totalElements(), page, size);
                HydratedArticleList hydrated = hydrateArticleListDtos(
                        rankPage.ids(),
                        rankPage.scores()
                );

                if (!hydrated.missingIds().isEmpty()) {
                    articleViewStore.removeRankMembers(hydrated.missingIds());
                    if (allowReadRepair) {
                        return getViewRankArticleList(page, size, false);
                    }
                }

                return new PageResult<>(
                        hydrated.dtos(),
                        rankPage.totalElements(),
                        page
                );
            }
        } catch (RuntimeException exception) {
            log.warn("Read article view rank failed, fallback to MySQL", exception);
        }

        return getMysqlViewCountArticleList(page, size);
    }

    private ArticlePageIndex queryLatestPageIndex(int page, int size) {
        LambdaQueryWrapper<BlogArticle> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .orderByDesc(BlogArticle::getArticleDate)
                .orderByDesc(BlogArticle::getId);
        Page<Integer> result = articleMapper.selectArticleIdPage(
                new Page<>(page, size),
                queryWrapper
        );
        return new ArticlePageIndex(result.getRecords(), result.getTotal(), page, size);
    }

    private PageResult<ArticleListDTO> getMysqlViewCountArticleList(int page, int size) {
        LambdaQueryWrapper<BlogArticle> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .orderByDesc(BlogArticle::getViewCount)
                .orderByDesc(BlogArticle::getId);
        Page<Integer> result = articleMapper.selectArticleIdPage(
                new Page<>(page, size),
                queryWrapper
        );
        validatePageBounds(result.getTotal(), page, size);
        HydratedArticleList hydrated = hydrateArticleListDtos(result.getRecords(), null);
        return new PageResult<>(hydrated.dtos(), result.getTotal(), page);
    }

    /**
     * 批量读取 DTO 缓存，仅对未命中 ID 回表并按原 ID 顺序恢复结果。
     */
    private HydratedArticleList hydrateArticleListDtos(List<Integer> articleIds,
                                                        Map<Integer, Long> rankScores) {
        if (articleIds == null || articleIds.isEmpty()) {
            return new HydratedArticleList(new ArrayList<>(), Collections.emptySet());
        }

        Map<Integer, ArticleListDTO> dtoById = new HashMap<>();
        boolean cacheAvailable = true;
        try {
            dtoById.putAll(articleCacheStore.getArticleListDtos(articleIds));
        } catch (RuntimeException exception) {
            cacheAvailable = false;
            log.warn("Read article DTO cache failed, fallback to MySQL", exception);
        }

        List<Integer> missingIds = articleIds.stream()
                .filter(articleId -> !dtoById.containsKey(articleId))
                .distinct()
                .toList();
        if (!missingIds.isEmpty()) {
            List<ArticleListDTO> databaseDtos = articleMapper.selectArticleListByIds(missingIds);
            normalizeArticleListDtos(databaseDtos);
            for (ArticleListDTO dto : databaseDtos) {
                if (dto.getId() != null) {
                    dtoById.put(dto.getId(), dto);
                }
            }

            if (cacheAvailable) {
                try {
                    articleCacheStore.cacheArticleListDtos(databaseDtos);
                } catch (RuntimeException exception) {
                    log.warn("Cache article DTOs failed", exception);
                }
            }
        }

        Map<Integer, Long> realtimeScores = rankScores;
        if (realtimeScores == null) {
            try {
                List<Long> scores = articleViewStore.getViewScores(articleIds);
                realtimeScores = new HashMap<>();
                for (int index = 0; index < articleIds.size(); index++) {
                    realtimeScores.put(articleIds.get(index), scores.get(index));
                }
            } catch (RuntimeException exception) {
                // 排行榜故障时继续返回 DTO 内的 MySQL 浏览量快照。
                realtimeScores = Collections.emptyMap();
                log.warn("Read realtime article view counts failed", exception);
            }
        }

        List<ArticleListDTO> result = new ArrayList<>(articleIds.size());
        Set<Integer> invalidIds = new LinkedHashSet<>();
        for (Integer articleId : articleIds) {
            ArticleListDTO dto = dtoById.get(articleId);
            if (dto == null) {
                invalidIds.add(articleId);
                continue;
            }

            ArticleListDTO responseDto = copyArticleListDto(dto);
            Long realtimeScore = realtimeScores.get(articleId);
            if (realtimeScore != null) {
                responseDto.setViewCount(realtimeScore);
            }
            result.add(responseDto);
        }
        return new HydratedArticleList(result, invalidIds);
    }

    private void normalizeArticleListDtos(Collection<ArticleListDTO> articleListDtos) {
        if (articleListDtos == null) {
            return;
        }

        articleListDtos.forEach(articleListDTO -> {
            String coverUrl = articleListDTO.getCoverURL();
            if (coverUrl == null || coverUrl.isBlank()) {
                articleListDTO.setCoverURL(null);
            }
        });
    }

    private ArticleListDTO copyArticleListDto(ArticleListDTO source) {
        ArticleListDTO target = new ArticleListDTO();
        target.setId(source.getId());
        target.setArticleTitle(source.getArticleTitle());
        target.setArticleAbstract(source.getArticleAbstract());
        target.setArticleDate(source.getArticleDate());
        target.setViewCount(source.getViewCount());
        target.setArticleCover(source.getArticleCover());
        target.setCoverURL(source.getCoverURL());
        return target;
    }

    private void validateArticleId(int articleId) {
        if (articleId <= 0) {
            throw new IllegalArgumentException("文章ID不合法");
        }
    }

    private void validatePageParams(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("页码不能小于1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("每页数量不能小于1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页数量不能超过" + MAX_PAGE_SIZE);
        }
    }

    private void validatePageBounds(long totalElements,
                                    int page,
                                    int size) {
        if (totalElements <= 0) {
            return;
        }

        long maxPage = (totalElements + size - 1) / size;
        if (page > maxPage) {
            throw new ResourceNotFoundException("文章列表页不存在");
        }
    }

    private String normalizeSort(String sort) {
        return "viewCountDesc".equals(sort) ? sort : "default";
    }

    private void applyRealtimeViewCount(ArticleDTO articleDTO,
                                        Long realtimeViewCount) {
        if (articleDTO != null && realtimeViewCount != null) {
            articleDTO.setViewCount(realtimeViewCount);
        }
    }

    private record HydratedArticleList(List<ArticleListDTO> dtos,
                                       Set<Integer> missingIds) {
    }
}
