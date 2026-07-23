import {createRouter, createWebHistory} from 'vue-router'
import MyHome from '@/components/MyHome.vue'
import MyAbout from "@/components/MyAbout.vue";
import UserLogin from "@/components/UserLogin.vue";
import UserRegister from "@/components/UserRegister.vue";
import PageNotFound from "@/components/PageNotFound.vue";
import ManagePage from "@/components/manage/ManagePage.vue";
import ArticleListPage from "@/components/articlelist-index/ArticleListPage.vue";
import ArticleDetail from "@/components/ArticleDetails/ArticleDetail.vue";
import ArticleEditPage from "@/components/manage/manage-index/Article-edit/ArticleEditPage.vue";

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
      }
    },
    {
        path: '/neko-panel/login',
        name: 'UserLogin',
        component: UserLogin
    },
    {
        path: '/register',
        name: 'UserRegister',
        component: UserRegister
    },
    {
        path:'/articlelist',
        name: 'ArticleList',
        component: ArticleListPage
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
      }
    },
    {
        path:'/neko-panel/manage/edit/:id',
        name:'editArticleRestful',
        component: ArticleEditPage,
        meta:{
            requireAuth:true,
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
