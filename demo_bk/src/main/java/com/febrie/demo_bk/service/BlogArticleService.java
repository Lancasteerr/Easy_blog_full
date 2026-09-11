package com.febrie.demo_bk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.febrie.demo_bk.dao.BlogArticleDAO;
import com.febrie.demo_bk.dto.ArticleDTO;
import com.febrie.demo_bk.dto.ArticleListDTO;
import com.febrie.demo_bk.dto.ArticlePageIndex;
import com.febrie.demo_bk.exception.ResourceNotFoundException;
import com.febrie.demo_bk.pojo.BlogArticle;
import com.febrie.demo_bk.pojo.FileObject;
import com.febrie.demo_bk.result.PageResult;
import com.febrie.demo_bk.service.pv.ArticleViewServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@AllArgsConstructor
public class BlogArticleService {
    private static final Pattern DATA_FILE_ID_PATTERN =
            Pattern.compile("data-file-id=[\"'](\\d+)[\"']");

    private static final int MAX_PAGE_SIZE = 50;

    private BlogArticleDAO blogArticleDAO;

    private RedisService redisService;

    private FileService fileService;

    private ArticleViewServiceImpl articleViewService;

    private ArticleIndexRedisService articleIndexRedisService;

    private ObjectMapper objectMapper;

    /**
     * 文章详细缓存 Key
     */
    private static final String ARTICLE_DETAIL_CACHE_KEY = "blog:article:detail:";

    /**
     * 更新文章
     * 先改数据库，再删Redis
     */
    //@OperationLoger(module = "文章",type = "增加或修改")
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdate(ArticleDTO articleDTO) {
        if (articleDTO == null) {
            throw new IllegalArgumentException("文章内容不能为空");
        }

        BlogArticle oldArticle = null;
        if (articleDTO.getId() != null) {
            if (articleDTO.getId() <= 0) {
                throw new IllegalArgumentException("文章ID不合法");
            }

            oldArticle = blogArticleDAO.selectById(articleDTO.getId());
            if (oldArticle == null) {
                throw new ResourceNotFoundException("文章不存在");
            }
        }

        setCoverObjectInfo(articleDTO);

        Set<Long> oldFileIds =
                collectArticleFileIds(oldArticle);

        Set<Long> newFileIds =
                collectArticleFileIds(articleDTO);

        fileService.validateImageFiles(newFileIds);

        BlogArticle blogArticle = BlogArticle.toPojo(articleDTO);
        boolean isNewArticle = articleDTO.getId() == null;
        if (isNewArticle) {
            // 浏览量只能由浏览统计任务维护，新增文章不能信任前端传入的值。
            blogArticle.setViewCount(0L);
            blogArticleDAO.insert(blogArticle);
            articleDTO.setId(blogArticle.getId());
        } else {
            // 管理端编辑不得覆盖已累计的浏览量。
            blogArticle.setViewCount(oldArticle.getViewCount());
            blogArticleDAO.updateById(blogArticle);
        }

        fileService.markBound(newFileIds);
        releaseRemovedFiles(oldFileIds, newFileIds);

        int articleId = articleDTO.getId();
        afterCommitBestEffort(() -> {
            redisService.delete(ARTICLE_DETAIL_CACHE_KEY + articleId);
            articleIndexRedisService.evictArticleListDto(articleId);
            // 当前产品规则中编辑会更新时间，因此新增、编辑都会改变最新列表顺序。
            articleIndexRedisService.increaseLatestVersion();
            if (isNewArticle) {
                articleIndexRedisService.addRankMember(articleId, 0L);
            }
        }, "同步文章列表缓存与排行榜失败");
    }

    /**
     * 无缓存则查库后写入缓存，文章不存在时返回 404，避免前端拿到 200 + null。
     */
    public ArticleDTO findById (int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("文章ID不合法");
        }

        String key = ARTICLE_DETAIL_CACHE_KEY + id;

