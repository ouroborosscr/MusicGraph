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

    <div class="chart-wrapper" ref="chartRef"></div>
    
    <div v-if="loading" class="loading-mask">
      <n-spin size="large" description="正在加载星图..." />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NIcon, NSpin, useMessage } from 'naive-ui'
import { ArrowBack, Play } from '@vicons/ionicons5'
import * as echarts from 'echarts'
import request from '../api/request'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const graphId = route.params.id
const chartRef = ref<HTMLElement | null>(null)
const loading = ref(true)
const graphName = ref('加载中...')
const nodeCount = ref(0)

// 全局变量
let myChart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

// 返回首页
const goBack = () => {
  router.push('/')
}

// 初始化图表
const initChart = (nodes: any[], links: any[]) => {
  if (!chartRef.value) return
  
  // 如果已经存在实例，先销毁（防止重复初始化）
  if (myChart) {
    myChart.dispose()
  }

  myChart = echarts.init(chartRef.value, 'dark')
  
  const option = {
    backgroundColor: '#101014',
    title: { show: false },
    tooltip: {
      trigger: 'item',
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
        // 开启布局动画，让点慢慢散开
        layoutAnimation: true,
        data: nodes.map(n => ({
          id: n.id,
          name: n.name,
          symbolSize: n.symbolSize,
          value: n.artist,
          artist: n.artist,
          // 传入随机初始坐标，打破“一条线”的平衡
          x: n.x,
          y: n.y,
          itemStyle: {
            color: n.category === 1 ? '#a18cd1' : '#1db954'
          },
          label: {
            show: true,
            position: 'right',
            color: '#fff',
            fontSize: 12
          }
        })),
        links: links.map(l => ({
          source: l.source,
          target: l.target,
          value: l.value,
          lineStyle: {
            width: Math.min(l.value, 5),
            curveness: 0.2
          }
        })),
        roam: true,
        label: { show: true },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [4, 10],
        force: {
          repulsion: 400,
          edgeLength: [50, 200],
          // 增加重力，防止点飞得太远
          gravity: 0.1
        },
        lineStyle: {
          color: 'source',
          opacity: 0.6
        }
      }
    ]
  }

  myChart.setOption(option)

  // 绑定点击事件
  myChart.on('click', (params) => {
    if (params.dataType === 'node') {
      message.info(`选中歌曲: ${params.data.name}`)
    }
  })
}

// 加载数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get(`/graph/data/${graphId}`)
    const { nodes, links } = res.data
    
    nodeCount.value = nodes.length
    graphName.value = `图谱 #${graphId}`
    
    // 【关键优化】给节点预置一个随机范围
    // 即使容器高度一开始是 0，这些初始坐标也能防止点全部挤在 Y=0 的线上
    const enhancedNodes = nodes.map((n: any) => ({
      ...n,
      x: Math.random() * 500, 
      y: Math.random() * 500
    }))
    
    // 等待 Vue 更新 DOM（确保容器已渲染）
    await nextTick()
    
    initChart(enhancedNodes, links)
  } catch (err) {
    message.error('无法加载图谱数据')
    console.error(err)
  } finally {
    loading.value = false
  }
}

// 处理容器大小变化
const handleResize = () => {
  if (myChart) {
    myChart.resize()
  }
}

onMounted(async () => {
  // 1. 初始化 ResizeObserver 监听容器大小变化
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => {
      handleResize()
    })
    resizeObserver.observe(chartRef.value)
  }

  // 2. 获取数据并渲染
  await fetchData()
})

onUnmounted(() => {
  // 清理
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
  myChart?.dispose()
})
</script>

<style scoped>
.player-container {
  width: 100%;
  height: 100%; /* 充满父容器 */
  display: flex;
  flex-direction: column;
  background-color: #101014;
  position: relative;
}

.player-header {
  height: 60px;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  z-index: 10;
}

.left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.info h2 {
  margin: 0;
  font-size: 1.1rem;
  line-height: 1.2;
}

.info .status {
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.5);
}

.chart-wrapper {
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 400px; /* 兜底高度，防止 Flex 计算延迟导致高度为 0 */
  overflow: hidden;
}

.loading-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background: rgba(0,0,0,0.5);
  z-index: 20;
}
</style>