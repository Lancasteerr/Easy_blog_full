<script setup>
import ArticleEditor from "@/components/manage/manage-index/Article-Editor.vue";
</script>

<template>
  <div class="index-container">
    <!-- MarkDown编辑器 -->
    <ArticleEditor></ArticleEditor>
  </div>
</template>

<style scoped lang="scss">
.index-container{
  width: min(1200px, calc(100% - 32px));
  margin: 0 auto 16px;
  // 桌面端 1200px 纸张内保留约 820px 写作列，窄屏时平滑收缩内边距。
  padding: 40px clamp(24px, 12.7vw, 190px);
  min-height: 800px;
  height: auto;
  background-color: #ffffff;
  border-radius: 12px;
  box-sizing: border-box;
}

@media (max-width: 640px) {
  .index-container{
    width: calc(100% - 16px);
    padding: 24px 16px 28px;
    border-radius: 10px;
  }
}
</style>
