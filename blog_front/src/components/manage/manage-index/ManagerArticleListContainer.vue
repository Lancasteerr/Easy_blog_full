<script setup>

import ManageArticleList from "@/components/manage/manage-index/ManageArticleList.vue";
</script>

<template>
  <div class="ArticleList-container">
    <ManageArticleList></ManageArticleList>
  </div>
</template>

<style scoped lang="scss">
.ArticleList-container {
  width: min(860px, calc(100% - 32px));
  margin: 104px auto 0;
  padding: 18px;
  background-color: rgba(43, 47, 54, 0.88);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  box-shadow: 0 18px 45px rgba(0, 0, 0, 0.42), 0 2px 8px rgba(0, 0, 0, 0.24);
  box-sizing: border-box;
}
</style>
