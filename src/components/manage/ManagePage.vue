<script setup>
import ManageHeader from "@/components/manage/ManageHeader.vue";
import ManageIndex from "@/components/manage/manage-index/Manage-Index.vue";
import CommonFooterLayout from "@/components/Footer/CommonFooterLayout.vue";
import ManagerArticleListContainer from "@/components/manage/manage-index/ManagerArticleListContainer.vue";
import {computed, ref} from "vue";

const articleView = ref("list");

const currentComponet = computed(() =>{
  return articleView.value === "list" ? ManagerArticleListContainer:ManageIndex;
})

</script>

<template>
  <div class="Manager-Container">
    <!-- 管理页面Header -->
    <ManageHeader
      @switch-write = "articleView = 'write'"
      @switch-list = "articleView = 'list'"
    ></ManageHeader>
    <div class ="ArticleBackgroundImg"></div>
    <CommonFooterLayout>
      <!-- 主管理页面 状态切换-->
      <component :is="currentComponet"></component>
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
