<template>
  <div class="player-container" ref="containerRef">
    <div class="player-header">
      <div class="left">
        <n-button circle secondary @click="goBack">
          <template #icon><n-icon><ArrowBack /></n-icon></template>
        </n-button>
        <div class="info">
          <h2>{{ graphName }}</h2>
          <span class="status">包含 {{ nodeCount }} 首歌曲</span>
        </div>
      </div>
      <div class="right">
        <n-button type="primary" size="small" round>
          <template #icon><n-icon><Play /></n-icon></template>
          随机播放
        </n-button>
      </div>
    </div>

    <div 
      class="chart-out-box" 
      ref="boxRef"
      :style="boxStyle"
    >
      <div class="chart-wrapper" ref="chartRef"></div>

      <div class="zoom-controls">
        <n-button-group vertical>
          <n-button secondary circle @click="handleZoom(1.2)">
            <template #icon><n-icon><Add /></n-icon></template>
          </n-button>
          <n-button secondary circle @click="handleZoom(0.8)">
            <template #icon><n-icon><Remove /></n-icon></template>
          </n-button>
        </n-button-group>
      </div>

      <div class="resize-handle right" @mousedown.prevent="startResize($event, 'width')"></div>
      <div class="resize-handle bottom" @mousedown.prevent="startResize($event, 'height')"></div>
      <div class="resize-handle corner" @mousedown.prevent="startResize($event, 'both')">
        <n-icon size="16" color="#aaa"><ResizeOutline /></n-icon>
      </div>
    </div>
    
    <div v-if="loading" class="loading-mask">
      <n-spin size="large" description="正在加载星图..." />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NButtonGroup, NIcon, NSpin, useMessage, useThemeVars } from 'naive-ui'
import { ArrowBack, Play, Add, Remove, ResizeOutline } from '@vicons/ionicons5' // 引入 Resize 图标
import * as echarts from 'echarts'
import request from '../api/request'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const themeVars = useThemeVars()

const graphId = route.params.id
const chartRef = ref<HTMLElement | null>(null)
const boxRef = ref<HTMLElement | null>(null)      // 【新增】引用外层盒子
const containerRef = ref<HTMLElement | null>(null)// 【新增】引用最外层容器
const loading = ref(true)
const graphName = ref('加载中...')
const nodeCount = ref(0)
const currentZoom = ref(0.3)

let myChart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

// ================== 拖拽缩放逻辑开始 ==================

// 记录盒子尺寸状态
const boxState = reactive({
  isResizing: false,
  width: 0,  // 0 表示自动(flex: 1)
  height: 0,
  startX: 0,
  startY: 0,
  startWidth: 0,
  startHeight: 0,
  resizeMode: '' as 'width' | 'height' | 'both'
})

// 计算动态样式
const boxStyle = computed(() => {
  const style: any = {}
  // 如果正在调整或已经调整过，则使用固定尺寸
  if (boxState.width > 0) style.width = `${boxState.width}px`
  if (boxState.height > 0) style.height = `${boxState.height}px`
  
  // 如果设置了尺寸，取消 flex: 1，改为 flex: none 以便生效
  if (boxState.width > 0 || boxState.height > 0) {
    style.flex = 'none'
  }
  return style
})

const startResize = (e: MouseEvent, mode: 'width' | 'height' | 'both') => {
  if (!boxRef.value) return

  boxState.isResizing = true
  boxState.resizeMode = mode
  boxState.startX = e.clientX
  boxState.startY = e.clientY

  // 获取当前实际尺寸作为起始值
  const rect = boxRef.value.getBoundingClientRect()
  boxState.startWidth = rect.width
  boxState.startHeight = rect.height
  
  // 如果是第一次拖动，初始化当前尺寸，防止跳变
  if (boxState.width === 0) boxState.width = rect.width
  if (boxState.height === 0) boxState.height = rect.height

  // 添加全局事件监听
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  document.body.style.userSelect = 'none' // 防止拖动时选中文字
  document.body.style.cursor = mode === 'both' ? 'nwse-resize' : (mode === 'width' ? 'ew-resize' : 'ns-resize')
}

