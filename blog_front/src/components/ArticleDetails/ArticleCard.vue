<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import DOMPurify from "dompurify";
import { getAppScrollContainer } from "@/utils/appScroll";

const props = defineProps({
  articleHtml: {
    type: String,
    default: ""
  }
});

// 文章 HTML 来自后端，渲染前先做白名单清洗，降低 XSS 风险。
const safeArticleHtml = computed(() =>
  DOMPurify.sanitize(props.articleHtml, {
    USE_PROFILES: { html: true },
  })
);

// 阅读进度条
const scrollProgress = ref(0);
let scrollContainer = null;

// 更新阅读进度条
const updateProgress = () => {
  const container = scrollContainer || document.documentElement;
  const scrollTop = container.scrollTop || 0;
  const docHeight = container.scrollHeight - container.clientHeight;

  scrollProgress.value = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0;
};

onMounted(async () => {
  await nextTick();

  // 主页面滚动已交给 App.vue 中的 el-scrollbar，这里监听统一封装后的真实滚动容器。
  scrollContainer = getAppScrollContainer();
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
        <!-- 正文 -->
        <div class="markdown-body" v-html="safeArticleHtml"></div>

      </el-card>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 页面容器 */
.article-container {
  display: flex;
  justify-content: center;
}

/* 卡片 */
.article-card {
  width: min(900px, 100%);
  background-color: rgba(43, 47, 54, 0.88);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.82);
  box-shadow: 0 14px 32px rgba(0, 0, 0, 0.28), 0 2px 8px rgba(0, 0, 0, 0.18);
}

.article-card :deep(.el-card__body) {
  padding: 38px 42px;
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

/* 正文排版美化 */
.markdown-body {
  max-width: 760px;
  margin: 0 auto;
  color: rgba(255, 255, 255, 0.82);
  line-height: 1.75;
  font-family: "Source Han Sans Regular", sans-serif;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  color: #ffffff;
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

.markdown-body :deep(p),
.markdown-body :deep(li) {
  color: rgba(255, 255, 255, 0.82);
}

.markdown-body :deep(a) {
  color: #7ccfff;
}

.markdown-body :deep(blockquote) {
  color: rgba(255, 255, 255, 0.78);
  background-color: rgba(255, 255, 255, 0.07);
  border-left-color: #4dbbff;
  border-radius: 4px;
}

.markdown-body :deep(code) {
  color: #f3f7fb;
  background-color: rgba(18, 18, 18, 0.34);
}

.markdown-body :deep(pre) {
  color: #d6deeb;
  background-color: rgba(24, 26, 30, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.markdown-body :deep(table tr) {
  background-color: rgba(35, 39, 45, 0.9);
  border-top-color: rgba(255, 255, 255, 0.14);
}

.markdown-body :deep(table tr:nth-child(2n)) {
  background-color: rgba(51, 56, 64, 0.72);
}

.markdown-body :deep(table th),
.markdown-body :deep(table td) {
  border-color: rgba(255, 255, 255, 0.14);
}

.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 6px;
  background-color: transparent;
}

@media (max-width: 640px) {
  .article-card :deep(.el-card__body) {
    padding: 24px 20px;
  }
}
</style>
