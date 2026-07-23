<script setup>
import CommonFooterLayout from "@/components/Footer/CommonFooterLayout.vue";
import ArticleEditHeader from "@/components/manage/manage-index/Article-edit/ArticleEditHeader.vue";
import ManageIndex from "@/components/manage/manage-index/Manage-Index.vue";
</script>

<template>
  <div class="ManagerEdit-Container">
    <!-- 管理页面背景 -->
    <div class ="ArticleBackgroundImg"></div>
    <CommonFooterLayout>
      <!-- 管理页面Header -->
      <ArticleEditHeader></ArticleEditHeader>
      <!-- 主管理页面 状态切换-->
      <ManageIndex></ManageIndex>
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
