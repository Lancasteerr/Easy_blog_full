import hljs from "highlight.js/lib/common";
import { common, createLowlight } from "lowlight";

const LANGUAGE_CLASS_PREFIX = "language-";

const LANGUAGE_ALIASES = {
  html: "xml",
  vue: "xml",
  js: "javascript",
  jsx: "javascript",
  ts: "typescript",
  tsx: "typescript",
  sh: "bash",
  shell: "bash",
  yml: "yaml",
  md: "markdown",
  text: "plaintext",
  plain: "plaintext",
};

export const codeLowlight = createLowlight(common);

export const CODE_LANGUAGE_OPTIONS = Object.freeze([
  { label: "自动识别", value: "" },
  { label: "Java", value: "java" },
  { label: "JavaScript", value: "javascript" },
  { label: "TypeScript", value: "typescript" },
  { label: "HTML / XML", value: "html" },
  { label: "CSS", value: "css" },
  { label: "JSON", value: "json" },
  { label: "SQL", value: "sql" },
  { label: "Bash", value: "bash" },
  { label: "Python", value: "python" },
  { label: "Markdown", value: "markdown" },
  { label: "YAML", value: "yaml" },
  { label: "Plain text", value: "plaintext" },
]);

const HIGHLIGHT_AUTO_LANGUAGES = [
  "java",
  "javascript",
  "typescript",
  "xml",
  "css",
  "json",
  "sql",
  "bash",
  "python",
  "markdown",
  "yaml",
  "c",
  "cpp",
  "csharp",
  "go",
  "rust",
].filter(language => hljs.getLanguage(language));

export const normalizeCodeLanguage = language => {
  const normalized = String(language || "")
    .trim()
    .toLowerCase()
    .replace(new RegExp(`^${LANGUAGE_CLASS_PREFIX}`), "");

  return LANGUAGE_ALIASES[normalized] || normalized;
};

const getLanguageFromCodeElement = codeElement => {
  const classLanguage = [...codeElement.classList]
    .find(className => className.startsWith(LANGUAGE_CLASS_PREFIX))
    ?.slice(LANGUAGE_CLASS_PREFIX.length);
  const dataLanguage =
    codeElement.getAttribute("data-language") ||
    codeElement.parentElement?.getAttribute("data-language");

  return normalizeCodeLanguage(classLanguage || dataLanguage);
};

const setHighlightedHtml = (codeElement, highlightResult, language) => {
  codeElement.innerHTML = highlightResult.value;
  codeElement.classList.add("hljs");

  if (language && ![...codeElement.classList].some(className => className.startsWith(LANGUAGE_CLASS_PREFIX))) {
    codeElement.classList.add(`${LANGUAGE_CLASS_PREFIX}${language}`);
  }
};

export const highlightArticleCode = container => {
  if (!container) return;

  container.querySelectorAll("pre code").forEach(codeElement => {
    const codeText = codeElement.textContent || "";

    if (!codeText.trim()) {
      return;
    }

    const language = getLanguageFromCodeElement(codeElement);

    try {
      if (language && hljs.getLanguage(language)) {
        // 文章内容已先经过 DOMPurify 清洗，这里只基于 textContent 生成高亮 HTML，避免执行用户输入。
        const result = hljs.highlight(codeText, {
          language,
          ignoreIllegals: true,
        });
        setHighlightedHtml(codeElement, result, language);
        return;
      }

      // 旧文章通常没有语言 class，限定常用语言自动识别可以降低误判和包体开销。
      const result = hljs.highlightAuto(codeText, HIGHLIGHT_AUTO_LANGUAGES);
      setHighlightedHtml(codeElement, result, result.language);
    } catch (error) {
      console.warn("Code highlight failed:", error);
      codeElement.classList.add("hljs");
    }
  });
};
