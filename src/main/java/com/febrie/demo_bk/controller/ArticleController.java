package com.febrie.demo_bk.controller;

import com.febrie.demo_bk.annotation.OperationLoger;
import com.febrie.demo_bk.dto.ArticleDTO;
import com.febrie.demo_bk.result.PageResult;
import com.febrie.demo_bk.service.BlogArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArticleController {
    @Autowired
    private BlogArticleService blogArticleService;

    @CrossOrigin

    @GetMapping("api/public/get_article_list")
    @OperationLoger(module = "文章列表",type = "获取")
    public PageResult getArticles(@RequestParam int page, @RequestParam int size){
        return blogArticleService.getArticleList(page,size);
    }

    @CrossOrigin

    @GetMapping("api/public/article")
    @OperationLoger(module = "文章详情",type = "通过id查找")
    public ArticleDTO getArticleById(@RequestParam int id){
        return blogArticleService.findById(id);
    }
}
