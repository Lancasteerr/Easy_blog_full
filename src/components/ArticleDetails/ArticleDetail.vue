<script setup>

import ArticleCard from "@/components/ArticleDetails/ArticleCard.vue";
import CommonFooterLayout from "@/components/Footer/CommonFooterLayout.vue";
import ArticleHeader from "@/components/ArticleDetails/ArticleHeader.vue";
</script>

<template>
  <div class="ArticleDetail-Container">
    <div class ="ArticleBackgroundImg"></div>
    <ArticleHeader></ArticleHeader>
    <CommonFooterLayout>
      <ArticleCard></ArticleCard>
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
