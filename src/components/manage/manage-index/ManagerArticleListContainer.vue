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
  margin-left: 25%;
  margin-top: 3%;
  width: 50%;

  padding: 10px;
  background-color: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(8px);
  border-radius: 12px;
  box-sizing: border-box;
}
</style>
