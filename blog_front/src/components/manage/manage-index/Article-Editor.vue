<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { EditorContent, useEditor } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";
import CodeBlockLowlight from "@tiptap/extension-code-block-lowlight";
import Image from "@tiptap/extension-image";
import request from "@/utils/request";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { CODE_LANGUAGE_OPTIONS, codeLowlight } from "@/utils/codeHighlight";
import { ARTICLE_HEADING_OPTIONS } from "@/utils/articleHeadings";

const fileInput = ref(null);
const coverFileInput = ref(null);
const contentImageIds = ref(new Set());
const boundImageIds = ref(new Set());
const sessionUploadedImages = ref(new Map());
// 记录本次会话新上传但尚未保存绑定的封面，离开页面时需要清理临时文件。
const sessionUploadedCoverId = ref(null);
const coverUploading = ref(false);
const route = useRoute();
const router = useRouter();
// Tiptap 编辑器实例不是普通响应式对象，使用计数器驱动工具栏状态重新计算。
const editorStateTick = ref(0);
const isDirty = ref(false);
const isSaving = ref(false);
// 加载已有文章时会连续写入表单与编辑器，此阶段不能误判为用户修改。
const isHydrating = ref(true);
// 与后端及数据库的文章标题、概要字符上限保持一致。
const ARTICLE_TITLE_MAX_LENGTH = 40;
const ARTICLE_ABSTRACT_MAX_LENGTH = 100;

const article = reactive({
  id: null,
  articleTitle: "",
  articleAbstract: "",
  articleContentHtml: "",
  articleContentJson: null,
  articleDate: "",
  articleCover: null,
  coverObjectUrl: "",
});

const markDirty = () => {
  if (!isHydrating.value) {
    isDirty.value = true;
  }
};

const ImageWithFileId = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      fileId: {
        default: null,
        parseHTML: element => element.getAttribute("data-file-id"),
        renderHTML: attributes => {
          if (!attributes.fileId) {
            return {};
          }

          return {
            "data-file-id": attributes.fileId,
          };
        },
      },
    };
  },
});

const collectImages = doc => {
  const images = {
    ids: new Set(),
    srcs: new Set(),
  };

  const walk = node => {
    if (!node) return;

    if (node.type === "image") {
      if (node.attrs?.fileId) {
        images.ids.add(String(node.attrs.fileId));
      }

      if (node.attrs?.src) {
        images.srcs.add(String(node.attrs.src));
      }
    }

    if (Array.isArray(node.content)) {
      node.content.forEach(walk);
    }
  };

  walk(doc);
  return images;
};

const deleteImageById = async id => {
  try {
    await request.delete(`/admin/files/${id}`);
    sessionUploadedImages.value.delete(id);
  } catch (error) {
    console.error("Delete uploaded image failed:", error);
    ElMessage.warning(`图片 ${id} 删除失败，请稍后在后台清理`);
  }
};

const uploadImageFile = async file => {
  const formData = new FormData();
  formData.append("file", file);

  const res = await request.post("/admin/files/upload", formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });

  return res.data;
};

const deleteRemovedImages = async currentImages => {
  const currentIds = currentImages.ids;
  const currentSrcs = currentImages.srcs;
  const removedIds = [
    ...new Set([
      ...[...sessionUploadedImages.value.entries()]
        .filter(([id, src]) =>
          !boundImageIds.value.has(id) && !currentIds.has(id) && !currentSrcs.has(src)
        )
        .map(([id]) => id),
    ]),
  ];

  contentImageIds.value = currentIds;
  await Promise.all(removedIds.map(deleteImageById));
};

const refreshEditorState = () => {
  editorStateTick.value += 1;
};

const editor = useEditor({
  content: "<p></p>",
  extensions: [
    StarterKit.configure({
      // 关闭 StarterKit 内置代码块，改用带语法高亮能力的 CodeBlockLowlight。
      codeBlock: false,
      link: {
        openOnClick: false,
        defaultProtocol: "https",
        HTMLAttributes: {
          target: "_blank",
          rel: "noopener noreferrer nofollow",
        },
      },
    }),
    CodeBlockLowlight.configure({
      lowlight: codeLowlight,
      defaultLanguage: null,
      enableTabIndentation: true,
      tabSize: 2,
    }),
    ImageWithFileId.configure({
      allowBase64: false,
      inline: false,
    }),
  ],
  editorProps: {
    attributes: {
      class: "simple-editor-content markdown-body",
    },
  },
  onUpdate: ({ editor }) => {
    refreshEditorState();
    markDirty();
    deleteRemovedImages(collectImages(editor.getJSON()));
  },
  onSelectionUpdate: refreshEditorState,
  onTransaction: refreshEditorState,
});

