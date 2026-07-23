# blog_front

从 `demo_front` 迁移过来的 Vite 版本博客前端。

## 开发

```sh
npm install
npm run dev
```

开发服务默认固定在 `http://127.0.0.1:8080/`，`/api` 会代理到后端 `http://localhost:8443`。

## 构建

```sh
npm run build
npm run preview
```

当前版本使用 Vue 3、Vue Router 4、Vuex 4、Element Plus、Axios 和 Tiptap，不再依赖 Vue CLI、Webpack 或 mavon-editor。
