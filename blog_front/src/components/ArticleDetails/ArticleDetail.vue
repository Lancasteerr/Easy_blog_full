<script setup>
import { computed, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import ArticleCard from "@/components/ArticleDetails/ArticleCard.vue";
import CommonFooterLayout from "@/components/Footer/CommonFooterLayout.vue";
import ArticleHeader from "@/components/ArticleDetails/ArticleHeader.vue";
import request from "@/utils/request";
import fallbackCoverOne from "@/assets/ArticleCoverImg/p2382636776.jpg";
import fallbackCoverTwo from "@/assets/ArticleCoverImg/p2415896447.jpg";
import { scrollAppToTop } from "@/utils/appScroll";

const route = useRoute();
const router = useRouter();
const fallbackCovers = [fallbackCoverOne, fallbackCoverTwo];
const SITE_TITLE = "Febrie 的博客 - Febrie's Blog";
const ARTICLE_TITLE_SUFFIX = "Febrie's Blog";
const ARTICLE_ROUTE_NAMES = new Set([
  "ArticleDetailQuery",
  "ArticleDetailRestful"
]);

const articleTitle = ref("文章加载中");
const articleHtml = ref("");
const articleDate = ref("未知日期");
const articleCoverUrl = ref(fallbackCoverOne);

const articleId = computed(() => route.params.id || route.query.id);

const isArticleRoute = () => ARTICLE_ROUTE_NAMES.has(route.name);

const normalizeArticleId = id => {
  const idText = String(id ?? "").trim();

  if (!/^\d+$/.test(idText)) {
    return null;
  }

  const numericId = Number.parseInt(idText, 10);
  return numericId > 0 ? numericId : null;
};

const isNotFoundStatus = error => [400, 404].includes(error.response?.status);

const goToNotFound = () => {
  // 公开文章访问错误统一交给错误页展示，避免误跳登录页。
  router.replace("/404");
};

const setArticleDocumentTitle = title => {
  const safeTitle = typeof title === "string" && title.trim() ? title.trim() : "未命名文章";

  // 文章详情页使用文章标题作为浏览器标签，方便用户在多个标签页中识别内容。
  document.title = `${safeTitle} | ${ARTICLE_TITLE_SUFFIX}`;
};

const formatArticleDate = date => {
  if (!date) return "未知日期";

  const dateText = String(date);

  // 后端 LocalDateTime 会带 T，这里统一裁剪成 yyyy-mm-dd。
  return dateText.includes("T") ? dateText.split("T")[0] : dateText.slice(0, 10);
};

const getFallbackCover = id => {
  const numericId = Number.parseInt(id, 10);

  // 空封面按文章 id 稳定选择示例图，避免刷新时封面跳变。
  if (!Number.isNaN(numericId)) {
    return fallbackCovers[Math.abs(numericId) % fallbackCovers.length];
  }

  return fallbackCovers[0];
};

const getArticleCoverUrl = (article, id) => {
  const coverUrl = article?.coverObjectUrl || article?.coverURL;

  if (typeof coverUrl === "string" && coverUrl.trim()) {
    return coverUrl.trim();
  }

  return getFallbackCover(id);
};

const loadArticle = async id => {
  const normalizedId = normalizeArticleId(id);

  if (!normalizedId) {
    goToNotFound();
    return;
  }

  try {
    // 详情页统一在外层取数，header 和正文组件只负责展示。
    const res = await request.get("/public/article", {
      params: { id: normalizedId }
    });

    const data = res.data;

    if (!data || !data.id) {
      goToNotFound();
      return;
    }

    articleTitle.value = data.articleTitle || "未命名文章";
    articleHtml.value = data.articleContentHtml || "";
    articleDate.value = formatArticleDate(data.articleDate);
    articleCoverUrl.value = getArticleCoverUrl(data, normalizedId);
    setArticleDocumentTitle(articleTitle.value);
  } catch (error) {
    if (isNotFoundStatus(error)) {
      goToNotFound();
      return;
    }

    console.error("Get article detail fail:", error);
    articleTitle.value = "文章加载失败";
    articleHtml.value = "";
    articleDate.value = "未知日期";
    articleCoverUrl.value = getFallbackCover(normalizedId);
    setArticleDocumentTitle(articleTitle.value);
  }
};

watch(articleId, id => {
  if (!isArticleRoute()) {
    // 离开文章详情页时路由参数会短暂变空，此时不能触发文章页的 404 逻辑。
    return;
  }

  // 从文章列表中部进入详情时，先把 App 级滚动容器归零，确保展示顶部封面。
  scrollAppToTop();
  loadArticle(id);
}, { immediate: true });

onUnmounted(() => {
  // 离开文章详情页后恢复站点默认标题，避免文章标题残留到其它页面。
  document.title = SITE_TITLE;
});
</script>

<template>
  <div class="ArticleDetail-Container">
    <ArticleHeader
      :article-title="articleTitle"
      :article-date="articleDate"
      :cover-url="articleCoverUrl"
    ></ArticleHeader>

    <CommonFooterLayout>
      <main class="article-main">
        <ArticleCard :article-html="articleHtml"></ArticleCard>
      </main>
    </CommonFooterLayout>
  </div>
</template>

<style scoped lang="scss">
.ArticleDetail-Container {
  min-height: 100vh;
  background-color: #121212;
  color: #ffffff;
  font-family: "Source Han Sans Regular", sans-serif;
}

.article-main {
  width: min(960px, calc(100% - 32px));
  margin: 36px auto 0;
}

@media (max-width: 640px) {
  .article-main {
    width: min(100% - 20px, 960px);
    margin-top: 28px;
  }
}
</style>