const canUndo = computed(() => {
  editorStateTick.value;
  return editor.value?.can().undo() ?? false;
});
const canRedo = computed(() => {
  editorStateTick.value;
  return editor.value?.can().redo() ?? false;
});
const isEmpty = computed(() => {
  editorStateTick.value;
  return editor.value?.isEmpty ?? true;
});
// Array.from 按 Unicode 码点拆分，保证 emoji 与后端一样按一个字符计算。
const countUnicodeCharacters = value => Array.from(value || "").length;
const articleTitleLength = computed(() => countUnicodeCharacters(article.articleTitle));
const isArticleTitleTooLong = computed(() => articleTitleLength.value > ARTICLE_TITLE_MAX_LENGTH);
const articleAbstractLength = computed(() => countUnicodeCharacters(article.articleAbstract));
const isArticleAbstractTooLong = computed(
  () => articleAbstractLength.value > ARTICLE_ABSTRACT_MAX_LENGTH
);
const isPublishDisabled = computed(
  () =>
    isEmpty.value ||
    !article.articleTitle.trim() ||
    isArticleTitleTooLong.value ||
    isArticleAbstractTooLong.value ||
    isSaving.value
);
const articleContentLength = computed(() => {
  editorStateTick.value;
  return countUnicodeCharacters(editor.value?.getText({ blockSeparator: "\n" }) || "");
});
const publishStatusText = computed(() => {
  if (isSaving.value) return "发布中";
  if (isDirty.value) return "有未发布修改";
  return article.id ? "已发布" : "未发布";
});
const publishStatusClass = computed(() => ({
  "is-saving": isSaving.value,
  "is-dirty": isDirty.value && !isSaving.value,
  "is-published": Boolean(article.id) && !isDirty.value && !isSaving.value,
}));
const isCodeBlockActive = computed(() => {
  editorStateTick.value;
  return editor.value?.isActive("codeBlock") ?? false;
});
const currentCodeLanguage = computed(() => {
  editorStateTick.value;

  if (!isCodeBlockActive.value) {
    return "";
  }

  return editor.value?.getAttributes("codeBlock").language || "";
});
const currentBlockType = computed(() => {
  editorStateTick.value;

  for (const heading of ARTICLE_HEADING_OPTIONS) {
    if (editor.value?.isActive("heading", { level: heading.level })) {
      return `heading-${heading.level}`;
    }
  }

  return "paragraph";
});

const setEditorContent = content => {
  if (!editor.value) return;

  if (content) {
    editor.value.commands.setContent(content, false);
  } else {
    editor.value.commands.clearContent(false);
  }

  contentImageIds.value = collectImages(editor.value.getJSON()).ids;
  boundImageIds.value = new Set(contentImageIds.value);
  refreshEditorState();
};

const setCodeBlockLanguage = event => {
  if (!editor.value || !isCodeBlockActive.value) return;

  const language = event.target.value || null;

  // 语言写入 codeBlock 属性后，保存的 HTML 会带上 language-* class，阅读页可直接使用。
  editor.value.chain().focus().updateAttributes("codeBlock", { language }).run();
  refreshEditorState();
};

const setBlockType = event => {
  if (!editor.value) return;

  const value = event.target.value;
  const chain = editor.value.chain().focus();

  if (value === "paragraph") {
    chain.setParagraph().run();
    return;
  }

  const level = Number.parseInt(value.replace("heading-", ""), 10);

  if (ARTICLE_HEADING_OPTIONS.some(option => option.level === level)) {
    chain.setHeading({ level }).run();
  }
};

const clearFormatting = () => {
  editor.value?.chain().focus().unsetAllMarks().clearNodes().run();
};

const parseJsonContent = value => {
  if (!value) return null;

  if (typeof value === "object") {
    return value;
  }

  try {
    return JSON.parse(value);
  } catch (error) {
    console.warn("Parse articleContentJson failed:", error);
    return null;
  }
};

