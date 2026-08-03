<script>

import {globalZIndex} from "@/utils/DraggableZIndex";

export default {
  name:"AboutDraggable",
  props: {
    card: {
      type: Object,
      required: true,
    },
  },
  data(){
    return {
      position: {
        x: 40,
        y: 130,
      },
      dragging: false,
      offset: {
        x: 0,
        y: 0,
      },
      zIndex: 1
    }
  },
  methods:{
    onMouseDown(event){
      document.body.style.userSelect = 'none';//禁止文本选中
      this.dragging = true;
      this.offset.x = event.clientX - this.position.x;//给相对位置初值
      this.offset.y = event.clientY - this.position.y;
      //event.target.setPointerCapture(event.pointerId);//追踪指针直到离开窗口
      this.zIndex = ++globalZIndex.value;
      document.addEventListener("pointermove",this.onMouseMove);
      document.addEventListener("pointerup",this.onMouseUp);
    },
    onMouseMove(event){
      if(this.dragging){
        let newX = event.clientX - this.offset.x;
        let newY = event.clientY - this.offset.y;

        const el = this.$el;//当前组件DOM
        const maxX = window.innerWidth - el.offsetWidth;
        const maxY = window.innerHeight - el.offsetHeight;

        newX = Math.max(0,Math.min(newX,maxX));
        newY = Math.max(80,Math.min(newY,maxY));

        this.position.x = newX;//更新组件位置
        this.position.y = newY;
      }
    },
    onMouseUp(){
      this.dragging=false;
      //event.target.releasePointerCapture(event.pointerId);
      document.removeEventListener("pointermove", this.onMouseMove);//移除鼠标移动监听器
      document.removeEventListener("pointerup", this.onMouseUp);//移除鼠标释放监听器
      document.body.style.userSelect = '';
    }
  }
}

</script>

<template>
  <div class="About-Draggable" :style="{left:position.x + 'px' , top:position.y + 'px',zIndex: zIndex}" @pointerdown = "onMouseDown" v-bind="$attrs">
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
  </div>
</template>

<style scoped lang="scss">
.About-Draggable{
  position: absolute;
  cursor:move;
  width: 400px;
  height: 135px;
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
