<script setup>
// 注册功能已关闭：保留原逻辑注释，防止组件再次发起注册请求。
// import {reactive} from "vue";
// import request from "@/utils/request";
// import router from "@/router";
//
// const registerForm = reactive({
//   userName:'',
//   password:'',
// });
//    const register = async () =>{
//      try {
//        const respond = await request.post('/public/register',{
//          userName: registerForm.userName,
//          password: registerForm.password,
//        });
//        if(respond.data.code===200) {
//          router.replace('/neko-panel/login')
//        } else {
//          alert("账号名重复")
//        }
//      }catch (error){
//          console.log("Register Fail: ",error);
//      }
// }

</script>

<template>
  <!-- 注册功能已关闭：页面结构保留注释，避免继续展示注册入口。 -->
  <!--
    <div id="poster">
      <el-form class = "register-container" label-position="left" label-width="0px">
        <h3 class = "register_title" style="text-align: center">注册用户</h3>
        <el-form-item>
          <el-input type="text" v-model="registerForm.userName" auto-complete="off" placeholder="账号"></el-input>
        </el-form-item>
        <el-form-item>
          <el-input type="password" v-model="registerForm.password" auto-complete="off" placeholder="密码"></el-input>
        </el-form-item>
        <el-form-item style="width: 100%">
          <el-button type="primary" style="width: 100%;background:#505458;border:none" v-on:click="register">注册</el-button>
        </el-form-item>
      </el-form>
    </div>
  -->
</template>

<style scoped lang="scss">
.register-container{
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
