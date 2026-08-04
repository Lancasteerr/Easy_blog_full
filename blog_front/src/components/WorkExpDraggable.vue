<script setup>
import DraggablePanel from "@/components/common/DraggablePanel.vue";

defineOptions({
  name: "WorkExpDraggable",
});

defineProps({
  card: {
    type: Object,
    required: true,
  },
});
</script>

<template>
  <DraggablePanel class="WorkExp-Draggable" :initial-x="40" :initial-y="280" :width="400" :height="135">
    <div class="WorkExp">
      <div class="WorkExptitle">
        <div class="WorkExpToalTitle">
          <div>{{ card.title }}</div>
          <div class="WorkExpStitle">{{ card.subtitle }}</div>
        </div>
      </div>
      <el-scrollbar class="WorkExpcontext" @pointerdown.stop>
        <div class="WorkExpcontextInner">
          <p v-for="(paragraph, index) in card.paragraphs" :key="index">{{ paragraph }}</p>
          <ul v-if="card.items && card.items.length" class="WorkExpList">
            <li v-for="(item, index) in card.items" :key="index">{{ item }}</li>
          </ul>
        </div>
      </el-scrollbar>
    </div>
  </DraggablePanel>
</template>

<style scoped lang="scss">
.WorkExp-Draggable{
  // 浮窗定位和拖拽行为统一由 DraggablePanel 管理，这里只保留业务外观。
  background-color: rgba(0,0,0,.88);
  box-shadow: 0 0 15px #000000;
}
.WorkExp{
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.WorkExpToalTitle{
  box-sizing: border-box;
  display: flex;
  font-size: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #f3ff00;
}
.WorkExptitle{
  display: flex;
  box-sizing: border-box;
  width: 100%;
  height: 60px;
  padding: 15px;
  color: #ffffff;
  font-family: Source Han Sans Bold , sans-serif;
}
.WorkExpStitle{
  box-sizing: border-box;
  font-size: 12px;
  padding-left: 6px;
  line-height: 28px;
  font-family: Source Han Sans Regular , sans-serif;
}
.WorkExpcontext{
  flex: 1;
  min-height: 0;
  cursor: auto;
}
.WorkExpcontextInner{
  color: #ffffff;
  box-sizing: border-box;
  padding: 0 15px 15px;
  line-height: 1.65;
  font-size: 13px;
  font-family: Source Han Sans Regular , sans-serif;
}
.WorkExpcontextInner p{
  margin: 0 0 7px;
}
.WorkExpcontextInner p:last-child{
  margin-bottom: 0;
}
.WorkExpList{
  margin: 0;
  padding-left: 18px;
}
.WorkExpList li{
  margin-bottom: 5px;
}
.WorkExpList li:last-child{
  margin-bottom: 0;
}
</style>
