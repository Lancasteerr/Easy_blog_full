<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  items: {
    type: Array,
    default: () => []
  },
  activeId: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["select"]);
const isExpanded = ref(true);
const CATALOG_LIST_ID = "article-catalog-list";

const toggleCatalog = () => {
  isExpanded.value = !isExpanded.value;
};

const selectHeading = id => {
  emit("select", id);
};

watch(
  () => props.items,
  () => {
    // 切换文章后恢复展开状态，避免沿用上一篇文章的折叠选择。
    isExpanded.value = true;
  }
);
</script>

<template>
  <nav class="article-catalog" aria-label="文章目录">
    <div class="catalog-header">
      <h2 class="catalog-title">目录</h2>

      <button
        class="catalog-toggle"
        type="button"
        :aria-expanded="isExpanded"
        :aria-controls="CATALOG_LIST_ID"
        @click="toggleCatalog"
      >
        <span>{{ isExpanded ? "收起" : "展开" }}</span>
        <span class="catalog-chevron" :class="{ 'is-collapsed': !isExpanded }" aria-hidden="true"></span>
      </button>
    </div>

    <div v-show="isExpanded" :id="CATALOG_LIST_ID" class="catalog-list">
      <button
        v-for="item in items"
        :key="item.id"
        class="catalog-item"
        :class="{ 'is-active': item.id === activeId }"
        :style="{ paddingLeft: `${10 + item.depth * 16}px` }"
        :title="item.text"
        :aria-current="item.id === activeId ? 'location' : undefined"
        type="button"
        @click="selectHeading(item.id)"
      >
        <span>{{ item.text }}</span>
      </button>
    </div>
  </nav>
</template>

<style scoped lang="scss">
.article-catalog {
  display: none;
  position: sticky;
  top: 24px;
  width: 280px;
  max-height: calc(100vh - 48px);
  overflow: hidden;
  box-sizing: border-box;
  background-color: rgba(43, 47, 54, 0.88);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  color: rgba(255, 255, 255, 0.82);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.24), 0 2px 8px rgba(0, 0, 0, 0.16);
}

.catalog-header {
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  box-sizing: border-box;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.catalog-title {
  margin: 0;
  color: #ffffff;
  font-family: "Source Han Sans Bold", sans-serif;
  font-size: 17px;
  font-weight: 700;
  line-height: 1;
}

.catalog-toggle {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 0;
  border: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.56);
  font: inherit;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  transition: color 0.2s ease;
}

.catalog-toggle:hover,
.catalog-toggle:focus-visible {
  color: #7ccfff;
}

.catalog-toggle:focus-visible,
.catalog-item:focus-visible {
  outline: 2px solid rgba(124, 207, 255, 0.8);
  outline-offset: 2px;
}

.catalog-chevron {
  width: 7px;
  height: 7px;
  border-top: 1px solid currentColor;
  border-left: 1px solid currentColor;
  transform: translateY(2px) rotate(45deg);
  transition: transform 0.2s ease;
}

.catalog-chevron.is-collapsed {
  transform: translateY(-2px) rotate(225deg);
}

.catalog-list {
  max-height: calc(100vh - 102px);
  padding: 8px 8px 12px;
  overflow-y: auto;
  box-sizing: border-box;
}

.catalog-item {
  width: 100%;
  min-height: 34px;
  display: block;
  padding: 6px 10px;
  border: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.72);
  font-family: "Source Han Sans Regular", sans-serif;
  font-size: 14px;
  line-height: 22px;
  text-align: left;
  cursor: pointer;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.catalog-item:hover {
  color: #ffffff;
  background-color: rgba(255, 255, 255, 0.06);
}

.catalog-item.is-active {
  color: #7ccfff;
  background-color: rgba(124, 207, 255, 0.08);
}

.catalog-item span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (min-width: 1100px) {
  .article-catalog {
    display: block;
  }
}
</style>
