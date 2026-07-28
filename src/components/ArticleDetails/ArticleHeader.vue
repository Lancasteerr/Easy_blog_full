<script setup>
import router from "@/router";

defineProps({
  articleTitle: {
    type: String,
    default: "未命名文章"
  },
  articleDate: {
    type: String,
    default: "未知日期"
  },
  coverUrl: {
    type: String,
    required: true
  }
});

const goToHomepage = () => {
  router.push("/");
};
</script>

<template>
  <section class="article-hero">
    <img class="hero-cover" :src="coverUrl" :alt="`${articleTitle || '文章'}封面`">
    <div class="hero-mask"></div>

    <el-header class="site-header">
      <button class="site-brand" type="button" @click="goToHomepage">neko dev</button>

      <nav class="site-nav" aria-label="文章详情导航">
        <el-button class="nav-link" link @click="goToHomepage">首页</el-button>
      </nav>
    </el-header>

    <div class="hero-content">
      <h1 class="hero-title">{{ articleTitle }}</h1>
      <div class="hero-meta">
        <span>更新于 {{ articleDate }}</span>
        <span class="meta-divider">|</span>
        <span>作者 Febrie</span>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.article-hero {
  position: relative;
  height: 420px;
  min-height: 320px;
  overflow: hidden;
  background-color: #121212;
}

.hero-cover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transform: scale(1.06);
  transform-origin: center;
}

.hero-mask {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(18, 18, 18, 0.44) 0%, rgba(18, 18, 18, 0.34) 48%, rgba(18, 18, 18, 0.88) 100%),
    rgba(18, 18, 18, 0.14);
}

.site-header {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  width: 100%;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 56px;
  box-sizing: border-box;
  background: linear-gradient(180deg, rgba(18, 18, 18, 0.42) 0%, rgba(18, 18, 18, 0) 100%);
}

.site-brand {
  padding: 0;
  border: 0;
  background: transparent;
  color: #ffffff;
  font-family: "Source Han Sans Bold", sans-serif;
  font-size: 22px;
  font-style: italic;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
  cursor: pointer;
}

.site-brand:focus-visible {
  outline: 2px solid rgba(255, 255, 255, 0.72);
  outline-offset: 4px;
}

.site-nav {
  display: flex;
  align-items: center;
}

.nav-link {
  font-family: "Source Han Sans Bold", sans-serif;
  font-size: 16px;
  color: #ffffff;
  --el-button-text-color: #ffffff;
  --el-button-hover-text-color: #ffffff;
  --el-button-active-text-color: #ffffff;
}

.nav-link :deep(span) {
  color: inherit;
}

.hero-content {
  position: absolute;
  left: 50%;
  bottom: 48px;
  z-index: 2;
  width: min(1180px, calc(100% - 80px));
  transform: translateX(-50%);
  color: #ffffff;
}

.hero-title {
  max-width: 920px;
  margin: 0 0 14px;
  color: #ffffff;
  font-family: "Source Han Sans Bold", sans-serif;
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.3;
  text-shadow: 0 4px 18px rgba(0, 0, 0, 0.58);
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 9px;
  color: rgba(255, 255, 255, 0.82);
  font-family: "Source Han Sans Regular", sans-serif;
  font-size: 14px;
  line-height: 1.6;
  text-shadow: 0 3px 12px rgba(0, 0, 0, 0.54);
}

.meta-divider {
  color: rgba(255, 255, 255, 0.5);
}

@media (max-width: 640px) {
  .article-hero {
    height: 340px;
    min-height: 300px;
  }

  .site-header {
    height: 64px;
    padding: 0 20px;
  }

  .site-brand {
    font-size: 20px;
  }

  .nav-link {
    font-size: 15px;
  }

  .hero-content {
    bottom: 34px;
    width: calc(100% - 40px);
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-meta {
    font-size: 13px;
  }
}
</style>
