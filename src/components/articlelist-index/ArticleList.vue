<template>
  <div class="article-list">
    <div class="article-item" v-for="item in articles" :key="item.id">

      <div class="title" @click = "jumpto(item.id)">{{ item.articleTitle }}</div>

      <div class="abstract">{{ item.articleAbstract }}</div>

      <div class="date">{{ item.articleDate.slice(0, 10) }}</div>

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
</script>

<style scoped>
.article-list {
  width: 100%;
  box-sizing: border-box;
}

/* 每篇文章整体 */
.article-item {
  padding: 16px 0;
}

/* 标题 */
.title {
  font-size: 20px;
  font-weight: bold;
  color: #222;
  margin-bottom: 6px;
}

/* 摘要 */
.abstract {
  font-size: 16px;
  color: #555;
  line-height: 1.5;
  margin-bottom: 4px;
}

/* 日期 */
.date {
  font-size: 13px;
  color: #999;
  margin-bottom: 12px;
}

/* 分隔线 */
.divider {
  width: 100%;
  height: 1px;
  background-color: #5a5a5a42;
}
</style>
