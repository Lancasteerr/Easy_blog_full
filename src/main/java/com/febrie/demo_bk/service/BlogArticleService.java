package com.febrie.demo_bk.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.dao.ArticleViewStatDAO;
import com.febrie.demo_bk.dao.BlogArticleDAO;
import com.febrie.demo_bk.dto.ArticleDTO;
import com.febrie.demo_bk.dto.ArticleListDTO;
import com.febrie.demo_bk.pojo.BlogArticle;
import com.febrie.demo_bk.pojo.FileObject;
import com.febrie.demo_bk.result.PageResult;
import com.febrie.demo_bk.service.pv.ArticleViewServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
public class BlogArticleService {
    private static final Pattern DATA_FILE_ID_PATTERN =
            Pattern.compile("data-file-id=[\"'](\\d+)[\"']");

    private BlogArticleDAO blogArticleDAO;

    private RedisService redisService;

    private ArticleViewStatDAO articleViewStatDAO;

    private FileService fileService;

    private ArticleViewServiceImpl articleViewService;

    /**
     * 文章详细缓存 Key
     */
    private static final String ARTICLE_DETAIL_CACHE_KEY = "blog:article:detail:";

    /**
     * 未排序文章列表 Key
     */
    private static final String DEFAULT_ARTICLE_LIST_VERSION_KEY = "blog:article:page:";

    /**
     * 按浏览量排序文章列表 Key
     */
    private static final String PV_DESC_ARTICLE_LIST_VERSION_KEY = "blog:article:page:hot:";

    /**
     * 更新文章
     * 先改数据库，再删Redis
     */
    //@OperationLoger(module = "文章",type = "增加或修改")
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdate(ArticleDTO articleDTO) {
        BlogArticle oldArticle = null;
        if (articleDTO.getId() != null) {
            oldArticle = blogArticleDAO.selectById(articleDTO.getId());
            if (oldArticle == null) {
                throw new IllegalArgumentException("文章不存在");
            }
        }

        setCoverObjectInfo(articleDTO);

        Set<Long> oldFileIds =
                collectArticleFileIds(oldArticle);

        Set<Long> newFileIds =
                collectArticleFileIds(articleDTO);

        fileService.validateImageFiles(newFileIds);

        BlogArticle blogArticle = BlogArticle.toPojo(articleDTO);
        if(articleDTO.getId() == null) {
            blogArticleDAO.insert(blogArticle);
            articleDTO.setId(blogArticle.getId());
        } else {
            blogArticleDAO.updateById(blogArticle);
            redisService.delete(ARTICLE_DETAIL_CACHE_KEY + articleDTO.getId());
        }

        fileService.markBound(newFileIds);
        releaseRemovedFiles(oldFileIds, newFileIds);

        //两种列表更新版本号
        redisService.ValueIncrease(DEFAULT_ARTICLE_LIST_VERSION_KEY + "version");
        redisService.ValueIncrease(PV_DESC_ARTICLE_LIST_VERSION_KEY + "version");
    }

    /**
     * 无缓存则查库后写入缓存，注意此处空对象不写入redis，直接返回null
     */
    public ArticleDTO findById (int id) {

        String key = ARTICLE_DETAIL_CACHE_KEY + id;

        ArticleDTO cache = redisService.getObject(key,ArticleDTO.class);
        if(cache==null){
            ArticleDTO dto = BlogArticle.toDTO(blogArticleDAO.selectById(id));
            if(dto==null) return null;
            articleViewService.recordView((long) id);
            redisService.setObject(key,dto,30, TimeUnit.DAYS);//文章详细缓存TTL
            return dto;
        }

        articleViewService.recordView((long) id);
        return cache;
    }

