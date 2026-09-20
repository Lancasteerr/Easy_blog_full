<script setup>
import ManageHeader from "@/components/manage/ManageHeader.vue";
import CommonFooterLayout from "@/components/Footer/CommonFooterLayout.vue";
import ManagerArticleListContainer from "@/components/manage/manage-index/ManagerArticleListContainer.vue";
import { useRouter } from "vue-router";

const router = useRouter();

const goToWriteArticle = () => {
  // 新建与修改统一进入独立编辑路由，避免管理页维护第二套写作外壳。
  router.push({ name: "editArticleQuery" });
};

</script>

<template>
  <div class="Manager-Container">
    <!-- 管理页面Header -->
    <ManageHeader
      @switch-write="goToWriteArticle"
    ></ManageHeader>
    <CommonFooterLayout>
      <!-- 管理页只承载文章列表，写作流程统一交给独立编辑路由。 -->
      <ManagerArticleListContainer></ManagerArticleListContainer>
    </CommonFooterLayout>
  </div>
</template>
