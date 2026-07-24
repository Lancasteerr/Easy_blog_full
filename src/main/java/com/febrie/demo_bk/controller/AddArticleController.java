package com.febrie.demo_bk.controller;

import com.febrie.demo_bk.annotation.OperationLoger;
import com.febrie.demo_bk.dto.ArticleDTO;
import com.febrie.demo_bk.result.Result;
import com.febrie.demo_bk.service.BlogArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
public class AddArticleController {
    @Autowired
    BlogArticleService blogArticleService;

    @CrossOrigin
    @PostMapping(value = "api/admin/content/article")
    @ResponseBody
    @OperationLoger(module = "文章",type = "增加或修改")
    public Result article(@RequestBody ArticleDTO articleDTO) {
        articleDTO.setArticleDate(LocalDateTime.now(
                ZoneId.of("Asia/Shanghai")
        ));
        blogArticleService.addOrUpdate(articleDTO);
        return new Result(200);
    }

    @CrossOrigin
    @DeleteMapping(value = "api/admin/content/delarticle/{id}")
    @ResponseBody
    @OperationLoger(module = "文章",type = "删除")
    public Result delarticel(@PathVariable int id){
        blogArticleService.delete(id);
        return new Result(200);
    }

}
