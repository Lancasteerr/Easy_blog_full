package com.febrie.demo_bk.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.dao.ArticleViewStatDAO;
import com.febrie.demo_bk.dao.BlogArticleDAO;
import com.febrie.demo_bk.dto.ArticleDTO;
import com.febrie.demo_bk.pojo.BlogArticle;
import com.febrie.demo_bk.result.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class BlogArticleService {
    @Autowired
    private BlogArticleDAO blogArticleDAO;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ArticleViewStatDAO articleViewStatDAO;

    /**
     * 先改数据库，再删Redis
     */
    //@OperationLoger(module = "文章",type = "增加或修改")
    public void addOrUpdate(ArticleDTO articleDTO) {
        if(articleDTO.getId() == null) {
            blogArticleDAO.insert(BlogArticle.toPojo(articleDTO));
        } else {
            blogArticleDAO.updateById(BlogArticle.toPojo(articleDTO));
        }
        redisService.delete("blog:article:detail:"+articleDTO.getId());
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
            redisService.setObject(key,dto,30, TimeUnit.DAYS);
            return dto;
        }

        return cache;
    }

    //删除文章
    public void delete(int id) {
        blogArticleDAO.deleteById(id);
        redisService.delete("blog:article:detail:" + id);
        //所有页面缓存失效
        increasePageVersion();
    }

    //缓存文章列表
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
        Long version = getArticlePageVersion();
        String cacheKey = String.format(
                "blog:article:page:%d:%d:%d",
                version, page, size);
        PageResult pageResult = redisService.getObject(cacheKey, PageResult.class);
        if(pageResult == null){

            Page<BlogArticle> articlePage =
                    new Page<>(
                            page,
                            size
                    );

            Page<BlogArticle> result = blogArticleDAO.selectPage(
                    articlePage,
                    null
            );


            pageResult = PageResult.from(result);
            //加入新缓存
            redisService.setObject(cacheKey,pageResult,7,TimeUnit.DAYS);

            return pageResult;
        }
        return pageResult;
    }

    //版本号自增
    private void increasePageVersion(){
        redisService.valueIncrease(ARTICLE_PAGE_VERSION_KEY);
    }

}
