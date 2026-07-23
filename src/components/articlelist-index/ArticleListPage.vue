<script setup>

import ArticleListContainer from "@/components/articlelist-index/ArticleList-container.vue";
import ArticleListHeader from "@/components/articlelist-index/ArticleListHeader.vue";
import CommonFooterLayout from "@/components/Footer/CommonFooterLayout.vue";
</script>

<template>
  <div class="ArticleListImg-Container">
    <ArticleListHeader></ArticleListHeader>
    <div class ="ArticleBackgroundImg"></div>
    <CommonFooterLayout>
      <ArticleListContainer></ArticleListContainer>
    </CommonFooterLayout>
  </div>
</template>

<style scoped lang="scss">
.ArticleBackgroundImg{
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: url('@/assets/ManageBackground/1.jpg');
  background-size: cover;
  background-position: center;
  z-index: -1;
}
</style>
