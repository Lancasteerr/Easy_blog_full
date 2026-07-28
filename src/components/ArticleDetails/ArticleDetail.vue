<script setup>
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import ArticleCard from "@/components/ArticleDetails/ArticleCard.vue";
import CommonFooterLayout from "@/components/Footer/CommonFooterLayout.vue";
import ArticleHeader from "@/components/ArticleDetails/ArticleHeader.vue";
import request from "@/utils/request";
import fallbackCoverOne from "@/assets/ArticleCoverImg/p2382636776.jpg";
import fallbackCoverTwo from "@/assets/ArticleCoverImg/p2415896447.jpg";

const route = useRoute();
const fallbackCovers = [fallbackCoverOne, fallbackCoverTwo];

const articleTitle = ref("文章加载中");
const articleHtml = ref("");
const articleDate = ref("未知日期");
const articleCoverUrl = ref(fallbackCoverOne);

const articleId = computed(() => route.params.id || route.query.id);

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
  if (!id) {
    articleTitle.value = "文章不存在";
    articleHtml.value = "";
    articleDate.value = "未知日期";
    articleCoverUrl.value = fallbackCovers[0];
    return;
  }

  try {
    // 详情页统一在外层取数，header 和正文组件只负责展示。
    const res = await request.get("/public/article", {
      params: { id }
    });

    const data = res.data || {};

    articleTitle.value = data.articleTitle || "未命名文章";
    articleHtml.value = data.articleContentHtml || "";
    articleDate.value = formatArticleDate(data.articleDate);
    articleCoverUrl.value = getArticleCoverUrl(data, id);
  } catch (error) {
    console.error("Get article detail fail:", error);
    articleTitle.value = "文章加载失败";
    articleHtml.value = "";
    articleDate.value = "未知日期";
    articleCoverUrl.value = getFallbackCover(id);
  }
};

watch(articleId, loadArticle, { immediate: true });
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
