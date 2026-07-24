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
import com.febrie.demo_bk.result.PageResult;
import com.febrie.demo_bk.service.pv.ArticleViewServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BlogArticleService {
    private static final Pattern DATA_FILE_ID_PATTERN =
            Pattern.compile("data-file-id=[\"'](\\d+)[\"']");

    @Autowired
    private BlogArticleDAO blogArticleDAO;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ArticleViewStatDAO articleViewStatDAO;
    @Autowired
    private FileService fileService;
    @Autowired
    private ArticleViewServiceImpl articleViewService;

    /**
     * 先改数据库，再删Redis
     */
    //@OperationLoger(module = "文章",type = "增加或修改")
    public void addOrUpdate(ArticleDTO articleDTO) {
        BlogArticle oldArticle = null;
        if (articleDTO.getId() != null) {
            oldArticle = blogArticleDAO.selectById(articleDTO.getId());
        }

        BlogArticle blogArticle = BlogArticle.toPojo(articleDTO);
        if(articleDTO.getId() == null) {
            blogArticleDAO.insert(blogArticle);
            articleDTO.setId(blogArticle.getId());
        } else {
            blogArticleDAO.updateById(blogArticle);
            deleteRemovedArticleImages(oldArticle, articleDTO);
            redisService.delete("blog:article:detail:"+articleDTO.getId());
        }

        increasePageVersion();
    }

    /**
     * 无缓存则查库后写入缓存，注意此处空对象不写入redis，直接返回null
     */
    public ArticleDTO findById (int id) {

        String key = "blog:article:detail:" + id;

        ArticleDTO cache = redisService.getObject(key,ArticleDTO.class);
        if(cache==null){
            ArticleDTO dto = BlogArticle.toDTO(blogArticleDAO.selectById(id));
            if(dto==null) return null;
            articleViewService.recordView((long) id);
            redisService.setObject(key,dto,30, TimeUnit.DAYS);
            return dto;
        }

        articleViewService.recordView((long) id);
        return cache;
    }

    //删除文章
    public void delete(int id) {
        BlogArticle article = blogArticleDAO.selectById(id);
        blogArticleDAO.deleteById(id);
        deleteArticleImages(article);
        redisService.delete("blog:article:detail:" + id);
        //所有页面缓存失效
        increasePageVersion();
    }

    private void deleteRemovedArticleImages(BlogArticle oldArticle, ArticleDTO newArticle) {
        Set<Long> oldImageIds = collectImageIds(
                oldArticle == null ? null : oldArticle.getArticleContentJson(),
                oldArticle == null ? null : oldArticle.getArticleContentHtml()
        );
        Set<Long> newImageIds = collectImageIds(
                newArticle == null ? null : newArticle.getArticleContentJson(),
                newArticle == null ? null : newArticle.getArticleContentHtml()
        );

        oldImageIds.removeAll(newImageIds);
        oldImageIds.forEach(fileService::delete);
    }

    private void deleteArticleImages(BlogArticle article) {
        collectImageIds(
                article == null ? null : article.getArticleContentJson(),
                article == null ? null : article.getArticleContentHtml()
        )
                .forEach(fileService::delete);
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

    private static final String ARTICLE_PAGE_VERSION_KEY = "blog:article:page:version";

    //获取分页版本号
    private Long getArticlePageVersion(){
        Long version = redisService.getObject(ARTICLE_PAGE_VERSION_KEY,long.class);
        if(version==null){
            redisService.setObject(ARTICLE_PAGE_VERSION_KEY,1L);
            return 1L;
        }
        return version;
    }

    public PageResult getArticleList(int page, int size) {
        return getArticleList(page, size, null);
    }

    public PageResult getArticleList(int page, int size, String sort) {
        Long version = getArticlePageVersion();
        String normalizedSort = normalizeArticleListSort(sort);
        String cacheKey = String.format(
                "blog:article:page:%d:%d:%d:%s",
                version, page, size, normalizedSort);
        PageResult pageResult = redisService.getObject(cacheKey, PageResult.class);
        //访问MySql
        if(pageResult == null){

            Page<ArticleListDTO> articlePage =
                    new Page<>(
                            page,
                            size
                    );

            LambdaQueryWrapper<BlogArticle> queryWrapper = null;
            if ("viewCountDesc".equals(normalizedSort)) {
                queryWrapper = new LambdaQueryWrapper<BlogArticle>()
                        .orderByDesc(BlogArticle::getViewCount)
                        .orderByDesc(BlogArticle::getArticleDate)
                        .orderByDesc(BlogArticle::getId);
            }

            Page<ArticleListDTO> result = blogArticleDAO.selectArticleListPage(
                    articlePage,
                    queryWrapper
            );


            pageResult = PageResult.from(result);
            //加入新缓存
            redisService.setObject(cacheKey,pageResult,30,TimeUnit.MINUTES);

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

    //版本号自增
    private void increasePageVersion(){
        redisService.ValueIncrease(ARTICLE_PAGE_VERSION_KEY);
    }

    public void invalidateViewCountCache(Set<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return;
        }
        //删除浏览量更新文章的详细缓存
        articleIds.forEach(id -> redisService.delete("blog:article:detail:" + id));
        //废除所有列表缓存
        increasePageVersion();
    }

}
