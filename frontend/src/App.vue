<script setup lang="ts">
import { darkTheme, NConfigProvider, NMessageProvider, NGlobalStyle } from 'naive-ui'
</script>

<template>
  <n-config-provider :theme="darkTheme">
    <n-global-style />
    
    <n-message-provider>
      <router-view />
    </n-message-provider>
    
  </n-config-provider>
</template>

<style>
/* 确保占满全屏 */
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  width: 100%;
  background-color: #101014; /* 兜底背景色 */
}
</style>