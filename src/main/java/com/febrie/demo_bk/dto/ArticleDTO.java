package com.febrie.demo_bk.dto;

import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Data
@Getter
public class ArticleDTO {
    private Integer id;

    private String articleTitle;

    private String articleContentHtml;

    private String articleContentJson;

    private String articleAbstract;

    private LocalDateTime articleDate;

    private Long viewCount;

    private Long articleCover;

    private String coverObjectKey;

    private String coverObjectUrl;
}
