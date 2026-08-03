import axios from "axios";
import router from "@/router";

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
});

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

    if (status === 401) {
      router.push("/neko-panel/login");
      return Promise.reject(error);
    }

    if (status === 403) {
      localStorage.removeItem("token");
      router.push("/neko-panel/login");
      return Promise.reject(error);
    }

    return Promise.reject(error);
  }
);

export default request;
