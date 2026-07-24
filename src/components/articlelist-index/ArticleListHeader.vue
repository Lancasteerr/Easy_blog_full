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
  backdrop-filter: blur(10px);
  background-color: rgba(33, 36, 42, 0.78);
  border-bottom: 1px solid rgba(243, 255, 0, 0.18);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.28);
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
  --el-button-text-color: #ffffff;
  --el-button-hover-text-color: #f3ff00;
  --el-button-active-text-color: #f3ff00;
  transition: color 0.2s ease;
}

.to-homePage :deep(span){
  color: inherit;
}
</style>
