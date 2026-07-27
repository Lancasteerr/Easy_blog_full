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

    <!-- 分页 -->
    <div class="pagination-box">
      <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import request from "@/utils/request";
import {useRouter} from "vue-router";
import fallbackCoverOne from "@/assets/ArticleCoverImg/p2382636776.jpg";
import fallbackCoverTwo from "@/assets/ArticleCoverImg/p2415896447.jpg";

const router = useRouter();
const articles = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const fallbackCovers = [fallbackCoverOne, fallbackCoverTwo];

const loadArticles = async () => {
  try {
    const res = await request.get("/public/get_article_list", {
      params: {
        page: page.value,   // 后端会自动 -1
        size: pageSize.value,
      }
    });

    const data = res.data;
    articles.value = data.content;
    total.value = data.totalElements;
    page.value = data.number + 1;
  }catch (error){
    console.error("Get article_list fail:",error);
  }
};

const jumpto = (id) =>{
  router.push({ path: '/article', query: { id: id } })
}

const formatArticleDate = date => {
  if (!date) return "未知日期";

  const dateText = String(date);
  return dateText.includes("T") ? dateText.split("T")[0] : dateText.slice(0, 10);
};

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
  page.value = newPage;
  loadArticles();
};

onMounted(loadArticles);
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

.pagination-box {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

.pagination-box :deep(.el-pagination) {
  --el-pagination-bg-color: rgba(67, 72, 81, 0.92);
  --el-pagination-button-color: rgba(255, 255, 255, 0.78);
  --el-pagination-button-disabled-bg-color: rgba(67, 72, 81, 0.44);
  --el-pagination-button-disabled-color: rgba(255, 255, 255, 0.28);
  --el-pagination-hover-color: #f3ff00;
  color: rgba(255, 255, 255, 0.7);
}

.pagination-box :deep(.el-pagination__total),
.pagination-box :deep(.el-pagination button),
.pagination-box :deep(.el-pager li) {
  color: rgba(255, 255, 255, 0.78);
}

.pagination-box :deep(.el-pagination.is-background .btn-prev),
.pagination-box :deep(.el-pagination.is-background .btn-next),
.pagination-box :deep(.el-pagination.is-background .el-pager li) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background-color: rgba(67, 72, 81, 0.92);
  border-radius: 4px;
  box-shadow: 0 5px 14px rgba(0, 0, 0, 0.22);
}

.pagination-box :deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: #f3ff00;
  border-color: #f3ff00;
  color: #1f2329;
}

.pagination-box :deep(.el-pagination.is-background .btn-prev:not(:disabled):hover),
.pagination-box :deep(.el-pagination.is-background .btn-next:not(:disabled):hover),
.pagination-box :deep(.el-pagination.is-background .el-pager li:not(.is-active):hover) {
  border-color: rgba(243, 255, 0, 0.56);
  color: #f3ff00;
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
