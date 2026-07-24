<script>

import {globalZIndex} from "@/utils/DraggableZIndex";

export default {
  name:"ContactMeDraggable",
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
        y: 430,
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
  <div class="ContactMe-Draggable" :style="{left:position.x + 'px' , top:position.y + 'px',zIndex: zIndex}" @pointerdown = "onMouseDown" v-bind="$attrs">
    <div class="ContactMe">
      <div class="ContactMetitle">
        <div class="ContactMeToalTitle">
          <div>{{ card.title }}</div>
          <div class="ContactMeStitle">{{ card.subtitle }}</div>
        </div>
      </div>
      <div class="ContactMecontext" @pointerdown.stop>
        <p v-for="(paragraph, index) in card.paragraphs" :key="index">{{ paragraph }}</p>
        <ul v-if="card.items && card.items.length" class="ContactMeList">
          <li v-for="(item, index) in card.items" :key="index">{{ item }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ContactMe-Draggable{
  position: absolute;
  cursor:move;
  width: 300px;
  height: 150px;
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
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(48,48,48,.95) transparent;
  cursor: auto;
  color: #ffffff;
  box-sizing: border-box;
  padding: 0 15px 15px;
  line-height: 1.65;
  font-size: 13px;
  font-family: Source Han Sans Regular , sans-serif;
}
.ContactMecontext p{
  margin: 0 0 7px;
}
.ContactMecontext p:last-child{
  margin-bottom: 0;
}
.ContactMecontext::-webkit-scrollbar{
  width: 4px;
}
.ContactMecontext::-webkit-scrollbar-track{
  border: 0;
  background: transparent;
}
.ContactMecontext::-webkit-scrollbar-thumb{
  border: 0;
  border-radius: 999px;
  background-color: rgba(48,48,48,.95);
}
.ContactMecontext::-webkit-scrollbar-thumb:hover{
  background-color: rgba(72,72,72,.98);
}
.ContactMecontext::-webkit-scrollbar-button,
.ContactMecontext::-webkit-scrollbar-corner{
  display: none;
  width: 0;
  height: 0;
  background: transparent;
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
