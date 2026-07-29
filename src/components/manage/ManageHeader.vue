<script setup>

import router from "@/router";

const goTohomepage = () =>{
  router.push('/');
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
            <el-button class="Write-Article" link @click="$emit('switch-write')">写文章</el-button>
            <el-button class="Manage-Article" link @click="$emit('switch-list')">管理文章</el-button>
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
  backdrop-filter: blur(10px);
  background-color: rgba(33, 36, 42, 0.78);
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.28);
}
.Manage-logo{
  display: flex;
  align-items: center;
  width: 60%;
  min-width: 0;
}
.manage-brand-text{
  // 管理页 Header 使用文字品牌，避免继续依赖旧图片 Logo。
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
.Write-Article,
.Manage-Article{
  font-size: 20px;
  color: #ffffff;
  font-family: "Source Han Sans Bold" , sans-serif;
  --el-button-text-color: #ffffff;
  --el-button-hover-text-color: #f3ff00;
  --el-button-active-text-color: #f3ff00;
  transition: color 0.2s ease;
}
.Write-Article{
  margin-left: 20px !important;
}
.Manage-Article{
  margin-left: 20px !important;
}

.to-homePage :deep(span),
.Write-Article :deep(span),
.Manage-Article :deep(span){
  color: inherit;
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
