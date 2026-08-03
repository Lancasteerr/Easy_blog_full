import {createRouter, createWebHistory} from 'vue-router'

// 路由级懒加载，避免后台编辑器、Element Plus 组件和 Tiptap 过早进入首屏主包。
const MyHome = () => import('@/components/MyHome.vue')
const MyAbout = () => import("@/components/MyAbout.vue");
const UserLogin = () => import("@/components/UserLogin.vue");
// 注册页已关闭，暂不引入注册组件。
// const UserRegister = () => import("@/components/UserRegister.vue");
const PageNotFound = () => import("@/components/PageNotFound.vue");
const ManagePage = () => import("@/components/manage/ManagePage.vue");
const ArticleListPage = () => import("@/components/articlelist-index/ArticleListPage.vue");
const ArticleDetail = () => import("@/components/ArticleDetails/ArticleDetail.vue");
const ArticleEditPage = () => import("@/components/manage/manage-index/Article-edit/ArticleEditPage.vue");

const routes = [
    {   path :'/',
        name : 'MyHome',
        component: MyHome
    },
    {
        path: '/about',
        name: 'MyAbout',
        component: MyAbout,
        meta:{
            //需要登录后访问
            requireAuth:true,
        }
    },
    {
      path:'/neko-panel/manage',
      name: 'manage',
      component: ManagePage,
      meta:{
          requireAuth:true,
          // 管理相关页面统一由 App.vue 主滚动容器承载固定背景。
          useManageBackground:true,
      }
    },
    {
        path: '/neko-panel/login',
        name: 'UserLogin',
        component: UserLogin
    },
    // 注册页已关闭，保留原路由配置注释方便后续恢复。
    // {
    //     path: '/register',
    //     name: 'UserRegister',
    //     component: UserRegister
    // },
    {
        path:'/articlelist',
        name: 'ArticleList',
        component: ArticleListPage,
        meta:{
            // 文章列表页使用管理背景，保证滚动条轨道也显示花纹。
            useManageBackground:true,
        }
    },
    {
      path:'/article',
      name: 'ArticleDetailQuery',
      component: ArticleDetail
    },
    {
        path:'/article/:id',
        name: 'ArticleDetailRestful',
        component: ArticleDetail
    },
    {
      path:'/neko-panel/manage/edit',
      name:'editArticleQuery',
      component: ArticleEditPage,
      meta:{
          requireAuth:true,
          useManageBackground:true,
      }
    },
    {
        path:'/neko-panel/manage/edit/:id',
        name:'editArticleRestful',
        component: ArticleEditPage,
        meta:{
            requireAuth:true,
            useManageBackground:true,
        }
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'PageNotFound',
        component: PageNotFound
    },
]

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

export default router
