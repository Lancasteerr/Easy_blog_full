<script setup>
import router from "@/router";
import request from "@/utils/request";

defineOptions({
  name: "AdminHeader",
});

defineProps({
  actions: {
    type: Array,
    default: () => [],
  },
  variant: {
    type: String,
    default: "solid",
    validator: value => ["solid", "transparent", "floating"].includes(value),
  },
});

const emit = defineEmits(["action"]);

const goToHomepage = () => {
  router.push("/");
};

const logout = async () => {
  // 先触发路由守卫；编辑页若取消“放弃修改”，就不能提前注销并清空令牌。
  const navigationFailure = await router.push({ name: "MyHome" });

  if (navigationFailure) {
    return;
  }

  try {
    await request.post("/admin/logout");
  } catch (error) {
    // 后端注销失败时仍退出当前浏览器，避免用户被异常请求卡在后台页面。
    console.warn("退出登录请求失败:", error);
  } finally {
    localStorage.removeItem("token");
  }
};

const handleAction = action => {
  if (action.name === "home") {
    goToHomepage();
    return;
  }

  // 自定义动作交给外层页面处理，保持原有 Header 事件边界。
  emit("action", action.name);
};
</script>

<template>
  <div>
    <el-header class="Manage-header" :class="`is-${variant}`">
      <div class="Manage-header-container" :class="`is-${variant}`">
        <div class="Manage-logo">
          <span class="manage-brand-text">Febrie's Blog</span>
        </div>
        <div class="Manage-header-items">
          <div class="function-items">
            <el-button
              v-for="action in actions"
              :key="action.name"
              class="admin-nav-button"
              :class="action.className"
              link
              @click="handleAction(action)"
            >
              {{ action.label }}
            </el-button>
          </div>
          <div class="account-items">
            <el-button class="Login-Exit" link @click="logout">
              <img
                src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABoAAAAaCAYAAACpSkzOAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAzUlEQVR4nO2WsQ0CMQwAUwAlbEDHEvCsQcEEjEHHMwKrpEUMAMzwNAwANBwySqQUT/SYfIQQV1qOL46SyMYEAD1gDZzQUwGl1DKvcAmpKGMi2Y0wNkqAwncWS3qilTSu8xcJwAIYmQBg34boBpyBSRDrAza1yHMBZkG825ZIuAPLxotq4jveYwN0NCINFhjkEAlHYJhDdMghspqj22a5DDFIeb2/5cFec31B8yyfqqrOT4oql1N8IJm6GtHhJOW4tYqJZIAUme9MQ+0A+QAO5hz9b4NdIgAAAABJRU5ErkJggg=="
                alt="退出登录"
              >
            </el-button>
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
}

.Manage-header-container.is-solid{
  --admin-nav-hover-color: #f3ff00;
  backdrop-filter: blur(10px);
  background-color: rgba(33, 36, 42, 0.78);
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.28);
}

.Manage-header-container.is-transparent{
  --admin-nav-hover-color: #ffffff;
  background-color: rgba(0,0,0,0);
  border-bottom: 1px solid hsla(0,0%,100%,.5);
}

.Manage-header.is-floating{
  // 为 fixed 头部保留正常文档流高度，避免编辑卡片跳到头部下方。
  height: 100px;
  padding: 0;
}

.Manage-header.is-floating::before{
  // 滚动时遮住从悬浮头部上下缝隙穿过的正文，行为与参考页顶部遮罩一致。
  content: "";
  position: fixed;
  top: 0;
  left: 0;
  z-index: 199;
  width: 100%;
  height: 100px;
  background: #0b0b0b;
  pointer-events: none;
}

.Manage-header-container.is-floating{
  --admin-nav-hover-color: #c8f300;
  position: fixed;
  top: 16px;
  left: 50%;
  z-index: 200;
  width: min(1200px, calc(100% - 32px));
  height: 68px;
  padding: 0 28px;
  transform: translateX(-50%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background-color: rgba(45, 45, 45, 0.96);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.26);
  backdrop-filter: blur(12px);
}

.Manage-logo{
  display: flex;
  align-items: center;
  width: 60%;
  min-width: 0;
}

.manage-brand-text{
  // 后台 Header 统一使用文字品牌，避免管理页和编辑页各维护一套样式。
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

.admin-nav-button{
  font-size: 20px;
  color: #ffffff;
  font-family: "Source Han Sans Bold" , sans-serif;
  --el-button-text-color: #ffffff;
  --el-button-hover-text-color: var(--admin-nav-hover-color);
  --el-button-active-text-color: var(--admin-nav-hover-color);
  transition: color 0.2s ease;
}

.admin-nav-button + .admin-nav-button{
  margin-left: 20px !important;
}

.admin-nav-button :deep(span){
  color: inherit;
}

.Manage-header-container.is-transparent .admin-nav-button :deep(span){
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

  .Manage-header.is-floating{
    height: 80px;
  }

  .Manage-header.is-floating::before{
    height: 80px;
  }

  .Manage-header-container.is-floating{
    top: 8px;
    width: calc(100% - 16px);
    height: 64px;
    padding: 0 14px;
    border-radius: 10px;
  }

  .Manage-header-container.is-floating .Manage-logo{
    width: 45%;
  }

  .Manage-header-container.is-floating .Manage-header-items{
    width: 55%;
  }

  .Manage-header-container.is-floating .admin-nav-button{
    font-size: 16px;
  }

  .Manage-header-container.is-floating .admin-nav-button + .admin-nav-button{
    margin-left: 8px !important;
  }
}
</style>
