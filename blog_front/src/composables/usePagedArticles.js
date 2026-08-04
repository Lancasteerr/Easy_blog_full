import { computed, ref } from "vue";
import { fetchPublicArticleList } from "@/api/articles";

const toPositiveNumber = (value, fallback) => {
  const numberValue = Number(value);

  return Number.isFinite(numberValue) && numberValue > 0 ? numberValue : fallback;
};

const isValidPageResult = (data, requestedPage, pageSize) => {
  const content = data?.content;
  const totalElements = Number(data?.totalElements);
  const currentPage = Number(data?.number);

  if (
    !Array.isArray(content)
    || !Number.isFinite(totalElements)
    || totalElements < 0
    || !Number.isFinite(currentPage)
    || currentPage < 1
  ) {
    return false;
  }

  const maxPage = totalElements > 0 ? Math.ceil(totalElements / pageSize) : 1;

  return !(totalElements > 0 && requestedPage > maxPage);
};

export const formatArticleDate = (date, fallback = "未知日期") => {
  if (!date) {
    return fallback;
  }

  const dateText = String(date);

  // 后端 LocalDateTime 可能带 T，这里统一裁剪成 yyyy-mm-dd。
  return dateText.includes("T") ? dateText.split("T")[0] : dateText.slice(0, 10);
};

export const formatArticleViewCount = viewCount => {
  const count = Number(viewCount);

  if (!Number.isFinite(count) || count <= 0) {
    return "0";
  }

  if (count >= 10000) {
    return `${(count / 10000).toFixed(count >= 100000 ? 0 : 1)}万`;
  }

  return String(count);
};

export const usePagedArticles = ({
  initialPage = 1,
  pageSize: initialPageSize = 10,
  sort,
  validatePageResult = false,
  onInvalidPageResult,
  onLoadError,
} = {}) => {
  const articles = ref([]);
  const total = ref(0);
  const page = ref(toPositiveNumber(initialPage, 1));
  const pageSize = ref(toPositiveNumber(initialPageSize, 10));
  const loading = ref(false);
  const loadFailed = ref(false);

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
  const pageDots = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1));

  const loadArticles = async () => {
    loading.value = true;
    loadFailed.value = false;

    const requestedPage = page.value;

    try {
      const res = await fetchPublicArticleList({
        page: requestedPage,
        size: pageSize.value,
        sort,
      });
      const data = res.data || {};

      if (validatePageResult && !isValidPageResult(data, requestedPage, pageSize.value)) {
        onInvalidPageResult?.(data);
        return false;
      }

      articles.value = Array.isArray(data.content) ? data.content : [];
      total.value = Number(data.totalElements) || 0;
      // 后端 PageResult.number 已经是 1 起始页码，直接同步给分页组件。
      page.value = Number(data.number) || page.value;

      return true;
    } catch (error) {
      loadFailed.value = true;

      if (onLoadError) {
        return onLoadError(error);
      }

      console.error("Get article_list fail:", error);
      return false;
    } finally {
      loading.value = false;
    }
  };

  const changePage = nextPage => {
    if (loading.value || nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) {
      return Promise.resolve(false);
    }

    page.value = nextPage;
    return loadArticles();
  };

  return {
    articles,
    total,
    page,
    pageSize,
    loading,
    loadFailed,
    totalPages,
    pageDots,
    loadArticles,
    changePage,
    formatArticleDate,
    formatViewCount: formatArticleViewCount,
  };
};
