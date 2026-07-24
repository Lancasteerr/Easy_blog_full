<script setup>

import router from "@/router";

const goTohomepage = () =>{
  router.push('/');
}
const goBack = () =>{
  router.go(-1)
}

</script>


<template>
  <div>
    <el-header class="Manage-header">
      <div class="Manage-header-container">
        <div id="Manage-logo" style="width: 60%">
          <img src="@/assets/nekoicon.png" alt="Logo">
        </div>
        <div class="Manage-header-items">
          <div class="function-items">
            <el-button class="to-homePage" link @click="goTohomepage">首页</el-button>
            <el-button class="Manage-Article" link @click="goBack">返回</el-button>
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
  justify-content: right;
}
.function-items{
  box-sizing: border-box;
  height: 100%;
  width: 85%;
  display: flex;
  justify-content: right;
  align-items: center;
}
.account-items{
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  width: 15%;
}
.to-homePage,
.Manage-Article{
  font-size: 20px;
  color: #ffffff;
  font-family: "Source Han Sans Bold" , sans-serif;
  --el-button-text-color: #ffffff;
  --el-button-hover-text-color: #ffffff;
  --el-button-active-text-color: #ffffff;
}
.Manage-Article{
  margin-left: 20px !important;
}

.to-homePage :deep(span),
.Manage-Article :deep(span){
  color: #ffffff;
}
</style>
