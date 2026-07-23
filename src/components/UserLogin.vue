<script setup>
import { reactive } from 'vue';
import { useRouter } from 'vue-router';
import { useStore } from 'vuex';
import request from '@/utils/request';

// 使用组合式API的方式获取router和store
const router = useRouter();
const store = useStore();

// 定义响应式的loginForm
const loginForm = reactive({
  userName: '',
  password: ''
});

// login函数
const login = async () => {
  try {
    console.log(store.state);

    const response = await request.post('/public/login', {
      userName: loginForm.userName,
      password: loginForm.password
    });

    if (response.data.code === 200) {
      // 利用store的commit调用login方法更新状态
      // store.commit('login', loginForm);
      localStorage.setItem("token",response.data.token)
      console.log(store.state);

      // 获取来源的URL，默认跳转到 /neko-panel/manage 页面
      const path = router.currentRoute.value.query.redirect;
      router.replace({ path: path === '/' || path === undefined ? '/neko-panel/manage' : path });
    } else {
      alert('账号或密码错误');
    }
  } catch (error) {
    console.error('Login failed:', error);
  }
};
</script>

<template>
  <div id="poster">
      <el-form class = "login-container" label-position="left" label-width="0px">
        <h3 class = "login_title" style="text-align: center">系统登录</h3>
        <el-form-item>
          <el-input type="text" v-model="loginForm.userName" auto-complete="off" placeholder="账号"></el-input>
        </el-form-item>
        <el-form-item>
          <el-input type="password" v-model="loginForm.password" auto-complete="off" placeholder="密码"></el-input>
        </el-form-item>
        <el-form-item style="width: 100%">
          <el-button type="primary" style="width: 100%;background:#505458;border:none" v-on:click="login">登录</el-button>
        </el-form-item>
      </el-form>
  </div>
</template>

<style scoped lang="scss">
  .login-container{
    border-radius: 15px;
    background-clip: padding-box;
    margin: 90px auto;
    width: 350px;
    padding: 35px 35px 15px 35px;
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid #eaeaea;
    //box-shadow: 0 0 15px #cac6c6;
  }
  #poster{
    background-image: url("../assets/loginbg.png");
    width: 100%;
    height: 100%;
    position: fixed;
    background-size: cover;
    background-position: center;
    display: flex;
    align-items: center;
  }
</style>