const normalizeArticleId = id => {
  const idText = String(id ?? "").trim();

  if (!/^\d+$/.test(idText)) {
    return null;
  }

  const numericId = Number.parseInt(idText, 10);
  return numericId > 0 ? numericId : null;
};

const isNotFoundStatus = error => [400, 404].includes(error.response?.status);

const goToNotFound = () => {
  // 编辑已有文章时，非法 ID 或文章不存在都展示错误页；新建文章路径不受影响。
  router.replace("/404");
};

const loadArticle = async id => {
  const normalizedId = normalizeArticleId(id);

  if (!normalizedId) {
    goToNotFound();
    return;
  }

  try {
    const res = await request.get("/public/article", {
      params: { id: normalizedId },
    });

    const data = res.data;

    if (!data || !data.id) {
      goToNotFound();
      return;
    }

    article.id = data.id;
    article.articleTitle = data.articleTitle || "";
    article.articleAbstract = data.articleAbstract || "";
    article.articleContentHtml = data.articleContentHtml || "";
    article.articleContentJson = parseJsonContent(data.articleContentJson);
    article.articleCover = data.articleCover ?? null;
    article.coverObjectUrl = data.coverObjectUrl || data.coverURL || "";

    await nextTick();
    setEditorContent(article.articleContentJson || article.articleContentHtml || "<p></p>");
    isDirty.value = false;
  } catch (error) {
    if (isNotFoundStatus(error)) {
      goToNotFound();
      return;
    }

    console.error("Load edit article failed:", error);
    ElMessage.error("文章加载失败");
  } finally {
    isHydrating.value = false;
  }
};

const chooseImage = () => {
  fileInput.value?.click();
};

const chooseCover = () => {
  coverFileInput.value?.click();
};

const clearSessionCover = async () => {
  if (!sessionUploadedCoverId.value) return;

  await deleteImageById(sessionUploadedCoverId.value);
  sessionUploadedCoverId.value = null;
};

const uploadCover = async event => {
  const file = event.target.files?.[0];
  event.target.value = "";

  if (!file) return;

  if (!file.type.startsWith("image/")) {
    ElMessage.warning("请选择图片文件");
    return;
  }

  const previousSessionCoverId = sessionUploadedCoverId.value;
  coverUploading.value = true;

  try {
    const image = await uploadImageFile(file);
    const fileId = String(image.id);

    // 新封面上传成功后再清理上一张临时封面，避免上传失败时丢失当前预览。
    if (previousSessionCoverId && previousSessionCoverId !== fileId) {
      await deleteImageById(previousSessionCoverId);
    }

    article.articleCover = fileId;
    article.coverObjectUrl = image.url;
    sessionUploadedCoverId.value = fileId;
    ElMessage.success("封面上传成功");
  } catch (error) {
    console.error("Upload cover failed:", error);
    ElMessage.error("封面上传失败");
  } finally {
    coverUploading.value = false;
  }
};

const removeArticleCover = async () => {
  // 只主动删除本次会话上传的临时封面；已保存的旧封面交给后端在保存文章时释放。
  await clearSessionCover();
  article.articleCover = null;
  article.coverObjectUrl = "";
};

const uploadImage = async event => {
  const file = event.target.files?.[0];
  event.target.value = "";

  if (!file || !editor.value) return;

  if (!file.type.startsWith("image/")) {
    ElMessage.warning("请选择图片文件");
    return;
  }

  try {
    const image = await uploadImageFile(file);
    const fileId = String(image.id);
    sessionUploadedImages.value.set(fileId, image.url);

    editor.value
      .chain()
      .focus()
      .setImage({
        src: image.url,
        alt: file.name,
        title: file.name,
        fileId,
      })
      .run();

    contentImageIds.value = collectImages(editor.value.getJSON()).ids;
    ElMessage.success("图片上传成功");
  } catch (error) {
    console.error("Upload image failed:", error);
    ElMessage.error("图片上传失败");
  }
};

