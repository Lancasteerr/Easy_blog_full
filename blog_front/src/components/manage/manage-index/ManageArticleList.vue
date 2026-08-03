<template>
  <div class="article-list">
    <div class="article-item" v-for="item in articles" :key="item.id">

      <div class="title" @click = "jumpto(item.id)">{{ item.articleTitle }}</div>

      <div class="abstract">{{ item.articleAbstract }}</div>

      <div class="date">{{ item.articleDate.slice(0, 10) }}</div>

    <!-- 操作按钮区域 -->
    <div class="action-buttons">
      <el-button
          class="article-action edit-button"
          size="small"
          type="primary"
          plain
          @click="editArticle(item.id)"
      >
        修改
      </el-button>

      <el-button
          class="article-action delete-button"
          size="small"
          type="danger"
          plain
          @click="delArticle(item.id)"
      >
        删除
      </el-button>
    </div>

    <div class="divider"></div>
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

const router = useRouter();
const articles = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);

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

const handlePageChange = (newPage) => {
  page.value = newPage;
  loadArticles();
};

onMounted(loadArticles);

const delArticle = async (id) => {
  try {
    const response = await request.delete(`/admin/content/delarticle/${id}`)
    if(response.data.code === 200){
      window.location.reload();
    }
  }catch (error){
    console.error('Articledel Failed:',error);
  }
}

const editArticle = (id) =>{
  window.open(`/neko-panel/manage/edit/${id}`,'_blank')
}
</script>

<style scoped>
.article-list {
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
  font-family: "Source Han Sans Regular", sans-serif;
}

/* 每篇文章整体 */
.article-item {
  position: relative;
  overflow: hidden;
  padding: 20px 22px 18px;
  background: rgba(67, 72, 81, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.28);
  color: #ffffff;
  box-sizing: border-box;
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease;
}

.article-item::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background-color: #f3ff00;
  opacity: 0.86;
}

.article-item:hover {
  transform: translateY(-2px);
  border-color: rgba(243, 255, 0, 0.38);
  box-shadow: 0 14px 30px rgba(0, 0, 0, 0.36);
}

/* 标题 */
.title {
  font-size: 20px;
  font-weight: bold;
  color: #ffffff;
  margin-bottom: 8px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.title:hover {
  color: #f3ff00;
}

/* 摘要 */
.abstract {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.78);
  line-height: 1.6;
  margin-bottom: 8px;
  word-break: break-word;
}

/* 日期 */
.date {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.56);
  margin-bottom: 14px;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.article-action {
  margin-left: 0 !important;
  border-radius: 4px;
  background-color: rgba(255, 255, 255, 0.05);
  box-shadow: 0 5px 14px rgba(0, 0, 0, 0.18);
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.edit-button {
  --el-button-text-color: #f3ff00;
  --el-button-border-color: rgba(243, 255, 0, 0.42);
  --el-button-bg-color: rgba(243, 255, 0, 0.08);
  --el-button-hover-text-color: #1f2329;
  --el-button-hover-border-color: #f3ff00;
  --el-button-hover-bg-color: #f3ff00;
  --el-button-active-text-color: #1f2329;
  --el-button-active-border-color: #dce600;
  --el-button-active-bg-color: #dce600;
}

.delete-button {
  --el-button-text-color: #ff9a9a;
  --el-button-border-color: rgba(255, 120, 120, 0.42);
  --el-button-bg-color: rgba(255, 120, 120, 0.08);
  --el-button-hover-text-color: #ffffff;
  --el-button-hover-border-color: rgba(255, 120, 120, 0.72);
  --el-button-hover-bg-color: rgba(255, 90, 90, 0.22);
  --el-button-active-text-color: #ffffff;
  --el-button-active-border-color: rgba(255, 120, 120, 0.86);
  --el-button-active-bg-color: rgba(255, 90, 90, 0.32);
}

/* 分隔线 */
.divider {
  display: none;
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
  .article-item {
    padding: 18px 18px 16px;
  }

  .title {
    font-size: 18px;
  }

  .abstract {
    font-size: 14px;
  }
}
</style>