    //删除文章
    @Transactional(rollbackFor = Exception.class)
    public void delete(int id) {
        BlogArticle article = blogArticleDAO.selectById(id);
        Set<Long> articleFileIds =
                collectArticleFileIds(article);

        blogArticleDAO.deleteById(id);
        fileService.markTemp(articleFileIds);
        deleteTempFilesAfterCommit(articleFileIds);
        redisService.delete(ARTICLE_DETAIL_CACHE_KEY + id);
        //所有页面缓存失效
        redisService.ValueIncrease(DEFAULT_ARTICLE_LIST_VERSION_KEY + "version");
        redisService.ValueIncrease(PV_DESC_ARTICLE_LIST_VERSION_KEY + "version");
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
                collectImageIds(JSON.parseObject(articleContentJson), imageIds);
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

    private void collectImageIds(JSONObject node, Set<Long> imageIds) {
        if (node == null) {
            return;
        }

        if ("image".equals(node.getString("type"))) {
            JSONObject attrs = node.getJSONObject("attrs");
            if (attrs != null) {
                Long fileId = attrs.getLong("fileId");
                if (fileId != null) {
                    imageIds.add(fileId);
                }
            }
        }

        JSONArray content = node.getJSONArray("content");
        if (content == null) {
            return;
        }

        for (int i = 0; i < content.size(); i++) {
            collectImageIds(content.getJSONObject(i), imageIds);
        }
    }

    /**
     * 获取浏览量排序版本号 此处需保证操作setIfAbsent原子性
     */
    private Long getArticlePageHotVersion(){
        String key = PV_DESC_ARTICLE_LIST_VERSION_KEY + "version";

        redisService.setIfAbsent(key, 1L);

        return redisService.getObject(key, Long.class);
    }

    /**
     * 获取默认排序版本号 此处需保证操作setIfAbsent原子性
     */
    private Long getArticlePageDefaultVersion(){
        String key = DEFAULT_ARTICLE_LIST_VERSION_KEY + "version";

        redisService.setIfAbsent(key, 1L);

        return redisService.getObject(key, Long.class);
    }

    public PageResult getArticleList(int page, int size) {
        return getArticleList(page, size, null);
    }

    public PageResult getArticleList(int page, int size, String sort) {
        Long version;
        String cacheKey;
        String normalizedSort = normalizeArticleListSort(sort);

        if("viewCountDesc".equals(normalizedSort)) {
            version = getArticlePageHotVersion();
            cacheKey = PV_DESC_ARTICLE_LIST_VERSION_KEY;
        } else {//default
            version = getArticlePageDefaultVersion();
            cacheKey = DEFAULT_ARTICLE_LIST_VERSION_KEY;
        }

        cacheKey = cacheKey + String.format(
                "%d:%d:%d:%s",
                version, page, size, normalizedSort);
        PageResult pageResult = redisService.getObject(cacheKey, PageResult.class);
        //访问MySql
        if(pageResult == null){

            Page<ArticleListDTO> articlePage =
                    new Page<>(
                            page,
                            size
                    );

            LambdaQueryWrapper<BlogArticle> queryWrapper =
                    new LambdaQueryWrapper<>();

            if ("viewCountDesc".equals(normalizedSort)) {

                queryWrapper
                        .orderByDesc(BlogArticle::getViewCount)
                        .orderByDesc(BlogArticle::getArticleDate)
                        .orderByDesc(BlogArticle::getId);
            } else {
                queryWrapper
                        .orderByDesc(BlogArticle::getArticleDate);
            }

            Page<ArticleListDTO> result = blogArticleDAO.selectArticleListPage(
                    articlePage,
                    queryWrapper
            );

            result.getRecords().forEach(
                    articleListDTO -> {
                        String coverURL = articleListDTO.getCoverURL();
                        if(coverURL == null || coverURL.isBlank()){
                            articleListDTO.setCoverURL(null);
                        }
                    }
            );

            pageResult = PageResult.from(result);
            //加入新缓存 按列表类型设置缓存TTL
            if("viewCountDesc".equals(normalizedSort)) {
                redisService.setObject(cacheKey,pageResult,1,TimeUnit.HOURS);
            } else {
                redisService.setObject(cacheKey,pageResult,7,TimeUnit.DAYS);
            }

            return pageResult;
        }
        return pageResult;
    }

    private String normalizeArticleListSort(String sort) {
        if ("viewCountDesc".equals(sort)) {
            return sort;
        }
        return "default";
    }

    public void invalidateViewCountCache(Set<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return;
        }
        //删除浏览量更新文章的详细缓存
        articleIds.forEach(id -> redisService.delete("blog:article:detail:" + id));
        //废除浏览量列表缓存
        redisService.ValueIncrease(PV_DESC_ARTICLE_LIST_VERSION_KEY + "version");
    }

}
