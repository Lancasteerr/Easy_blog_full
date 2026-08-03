<script>
import {globalZIndex} from "@/utils/DraggableZIndex";
import request from "@/utils/request";
import router from "@/router";
import {ArrowLeft, ArrowRight} from "@element-plus/icons-vue";

export default {
  name:"ArticleListDraggable",
  components: {
    ArrowLeft,
    ArrowRight,
  },
  data(){
    return {
      position: {
        x: 0.74 * window.innerWidth,
        y: 0.18 * window.innerHeight,
      },
      dragging: false,
      offset: {
        x: 0,
        y: 0,
      },
      zIndex: 1,
      articles: [],
      total: 0,
      page: 1,
      pageSize: 5,
      loading: false,
      loadFailed: false,
    }
  },
  computed: {
    totalPages() {
      return Math.max(1, Math.ceil(this.total / this.pageSize));
    },
    pageDots() {
      return Array.from({length: this.totalPages}, (_, index) => index + 1);
    },
  },
  mounted() {
    this.loadArticles();
  },
  beforeUnmount() {
    document.removeEventListener("pointermove", this.onMouseMove);
    document.removeEventListener("pointerup", this.onMouseUp);
    document.body.style.userSelect = '';
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
    },
    async loadArticles(){
      this.loading = true;
      this.loadFailed = false;

      try {
        const res = await request.get("/public/get_article_list", {
          params: {
            page: this.page,
            size: this.pageSize,
            sort: "viewCountDesc",
          }
        });

        const data = res.data || {};
        this.articles = Array.isArray(data.content) ? data.content : [];
        this.total = Number(data.totalElements) || 0;
        this.page = Number(data.number) || this.page;
      } catch (error) {
        this.loadFailed = true;
        console.error("Get hot article_list fail:", error);
      } finally {
        this.loading = false;
      }
    },
    changePage(nextPage){
      if (this.loading || nextPage < 1 || nextPage > this.totalPages || nextPage === this.page) {
        return;
      }

      this.page = nextPage;
      this.loadArticles();
    },
    goToArticleList(){
      router.push('/articlelist');
    },
    jumpto(id){
      router.push({ path: '/article', query: { id: id } });
    },
    formatDate(date){
      if (!date) {
        return "--";
      }

      return String(date).slice(0, 10);
    },
    formatViewCount(viewCount){
      const count = Number(viewCount);
      if (!Number.isFinite(count) || count <= 0) {
        return "0";
      }

      if (count >= 10000) {
        return `${(count / 10000).toFixed(count >= 100000 ? 0 : 1)}万`;
      }

      return String(count);
    }
  }
}

</script>

