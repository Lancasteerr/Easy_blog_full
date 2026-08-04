<script setup>
import { computed, onBeforeUnmount, ref } from "vue";
import { globalZIndex } from "@/utils/DraggableZIndex";

defineOptions({
  name: "DraggablePanel",
  inheritAttrs: false,
});

const props = defineProps({
  initialX: {
    type: Number,
    required: true,
  },
  initialY: {
    type: Number,
    required: true,
  },
  width: {
    type: [Number, String],
    default: null,
  },
  height: {
    type: [Number, String],
    default: null,
  },
  minTop: {
    type: Number,
    default: 80,
  },
});

const rootRef = ref(null);
const position = ref({
  x: props.initialX,
  y: props.initialY,
});
const offset = ref({
  x: 0,
  y: 0,
});
const dragging = ref(false);
const zIndex = ref(1);

const toCssSize = value => (typeof value === "number" ? `${value}px` : value);

const panelStyle = computed(() => ({
  left: `${position.value.x}px`,
  top: `${position.value.y}px`,
  zIndex: zIndex.value,
  ...(props.width ? { width: toCssSize(props.width) } : {}),
  ...(props.height ? { height: toCssSize(props.height) } : {}),
}));

const removeDocumentListeners = () => {
  document.removeEventListener("pointermove", onPointerMove);
  document.removeEventListener("pointerup", onPointerUp);
};

const onPointerMove = event => {
  if (!dragging.value) {
    return;
  }

  const el = rootRef.value;

  if (!el) {
    return;
  }

  let newX = event.clientX - offset.value.x;
  let newY = event.clientY - offset.value.y;
  const maxX = window.innerWidth - el.offsetWidth;
  const maxY = window.innerHeight - el.offsetHeight;

  // 保持旧浮窗的边界规则：左右不出屏幕，顶部至少低于 Header。
  newX = Math.max(0, Math.min(newX, maxX));
  newY = Math.max(props.minTop, Math.min(newY, maxY));

  position.value = {
    x: newX,
    y: newY,
  };
};

const onPointerUp = () => {
  dragging.value = false;
  removeDocumentListeners();
  document.body.style.userSelect = "";
};

const onPointerDown = event => {
  document.body.style.userSelect = "none";
  dragging.value = true;
  offset.value = {
    x: event.clientX - position.value.x,
    y: event.clientY - position.value.y,
  };
  zIndex.value = ++globalZIndex.value;
  document.addEventListener("pointermove", onPointerMove);
  document.addEventListener("pointerup", onPointerUp);
};

onBeforeUnmount(() => {
  removeDocumentListeners();
  document.body.style.userSelect = "";
});
</script>

<template>
  <div
    ref="rootRef"
    class="draggable-panel"
    :style="panelStyle"
    v-bind="$attrs"
    @pointerdown="onPointerDown"
  >
    <slot></slot>
  </div>
</template>

<style scoped>
.draggable-panel {
  position: absolute;
  cursor: move;
}
</style>
