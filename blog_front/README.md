# blog_front

从 `demo_front` 迁移过来的 Vite 版本博客前端。

## 开发

```sh
npm install
npm run dev
```

开发服务默认固定在 `http://127.0.0.1:8080/`，`/api` 会代理到后端 `http://localhost:5090`。

## 构建

```sh
npm run build
npm run preview
```

## Docker / Nginx

```sh
docker build -t blog-front:latest .
docker run --rm -p 8081:8080 blog-front:latest
```

生产部署推荐从单仓库根目录进入 `demo_bk`，使用 `docker-compose.prod.pull.yml` 拉取 GitHub Actions 预先构建好的 GHCR 镜像并启动前端 nginx、后端、MySQL 和 Redis。前端生产环境仍使用 `/api` 访问后端，由 nginx 在同源下完成反代。

当前版本使用 Vue 3、Vue Router 4、Element Plus、Axios、DOMPurify 和 Tiptap，不再依赖 Vue CLI、Webpack、Vuex 或 mavon-editor。
