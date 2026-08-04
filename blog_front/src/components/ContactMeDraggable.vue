<script setup>
import DraggablePanel from "@/components/common/DraggablePanel.vue";

defineOptions({
  name: "ContactMeDraggable",
});

defineProps({
  card: {
    type: Object,
    required: true,
  },
});
</script>

<template>
  <DraggablePanel class="ContactMe-Draggable" :initial-x="40" :initial-y="430" :width="300" :height="150">
    <div class="ContactMe">
      <div class="ContactMetitle">
        <div class="ContactMeToalTitle">
          <div>{{ card.title }}</div>
          <div class="ContactMeStitle">{{ card.subtitle }}</div>
        </div>
      </div>
      <el-scrollbar class="ContactMecontext" @pointerdown.stop>
        <div class="ContactMecontextInner">
          <p v-for="(paragraph, index) in card.paragraphs" :key="index">{{ paragraph }}</p>
          <ul v-if="card.items && card.items.length" class="ContactMeList">
            <li v-for="(item, index) in card.items" :key="index">{{ item }}</li>
          </ul>
        </div>
      </el-scrollbar>
    </div>
  </DraggablePanel>
</template>

<style scoped lang="scss">
.ContactMe-Draggable{
  // 浮窗定位和拖拽行为统一由 DraggablePanel 管理，这里只保留业务外观。
  background-color: rgba(0,0,0,.88);
  box-shadow: 0 0 15px #000000;
}
.ContactMe{
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.ContactMeToalTitle{
  box-sizing: border-box;
  display: flex;
  font-size: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #f3ff00;
}
.ContactMetitle{
  display: flex;
  box-sizing: border-box;
  width: 100%;
  height: 60px;
  padding: 15px;
  color: #ffffff;
  font-family: Source Han Sans Bold , sans-serif;
}
.ContactMeStitle{
  box-sizing: border-box;
  font-size: 12px;
  padding-left: 6px;
  line-height: 28px;
  font-family: Source Han Sans Regular , sans-serif;
}
.ContactMecontext{
  flex: 1;
  min-height: 0;
  cursor: auto;
}
.ContactMecontextInner{
  color: #ffffff;
  box-sizing: border-box;
  padding: 0 15px 15px;
  line-height: 1.65;
  font-size: 13px;
  font-family: Source Han Sans Regular , sans-serif;
}
.ContactMecontextInner p{
  margin: 0 0 7px;
}
.ContactMecontextInner p:last-child{
  margin-bottom: 0;
}
.ContactMeList{
  margin: 0;
  padding-left: 18px;
}
.ContactMeList li{
  margin-bottom: 5px;
}
.ContactMeList li:last-child{
  margin-bottom: 0;
}
</style>
