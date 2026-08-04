import axios from "axios";
import router from "@/router";

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
});

const getRequestPath = config => {
  const url = config?.url || "";

  if (!url) {
    return "";
  }

  if (/^https?:\/\//i.test(url)) {
    return new URL(url).pathname;
  }

  return url.startsWith("/") ? url : `/${url}`;
};

const isAdminRequest = config => {
  const path = getRequestPath(config);

  // axios 已经配置 baseURL=/api，所以实际写法可能是 /admin 或 /api/admin。
  return path.startsWith("/admin/") || path.startsWith("/api/admin/");
};

const isProtectedRoute = () =>
  router.currentRoute.value.matched.some(routeRecord => routeRecord.meta.requireAuth);

const redirectToLogin = () => {
  const currentRoute = router.currentRoute.value;

  if (currentRoute.path === "/neko-panel/login") {
    return;
  }

  router.push({
    path: "/neko-panel/login",
    query: { redirect: currentRoute.fullPath }
  }).catch(() => {});
};

request.interceptors.request.use(
  config => {
    const token = localStorage.getItem("token");

    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  error => Promise.reject(error)
);

request.interceptors.response.use(
  response => response,
  error => {
    const status = error.response?.status;

    if ((status === 401 || status === 403) && (isAdminRequest(error.config) || isProtectedRoute())) {
      // 只有后台接口或后台页面发生认证失败时才回登录页，公开内容错误由页面自己处理。
      localStorage.removeItem("token");
      redirectToLogin();
      return Promise.reject(error);
    }

    return Promise.reject(error);
  }
);

export default request;
