<script setup>
defineOptions({
  name: "ArticlePagination",
});

defineProps({
  total: {
    type: Number,
    required: true,
  },
  pageSize: {
    type: Number,
    required: true,
  },
  currentPage: {
    type: Number,
    required: true,
  },
});

const emit = defineEmits(["current-change"]);
</script>

<template>
  <div class="pagination-box">
    <!-- 前后台文章列表共用同一套分页外观，避免两边样式漂移。 -->
    <el-pagination
      background
      layout="total, prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="currentPage"
      @current-change="page => emit('current-change', page)"
    />
  </div>
</template>

<style scoped>
.pagination-box {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

.pagination-box :deep(.el-pagination) {
  --el-pagination-bg-color: rgba(67, 72, 81, 0.92);
  --el-pagination-button-color: rgba(255, 255, 255, 0.78);
  --el-pagination-button-disabled-bg-color: rgba(67, 72, 81, 0.44);
  --el-pagination-button-disabled-color: rgba(255, 255, 255, 0.28);
  --el-pagination-hover-color: #f3ff00;
  color: rgba(255, 255, 255, 0.7);
}

.pagination-box :deep(.el-pagination__total),
.pagination-box :deep(.el-pagination button),
.pagination-box :deep(.el-pager li) {
  color: rgba(255, 255, 255, 0.78);
}

.pagination-box :deep(.el-pagination.is-background .btn-prev),
.pagination-box :deep(.el-pagination.is-background .btn-next),
.pagination-box :deep(.el-pagination.is-background .el-pager li) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background-color: rgba(67, 72, 81, 0.92);
  border-radius: 4px;
  box-shadow: 0 5px 14px rgba(0, 0, 0, 0.22);
}

.pagination-box :deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: #f3ff00;
  border-color: #f3ff00;
  color: #1f2329;
}

.pagination-box :deep(.el-pagination.is-background .btn-prev:not(:disabled):hover),
.pagination-box :deep(.el-pagination.is-background .btn-next:not(:disabled):hover),
.pagination-box :deep(.el-pagination.is-background .el-pager li:not(.is-active):hover) {
  border-color: rgba(243, 255, 0, 0.56);
  color: #f3ff00;
}
</style>
