<script>

import {globalZIndex} from "@/utils/DraggableZIndex";

export default {
  name:"WorkExpDraggable",
  data(){
    return {
      position: {
        x: 40,
        y: 280,
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
  <div class="WorkExp-Draggable" :style="{left:position.x + 'px' , top:position.y + 'px',zIndex: zIndex}" @pointerdown = "onMouseDown" v-bind="$attrs">
    <div class="WorkExp">
      <div class="WorkExptitle">
        <div style="display: flex;font-size: 16px;" class="WorkExpToalTitle">
          <div>工作履历</div>
          <div class="WorkExpStitle">CAREER</div>
        </div>
      </div>
      <div class="WorkExpcontext">
        Type WorkExp context.
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.WorkExp-Draggable{
  position: absolute;
  cursor:move;
  width: 400px;
  height: 135px;
  background-color: rgba(0,0,0,.88);
  box-shadow: 0 0 15px #000000;
}
.WorkExp{
  width: 100%;
  height: 100%;
}
.WorkExpToalTitle{
  box-sizing: border-box;
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
  color: #ffffff;
  box-sizing: border-box;
  padding-left: 15px;
  padding-right: 15px;
  padding-bottom: 15px;
  font-family: Source Han Sans Regular , sans-serif;
}
</style>