const onMouseMove = (e: MouseEvent) => {
  if (!boxState.isResizing) return

  const dx = e.clientX - boxState.startX
  const dy = e.clientY - boxState.startY

  // 限制最小尺寸
  const minSize = 200

  if (boxState.resizeMode === 'width' || boxState.resizeMode === 'both') {
    boxState.width = Math.max(minSize, boxState.startWidth + dx)
  }
  if (boxState.resizeMode === 'height' || boxState.resizeMode === 'both') {
    boxState.height = Math.max(minSize, boxState.startHeight + dy)
  }
}

const onMouseUp = () => {
  boxState.isResizing = false
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
  document.body.style.userSelect = ''
  document.body.style.cursor = ''
  
  // 触发一次图表重绘（虽然 ResizeObserver 会处理，但手动触发更稳妥）
  myChart?.resize()
}

// ================== 拖拽缩放逻辑结束 ==================

const goBack = () => {
  router.push('/')
}

const themeBlue = '#66ccff'

const handleZoom = (ratio: number) => {
  if (!myChart) return
  currentZoom.value *= ratio
  if (currentZoom.value < 0.1) currentZoom.value = 0.1
  if (currentZoom.value > 10) currentZoom.value = 10
  myChart.setOption({ series: [{ zoom: currentZoom.value }] })
}

const initChart = (nodes: any[], links: any[]) => {
  if (!chartRef.value) return
  if (chartRef.value.clientWidth === 0 || chartRef.value.clientHeight === 0) return

  if (myChart) myChart.dispose()

  myChart = echarts.init(chartRef.value)
  
  const tooltipBg = themeVars.value.cardColor
  const borderColor = themeVars.value.borderColor
  const textColor = themeVars.value.textColorBase

  const option = {
    backgroundColor: 'transparent',
    title: { show: false },
    tooltip: {
      trigger: 'item',
      backgroundColor: tooltipBg,
      borderColor: borderColor,
      textStyle: { color: textColor },
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          return `<b>${params.data.name}</b><br/>歌手: ${params.data.artist}`
        }
        return `${params.data.source} -> ${params.data.target}<br/>权重: ${params.data.value}`
      }
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        layoutAnimation: true,
        zoom: currentZoom.value,
        label: { show: true, position: 'right', color: themeBlue, fontSize: 12 },
        data: nodes.map(n => ({
          id: n.id,
          name: n.name,
          symbolSize: n.symbolSize,
          value: n.artist,
          artist: n.artist,
          x: n.x, y: n.y,
          itemStyle: { color: themeBlue, borderColor: '#4dabf7', borderWidth: 1, shadowBlur: 10, shadowColor: 'rgba(102, 204, 255, 0.5)' }
        })),
        links: links.map(l => ({
          source: l.source, target: l.target, value: l.value,
          lineStyle: { width: Math.min(l.value, 5), curveness: 0.2, color: themeBlue, opacity: 0.3 }
        })),
        roam: true,
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [4, 10],
        force: { repulsion: 400, edgeLength: [50, 200], gravity: 0.1 }
      }
    ]
  }

  myChart.setOption(option)
  
  myChart.on('click', (params) => {
    if (params.dataType === 'node') message.info(`选中歌曲: ${params.data.name}`)
  })
  myChart.on('graphRoam', (params: any) => {
    if (params.zoom) currentZoom.value *= params.zoom
  })
}

watch(() => themeVars.value, (newVars) => {
  if (myChart) {
    myChart.setOption({
      tooltip: { backgroundColor: newVars.cardColor, borderColor: newVars.borderColor, textStyle: { color: newVars.textColorBase } }
    })
  }
}, { deep: true })

let cachedNodes: any[] = []
let cachedLinks: any[] = []

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get(`/graph/data/${graphId}`)
    const { nodes, links } = res.data
    nodeCount.value = nodes.length
    graphName.value = `图谱 #${graphId}`
    cachedNodes = nodes.map((n: any) => ({ ...n, x: Math.random() * 800, y: Math.random() * 600 }))
    cachedLinks = links
    await nextTick()
    initChart(cachedNodes, cachedLinks)
  } catch (err) {
    message.error('无法加载图谱数据')
  } finally {
    loading.value = false
  }
}