<template>
  <div class="ArticleList-Draggable" :style="{left:position.x + 'px' , top:position.y + 'px',zIndex: zIndex}" @pointerdown = "onMouseDown" v-bind="$attrs">
    <div class="ArticleList">
      <div class="ArticleListtitle">
        <button
            type="button"
            class="ArticleListTitleButton"
            aria-label="查看文章列表"
            @pointerdown.stop
            @click="goToArticleList"
        >
          <span>文章</span>
          <span class="ArticleListStitle">ARTICLE</span>
        </button>
      </div>
      <div class="ArticleListcontext">
        <div v-if="loading" class="ArticleState">加载中...</div>
        <div v-else-if="loadFailed" class="ArticleState">文章加载失败</div>
        <div v-else-if="articles.length === 0" class="ArticleState">暂无文章</div>
        <div v-else class="HotArticleList">
          <button
              v-for="item in articles"
              :key="item.id"
              type="button"
              class="HotArticleItem"
              @pointerdown.stop
              @click="jumpto(item.id)"
          >
            <span class="HotArticleText">
              <span class="HotArticleTitle">{{ item.articleTitle }}</span>
              <span class="HotArticleDate">{{ formatDate(item.articleDate) }}</span>
            </span>
            <span class="HotArticleViews">{{ formatViewCount(item.viewCount) }} 浏览</span>
          </button>
        </div>
        <div v-if="totalPages > 1" class="ArticlePagination" @pointerdown.stop>
          <button
              type="button"
              class="ArticlePageButton"
              aria-label="上一页"
              :disabled="page <= 1 || loading"
              @click="changePage(page - 1)"
          >
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <div class="ArticlePaginationDots">
            <button
                v-for="dot in pageDots"
                :key="dot"
                type="button"
                class="ArticlePaginationDot"
                :class="{'is-active': dot === page}"
                :aria-label="'第 ' + dot + ' 页'"
                @click="changePage(dot)"
            ></button>
          </div>
          <button
              type="button"
              class="ArticlePageButton"
              aria-label="下一页"
              :disabled="page >= totalPages || loading"
              @click="changePage(page + 1)"
          >
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ArticleList-Draggable{
  position: absolute;
  cursor:move;
  width: 400px;
  height: 250px;
  background-color: rgba(0,0,0,.88);
  box-shadow: 0 0 15px #000000;
}
.ArticleList{
  width: 100%;
  height: 100%;
}
.ArticleListtitle{
  display: flex;
  box-sizing: border-box;
  justify-content: space-between;
  width: 100%;
  height: 50px;
  padding: 15px;
  color: #ffffff;
  font-family: "Source Han Sans Bold" , sans-serif;
}
.ArticleListTitleButton{
  display: flex;
  align-items: baseline;
  justify-content: center;
  height: 28px;
  padding: 0;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #ffffff;
  font: inherit;
  font-size: 16px;
  cursor: pointer;
  transition: border-color .2s ease, color .2s ease;
}
.ArticleListTitleButton:hover,
.ArticleListTitleButton:focus-visible{
  border-bottom-color: #f3ff00;
  color: #f3ff00;
  outline: none;
}
.ArticleListStitle{
  box-sizing: border-box;
  font-size: 12px;
  padding-left: 6px;
  line-height: 28px;
  font-family: Source Han Sans Regular , sans-serif;
}
.ArticleListcontext{
  box-sizing: border-box;
  width: 100%;
  height: calc(100% - 50px);
  padding: 0 15px 12px;
  color: #ffffff;
  display: flex;
  flex-direction: column;
  font-family: Source Han Sans Regular , sans-serif;
}
.HotArticleList{
  flex: 1;
  min-height: 0;
}
.HotArticleItem{
  width: 100%;
  height: 30px;
  padding: 0;
  border: 0;
  border-top: 1px solid rgba(255,255,255,.14);
  background: transparent;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color .2s ease, border-color .2s ease;
}
.HotArticleItem:last-child{
  border-bottom: 1px solid rgba(255,255,255,.14);
}
.HotArticleItem:hover{
  background-color: rgba(255,255,255,.08);
  border-top-color: rgba(255,255,255,.28);
}
.HotArticleText{
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.HotArticleTitle{
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-family: Source Han Sans Bold , sans-serif;
}
.HotArticleDate{
  flex: none;
  font-size: 10px;
  color: rgba(255,255,255,.48);
}
.HotArticleViews{
  flex: none;
  min-width: 58px;
  text-align: right;
  font-size: 11px;
  color: rgba(255,255,255,.66);
}
.ArticleState{
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,.62);
  font-size: 13px;
  letter-spacing: 0;
}
.ArticlePagination{
  height: 28px;
  padding-top: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.ArticlePageButton{
  width: 24px;
  height: 24px;
  padding: 0;
  border: 1px solid rgba(255,255,255,.22);
  background-color: rgba(255,255,255,.04);
  color: rgba(255,255,255,.72);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color .2s ease, border-color .2s ease, color .2s ease;
}
.ArticlePageButton:not(:disabled):hover{
  border-color: #f3ff00;
  background-color: rgba(243,255,0,.12);
  color: #ffffff;
}
.ArticlePageButton:disabled{
  opacity: .25;
  cursor: not-allowed;
}
.ArticlePaginationDots{
  max-width: 252px;
  overflow-x: auto;
  display: flex;
  align-items: center;
  gap: 7px;
  scrollbar-width: none;
}
.ArticlePaginationDots::-webkit-scrollbar{
  display: none;
}
.ArticlePaginationDot{
  width: 6px;
  height: 6px;
  padding: 0;
  flex: none;
  border: 0;
  border-radius: 50%;
  background-color: rgba(255,255,255,.34);
  cursor: pointer;
  transition: background-color .2s ease, box-shadow .2s ease, transform .2s ease;
}
.ArticlePaginationDot:hover{
  background-color: rgba(255,255,255,.7);
}
.ArticlePaginationDot.is-active{
  background-color: rgba(255,255,255,.96);
  box-shadow: 0 0 0 3px rgba(255,255,255,.11);
  transform: scale(1.15);
}
</style>
