const APP_SCROLL_WRAP_SELECTOR = ".app-scrollbar .el-scrollbar__wrap";

export const getAppScrollContainer = () => {
  if (typeof document === "undefined") {
    return null;
  }

  // App.vue 使用 Element Plus 的 el-scrollbar，真实滚动发生在内部 wrap 上，
  // 浏览器和 Vue Router 的默认滚动恢复不会自动处理这个容器。
  return document.querySelector(APP_SCROLL_WRAP_SELECTOR);
};

export const getAppScrollTop = () => {
  const container = getAppScrollContainer();

  return container?.scrollTop || 0;
};

export const setAppScrollTop = top => {
  const container = getAppScrollContainer();

  if (!container) {
    return;
  }

  container.scrollTop = Number.isFinite(top) ? Math.max(top, 0) : 0;
};

export const scrollAppToTop = () => {
  setAppScrollTop(0);
};
