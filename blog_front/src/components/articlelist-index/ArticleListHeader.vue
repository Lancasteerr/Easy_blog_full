<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import router from "@/router";
import { getAppScrollContainer } from "@/utils/appScroll";

const isHeaderHidden = ref(false);
const SCROLL_HIDE_THRESHOLD = 8;
const TOP_VISIBLE_DISTANCE = 4;
let scrollContainer = null;
let lastScrollTop = 0;

const goToHomepage = () => {
  router.push("/");
};

const getCurrentScrollTop = () => {
  return Math.max(scrollContainer?.scrollTop || 0, 0);
};

const updateHeaderVisible = () => {
  const currentScrollTop = getCurrentScrollTop();

  // 回到页面顶部时始终展开 header，避免首屏入口被隐藏。
  if (currentScrollTop <= TOP_VISIBLE_DISTANCE) {
    isHeaderHidden.value = false;
    lastScrollTop = currentScrollTop;
    return;
  }

  const scrollDelta = currentScrollTop - lastScrollTop;

  if (Math.abs(scrollDelta) < SCROLL_HIDE_THRESHOLD) {
    return;
  }

  // 向下滚动收起，向上滚动重新放出。
  isHeaderHidden.value = scrollDelta > 0;
  lastScrollTop = currentScrollTop;
};

onMounted(async () => {
  await nextTick();

  // 页面主滚动发生在 App.vue 的 el-scrollbar 内部 wrap 上。
  scrollContainer = getAppScrollContainer();
  lastScrollTop = getCurrentScrollTop();
  scrollContainer?.addEventListener("scroll", updateHeaderVisible, { passive: true });
  updateHeaderVisible();
});

onBeforeUnmount(() => {
  scrollContainer?.removeEventListener("scroll", updateHeaderVisible);
});
</script>


<template>
  <el-header class="Manage-header" :class="{ 'is-hidden': isHeaderHidden }">
    <div class="Manage-header-container">
      <button class="home-logo-button" type="button" aria-label="返回首页" @click="goToHomepage">
        <span class="home-brand-text">Febrie's Blog</span>
      </button>
    </div>
  </el-header>
</template>

<style scoped lang="scss">
.Manage-header{
  --el-header-height: 72px;
  position: fixed;
  top: 0;
  left: 0;
  z-index: 10;
  width: 100%;
  height: 72px;
  padding: 0;
  transform: translateY(0);
  transition: transform 0.24s ease;
  will-change: transform;
}

.Manage-header.is-hidden{
  transform: translateY(-100%);
}

.Manage-header-container{
  box-sizing: border-box;
  display: flex;
  height: 72px;
  width: 100%;
  align-items: center;
  justify-content: flex-start;
  padding: 0 24px;
  backdrop-filter: blur(10px);
  background-color: rgba(33, 36, 42, 0.78);
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.28);
}

.home-logo-button{
  width: auto;
  max-width: 100%;
  height: 72px;
  padding: 0;
  border: 0;
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  outline: none;
}

.home-brand-text{
  // 列表页 Header 保留返回首页能力，视觉上统一为文字品牌。
  color: #ffffff;
  font-family: "Source Han Sans Bold", sans-serif;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .Manage-header-container{
    padding: 0 16px;
  }

  .home-brand-text{
    font-size: 24px;
  }
}
</style>
