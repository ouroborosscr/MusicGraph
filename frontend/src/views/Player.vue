<template>
  <div class="player-container">
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

    <div class="chart-out-box">
      <div class="chart-wrapper" ref="chartRef"></div>
    </div>
    
    <div v-if="loading" class="loading-mask">
      <n-spin size="large" description="正在加载星图..." />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NIcon, NSpin, useMessage, useThemeVars } from 'naive-ui'
import { ArrowBack, Play } from '@vicons/ionicons5'
import * as echarts from 'echarts'
import request from '../api/request'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const themeVars = useThemeVars()

const graphId = route.params.id
const chartRef = ref<HTMLElement | null>(null)
const loading = ref(true)
const graphName = ref('加载中...')
const nodeCount = ref(0)

let myChart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const goBack = () => {
  router.push('/')
}

// 定义统一的浅蓝色
const themeBlue = '#66ccff'

// 初始化图表
const initChart = (nodes: any[], links: any[]) => {
  if (!chartRef.value) return
  
  // 安全检查
  if (chartRef.value.clientWidth === 0 || chartRef.value.clientHeight === 0) {
    return
  }

  if (myChart) myChart.dispose()

  myChart = echarts.init(chartRef.value)
  
  // 获取主题变量用于 tooltip 和背景
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
        // 【修改点 1】全局设置所有节点的文字颜色为浅蓝色
        label: {
          show: true,
          position: 'right',
          color: themeBlue, 
          fontSize: 12
        },
        data: nodes.map(n => ({
          id: n.id,
          name: n.name,
          symbolSize: n.symbolSize,
          value: n.artist,
          artist: n.artist,
          x: n.x,
          y: n.y,
          itemStyle: {
            // 【修改点 2】统一设置节点颜色为浅蓝色
            color: themeBlue,
            // 描边稍微深一点，增加立体感
            borderColor: '#4dabf7',
            borderWidth: 1,
            shadowBlur: 10,
            shadowColor: 'rgba(102, 204, 255, 0.5)'
          }
        })),
        links: links.map(l => ({
          source: l.source,
          target: l.target,
          value: l.value,
          lineStyle: {
            width: Math.min(l.value, 5),
            curveness: 0.2,
            // 【修改点 3】连线也使用半透明的浅蓝色
            color: themeBlue,
            opacity: 0.3
          }
        })),
        roam: true,
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [4, 10],
        force: {
          repulsion: 400,
          edgeLength: [50, 200],
          gravity: 0.1
        }
      }
    ]
  }

  myChart.setOption(option)

  myChart.on('click', (params) => {
    if (params.dataType === 'node') {
      message.info(`选中歌曲: ${params.data.name}`)
    }
  })
}

// 监听主题变化，更新 tooltip 样式
watch(() => themeVars.value, (newVars) => {
  if (myChart) {
    myChart.setOption({
      tooltip: {
        backgroundColor: newVars.cardColor,
        borderColor: newVars.borderColor,
        textStyle: { color: newVars.textColorBase }
      }
    })
  }
}, { deep: true })

// 暂存数据
let cachedNodes: any[] = []
let cachedLinks: any[] = []

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get(`/graph/data/${graphId}`)
    const { nodes, links } = res.data
    
    nodeCount.value = nodes.length
    graphName.value = `图谱 #${graphId}`
    
    cachedNodes = nodes.map((n: any) => ({
      ...n,
      x: Math.random() * 800, 
      y: Math.random() * 600
    }))
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
  if (myChart) {
    myChart.resize()
  } else if (cachedNodes.length > 0) {
    initChart(cachedNodes, cachedLinks)
  }
}

onMounted(async () => {
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => handleResize())
    resizeObserver.observe(chartRef.value)
  }
  await fetchData()
})

onUnmounted(() => {
  if (resizeObserver) resizeObserver.disconnect()
  myChart?.dispose()
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
  transition: background-color 0.3s, border-color 0.3s;
}

.left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.info h2 {
  margin: 0;
  font-size: 1.1rem;
  color: var(--n-text-color);
}

.info .status {
  font-size: 0.8rem;
  color: var(--n-text-color-3);
}

/* 容器：添加外边距和虚线边框 */
.chart-out-box {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  
  /* 【关键修改】使用 v-bind 绑定主题变量 */
  margin: 20px;
  border: 2px dashed v-bind('themeVars.borderColor'); /* 加粗一点(2px)方便观察 */
  border-radius: 12px;
  background-color: v-bind('themeVars.tableColor'); /* 给一个极淡的背景色区分 */
}

/* 内部图表框：保持实线边框 */
.chart-wrapper {
  flex: 1;
  width: 100%;
  min-height: 400px;
  overflow: hidden;
  position: relative;
  
  /* 【关键修改】同样替换为 v-bind */
  border: 2px solid v-bind('themeVars.borderColor'); 
  border-radius: 16px;
  background-color: v-bind('themeVars.cardColor');
  transition: border-color 0.3s, background-color 0.3s;
  box-sizing: border-box;
}

.loading-mask {
  position: absolute;
  top: 60px; /* 让出头部高度 */
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
</style>