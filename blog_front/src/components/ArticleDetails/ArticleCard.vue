<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import DOMPurify from "dompurify";
import ArticleCatalog from "@/components/ArticleDetails/ArticleCatalog.vue";
import { getAppScrollContainer } from "@/utils/appScroll";
import { highlightArticleCode } from "@/utils/codeHighlight";
import { ARTICLE_CATALOG_SELECTOR } from "@/utils/articleHeadings";

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

const scrollProgress = ref(0);
const articleBodyRef = ref(null);
const catalogItems = ref([]);
const activeHeadingId = ref("");
let scrollContainer = null;
let headingElements = [];
let scrollFrameId = null;

// 更新阅读进度条
const updateProgress = () => {
  const container = scrollContainer || document.documentElement;
  const scrollTop = container.scrollTop || 0;
  const docHeight = container.scrollHeight - container.clientHeight;

  scrollProgress.value = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0;
};

const updateActiveHeading = () => {
  if (!headingElements.length) {
    activeHeadingId.value = "";
    return;
  }

  const containerTop = scrollContainer?.getBoundingClientRect().top || 0;
  const activeThreshold = containerTop + 96;
  let activeHeading = headingElements[0];

  // 取越过视口上方阅读线的最后一个标题，向上滚动时也能立即恢复上一章节。
  for (const heading of headingElements) {
    if (heading.getBoundingClientRect().top > activeThreshold) {
      break;
    }

    activeHeading = heading;
  }

  activeHeadingId.value = activeHeading.id;
};

const updateReadingState = () => {
  updateProgress();
  updateActiveHeading();
};

const scheduleReadingStateUpdate = () => {
  if (scrollFrameId !== null) {
    return;
  }

  // 滚动事件通过动画帧合并，避免长目录在一次滚动中重复测量布局。
  scrollFrameId = window.requestAnimationFrame(() => {
    scrollFrameId = null;
    updateReadingState();
  });
};

const getUniqueHeadingId = (heading, index, usedIds) => {
  const originalId = heading.id.trim();
  const baseId = originalId || `article-heading-${index + 1}`;
  let uniqueId = baseId;
  let duplicateIndex = 2;

  while (usedIds.has(uniqueId)) {
    uniqueId = `${baseId}-${duplicateIndex}`;
    duplicateIndex += 1;
  }

  usedIds.add(uniqueId);
  heading.id = uniqueId;
  return uniqueId;
};

const buildCatalog = () => {
  const headingNodes = Array.from(
    articleBodyRef.value?.querySelectorAll(ARTICLE_CATALOG_SELECTOR) || []
  ).filter(heading => heading.textContent?.trim());

  if (!headingNodes.length) {
    headingElements = [];
    catalogItems.value = [];
    activeHeadingId.value = "";
    return;
  }

  const usedIds = new Set();
  const minimumLevel = Math.min(
    ...headingNodes.map(heading => Number.parseInt(heading.tagName.slice(1), 10))
  );

  headingElements = headingNodes;
  catalogItems.value = headingNodes.map((heading, index) => {
    const level = Number.parseInt(heading.tagName.slice(1), 10);

    return {
      id: getUniqueHeadingId(heading, index, usedIds),
      text: heading.textContent.trim(),
      level,
      // 以文章实际出现的最浅标题为起点，避免只有 H2 时所有条目无故缩进。
      depth: level - minimumLevel
    };
  });
  activeHeadingId.value = catalogItems.value[0].id;
};

const prepareArticleLinks = () => {
  const links = articleBodyRef.value?.querySelectorAll("a[href]") || [];

  links.forEach(link => {
    const href = link.getAttribute("href")?.trim() || "";

    // 空链接不处理；页内锚点继续在当前文章内定位，避免新开无意义的标签页。
    if (!href || href.startsWith("#")) {
      return;
    }

    link.setAttribute("target", "_blank");

    // 保留编辑器写入的 nofollow 等值，同时补齐新标签页所需的安全隔离。
    const relValues = new Set(
      (link.getAttribute("rel") || "")
        .split(/\s+/)
        .filter(Boolean)
        .map(value => value.toLowerCase())
    );
    relValues.add("noopener");
    relValues.add("noreferrer");
    link.setAttribute("rel", Array.from(relValues).join(" "));
  });
};

const refreshArticleContent = async () => {
  await nextTick();
  // 只处理 DOMPurify 清洗后的链接，兼容没有 target 属性的历史文章。
  prepareArticleLinks();
  highlightArticleCode(articleBodyRef.value);
  buildCatalog();
  scheduleReadingStateUpdate();
};

const scrollToHeading = id => {
  const targetHeading = headingElements.find(heading => heading.id === id);

  if (!targetHeading) {
    return;
  }

  activeHeadingId.value = id;
  targetHeading.scrollIntoView({
    behavior: "smooth",
    block: "start"
  });
};

onMounted(async () => {
  // 主页面滚动已交给 App.vue 中的 el-scrollbar，这里监听统一封装后的真实滚动容器。
  scrollContainer = getAppScrollContainer();
  scrollContainer?.addEventListener("scroll", scheduleReadingStateUpdate, { passive: true });
  await refreshArticleContent();
});

watch(safeArticleHtml, async () => {
  // v-html 更新完成后重建代码高亮和目录，避免路由切换时残留上一篇文章状态。
  await refreshArticleContent();
}, { flush: "post" });

onBeforeUnmount(() => {
  scrollContainer?.removeEventListener("scroll", scheduleReadingStateUpdate);

  if (scrollFrameId !== null) {
    window.cancelAnimationFrame(scrollFrameId);
  }

  headingElements = [];
});
</script>

<template>
  <div class="article-reading-layout" :class="{ 'has-catalog': catalogItems.length > 0 }">
    <!-- 阅读进度条 -->
    <div class="progress-bar" :style="{ width: scrollProgress + '%' }"></div>

    <div class="article-container">
      <el-card class="article-card">
        <!-- 正文 -->
        <div ref="articleBodyRef" class="markdown-body" v-html="safeArticleHtml"></div>
      </el-card>
    </div>

    <ArticleCatalog
      v-if="catalogItems.length"
      :items="catalogItems"
      :active-id="activeHeadingId"
      @select="scrollToHeading"
    ></ArticleCatalog>
  </div>
</template>

<style scoped lang="scss">
.article-reading-layout {
  width: 100%;
}

/* 页面容器 */
.article-container {
  width: min(900px, 100%);
  min-width: 0;
  margin: 0 auto;
}

/* 卡片 */
.article-card {
  width: 100%;
  box-sizing: border-box;
  background-color: rgba(43, 47, 54, 0.88);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
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

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  scroll-margin-top: 24px;
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

.markdown-body :deep(:not(pre) > code) {
  color: #f3f7fb;
  background-color: rgba(18, 18, 18, 0.34);
}

.markdown-body :deep(pre) {
  color: #d6deeb;
  background-color: rgba(24, 26, 30, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.markdown-body :deep(pre code) {
  padding: 0;
  color: inherit;
  background: transparent;
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

@media (min-width: 1100px) {
  .article-reading-layout.has-catalog {
    display: grid;
    grid-template-columns: minmax(0, 900px) 280px;
    align-items: start;
    gap: 24px;
  }

  .article-reading-layout.has-catalog .article-container {
    width: 100%;
    margin: 0;
  }
}

@media (max-width: 640px) {
  .article-card :deep(.el-card__body) {
    padding: 24px 20px;
  }
}
</style>
