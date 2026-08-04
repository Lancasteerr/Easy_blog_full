<script setup>
import AdminHeader from "@/components/common/AdminHeader.vue";

defineOptions({
  name: "ManageHeader",
});

const emit = defineEmits(["switch-write", "switch-list"]);

const headerActions = [
  { name: "home", label: "首页", className: "to-homePage" },
  { name: "switch-write", label: "写文章", className: "Write-Article" },
  { name: "switch-list", label: "管理文章", className: "Manage-Article" },
];

const handleAction = actionName => {
  // 继续向管理页抛出原事件名，避免改动 ManagePage 的视图切换逻辑。
  emit(actionName);
};
</script>

<template>
  <AdminHeader :actions="headerActions" @action="handleAction"></AdminHeader>
</template>
