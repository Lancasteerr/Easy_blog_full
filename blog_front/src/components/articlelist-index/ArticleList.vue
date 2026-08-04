<template>
  <div class="article-list">
    <div
      class="article-item"
      :class="{ reverse: index % 2 === 1 }"
      v-for="(item, index) in articles"
      :key="item.id"
      role="button"
      tabindex="0"
      @click="jumpto(item.id)"
      @keyup.enter="jumpto(item.id)"
    >
      <div class="article-cover">
        <img :src="getCoverUrl(item, index)" :alt="`${item.articleTitle || '文章'}封面`" />
      </div>

      <div class="article-content">
        <h2 class="title">{{ item.articleTitle }}</h2>

        <div class="article-meta">
          <span>发表于 {{ formatArticleDate(item.articleDate) }}</span>
          <span class="meta-divider">|</span>
          <span>作者 Febrie</span>
        </div>

        <p class="abstract">{{ item.articleAbstract || "暂无概要" }}</p>
      </div>
    </div>

    <ArticlePagination
      :total="total"
      :page-size="pageSize"
      :current-page="page"
      @current-change="handlePageChange"
    ></ArticlePagination>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from "vue";
import { useRouter } from "vue-router";
import fallbackCoverOne from "@/assets/ArticleCoverImg/p2382636776.jpg";
import fallbackCoverTwo from "@/assets/ArticleCoverImg/p2415896447.jpg";
import ArticlePagination from "@/components/common/ArticlePagination.vue";
import { usePagedArticles } from "@/composables/usePagedArticles";
import { getAppScrollTop, setAppScrollTop } from "@/utils/appScroll";

const router = useRouter();
const fallbackCovers = [fallbackCoverOne, fallbackCoverTwo];
const ARTICLE_LIST_SCROLL_STATE_KEY = "articleListScrollState";

const isNotFoundStatus = error => [400, 404].includes(error.response?.status);

const goToNotFound = () => {
  // 列表页参数或页码越界时进入错误页，正常第一页空列表不算错误。
  router.replace("/404");
};

const {
  articles,
  total,
  page,
  pageSize,
  loadArticles,
  changePage,
  formatArticleDate,
} = usePagedArticles({
  pageSize: 10,
  validatePageResult: true,
  onInvalidPageResult: goToNotFound,
  onLoadError(error) {
    if (isNotFoundStatus(error)) {
      goToNotFound();
      return false;
    }

    console.error("Get article_list fail:", error);
    return false;
  },
});

const waitForPagePaint = async () => {
  await nextTick();

  await new Promise(resolve => {
    requestAnimationFrame(() => requestAnimationFrame(resolve));
  });
};

const readSavedScrollState = () => {
  const rawState = sessionStorage.getItem(ARTICLE_LIST_SCROLL_STATE_KEY);

  if (!rawState) {
    return null;
  }

  try {
    const state = JSON.parse(rawState);
    const savedPage = Number.parseInt(state.page, 10);
    const savedScrollTop = Number(state.scrollTop);

    return {
      page: Number.isFinite(savedPage) && savedPage > 0 ? savedPage : 1,
      scrollTop: Number.isFinite(savedScrollTop) ? Math.max(savedScrollTop, 0) : 0
    };
  } catch (error) {
    sessionStorage.removeItem(ARTICLE_LIST_SCROLL_STATE_KEY);
    return null;
  }
};

const saveScrollState = () => {
  // 点击文章前记录当前列表页和真实滚动容器位置，用于从详情页返回时恢复。
  sessionStorage.setItem(
    ARTICLE_LIST_SCROLL_STATE_KEY,
    JSON.stringify({
      page: page.value,
      scrollTop: getAppScrollTop()
    })
  );
};

const jumpto = (id) =>{
  saveScrollState();
  router.push({ path: '/article', query: { id: id } })
}

const getFallbackCoverIndex = (item, index) => {
  const numericId = Number.parseInt(item?.id, 10);

  // 空封面按文章ID稳定选择备用图，避免列表重新渲染时图片跳变。
  if (!Number.isNaN(numericId)) {
    return Math.abs(numericId) % fallbackCovers.length;
  }

  return index % fallbackCovers.length;
};

const getCoverUrl = (item, index) => {
  const coverUrl = item?.coverURL || item?.coverObjectUrl;

  if (typeof coverUrl === "string" && coverUrl.trim()) {
    return coverUrl;
  }

  return fallbackCovers[getFallbackCoverIndex(item, index)];
};

const handlePageChange = (newPage) => {
  changePage(newPage);
};

onMounted(async () => {
  const savedScrollState = readSavedScrollState();

  if (savedScrollState) {
    page.value = savedScrollState.page;
  }

  const loaded = await loadArticles();

  if (!loaded || !savedScrollState) {
    return;
  }

  await waitForPagePaint();
  setAppScrollTop(savedScrollState.scrollTop);
  sessionStorage.removeItem(ARTICLE_LIST_SCROLL_STATE_KEY);
});
</script>

<style scoped>
.article-list {
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 22px;
  font-family: "Source Han Sans Regular", sans-serif;
}

/* 每篇文章整体 */
.article-item {
  position: relative;
  overflow: hidden;
  min-height: 248px;
  display: grid;
  grid-template-columns: minmax(300px, 42%) 1fr;
  background: rgba(67, 72, 81, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.28);
  color: #ffffff;
  box-sizing: border-box;
  cursor: pointer;
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease;
}

.article-item.reverse {
  grid-template-columns: 1fr minmax(300px, 42%);
}

.article-item:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 255, 255, 0.18);
  box-shadow: 0 14px 30px rgba(0, 0, 0, 0.36);
}

.article-item:focus-visible {
  outline: 2px solid rgba(255, 255, 255, 0.46);
  outline-offset: 3px;
}

.article-cover {
  min-width: 0;
  min-height: 248px;
  overflow: hidden;
  background: rgba(31, 35, 41, 0.88);
}

.article-cover img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transition: transform 0.36s ease;
}

.article-item:hover .article-cover img {
  transform: scale(1.06);
}

.article-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 34px 40px;
}

.article-item.reverse .article-cover {
  order: 2;
}

.article-item.reverse .article-content {
  order: 1;
}

/* 标题 */
.title {
  font-size: 24px;
  font-weight: bold;
  color: #ffffff;
  line-height: 1.35;
  margin: 0 0 14px;
  transition: color 0.2s ease;
}

.article-item:hover .title {
  color: #ffffff;
}

.article-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.56);
  font-size: 13px;
  margin-bottom: 18px;
}

.meta-divider {
  color: rgba(255, 255, 255, 0.32);
}

/* 摘要 */
.abstract {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.78);
  line-height: 1.75;
  margin: 0;
  word-break: break-word;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

@media (max-width: 640px) {
  .article-list {
    gap: 16px;
  }

  .article-item {
    min-height: 0;
    grid-template-columns: 1fr;
  }

  .article-item.reverse {
    grid-template-columns: 1fr;
  }

  .article-cover {
    min-height: 0;
    aspect-ratio: 16 / 9;
    order: 1 !important;
  }

  .article-content {
    padding: 22px 20px 24px;
    order: 2 !important;
  }

  .title {
    font-size: 20px;
  }

  .article-meta {
    margin-bottom: 12px;
  }

  .abstract {
    font-size: 14px;
  }
}
</style>
