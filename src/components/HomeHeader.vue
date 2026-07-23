<script setup>

import router from "@/router";

const goToLoginpage = () => {
  router.push('/neko-panel/login')
}

const goToRegisterpage = () => {
  router.push('/register')
}
</script>

<template>
  <el-header class="header">
    <div id="header-div">
      <div id="logo">
        <img src="../assets/nekoicon.png" alt="Logo">
      </div>
        <div class="butten-container">
          <el-button class="Loginbutton" link @click="goToLoginpage">登录</el-button>
          <div class="separator"></div>
          <el-button class="Register" link @click="goToRegisterpage">注册</el-button>
        </div>
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

.butten-container{
  display: flex;
  align-items: center;
}
.separator{
  width: 2px;
  height: 18px;
  background-color: #ffffff;
  margin: 0 13px;
}
.Loginbutton{
  font-size: 16px;
  color: #ffffff;
}
.Register{
  font-size: 16px;
  color: #ffffff;
}
</style>
