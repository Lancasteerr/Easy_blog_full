<script setup>

import ArticleList from "@/components/articlelist-index/ArticleList.vue";
</script>

<template>
 <div class="ArticleList-container">
  <ArticleList></ArticleList>
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
