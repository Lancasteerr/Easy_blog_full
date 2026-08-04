<script setup>
import { reactive } from 'vue';
import { useRouter } from 'vue-router';
import request from '@/utils/request';

// 使用组合式API的方式获取router
const router = useRouter();

// 定义响应式的loginForm
const loginForm = reactive({
  userName: '',
  password: ''
});

// 处理账号密码登录
const login = async () => {
  try {
    const response = await request.post('/public/login', {
      userName: loginForm.userName,
      password: loginForm.password
    });

    if (response.data.code === 200) {
      localStorage.setItem("token",response.data.token)

      // 获取来源的URL，默认跳转到 /neko-panel/manage 页面
      const path = router.currentRoute.value.query.redirect;
      router.replace({ path: path === '/' || path === undefined ? '/neko-panel/manage' : path });
    } else {
      alert('账号或密码错误');
    }
  } catch (error) {
    if (error.response?.status === 400) {
      alert('账号或密码错误，或登录过于频繁');
      return;
    }

    console.error('Login failed:', error);
    alert('登录失败，请稍后再试');
  }
};
</script>

<template>
  <div class="login-page">
    <section class="login-visual" aria-hidden="true"></section>

    <section class="login-panel">
      <div class="panel-lines" aria-hidden="true"></div>

      <div class="panel-top">
        <p class="panel-kicker">NEKO PANEL</p>
        <h1 class="login-title">系统登录</h1>
      </div>

      <div class="login-tabs">
        <span class="tab-item active">账号密码登录</span>
      </div>

      <el-form class="login-container" label-position="left" label-width="0px" @submit.prevent="login">
        <el-form-item>
          <el-input
            type="text"
            v-model="loginForm.userName"
            auto-complete="off"
            placeholder="请输入账号"
          ></el-input>
        </el-form-item>
        <el-form-item>
          <el-input
            type="password"
            v-model="loginForm.password"
            auto-complete="off"
            placeholder="请输入密码"
            show-password
          ></el-input>
        </el-form-item>
        <el-form-item class="login-action">
          <el-button class="login-button" type="primary" native-type="submit">登录</el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  position: fixed;
  inset: 0;
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(420px, 1fr);
  overflow: hidden;
  --grid-x: 196px;
  --grid-y: 196px;
  --grid-line-color: rgba(36, 36, 36, 0.065);
  --grid-node-color: rgba(36, 36, 36, 0.075);
  background: #ffffff;
  color: #222222;
  font-family: "Source Han Sans Regular", "Microsoft YaHei", sans-serif;
}

.login-visual,
.login-panel {
  position: relative;
  min-height: 100vh;
}

.login-visual {
  z-index: 1;
  // 左侧窗口单独绘制网格，右侧窗口不再共享这层线条。
  background-color: #ffffff;
  background-image:
    linear-gradient(
      to right,
      transparent calc(50% - 0.5px),
      var(--grid-line-color) calc(50% - 0.5px),
      var(--grid-line-color) calc(50% + 0.5px),
      transparent calc(50% + 0.5px)
    ),
    linear-gradient(
      to bottom,
      transparent calc(50% - 0.5px),
      var(--grid-line-color) calc(50% - 0.5px),
      var(--grid-line-color) calc(50% + 0.5px),
      transparent calc(50% + 0.5px)
    );
  background-size: var(--grid-x) var(--grid-y);
}

.login-visual::before,
.login-visual::after,
.login-panel::before,
.login-panel::after {
  content: "";
  position: absolute;
  pointer-events: none;
}

