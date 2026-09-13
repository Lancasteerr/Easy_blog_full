package com.febrie.demo_bk.article.web;

import com.febrie.demo_bk.article.ArticleDTO;
import com.febrie.demo_bk.article.ArticleListDTO;
import com.febrie.demo_bk.article.ArticleQueryService;
import com.febrie.demo_bk.shared.web.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ArticleController {
    private final ArticleQueryService articleQueryService;

    @GetMapping("api/public/get_article_list")
    //@OperationLoger(module = "文章列表",type = "获取")
    public PageResult<ArticleListDTO> getArticles(
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam(required = false) String sort
    ){
        return articleQueryService.findPage(page, size, sort);
    }

    @GetMapping("api/public/article")
    //@OperationLoger(module = "文章详情",type = "通过id查找")
    public ArticleDTO getArticleById(@RequestParam int id){
        return articleQueryService.findById(id);
    }
}
