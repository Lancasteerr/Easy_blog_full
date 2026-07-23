<script setup>
import ArticleEditor from "@/components/manage/manage-index/Article-Editor.vue";
</script>

<template>
  <div class="index-container">
    <!-- MarkDown编辑器 -->
    <ArticleEditor></ArticleEditor>
  </div>
</template>

<style scoped lang="scss">
.index-container{
  //position: absolute;
  //left: 10%;
  //top: 90px;
  margin-left: 10%;
  margin-top: 3%;
  width: 80%;
  height: 800px;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: rgb(255, 255, 255);
  border-radius: 12px;
  box-sizing: border-box;
}
</style>