.login-visual::before {
  inset: 0;
  // 交点处只保留低透明度的小 X，和网格同频重复。
  background-image:
    linear-gradient(
      45deg,
      transparent calc(50% - 0.5px),
      var(--grid-node-color) calc(50% - 0.5px),
      var(--grid-node-color) calc(50% + 0.5px),
      transparent calc(50% + 0.5px)
    ),
    linear-gradient(
      -45deg,
      transparent calc(50% - 0.5px),
      var(--grid-node-color) calc(50% - 0.5px),
      var(--grid-node-color) calc(50% + 0.5px),
      transparent calc(50% + 0.5px)
    );
  background-size: var(--grid-x) var(--grid-y);
  -webkit-mask-image: radial-gradient(circle at center, #000000 0 24px, transparent 25px);
  -webkit-mask-size: var(--grid-x) var(--grid-y);
  mask-image: radial-gradient(circle at center, #000000 0 24px, transparent 25px);
  mask-size: var(--grid-x) var(--grid-y);
}

.login-visual::after {
  inset: 0;
  background-image: radial-gradient(circle at center, rgba(36, 36, 36, 0.035) 0 2px, transparent 2.5px);
  background-size: 98px 98px;
}

.login-panel {
  z-index: 2;
  display: grid;
  grid-template-rows: 28% 78px 1fr;
  // 右侧是完全不透明的上层窗口，不能透出左侧的任何线条。
  background: #ffffff;
  border-right: 1px solid #e8e8e8;
  box-shadow: -12px 0 24px rgba(0, 0, 0, 0.08);
}

.login-panel::before {
  left: 0;
  right: 0;
  top: 28%;
  border-top: 1px solid #e8e8e8;
}

.login-panel::after {
  left: 0;
  right: 0;
  top: calc(28% + 78px);
  border-top: 1px solid #e8e8e8;
}

.panel-lines {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  // 右侧窗口使用独立的浅灰结构线勾勒登录区域，不复用左侧网格。
  background-image:
    linear-gradient(to right, transparent 13%, #e9e9e9 13%, #e9e9e9 calc(13% + 1px), transparent calc(13% + 1px)),
    linear-gradient(to right, transparent calc(87% - 1px), #e9e9e9 calc(87% - 1px), #e9e9e9 87%, transparent 87%),
    linear-gradient(to bottom, transparent calc(100% - 104px), #e9e9e9 calc(100% - 104px), #e9e9e9 calc(100% - 103px), transparent calc(100% - 103px));
}

.panel-lines::before,
.panel-lines::after {
  content: "";
  position: absolute;
  bottom: 0;
  width: 13%;
  height: 104px;
  pointer-events: none;
}

.panel-lines::before {
  left: 0;
  border-bottom: 1px solid #e9e9e9;
  background: linear-gradient(135deg, transparent 49.4%, #e0e0e0 50%, transparent 50.6%);
}

.panel-lines::after {
  right: 0;
  border-bottom: 1px solid #e9e9e9;
  background: linear-gradient(135deg, transparent 49.4%, #e0e0e0 50%, transparent 50.6%);
}

.panel-top,
.login-tabs,
.login-container {
  position: relative;
  z-index: 1;
}

.panel-top {
  align-self: end;
  justify-self: center;
  width: min(400px, 72%);
  padding-bottom: 54px;
}

.panel-kicker {
  margin: 0 0 12px;
  color: #8e8e8e;
  font-size: 13px;
  letter-spacing: 0;
}

.login-title {
  margin: 0;
  color: #111111;
  font-family: "Source Han Sans Bold", "Microsoft YaHei", sans-serif;
  font-size: 34px;
  line-height: 1.2;
}

.login-tabs {
  position: relative;
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 96px;
}

.tab-item {
  position: relative;
  display: flex;
  align-items: center;
  height: 100%;
  color: #222222;
  font-family: "Source Han Sans Bold", "Microsoft YaHei", sans-serif;
  font-size: 16px;
}

.tab-item.active::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: #00a6ff;
}

.login-container {
  justify-self: center;
  width: min(400px, 72%);
  padding-top: 60px;
}

.login-container :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-container :deep(.el-input__wrapper) {
  height: 48px;
  padding: 0 16px;
  border-radius: 0;
  background: #f3f3f3;
  box-shadow: inset 0 0 0 1px transparent;
  transition: box-shadow 0.2s ease, background-color 0.2s ease;
}

.login-container :deep(.el-input__wrapper.is-focus) {
  background: #f7f7f7;
  box-shadow: inset 0 -2px 0 #00a6ff;
}

.login-container :deep(.el-input__inner) {
  color: #222222;
  font-size: 14px;
}

.login-container :deep(.el-input__inner::placeholder) {
  color: #9a9a9a;
}

.login-container :deep(.el-input__prefix),
.login-container :deep(.el-input__suffix) {
  color: #9a9a9a;
}

.login-action {
  margin-top: 38px;
}

.login-button {
  width: 100%;
  height: 48px;
  border: 1px solid #242424;
  border-radius: 0;
  background: #242424;
  color: #ffffff;
  font-family: "Source Han Sans Bold", "Microsoft YaHei", sans-serif;
  font-size: 16px;
  letter-spacing: 0;
  transition: background-color 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.login-button:hover,
.login-button:focus {
  border-color: #111111;
  background: #111111;
  box-shadow: inset 0 -2px 0 #00a6ff;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
    --grid-x: 160px;
    --grid-y: 150px;
  }

  .login-visual {
    display: none;
  }

  .login-panel {
    grid-template-rows: clamp(180px, 30vh, 260px) 70px 1fr;
    min-height: 100vh;
  }

  .login-panel::before {
    top: clamp(180px, 30vh, 260px);
  }

  .login-panel::after {
    top: calc(clamp(180px, 30vh, 260px) + 70px);
  }

  .panel-top,
  .login-container {
    width: min(400px, calc(100% - 48px));
  }

  .panel-top {
    padding-bottom: 36px;
  }

  .login-title {
    font-size: 30px;
  }

  .login-container {
    padding-top: 44px;
  }
}

@media (max-width: 480px) {
  .login-page {
    --grid-x: 130px;
    --grid-y: 120px;
  }

  .panel-top,
  .login-container {
    width: calc(100% - 36px);
  }

  .login-panel {
    grid-template-rows: 168px 64px 1fr;
  }

  .login-panel::before {
    top: 168px;
  }

  .login-panel::after {
    top: 232px;
  }

  .panel-kicker {
    font-size: 12px;
  }

  .login-title {
    font-size: 28px;
  }

  .tab-item {
    font-size: 15px;
  }

}
</style>