const handleResize = () => {
  if (myChart) myChart.resize()
  else if (cachedNodes.length > 0) initChart(cachedNodes, cachedLinks)
}

onMounted(async () => {
  // 【新增】初始化盒子大小和位置
  if (containerRef.value) {
    const cw = containerRef.value.clientWidth
    const ch = containerRef.value.clientHeight
    
    // 设置为屏幕宽度的 65%，高度减去头部和一些边距
    boxState.width = Math.max(300, Math.floor(cw * 0.5))
    boxState.height = Math.max(800, Math.floor(ch - 100))
  }

  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => handleResize())
    resizeObserver.observe(chartRef.value)
  }
  await fetchData()
})

onUnmounted(() => {
  if (resizeObserver) resizeObserver.disconnect()
  myChart?.dispose()
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
})
</script>

<style scoped>
.player-container {
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  background-color: var(--n-body-color);
  overflow: hidden; /* 防止拖动过大撑开滚动条 */
}

.player-header {
  height: 60px;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: var(--n-card-color);
  border-bottom: 1px solid var(--n-border-color);
  z-index: 10;
  flex: none; /* 头部不参与 flex 伸缩 */
}

.left { display: flex; align-items: center; gap: 15px; }
.info h2 { margin: 0; font-size: 1.1rem; color: var(--n-text-color); }
.info .status { font-size: 0.8rem; color: var(--n-text-color-3); }

/* 容器样式调整 */
.chart-out-box {
  /* 默认 flex: 1，当 JS 设置 width/height 后这里会被覆盖为 flex: none */
  flex: 1; 
  padding: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative; 
  
  /* 【修改】靠右对齐：上 右 下 左。左边 auto 会把元素推向右边 */
  margin: 20px 20px 20px auto;
  
  border: 2px dashed v-bind('themeVars.borderColor');
  border-radius: 12px;
  background-color: v-bind('themeVars.tableColor');
  box-sizing: border-box; /* 关键：包含 padding 和 border */
  min-width: 300px;
  min-height: 300px;
  transition: border-color 0.3s;
}

/* 激活状态的边框高亮 */
.chart-out-box:hover {
  border-color: var(--n-primary-color);
}

.chart-wrapper {
  flex: 1;
  width: 100%;
  /* 移除最小高度限制，完全跟随父容器 */
  height: 100%; 
  overflow: hidden;
  border: 2px solid v-bind('themeVars.borderColor'); 
  border-radius: 16px;
  background-color: v-bind('themeVars.cardColor');
  box-sizing: border-box;
}

.zoom-controls {
  position: absolute;
  bottom: 40px;
  right: 40px;
  z-index: 90; /* 略低于 resize handle */
  background-color: var(--n-card-color);
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.loading-mask {
  position: absolute;
  top: 60px;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: var(--n-color-modal);
  z-index: 20;
  backdrop-filter: blur(2px);
}

/* ================== 拖拽手柄样式 ================== */
.resize-handle {
  position: absolute;
  z-index: 100;
  transition: background-color 0.2s;
}

/* 右边框感应区 */
.resize-handle.right {
  top: 0;
  right: -5px; /* 向外延伸一点方便抓取 */
  bottom: 20px; /* 留出角落 */
  width: 15px;
  cursor: ew-resize;
}

/* 下边框感应区 */
.resize-handle.bottom {
  bottom: -5px;
  left: 0;
  right: 20px;
  height: 15px;
  cursor: ns-resize;
}

/* 右下角拖拽点 */
.resize-handle.corner {
  bottom: 0;
  right: 0;
  width: 30px;
  height: 30px;
  cursor: nwse-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--n-card-color); /* 给个背景色挡住线条 */
  border-top-left-radius: 8px;
  border: 1px solid var(--n-border-color);
}
.resize-handle.corner:hover {
  background: var(--n-action-color);
}
</style>