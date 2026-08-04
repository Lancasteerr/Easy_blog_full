<script setup>
import router from "@/router";
import AdminHeader from "@/components/common/AdminHeader.vue";

defineOptions({
  name: "ArticleEditHeader",
});

const headerActions = [
  { name: "home", label: "首页", className: "to-homePage" },
  { name: "back", label: "返回", className: "Manage-Article" },
];

const handleAction = actionName => {
  if (actionName === "back") {
    // 编辑页保留原来的浏览器历史返回行为。
    router.go(-1);
  }
};
</script>

<template>
  <AdminHeader variant="transparent" :actions="headerActions" @action="handleAction"></AdminHeader>
</template>
