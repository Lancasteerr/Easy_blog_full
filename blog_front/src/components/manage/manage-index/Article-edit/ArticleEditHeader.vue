<script setup>

import router from "@/router";

const goTohomepage = () =>{
  router.push('/');
}
const goBack = () =>{
  router.go(-1)
}

const logout = () =>{
  localStorage.removeItem("token");
  router.push({name:'MyHome'});
}

</script>


<template>
  <div>
    <el-header class="Manage-header">
      <div class="Manage-header-container">
        <div class="Manage-logo">
          <span class="manage-brand-text">Febrie's Blog</span>
        </div>
        <div class="Manage-header-items">
          <div class="function-items">
            <el-button class="to-homePage" link @click="goTohomepage">首页</el-button>
            <el-button class="Manage-Article" link @click="goBack">返回</el-button>
          </div>
          <div class="account-items">
            <el-button class="Login-Exit" link @click="logout"><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABoAAAAaCAYAAACpSkzOAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAzUlEQVR4nO2WsQ0CMQwAUwAlbEDHEvCsQcEEjEHHMwKrpEUMAMzwNAwANBwySqQUT/SYfIQQV1qOL46SyMYEAD1gDZzQUwGl1DKvcAmpKGMi2Y0wNkqAwncWS3qilTSu8xcJwAIYmQBg34boBpyBSRDrAza1yHMBZkG825ZIuAPLxotq4jveYwN0NCINFhjkEAlHYJhDdMghspqj22a5DDFIeb2/5cFec31B8yyfqqrOT4oql1N8IJm6GtHhJOW4tYqJZIAUme9MQ+0A+QAO5hz9b4NdIgAAAABJRU5ErkJggg==" alt="退出登录"></el-button>
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
.Manage-logo{
  display: flex;
  align-items: center;
  width: 60%;
  min-width: 0;
}
.manage-brand-text{
  // 编辑页 Header 使用文字品牌，与管理页保持统一。
  color: #ffffff;
  font-family: "Source Han Sans Bold", sans-serif;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
  white-space: nowrap;
}
.Manage-header-items{
  height: 100%;
  width: 40%;
  display: flex;
  justify-content: space-between;
}
.function-items{
  box-sizing: border-box;
  height: 100%;
  width: 85%;
  display: flex;
  justify-content: center;
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

@media (max-width: 640px) {
  .Manage-header-container{
    padding: 0 14px;
  }

  .Manage-logo{
    width: 48%;
  }

  .manage-brand-text{
    font-size: 22px;
  }
}
</style>
