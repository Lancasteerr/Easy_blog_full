<script setup>
import DraggablePanel from "@/components/common/DraggablePanel.vue";

defineOptions({
  name: "AboutDraggable",
});

defineProps({
  card: {
    type: Object,
    required: true,
  },
});
</script>

<template>
  <DraggablePanel class="About-Draggable" :initial-x="40" :initial-y="130" :width="400" :height="135">
    <div class="AboutMe">
      <div class="AboutMetitle">
        <div class="AboutMeToalTitle">
          <div>{{ card.title }}</div>
          <div class="AboutMeStitle">{{ card.subtitle }}</div>
        </div>
        <img src="@/assets/AboutMeitem.png" alt="">
      </div>
      <el-scrollbar class="AboutMecontext" @pointerdown.stop>
        <div class="AboutMecontextInner">
          <p v-for="(paragraph, index) in card.paragraphs" :key="index">{{ paragraph }}</p>
          <ul v-if="card.items && card.items.length" class="AboutMeList">
            <li v-for="(item, index) in card.items" :key="index">{{ item }}</li>
          </ul>
        </div>
      </el-scrollbar>
    </div>
  </DraggablePanel>
</template>

<style scoped lang="scss">
.About-Draggable{
  // 浮窗定位和拖拽行为统一由 DraggablePanel 管理，这里只保留业务外观。
  background-color: rgba(0,0,0,.88);
  box-shadow: 0 0 15px #000000;
}
.AboutMe{
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.AboutMetitle{
  display: flex;
  box-sizing: border-box;
  justify-content: space-between;
  width: 100%;
  height: 50px;
  padding: 15px;
  color: #ffffff;
  font-family: "Source Han Sans Bold" , sans-serif;
}
.AboutMeToalTitle{
  display: flex;
  justify-content: center;
  font-size: 16px;
}
.AboutMeStitle{
  box-sizing: border-box;
  font-size: 12px;
  padding-left: 6px;
  line-height: 28px;
  font-family: Source Han Sans Regular , sans-serif;
}
.AboutMecontext{
  flex: 1;
  min-height: 0;
  cursor: auto;
}
.AboutMecontextInner{
  color: #ffffff;
  box-sizing: border-box;
  padding: 0 15px 15px;
  line-height: 1.65;
  font-size: 13px;
  font-family: Source Han Sans Regular , sans-serif;
}
.AboutMecontextInner p{
  margin: 0 0 7px;
}
.AboutMecontextInner p:last-child{
  margin-bottom: 0;
}
.AboutMeList{
  margin: 0;
  padding-left: 18px;
}
.AboutMeList li{
  margin-bottom: 5px;
}
.AboutMeList li:last-child{
  margin-bottom: 0;
}
</style>
