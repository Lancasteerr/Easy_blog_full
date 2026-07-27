<template>
  <div id="app">
    <el-scrollbar class="app-scrollbar" view-class="app-scrollbar-view">
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

body {
  margin: 0;
}
</style>
