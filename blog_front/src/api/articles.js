import request from "@/utils/request";

// 文章列表接口统一收口，避免多个页面重复拼接同一组请求参数。
export const fetchPublicArticleList = ({ page, size, sort } = {}) => {
  const params = {
    page,
    size,
  };

  if (sort) {
    params.sort = sort;
  }

  return request.get("/public/get_article_list", { params });
};
