<template>
  <el-header class="header">
    <div id="header-div">
      <div id="logo">
        <span class="brand-text">Febrie's Blog</span>
      </div>
      <!-- 首页已移除登录和注册入口，仅保留站点文字。 -->
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

#logo {
  display: flex;
  align-items: center;
  min-width: 0;
}

.brand-text {
  // Header 品牌位使用文字替代图片 Logo，保持首屏识别一致。
  color: #ffffff;
  font-family: "Source Han Sans Bold", sans-serif;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
  white-space: nowrap;
}

@media (max-width: 640px) {
  #header-div {
    padding-left: 20px;
  }

  .brand-text {
    font-size: 24px;
  }
}

</style>