const normalizeUrl = url => {
  const trimmedUrl = url.trim();

  if (!trimmedUrl) {
    return "";
  }

  if (/^[a-z][a-z0-9+.-]*:/i.test(trimmedUrl) || /^(\/|#|\.\/|\.\.\/)/.test(trimmedUrl)) {
    return trimmedUrl;
  }

  return `https://${trimmedUrl}`;
};

const setLink = async () => {
  if (!editor.value) return;

  const previousUrl = editor.value.getAttributes("link").href || "";
  const url = window.prompt("请输入链接地址", previousUrl);

  if (url === null) return;

  if (url === "") {
    editor.value.chain().focus().extendMarkRange("link").unsetLink().run();
    return;
  }

  const normalizedUrl = normalizeUrl(url);

  if (!normalizedUrl) {
    editor.value.chain().focus().extendMarkRange("link").unsetLink().run();
    return;
  }

  editor.value.chain().focus().extendMarkRange("link").setLink({ href: normalizedUrl }).run();
};

const saveArticles = async () => {
  if (!editor.value || isSaving.value) return;

  if (!article.articleTitle.trim()) {
    ElMessage.warning("请输入文章标题");
    return;
  }

  if (isArticleTitleTooLong.value) {
    ElMessage.warning("文章标题不能超过40个字符");
    return;
  }

  if (isArticleAbstractTooLong.value) {
    ElMessage.warning("文章概要不能超过100个字符");
    return;
  }

  const isNewArticle = article.id === null;

  try {
    await ElMessageBox.confirm("是否保存并发布文章？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    isSaving.value = true;
    const response = await request.post("/admin/content/article", {
      id: article.id,
      articleTitle: article.articleTitle,
      articleContentHtml: editor.value.getHTML(),
      articleContentJson: JSON.stringify(editor.value.getJSON()),
      articleAbstract: article.articleAbstract,
      articleDate: article.articleDate,
      // 保存封面文件ID；为空时表示文章不设置封面。
      articleCover: article.articleCover ?? null,
    });

    if (response && response.status === 200) {
      ElMessage.success("保存成功");
      contentImageIds.value = collectImages(editor.value.getJSON()).ids;
      boundImageIds.value = new Set(contentImageIds.value);
      sessionUploadedImages.value.clear();
      // 保存成功后封面已由后端标记为已绑定，前端不再按临时文件清理。
      sessionUploadedCoverId.value = null;
      isDirty.value = false;

      // 新建接口暂不返回文章 ID，发布后返回列表可避免再次点击造成重复创建。
      if (isNewArticle) {
        await router.replace({ name: "manage" });
      }
    }
  } catch (error) {
    if (error !== "cancel" && error !== "close") {
      if (error.response?.status === 404) {
        goToNotFound();
        return;
      }

      console.error("Save article failed:", error);
      const serverMessage = error.response?.data?.message;
      ElMessage.error(error.response?.status === 400 && serverMessage ? serverMessage : "保存失败");
      return;
    }

    ElMessage.info("已取消发布");
  } finally {
    isSaving.value = false;
  }
};

const confirmDiscardChanges = async () => {
  if (!isDirty.value || isSaving.value) return true;

  try {
    await ElMessageBox.confirm("当前修改尚未发布，确定要离开吗？", "未发布修改", {
      confirmButtonText: "离开",
      cancelButtonText: "继续编辑",
      type: "warning",
    });
    return true;
  } catch {
    return false;
  }
};

const handleBeforeUnload = event => {
  if (!isDirty.value || isSaving.value) return;

  event.preventDefault();
  event.returnValue = "";
};

watch(
  () => [article.articleTitle, article.articleAbstract, article.articleCover],
  markDirty
);

onBeforeRouteLeave(async () => confirmDiscardChanges());

onMounted(async () => {
  window.addEventListener("beforeunload", handleBeforeUnload);

  if (route.query.id) {
    loadArticle(route.query.id);
  } else if (route.params.id) {
    loadArticle(route.params.id);
  } else {
    await nextTick();
    isHydrating.value = false;
    isDirty.value = false;
  }
});

onBeforeUnmount(() => {
  window.removeEventListener("beforeunload", handleBeforeUnload);
  sessionUploadedImages.value.forEach((src, id) => {
    deleteImageById(id);
  });
  clearSessionCover();
  editor.value?.destroy();
});
</script>

<template>
  <div class="editor">
    <header class="editor-publish-header">
      <div class="publish-heading">
        <span class="publish-title">{{ article.id ? "编辑文章" : "发布图文" }}</span>
        <span class="publish-status" :class="publishStatusClass" aria-live="polite">
          {{ publishStatusText }}
        </span>
      </div>
      <span class="publish-type">富文本文章</span>
    </header>

    <div class="article-meta-field title-field" :class="{ 'is-invalid': isArticleTitleTooLong }">
      <el-input
        v-model="article.articleTitle"
        class="article-input"
        placeholder="请输入文章标题（必填）"
        :aria-invalid="isArticleTitleTooLong"
      />
      <div class="article-meta-feedback" aria-live="polite">
        <span class="article-meta-error">
          {{ isArticleTitleTooLong ? "文章标题不能超过40个字符" : "" }}
        </span>
        <span class="article-meta-count" :class="{ 'is-over-limit': isArticleTitleTooLong }">
          {{ articleTitleLength }}/{{ ARTICLE_TITLE_MAX_LENGTH }}
        </span>
      </div>
    </div>

    <section class="simple-editor">
      <header class="simple-editor-toolbar" v-if="editor">
        <div class="toolbar-group">
          <button
            type="button"
            class="toolbar-button"
            title="撤销"
            aria-label="撤销"
            :disabled="!canUndo"
            @click="editor.chain().focus().undo().run()"
          >
            ↶
          </button>
          <button
            type="button"
            class="toolbar-button"
            title="重做"
            aria-label="重做"
            :disabled="!canRedo"
            @click="editor.chain().focus().redo().run()"
          >
            ↷
          </button>
          <button
            type="button"
            class="toolbar-button clear-button"
            title="清除格式"
            aria-label="清除格式"
            @click="clearFormatting"
          >
            Tx
          </button>
        </div>

        <div class="toolbar-group">
          <button type="button" class="toolbar-button text-button" title="上传图片" aria-label="上传图片" @click="chooseImage">
            图片
          </button>
          <button
            type="button"
            class="toolbar-button text-button"
            :class="{ active: editor.isActive('link') }"
            title="添加或移除链接"
            aria-label="添加或移除链接"
            @click="setLink"
          >
            链接
          </button>
          <input ref="fileInput" class="file-input" type="file" accept="image/*" @change="uploadImage" />
        </div>

        <div class="toolbar-group">
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('bold') }"
            title="加粗"
            aria-label="加粗"
            @click="editor.chain().focus().toggleBold().run()"
          >
            B
          </button>
          <button
            type="button"
            class="toolbar-button italic"
            :class="{ active: editor.isActive('italic') }"
            title="斜体"
            aria-label="斜体"
            @click="editor.chain().focus().toggleItalic().run()"
          >
            I
          </button>
          <button
            type="button"
            class="toolbar-button underline"
            :class="{ active: editor.isActive('underline') }"
            title="下划线"
            aria-label="下划线"
            @click="editor.chain().focus().toggleUnderline().run()"
          >
            U
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('strike') }"
            title="删除线"
            aria-label="删除线"
            @click="editor.chain().focus().toggleStrike().run()"
          >
            S
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('code') }"
            title="行内代码"
            aria-label="行内代码"
            @click="editor.chain().focus().toggleCode().run()"
          >
            &lt;/&gt;
          </button>
        </div>

        <div class="toolbar-group">
          <select
            class="toolbar-select block-type-select"
            :value="currentBlockType"
            title="段落样式"
            aria-label="段落样式"
            @change="setBlockType"
          >
            <option value="paragraph">正文</option>
            <option
              v-for="heading in ARTICLE_HEADING_OPTIONS"
              :key="heading.level"
              :value="`heading-${heading.level}`"
            >
              {{ heading.title }}
            </option>
          </select>
        </div>

        <div class="toolbar-group">
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('bulletList') }"
            title="无序列表"
            aria-label="无序列表"
            @click="editor.chain().focus().toggleBulletList().run()"
          >
            •
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('orderedList') }"
            title="有序列表"
            aria-label="有序列表"
            @click="editor.chain().focus().toggleOrderedList().run()"
          >
            1.
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('blockquote') }"
            title="引用"
            aria-label="引用"
            @click="editor.chain().focus().toggleBlockquote().run()"
          >
            “
          </button>
          <button
            type="button"
            class="toolbar-button"
            title="分割线"
            aria-label="分割线"
            @click="editor.chain().focus().setHorizontalRule().run()"
          >
            —
          </button>
        </div>

        <div class="toolbar-group">
          <button
            type="button"
            class="toolbar-button code-block-button"
            :class="{ active: editor.isActive('codeBlock') }"
            title="代码块"
            aria-label="代码块"
            @click="editor.chain().focus().toggleCodeBlock().run()"
          >
            Code
          </button>
          <select
            v-if="isCodeBlockActive"
            class="toolbar-select code-language-select"
            :value="currentCodeLanguage"
            title="代码语言"
            aria-label="代码语言"
            @change="setCodeBlockLanguage"
          >
            <option
              v-for="language in CODE_LANGUAGE_OPTIONS"
              :key="language.value || 'auto'"
              :value="language.value"
            >
              {{ language.label }}
            </option>
          </select>
        </div>
      </header>

      <!-- 正文不再创建独立纵向滚动区，统一交给应用主滚动容器承载。 -->
      <EditorContent class="editor-content" :editor="editor" />
      <div class="editor-word-count" aria-live="polite">{{ articleContentLength }} 字</div>
    </section>

    <section class="publish-settings">
      <h2 class="settings-heading">发布设置</h2>

      <div class="setting-item">
        <label class="setting-label">文章概要</label>
        <div class="article-meta-field" :class="{ 'is-invalid': isArticleAbstractTooLong }">
          <el-input
            v-model="article.articleAbstract"
            class="article-input"
            placeholder="请输入文章概要"
            :aria-invalid="isArticleAbstractTooLong"
          />
          <div class="article-meta-feedback" aria-live="polite">
            <span class="article-meta-error">
              {{ isArticleAbstractTooLong ? "文章概要不能超过100个字符" : "" }}
            </span>
            <span class="article-meta-count" :class="{ 'is-over-limit': isArticleAbstractTooLong }">
              {{ articleAbstractLength }}/{{ ARTICLE_ABSTRACT_MAX_LENGTH }}
            </span>
          </div>
        </div>
      </div>

      <div class="setting-item">
        <label class="setting-label">文章封面</label>
        <section class="cover-uploader">
          <div class="cover-preview" :class="{ empty: !article.coverObjectUrl }">
            <img v-if="article.coverObjectUrl" :src="article.coverObjectUrl" alt="文章封面预览" />
            <span v-else>暂无封面</span>
          </div>

          <div class="cover-actions">
            <p class="cover-description">建议使用 16:9 图片，作为文章列表和详情页封面。</p>
            <div class="cover-buttons">
              <el-button type="primary" plain :loading="coverUploading" :disabled="isSaving" @click="chooseCover">
                {{ article.coverObjectUrl ? "更换封面" : "上传封面" }}
              </el-button>
              <el-button
                v-if="article.coverObjectUrl"
                type="danger"
                plain
                :disabled="coverUploading || isSaving"
                @click="removeArticleCover"
              >
                移除封面
              </el-button>
            </div>
          </div>

          <input
            ref="coverFileInput"
            class="file-input"
            type="file"
            accept="image/*"
            @change="uploadCover"
          />
        </section>
      </div>

      <div class="publish-actions">
        <button
          type="button"
          class="publish-button"
          :disabled="isPublishDisabled"
          @click="saveArticles"
        >
          {{ isSaving ? "发布中..." : "保存发布" }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.editor {
  width: 100%;
  min-height: 792px;
  height: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  color: #222222;
}

.editor-publish-header {
  min-height: 38px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eeeeee;
}

.publish-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.publish-title {
  font-size: 18px;
  font-weight: 700;
}

.publish-status,
.publish-type {
  color: #a1a1a1;
  font-size: 12px;
}

.publish-status.is-dirty {
  color: #d18a00;
}

.publish-status.is-saving {
  color: #1677d2;
}

.publish-status.is-published {
  color: #5d8f00;
}

.article-input {
  flex: 0 0 auto;
}

.article-meta-field {
  flex: 0 0 auto;
}

.title-field :deep(.el-input__wrapper) {
  min-height: 52px;
  padding: 0 16px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e7e7e7 inset;
}

.title-field :deep(.el-input__inner) {
  color: #222222;
  font-size: 16px;
}

.article-meta-feedback {
  min-height: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 4px 0;
  font-size: 12px;
  line-height: 16px;
  color: #909399;
}

.article-meta-error,
.article-meta-count.is-over-limit {
  color: #f56c6c;
}

.article-meta-field.is-invalid :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}

