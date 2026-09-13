package com.febrie.demo_bk.article.internal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 文章事务提交后需要执行的派生数据同步信息。
 */
public record ArticleChangedEvent(int articleId,
                                  ChangeType changeType,
                                  Set<Long> releasedFileIds) {

    public ArticleChangedEvent {
        releasedFileIds = releasedFileIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(releasedFileIds));
    }

    public enum ChangeType {
        CREATED,
        UPDATED,
        DELETED
    }
}
