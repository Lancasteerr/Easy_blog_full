<script setup>
import CommonFooterLayout from "@/components/Footer/CommonFooterLayout.vue";
import ArticleEditHeader from "@/components/manage/manage-index/Article-edit/ArticleEditHeader.vue";
import ManageIndex from "@/components/manage/manage-index/Manage-Index.vue";
</script>

<template>
  <div class="ManagerEdit-Container">
    <CommonFooterLayout>
      <!-- 管理页面Header -->
      <ArticleEditHeader></ArticleEditHeader>
      <!-- 主管理页面 状态切换-->
      <ManageIndex></ManageIndex>
    </CommonFooterLayout>
  </div>
</template>