.cover-uploader {
  display: flex;
  align-items: center;
  gap: 14px;
}

.cover-preview {
  width: 180px;
  height: 96px;
  flex: 0 0 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px dashed #c8cdd6;
  border-radius: 6px;
  background: #f7f8fa;
  color: #909399;
  font-size: 14px;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-preview.empty {
  background: #fafafa;
}

.cover-actions {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cover-description {
  margin: 0;
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
}

.cover-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.simple-editor {
  min-height: 529px;
  flex: 1 0 auto;
  display: flex;
  flex-direction: column;
  // sticky 的所有祖先都必须保持纵向可见，否则工具栏会退化为普通定位。
  overflow: visible;
  border: 1px solid #e7e7e7;
  border-radius: 10px;
  background: #ffffff;
}

.simple-editor-toolbar {
  // 偏移量由编辑页外壳统一提供，桌面端对应悬浮头部下方的 100px 位置。
  position: sticky;
  top: var(--article-editor-sticky-top, 100px);
  z-index: 20;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-bottom: 1px solid #eeeeee;
  border-radius: 9px 9px 0 0;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.04);
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
}

.editor-content {
  flex: 1 0 auto;
}

.editor-word-count {
  padding: 0 16px 14px;
  color: #b1b1b1;
  font-size: 12px;
  text-align: right;
}

.toolbar-group {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding-right: 6px;
  border-right: 1px solid #ebebeb;
}

.toolbar-group:last-child {
  border-right: 0;
}

.toolbar-button {
  height: 32px;
  min-width: 32px;
  padding: 0 7px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.toolbar-button.text-button,
.code-block-button {
  min-width: auto;
}

.toolbar-select {
  height: 32px;
  min-width: 120px;
  padding: 0 28px 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #303133;
  font-size: 14px;
  cursor: pointer;
}

.toolbar-select:focus {
  outline: none;
  border-color: #1677d2;
}

.toolbar-select:disabled {
  cursor: not-allowed;
  opacity: 0.45;
  background: #eef0f3;
}

.block-type-select {
  min-width: 88px;
}

.code-language-select {
  flex: 0 0 116px;
  min-width: 116px;
}

.toolbar-button:hover,
.toolbar-button.active {
  border-color: #b8d7ff;
  background: #eaf4ff;
  color: #1677d2;
}

.toolbar-button:disabled,
.publish-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.italic {
  font-style: italic;
}

.underline {
  text-decoration: underline;
}

.clear-button {
  text-decoration: line-through;
}

.file-input {
  display: none;
}

:deep(.simple-editor-content) {
  min-height: 520px;
  max-width: none;
  margin: 0;
  padding: 30px 18px;
  box-sizing: border-box;
  outline: none;
}

:deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: "开始写一篇文章...";
  color: #a8abb2;
  float: left;
  height: 0;
  pointer-events: none;
}

:deep(.ProseMirror img) {
  max-width: 100%;
  border-radius: 6px;
}

.publish-settings {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding-top: 8px;
}

.settings-heading {
  margin: 0;
  padding-bottom: 14px;
  border-bottom: 1px solid #eeeeee;
  color: #222222;
  font-size: 18px;
}

.setting-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.setting-label {
  color: #222222;
  font-size: 15px;
  font-weight: 700;
}

.publish-actions {
  padding-top: 2px;
}

.publish-button {
  min-width: 160px;
  height: 42px;
  padding: 0 24px;
  border: 0;
  border-radius: 8px;
  background: #c8f300;
  color: #1f2500;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: background-color 0.2s ease, transform 0.2s ease;
}

.publish-button:hover:not(:disabled) {
  background: #b8e000;
  transform: translateY(-1px);
}

@media (max-width: 640px) {
  .editor {
    gap: 14px;
  }

  .editor-publish-header {
    align-items: flex-start;
  }

  .publish-type {
    display: none;
  }

  :deep(.simple-editor-content) {
    min-height: 440px;
    padding: 24px 16px;
  }

  .cover-uploader {
    align-items: stretch;
    flex-direction: column;
  }

  .cover-preview {
    width: 100%;
    height: auto;
    aspect-ratio: 16 / 9;
    flex-basis: auto;
  }

  .publish-button {
    width: 100%;
  }
}
</style>
