import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import request from './utils/request'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import './assets/fonts/font.css'

// 创建应用实例
const app = createApp(App)

// 全局注册 axios
app.config.globalProperties.$axios = request

// 使用 ElementPlus
app.use(ElementPlus)

// 使用 Vuex 状态管理
app.use(store)

// 使用 Vue Router
app.use(router)  // 在此时使用 router

// 路由守卫
router.beforeEach((to, from, next) => {

    if(localStorage.getItem("token") && to.path === '/neko-panel/login'){
        return next('/neko-panel/manage')
    }

    if (to.meta.requireAuth) {
        if (localStorage.getItem("token")) {
            next()
        } else {
            next({
                path: '/neko-panel/login',
                query: { redirect: to.fullPath }
            })
        }
    } else {
        next()
    }
})

// 挂载应用
app.mount('#app')
