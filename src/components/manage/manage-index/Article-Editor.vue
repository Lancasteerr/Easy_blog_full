<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { EditorContent, useEditor } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import request from "@/utils/request";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";

const fileInput = ref(null);
const contentImageIds = ref(new Set());
const sessionUploadedImages = ref(new Map());
const route = useRoute();

const article = reactive({
  id: null,
  articleTitle: "",
  articleAbstract: "",
  articleContentHtml: "",
  articleContentJson: null,
  articleDate: "",
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

const deleteRemovedImages = async currentImages => {
  const currentIds = currentImages.ids;
  const currentSrcs = currentImages.srcs;
  const removedIds = [
    ...new Set([
      ...[...contentImageIds.value].filter(id => !currentIds.has(id)),
      ...[...sessionUploadedImages.value.entries()]
        .filter(([id, src]) => !currentIds.has(id) && !currentSrcs.has(src))
        .map(([id]) => id),
    ]),
  ];

  contentImageIds.value = currentIds;
  await Promise.all(removedIds.map(deleteImageById));
};

const editor = useEditor({
  content: "<p></p>",
  extensions: [
    StarterKit.configure({
      link: {
        openOnClick: false,
      },
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
    deleteRemovedImages(collectImages(editor.getJSON()));
  },
});

const canUndo = computed(() => editor.value?.can().undo() ?? false);
const canRedo = computed(() => editor.value?.can().redo() ?? false);
const isEmpty = computed(() => editor.value?.isEmpty ?? true);

const setEditorContent = content => {
  if (!editor.value) return;

  if (content) {
    editor.value.commands.setContent(content, false);
  } else {
    editor.value.commands.clearContent(false);
  }

  contentImageIds.value = collectImages(editor.value.getJSON()).ids;
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

const loadArticle = async id => {
  if (!id) return;

  const res = await request.get("/public/article", {
    params: { id },
  });

  article.id = res.data.id;
  article.articleTitle = res.data.articleTitle || "";
  article.articleAbstract = res.data.articleAbstract || "";
  article.articleContentHtml = res.data.articleContentHtml || "";
  article.articleContentJson = parseJsonContent(res.data.articleContentJson);

  await nextTick();
  setEditorContent(article.articleContentJson || article.articleContentHtml || "<p></p>");
};

const chooseImage = () => {
  fileInput.value?.click();
};

const uploadImage = async event => {
  const file = event.target.files?.[0];
  event.target.value = "";

  if (!file || !editor.value) return;

  if (!file.type.startsWith("image/")) {
    ElMessage.warning("请选择图片文件");
    return;
  }

  const formData = new FormData();
  formData.append("file", file);

  try {
    const res = await request.post("/admin/files/upload", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });

    const image = res.data;
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

const setLink = async () => {
  if (!editor.value) return;

  const previousUrl = editor.value.getAttributes("link").href || "";
  const url = window.prompt("请输入链接地址", previousUrl);

  if (url === null) return;

  if (url === "") {
    editor.value.chain().focus().extendMarkRange("link").unsetLink().run();
    return;
  }

  editor.value.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
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
    });

    if (response && response.status === 200) {
      ElMessage.success("保存成功");
      contentImageIds.value = collectImages(editor.value.getJSON()).ids;
      sessionUploadedImages.value.clear();
    }
  } catch (error) {
    if (error !== "cancel") {
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
  editor.value?.destroy();
});
</script>

<template>
  <div class="editor">
    <el-input v-model="article.articleTitle" class="article-input" placeholder="请输入文章标题" />
    <el-input v-model="article.articleAbstract" class="article-input" placeholder="请输入文章概要" />

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

      <div class="editor-content-scroll">
        <EditorContent :editor="editor" />
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
@import "@/assets/markdown.css";

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
  overflow-y: auto;
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
</style>
