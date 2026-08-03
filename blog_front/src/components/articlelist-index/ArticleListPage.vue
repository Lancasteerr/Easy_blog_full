<script setup>

import ArticleListContainer from "@/components/articlelist-index/ArticleList-container.vue";
import ArticleListHeader from "@/components/articlelist-index/ArticleListHeader.vue";
import CommonFooterLayout from "@/components/Footer/CommonFooterLayout.vue";
</script>

<template>
  <div class="ArticleListImg-Container">
    <ArticleListHeader></ArticleListHeader>
    <CommonFooterLayout>
      <ArticleListContainer></ArticleListContainer>
    </CommonFooterLayout>
  </div>
</template>
