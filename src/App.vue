<template>
  <div id="app">
    <router-view></router-view>
  </div>
</template>

<style lang="scss">
* {
  scrollbar-width: thin;
  scrollbar-color: rgba(120, 120, 120, 0.85) transparent;
}

*::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}

*::-webkit-scrollbar-thumb {
  border: 0;
  border-radius: 0;
  background: rgba(120, 120, 120, 0.85);
}

*::-webkit-scrollbar-thumb:hover {
  background: rgba(150, 150, 150, 0.95);
}

*::-webkit-scrollbar-track {
  border: 0;
  background-color: transparent;
}

*::-webkit-scrollbar-button,
*::-webkit-scrollbar-corner {
  display: none;
  width: 0;
  height: 0;
  border: 0;
  background: transparent;
}

#app {
  margin-top: 0;
}

body{
  margin: 0;
}
</style>