        ArticleDTO cache = null;
        try {
            cache = redisService.getObject(key, ArticleDTO.class);
        } catch (RuntimeException exception) {
            // 详情缓存不可用时仍从 MySQL 返回文章，浏览量记录也会自行降级。
            log.warn("Read article detail cache failed, fallback to MySQL, articleId={}", id, exception);
        }
        if(cache==null){
            BlogArticle article =
                    blogArticleDAO.selectById(id);

            if(article == null) {
                throw new ResourceNotFoundException("文章不存在");
            }

            ArticleDTO dto = BlogArticle.toDTO(article);
            try {
                redisService.setObject(key, dto, 30, TimeUnit.DAYS);
            } catch (RuntimeException exception) {
                log.warn("Cache article detail failed, articleId={}", id, exception);
            }
            applyRealtimeViewCount(dto, articleViewService.recordView((long) id, dto.getViewCount()));
            return dto;
        }

        applyRealtimeViewCount(cache, articleViewService.recordView((long) id, cache.getViewCount()));
        return cache;
    }

    //删除文章
    @Transactional(rollbackFor = Exception.class)
    public void delete(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("文章ID不合法");
        }

        BlogArticle article = blogArticleDAO.selectById(id);

        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }

        Set<Long> articleFileIds =
                collectArticleFileIds(article);

        blogArticleDAO.deleteById(id);
        fileService.markTemp(articleFileIds);
        deleteTempFilesAfterCommit(articleFileIds);
        afterCommitBestEffort(() -> {
            redisService.delete(ARTICLE_DETAIL_CACHE_KEY + id);
            articleIndexRedisService.evictArticleListDto(id);
            articleIndexRedisService.increaseLatestVersion();
            articleIndexRedisService.removeRankMember(id);
        }, "删除文章后的缓存与排行榜清理失败");
    }

    private void setCoverObjectInfo(ArticleDTO articleDTO) {
        if (articleDTO == null) {
            return;
        }

        Long coverId =
                articleDTO.getArticleCover();

        if (coverId == null) {
            articleDTO.setCoverObjectUrl(null);
            return;
        }

        FileObject coverObject =
                fileService.getImageObject(coverId);

        articleDTO.setCoverObjectUrl(
                coverObject.getUrl()
        );
    }

    private Set<Long> collectArticleFileIds(BlogArticle article) {
        if (article == null) {
            return new HashSet<>();
        }

        Set<Long> fileIds =
                collectImageIds(
                        article.getArticleContentJson(),
                        article.getArticleContentHtml()
                );

        if (article.getArticleCover() != null) {
            fileIds.add(article.getArticleCover());
        }

        return fileIds;
    }

    private Set<Long> collectArticleFileIds(ArticleDTO article) {
        if (article == null) {
            return new HashSet<>();
        }

        Set<Long> fileIds =
                collectImageIds(
                        article.getArticleContentJson(),
                        article.getArticleContentHtml()
                );

        if (article.getArticleCover() != null) {
            fileIds.add(article.getArticleCover());
        }

        return fileIds;
    }

    private void releaseRemovedFiles(Set<Long> oldFileIds,
                                     Set<Long> newFileIds) {
        if (oldFileIds == null || oldFileIds.isEmpty()) {
            return;
        }

        oldFileIds.removeAll(newFileIds);
        fileService.markTemp(oldFileIds);
        deleteTempFilesAfterCommit(oldFileIds);
    }

    private void deleteTempFilesAfterCommit(Set<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileService.deleteTempFiles(fileIds);
            return;
        }

        Set<Long> idsToDelete =
                new HashSet<>(fileIds);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        fileService.deleteTempFiles(idsToDelete);
                    }
                }
        );
    }

    private Set<Long> collectImageIds(String articleContentJson, String articleContentHtml) {
        Set<Long> imageIds = new HashSet<>();

        if (articleContentJson != null && !articleContentJson.isBlank()) {
            try {
                collectImageIds(objectMapper.readTree(articleContentJson), imageIds);
            } catch (Exception ignored) {
            }
        }

        if (articleContentHtml != null && !articleContentHtml.isBlank()) {
            Matcher matcher = DATA_FILE_ID_PATTERN.matcher(articleContentHtml);
            while (matcher.find()) {
                imageIds.add(Long.parseLong(matcher.group(1)));
            }
        }

        return imageIds;
    }

    private void collectImageIds(JsonNode node, Set<Long> imageIds) {
        if (node == null) {
            return;
        }

        if ("image".equals(node.path("type").asText())) {
            JsonNode fileIdNode = node.path("attrs").path("fileId");
            Long fileId = parseFileId(fileIdNode);
            if (fileId != null) {
                imageIds.add(fileId);
            }
        }

        JsonNode content = node.path("content");
        if (!content.isArray()) {
            return;
        }

        for (JsonNode child : content) {
            collectImageIds(child, imageIds);
        }
    }

    private Long parseFileId(JsonNode fileIdNode) {
        if (fileIdNode == null || fileIdNode.isNull()) {
            return null;
        }

        if (fileIdNode.isIntegralNumber()) {
            return fileIdNode.asLong();
        }

        if (fileIdNode.isTextual()) {
            try {
                return Long.parseLong(fileIdNode.asText());
            } catch (NumberFormatException ignored) {
                // 非数字字符串不是有效文件ID，保持原有容错逻辑忽略即可。
            }
        }

        return null;
    }

    public PageResult getArticleList(int page, int size) {
        return getArticleList(page, size, null);
    }

    public PageResult getArticleList(int page, int size, String sort) {
        validateArticlePageParams(page, size);
        String normalizedSort = normalizeArticleListSort(sort);
        return "viewCountDesc".equals(normalizedSort)
                ? getViewRankArticleList(page, size, true)
                : getLatestArticleList(page, size);
    }

    /**
     * 最新列表只缓存分页 ID；排序、总数与分页仍由 MySQL 完成。
     */
    private PageResult getLatestArticleList(int page, int size) {
        ArticlePageIndex pageIndex = null;
        String cacheKey = null;

        try {
            long version = articleIndexRedisService.getLatestVersion();
            cacheKey = articleIndexRedisService.latestPageKey(version, page, size);
            pageIndex = articleIndexRedisService.getLatestPageIndex(cacheKey);
        } catch (RuntimeException exception) {
            log.warn("Read latest article index cache failed, fallback to MySQL", exception);
        }

        if (pageIndex == null) {
            pageIndex = queryLatestPageIndex(page, size);
            if (cacheKey != null) {
                try {
                    articleIndexRedisService.cacheLatestPageIndex(cacheKey, pageIndex);
                } catch (RuntimeException exception) {
                    log.warn("Cache latest article index failed", exception);
                }
            }
        }

        validateArticleListPageBounds(pageIndex.getTotalElements(), page, size);
        HydratedArticleList hydrated = hydrateArticleListDtos(pageIndex.getIds(), null);

        // 版本失效与删除操作并发时，页面索引可能短暂引用无效 ID，删掉后回库重建一次。
        if (!hydrated.missingIds().isEmpty() && cacheKey != null) {
            try {
                articleIndexRedisService.evictLatestPageIndex(cacheKey);
            } catch (RuntimeException exception) {
                log.warn("Evict stale latest article index failed", exception);
            }
            pageIndex = queryLatestPageIndex(page, size);
            hydrated = hydrateArticleListDtos(pageIndex.getIds(), null);
        }

        return new PageResult(
                hydrated.dtos(),
                pageIndex.getTotalElements(),
                pageIndex.getNumber()
        );
    }

    /**
     * 浏览量榜由 ZSet 负责排序；Redis 不可用或索引未建立时使用 MySQL 快照降级。
     */
    private PageResult getViewRankArticleList(int page,
                                               int size,
                                               boolean allowReadRepair) {
        try {
            if (articleIndexRedisService.hasRankIndex()) {
                ArticleIndexRedisService.RankPage rankPage =
                        articleIndexRedisService.getRankPage(page, size);
                validateArticleListPageBounds(rankPage.totalElements(), page, size);
                HydratedArticleList hydrated = hydrateArticleListDtos(
                        rankPage.ids(),
                        rankPage.scores()
                );

                if (!hydrated.missingIds().isEmpty()) {
                    articleIndexRedisService.removeRankMembers(hydrated.missingIds());
                    // 删除无效 member 后仅重查一次，避免异常数据导致递归循环。
                    if (allowReadRepair) {
                        return getViewRankArticleList(page, size, false);
                    }
                }

                return new PageResult(
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
        Page<Integer> result = blogArticleDAO.selectArticleIdPage(
                new Page<>(page, size),
                queryWrapper
        );
        return new ArticlePageIndex(result.getRecords(), result.getTotal(), page, size);
    }

    private PageResult getMysqlViewCountArticleList(int page, int size) {
        LambdaQueryWrapper<BlogArticle> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .orderByDesc(BlogArticle::getViewCount)
                .orderByDesc(BlogArticle::getId);
        Page<Integer> result = blogArticleDAO.selectArticleIdPage(
                new Page<>(page, size),
                queryWrapper
        );
        validateArticleListPageBounds(result.getTotal(), page, size);
        HydratedArticleList hydrated = hydrateArticleListDtos(result.getRecords(), null);
        return new PageResult(hydrated.dtos(), result.getTotal(), page);
    }

    /**
     * DTO 缓存采用旁路模式：先批量读 Redis，仅对未命中 ID 批量回表并逐项回填。
     */
    private HydratedArticleList hydrateArticleListDtos(List<Integer> articleIds,
                                                        Map<Integer, Long> rankScores) {
        if (articleIds == null || articleIds.isEmpty()) {
            return new HydratedArticleList(new ArrayList<>(), Collections.emptySet());
        }

        Map<Integer, ArticleListDTO> dtoById = new HashMap<>();
        boolean cacheAvailable = true;
        try {
            dtoById.putAll(articleIndexRedisService.getArticleListDtos(articleIds));
        } catch (RuntimeException exception) {
            cacheAvailable = false;
            log.warn("Read article DTO cache failed, fallback to MySQL", exception);
        }

        List<Integer> missingIds = articleIds.stream()
                .filter(articleId -> !dtoById.containsKey(articleId))
                .distinct()
                .toList();
        if (!missingIds.isEmpty()) {
            List<ArticleListDTO> databaseDtos = blogArticleDAO.selectArticleListByIds(missingIds);
            normalizeArticleListDtos(databaseDtos);
            for (ArticleListDTO dto : databaseDtos) {
                if (dto.getId() != null) {
                    dtoById.put(dto.getId(), dto);
                }
            }
            if (cacheAvailable) {
                try {
                    articleIndexRedisService.cacheArticleListDtos(databaseDtos);
                } catch (RuntimeException exception) {
                    log.warn("Cache article DTOs failed", exception);
                }
            }
        }

        Map<Integer, Long> realtimeScores = rankScores;
        if (realtimeScores == null) {
            try {
                List<Long> scores = articleIndexRedisService.getViewScores(articleIds);
                realtimeScores = new HashMap<>();
                for (int index = 0; index < articleIds.size(); index++) {
                    realtimeScores.put(articleIds.get(index), scores.get(index));
                }
            } catch (RuntimeException exception) {
                // ZSet 故障时继续返回 DTO 内的 MySQL 浏览量快照。
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
            String coverURL = articleListDTO.getCoverURL();
            if (coverURL == null || coverURL.isBlank()) {
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

    private void validateArticlePageParams(int page,
                                           int size) {
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

    private void validateArticleListPageBounds(long totalElements,
                                               int page,
                                               int size) {
        if (totalElements <= 0) {
            return;
        }

        long maxPage =
                (totalElements + size - 1) / size;

        if (page > maxPage) {
            // 有文章时访问超过最大页码才是不存在；第一页空列表仍然允许正常展示。
            throw new ResourceNotFoundException("文章列表页不存在");
        }
    }

    private String normalizeArticleListSort(String sort) {
        if ("viewCountDesc".equals(sort)) {
            return sort;
        }
        return "default";
    }

    private void applyRealtimeViewCount(ArticleDTO articleDTO, Long realtimeViewCount) {
        if (articleDTO != null && realtimeViewCount != null) {
            articleDTO.setViewCount(realtimeViewCount);
        }
    }

    /**
     * Redis 是可重建的派生数据，缓存异常不能使已提交的文章事务失败。
     */
    private void afterCommitBestEffort(Runnable action, String operation) {
        Runnable safeAction = () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                log.warn(operation, exception);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            safeAction.run();
                        }
                    }
            );
            return;
        }
        safeAction.run();
    }

    private record HydratedArticleList(List<ArticleListDTO> dtos,
                                       Set<Integer> missingIds) {
    }

}
