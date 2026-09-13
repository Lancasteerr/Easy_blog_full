package com.febrie.demo_bk.article.web;

import com.febrie.demo_bk.audit.OperationLoger;
import com.febrie.demo_bk.article.ArticleCommandService;
import com.febrie.demo_bk.article.ArticleDTO;
import com.febrie.demo_bk.shared.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ArticleAdminController {
    private final ArticleCommandService articleCommandService;

    @PostMapping(value = "api/admin/content/article")
    @OperationLoger(module = "文章",type = "增加或修改")
    public Result article(@RequestBody ArticleDTO articleDTO) {
        if (articleDTO == null) {
            throw new IllegalArgumentException("文章内容不能为空");
        }

        articleCommandService.save(articleDTO);
        return new Result(200);
    }

    @DeleteMapping(value = "api/admin/content/delarticle/{id}")
    @OperationLoger(module = "文章",type = "删除")
    public Result delarticel(@PathVariable int id){
        articleCommandService.delete(id);
        return new Result(200);
    }

}
