<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import request from "@/utils/request";

const route = useRoute();

// 数据
const ArticleTitle = ref("");
const ArticleHtml = ref("");
const ArticleDate = ref("");

// 阅读进度条
const scrollProgress = ref(0);
let scrollContainer = null;

// 加载文章
const loadArticle = async () => {
  const id = route.params.id || route.query.id;

  const res = await request.get("/public/article", {
    params: { id }
  });

  const data = res.data;

  ArticleTitle.value = data.articleTitle;
  ArticleHtml.value = data.articleContentHtml;
  ArticleDate.value = data.articleDate.split("T")[0]; // 只显示年月日
};

// 更新阅读进度条
const updateProgress = () => {
  const container = scrollContainer || document.documentElement;
  const scrollTop = container.scrollTop || 0;
  const docHeight = container.scrollHeight - container.clientHeight;

  scrollProgress.value = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0;
};

onMounted(async () => {
  await loadArticle();
  await nextTick();

  // 主页面滚动已交给 App.vue 中的 el-scrollbar，这里监听它的真实滚动容器。
  scrollContainer = document.querySelector(".app-scrollbar .el-scrollbar__wrap");
  scrollContainer?.addEventListener("scroll", updateProgress, { passive: true });
  updateProgress();
});

onBeforeUnmount(() => {
  scrollContainer?.removeEventListener("scroll", updateProgress);
});
</script>

<template>
  <div>
    <!-- 阅读进度条 -->
    <div class="progress-bar" :style="{ width: scrollProgress + '%' }"></div>

    <div class="article-container">
      <el-card class="article-card">

<!--        &lt;!&ndash; 返回按钮 &ndash;&gt;-->
<!--        <el-button type="primary" @click="router.back()" plain>-->
<!--          返回-->
<!--        </el-button>-->

        <!-- 标题 -->
        <h1 class="article-title">{{ ArticleTitle }}</h1>

        <!-- 日期 -->
        <div class="article-meta">
          <el-divider content-position="left">
            <span class="article-date">{{ ArticleDate }}</span>
          </el-divider>
        </div>

        <!-- 正文 -->
        <div class="markdown-body" v-html="ArticleHtml"></div>

      </el-card>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 页面容器 */
.article-container {
  margin-top: 40px;
  display: flex;
  justify-content: center;
}

/* 卡片 */
.article-card {
  width: 900px;
  padding: 40px;
}

/* 阅读进度条 */
.progress-bar {
  position: fixed;
  top: 0;
  left: 0;
  height: 3px;
  background-color: #c0e4ff;
  z-index: 999;
  transition: width 0.2s ease;
}

/* 标题样式 */
.article-title {
  font-size: 32px;
  font-weight: 700;
  margin: 20px 0 10px 0;
}

/* 日期 */
.article-meta {
  margin-bottom: 25px;
}

.article-date {
  font-size: 14px;
  color: #777;
}

/* 正文排版美化 */
.markdown-body {
  max-width: 760px;
  margin: 0 auto;
  line-height: 1.7;
}

.markdown-body img {
  max-width: 100%;
  border-radius: 6px;
}
</style>
