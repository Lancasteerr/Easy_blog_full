<script setup>
import router from "@/router";

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
    validator: value => ["solid", "transparent"].includes(value),
  },
});

const emit = defineEmits(["action"]);

const goToHomepage = () => {
  router.push("/");
};

const logout = () => {
  localStorage.removeItem("token");
  router.push({ name: "MyHome" });
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
    <el-header class="Manage-header">
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
}
</style>
