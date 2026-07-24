package com.febrie.demo_bk.dto;

import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Data
public class ArticleListDTO {
    private Integer id;

    private String articleTitle;

    private String articleAbstract;

    private LocalDateTime articleDate;

    private Long viewCount;

    //private String articleCover;
}
