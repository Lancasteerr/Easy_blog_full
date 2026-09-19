// 正文编辑器公开 H1-H3，旧文章中的更深层级标题仍由 Tiptap 保持兼容。
export const ARTICLE_HEADING_OPTIONS = Object.freeze([
  Object.freeze({ level: 1, label: "H1", title: "一级标题" }),
  Object.freeze({ level: 2, label: "H2", title: "二级标题" }),
  Object.freeze({ level: 3, label: "H3", title: "三级标题" }),
]);

// 目录与编辑器共用同一组标题层级，避免两处支持范围发生偏差。
export const ARTICLE_CATALOG_SELECTOR = ARTICLE_HEADING_OPTIONS
  .map(({ level }) => `h${level}`)
  .join(", ");
