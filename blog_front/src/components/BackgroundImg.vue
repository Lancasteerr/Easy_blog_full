<script>
import bg1 from "@/assets/BackgroundImgs/1.jpg";
import bg2 from "@/assets/BackgroundImgs/2.jpg";
import bg3 from "@/assets/BackgroundImgs/3.jpg";
import bg4 from "@/assets/BackgroundImgs/4.jpg";
import bg5 from "@/assets/BackgroundImgs/5.jpg";

export default {
  name: "BackgroundImg",
  data() {
    return {
      carouseData: [
        {url: bg1},
        {url: bg2},
        {url: bg3},
        {url: bg4},
        {url: bg5}
      ]
    };
  }
};
</script>

<template>
  <div class="page-background">
    <el-carousel trigger="click" height="100vh" :interval="15000" arrow="hover" indicator-position="none">
      <el-carousel-item v-for="item in carouseData" :key="item">
        <div class="carousel-item" :style="{backgroundImage: 'url('+item.url+')'}"></div>
      </el-carousel-item>
    </el-carousel>
  </div>
</template>


<style scoped lang="scss">
.page-background{
  position: absolute;
  height: 100vh;
  width: 100%;
  z-index: 0;
}
.carousel-item{
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
}
</style>
