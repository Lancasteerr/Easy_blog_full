<script setup>

import CommonPageFoot from "@/components/Footer/CommonPageFoot.vue";
</script>

<template>
  <div class="layout">
    <main class="content">
      <slot></slot>
    </main>

    <CommonPageFoot class="common-footer"></CommonPageFoot>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;        /* 保证占满整屏 */
  display: flex;
  flex-direction: column;
}

.content {
  flex: 1;                  /* 自动填充剩余空间，让 footer 贴底 */
}
.common-footer{
  margin-top: 23px;
}
</style>
