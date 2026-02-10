<template>
  <div class="player-container">
    <div class="header">
      <h2>正在播放图谱 ID: {{ graphId }}</h2>
      <n-button @click="$router.push('/')">返回首页</n-button>
    </div>
    <div class="graph-placeholder">
      图谱可视化区域 (开发中...)
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { NButton } from 'naive-ui'

const route = useRoute()
const graphId = route.params.id
</script>

<style scoped>
.player-container {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.graph-placeholder {
  flex: 1;
  background: rgba(255,255,255,0.05);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 20px;
  border: 2px dashed rgba(255,255,255,0.2);
}
</style>