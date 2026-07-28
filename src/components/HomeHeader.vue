<template>
  <el-header class="header">
    <div id="header-div">
      <div id="logo">
        <img src="../assets/nekoicon.png" alt="Logo">
      </div>
      <!-- 首页已移除登录和注册入口，仅保留站点 Logo。 -->
    </div>
  </el-header>
</template>

<style scoped lang="scss">
#header-div {
  box-sizing: border-box;
  height: 80px;
  width: 100%;
  justify-content: space-between;
  backdrop-filter: blur(8px);
  background-color: rgba(51,51,51,.88);
  display: flex;
  align-items: center;
  padding-left: 30px;
  padding-right: 20px;
  box-shadow: 0 0 15px #000;
}

#logo img {
  height: 70px;
  padding: 0;
}

</style>
