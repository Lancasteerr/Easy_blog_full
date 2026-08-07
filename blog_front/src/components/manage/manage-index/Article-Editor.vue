<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { EditorContent, useEditor } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";
import CodeBlockLowlight from "@tiptap/extension-code-block-lowlight";
import Image from "@tiptap/extension-image";
import request from "@/utils/request";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { CODE_LANGUAGE_OPTIONS, codeLowlight } from "@/utils/codeHighlight";

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
  } catch (error) {
    if (isNotFoundStatus(error)) {
      goToNotFound();
      return;
    }

    console.error("Load edit article failed:", error);
    ElMessage.error("文章加载失败");
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
  if (!editor.value) return;

  if (!article.articleTitle.trim()) {
    ElMessage.warning("请输入文章标题");
    return;
  }

  try {
    await ElMessageBox.confirm("是否保存并发布文章？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

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
    }
  } catch (error) {
    if (error !== "cancel") {
      if (error.response?.status === 404) {
        goToNotFound();
        return;
      }

      console.error("Save article failed:", error);
      ElMessage.error("保存失败");
      return;
    }

    ElMessage.info("已取消发布");
  }
};

onMounted(() => {
  if (route.query.id) {
    loadArticle(route.query.id);
  } else if (route.params.id) {
    loadArticle(route.params.id);
  }
});

onBeforeUnmount(() => {
  sessionUploadedImages.value.forEach((src, id) => {
    deleteImageById(id);
  });
  clearSessionCover();
  editor.value?.destroy();
});
</script>

<template>
  <div class="editor">
    <el-input v-model="article.articleTitle" class="article-input" placeholder="请输入文章标题" />
    <el-input v-model="article.articleAbstract" class="article-input" placeholder="请输入文章概要" />

    <section class="cover-uploader">
      <div class="cover-preview" :class="{ empty: !article.coverObjectUrl }">
        <img v-if="article.coverObjectUrl" :src="article.coverObjectUrl" alt="文章封面预览" />
        <span v-else>暂无封面</span>
      </div>

      <div class="cover-actions">
        <div class="cover-title">文章封面</div>
        <div class="cover-buttons">
          <el-button type="primary" plain :loading="coverUploading" @click="chooseCover">
            {{ article.coverObjectUrl ? "更换封面" : "上传封面" }}
          </el-button>
          <el-button
            v-if="article.coverObjectUrl"
            type="danger"
            plain
            :disabled="coverUploading"
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

    <section class="simple-editor">
      <header class="simple-editor-toolbar" v-if="editor">
        <div class="toolbar-group">
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('heading', { level: 1 }) }"
            title="一级标题"
            @click="editor.chain().focus().toggleHeading({ level: 1 }).run()"
          >
            H1
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('heading', { level: 2 }) }"
            title="二级标题"
            @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"
          >
            H2
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('paragraph') }"
            title="正文"
            @click="editor.chain().focus().setParagraph().run()"
          >
            P
          </button>
        </div>

        <div class="toolbar-group">
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('bold') }"
            title="加粗"
            @click="editor.chain().focus().toggleBold().run()"
          >
            B
          </button>
          <button
            type="button"
            class="toolbar-button italic"
            :class="{ active: editor.isActive('italic') }"
            title="斜体"
            @click="editor.chain().focus().toggleItalic().run()"
          >
            I
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('strike') }"
            title="删除线"
            @click="editor.chain().focus().toggleStrike().run()"
          >
            S
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('code') }"
            title="行内代码"
            @click="editor.chain().focus().toggleCode().run()"
          >
            &lt;/&gt;
          </button>
          <button
            type="button"
            class="toolbar-button code-block-button"
            :class="{ active: editor.isActive('codeBlock') }"
            title="代码块"
            @click="editor.chain().focus().toggleCodeBlock().run()"
          >
            Code
          </button>
          <select
            class="toolbar-select code-language-select"
            :value="currentCodeLanguage"
            :disabled="!isCodeBlockActive"
            title="代码语言"
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

        <div class="toolbar-group">
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('bulletList') }"
            title="无序列表"
            @click="editor.chain().focus().toggleBulletList().run()"
          >
            •
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('orderedList') }"
            title="有序列表"
            @click="editor.chain().focus().toggleOrderedList().run()"
          >
            1.
          </button>
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('blockquote') }"
            title="引用"
            @click="editor.chain().focus().toggleBlockquote().run()"
          >
            “
          </button>
          <button
            type="button"
            class="toolbar-button"
            title="分割线"
            @click="editor.chain().focus().setHorizontalRule().run()"
          >
            HR
          </button>
        </div>

        <div class="toolbar-group">
          <button
            type="button"
            class="toolbar-button"
            :class="{ active: editor.isActive('link') }"
            title="链接"
            @click="setLink"
          >
            Link
          </button>
          <button type="button" class="toolbar-button" title="上传图片" @click="chooseImage">
            Img
          </button>
          <input ref="fileInput" class="file-input" type="file" accept="image/*" @change="uploadImage" />
        </div>

        <div class="toolbar-group push-right">
          <button
            type="button"
            class="toolbar-button"
            title="撤销"
            :disabled="!canUndo"
            @click="editor.chain().focus().undo().run()"
          >
            Undo
          </button>
          <button
            type="button"
            class="toolbar-button"
            title="重做"
            :disabled="!canRedo"
            @click="editor.chain().focus().redo().run()"
          >
            Redo
          </button>
          <button type="button" class="save-button" :disabled="isEmpty" @click="saveArticles">
            保存发布
          </button>
        </div>
      </header>

      <el-scrollbar class="editor-content-scroll">
        <EditorContent :editor="editor" />
      </el-scrollbar>
    </section>
  </div>
</template>

<style scoped lang="scss">
.editor {
  width: 99%;
  height: 99%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.article-input {
  flex: 0 0 auto;
}

.cover-uploader {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
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

.cover-title {
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.cover-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.simple-editor {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
}

.simple-editor-toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-bottom: 1px solid #e4e7ed;
  background: #f7f8fa;
  overflow-x: auto;
}

.editor-content-scroll {
  min-height: 0;
  flex: 1;
}

.editor-content-scroll :deep(.el-scrollbar__view) {
  min-height: 100%;
}

.toolbar-group {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding-right: 8px;
  border-right: 1px solid #dcdfe6;
}

.toolbar-group:last-child {
  border-right: 0;
}

.push-right {
  margin-left: auto;
}

.toolbar-button,
.save-button {
  height: 32px;
  min-width: 32px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
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

.code-language-select {
  flex: 0 0 128px;
}

.toolbar-button:hover,
.toolbar-button.active {
  border-color: #b8d7ff;
  background: #eaf4ff;
  color: #1677d2;
}

.toolbar-button:disabled,
.save-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.italic {
  font-style: italic;
}

.save-button {
  min-width: 84px;
  padding: 0 14px;
  background: #1677d2;
  color: #fff;
}

.save-button:hover:not(:disabled) {
  background: #0f66b8;
}

.file-input {
  display: none;
}

:deep(.simple-editor-content) {
  min-height: 100%;
  max-width: none;
  margin: 0;
  padding: 28px 34px;
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

@media (max-width: 640px) {
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
}
</style>
