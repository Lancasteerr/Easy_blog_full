import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import request from './utils/request'
import {
    ElButton,
    ElCard,
    ElCarousel,
    ElCarouselItem,
    ElForm,
    ElFormItem,
    ElHeader,
    ElIcon,
    ElInput,
    ElPagination,
    ElScrollbar,
} from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-card.css'
import 'element-plus/theme-chalk/el-carousel.css'
import 'element-plus/theme-chalk/el-carousel-item.css'
import 'element-plus/theme-chalk/el-form.css'
import 'element-plus/theme-chalk/el-form-item.css'
import 'element-plus/theme-chalk/el-header.css'
import 'element-plus/theme-chalk/el-icon.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-pagination.css'
import 'element-plus/theme-chalk/el-scrollbar.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-overlay.css'

import './assets/fonts/font.css'
import './assets/markdown.css'
import './assets/scrollbar.css'

const elementPlusComponents = [
    ElButton,
    ElCard,
    ElCarousel,
    ElCarouselItem,
    ElForm,
    ElFormItem,
    ElHeader,
    ElIcon,
    ElInput,
    ElPagination,
    ElScrollbar,
]

// 创建应用实例
const app = createApp(App)

// 全局注册 axios
app.config.globalProperties.$axios = request

// 只注册当前模板实际使用的 Element Plus 组件，降低生产包体积。
elementPlusComponents.forEach(component => {
    app.component(component.name, component)
})

// 使用 Vue Router
app.use(router)  // 在此时使用 router

// 路由守卫
router.beforeEach((to, from, next) => {
    const hasToken = Boolean(localStorage.getItem("token"))
    const requireAuth = to.matched.some(routeRecord => routeRecord.meta.requireAuth)

    if(hasToken && to.path === '/neko-panel/login'){
        return next('/neko-panel/manage')
    }

    if (requireAuth && !hasToken) {
        // 只有后台管理路由要求登录，公开页面的错误访问交给 404 页面处理。
        return next({
            path: '/neko-panel/login',
            query: { redirect: to.fullPath }
        })
    }

    next()
})

// 挂载应用
app.mount('#app')
