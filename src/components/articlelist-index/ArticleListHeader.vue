<script setup>

import router from "@/router";

const goTohomepage = () =>{
  router.push('/');
}
</script>


<template>
  <div>
    <el-header class="Manage-header">
      <div class="Manage-header-container">
        <div id="Manage-logo" style="width: 60%;display: flex">
          <img src="@/assets/nekoicon.png" alt="Logo">
          <div class="function-items">
            <el-button class="to-homePage" link @click="goTohomepage">首页</el-button>
          </div>
        </div>
      </div>
    </el-header>
  </div>
</template>

<style scoped lang="scss">
.Manage-header-container{
  box-sizing: border-box;
  display: flex;
  position: absolute;
  top: 0;
  left: 0;
  z-index: 10;
  height: 80px;
  width: 100%;
  padding: 0 20px;
  background-color: rgba(0,0,0,0);
  border-bottom: 1px solid hsla(0,0%,100%,.5);
}
#Manage-logo img{
  height: 70px;
  padding: 0;
}
.Manage-header-items{
  height: 100%;
  width: 40%;
  display: flex;
  justify-content: center;
}
.function-items{
  box-sizing: border-box;
  height: 100%;
  width: 90px;
  display: flex;
  justify-content: center;
  align-items: center;
}
.to-homePage{
  font-size: 20px;
  color: #ffffff;
  font-family: "Source Han Sans Bold" , sans-serif;
}
</style>
