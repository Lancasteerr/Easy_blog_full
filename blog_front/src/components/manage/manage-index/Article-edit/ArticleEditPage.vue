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

<style scoped>
.ManagerEdit-Container {
  /* 工具栏与悬浮头部共享同一个吸顶基准，避免两处高度调整后互相遮挡。 */
  --article-editor-sticky-top: 100px;
  min-height: 100vh;
}

@media (max-width: 640px) {
  .ManagerEdit-Container {
    --article-editor-sticky-top: 80px;
  }
}
</style>
