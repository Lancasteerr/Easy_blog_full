package com.febrie.demo_bk.article.internal;

import com.febrie.demo_bk.article.ArticleListDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文章列表页面索引。
 *
 * <p>该对象只缓存分页后的文章 ID 与分页元数据，具体展示数据统一由
 * {@link ArticleListDTO} 的旁路缓存提供，避免页面缓存与 DTO 缓存保存两份文章内容。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticlePageIndex {

    private List<Integer> ids;

    private long totalElements;

    private int number;

    private int size;
}
