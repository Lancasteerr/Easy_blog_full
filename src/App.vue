<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();

const appScrollbarClass = computed(() => ({
  // 背景放在主滚动容器上，透明滚动条轨道才能露出同一张花纹图。
  "app-scrollbar--manage-background": Boolean(route.meta.useManageBackground),
}));
</script>

<template>
  <div class="app-shell">
    <el-scrollbar class="app-scrollbar" :class="appScrollbarClass" view-class="app-scrollbar-view">
      <router-view></router-view>
    </el-scrollbar>
  </div>
</template>

<style lang="scss">
#app {
  width: 100%;
  height: 100%;
  margin-top: 0;
}

.app-shell {
  width: 100%;
  height: 100%;
}

body {
  margin: 0;
}
</style>
