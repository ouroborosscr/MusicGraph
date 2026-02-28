<template>
  <div class="page-container">
    <div class="left-panel">
      
      <div class="control-box">
        <div class="box-header" @click="toggleCollapse('manual')">
          <span class="title">手动修改图</span>
          <span class="toggle-icon">{{ collapsed.manual ? '+' : '-' }}</span>
        </div>
        
        <div v-show="!collapsed.manual" class="box-content">
          <div class="module-tabs">
            <div 
              class="tab-item" 
              :class="{ active: activeModule === 'listen' }"
              @click="activeModule = 'listen'"
            >
              添加听歌记录
            </div>
            <div 
              class="tab-item" 
              :class="{ active: activeModule === 'newListen' }"
              @click="activeModule = 'newListen'"
            >
              添加新的记录
            </div>
            <div 
              class="tab-item" 
              :class="{ active: activeModule === 'delete' }"
              @click="activeModule = 'delete'"
            >
              删除歌曲关联
            </div>
            <div 
              class="tab-item" 
              :class="{ active: activeModule === 'deleteNode' }"
              @click="activeModule = 'deleteNode'"
            >
              删除歌曲
            </div>
          </div>

          <div class="module-body">
            <div v-if="activeModule === 'listen'" class="form-group">
              <input v-model="forms.listenName" type="text" placeholder="请输入歌曲名称 (如: 稻香)" class="custom-input" />
              <div class="btn-row">
                <button class="btn btn-secondary" @click="forms.listenName = ''">清空</button>
                <button class="btn btn-primary" @click="handleAddListen">确定</button>
              </div>
            </div>

            <div v-if="activeModule === 'newListen'" class="form-group">
              <input v-model="forms.newListenName" type="text" placeholder="请输入歌曲名称" class="custom-input" />
              <div class="btn-row">
                <button class="btn btn-secondary" @click="forms.newListenName = ''">清空</button>
                <button class="btn btn-primary" @click="handleAddNewListen">确定</button>
              </div>
            </div>

            <div v-if="activeModule === 'delete'" class="form-group">
              <input v-model="forms.delFirstName" type="text" placeholder="歌曲 1" class="custom-input mb-2" />
              <input v-model="forms.delNextName" type="text" placeholder="歌曲 2" class="custom-input" />
              <div class="btn-row">
                <button class="btn btn-secondary" @click="clearDeleteForm">清空</button>
                <button class="btn btn-primary" @click="handleDeleteRelation">确定</button>
              </div>
            </div>

            <div v-if="activeModule === 'deleteNode'" class="form-group">
              <input v-model="forms.delNodeName" type="text" placeholder="请输入要删除的歌曲名称" class="custom-input" />
              <div class="btn-row">
                <button class="btn btn-secondary" @click="forms.delNodeName = ''">清空</button>
                <button class="btn btn-primary" @click="handleDeleteNode">确定</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="control-box">
        <div class="box-header" @click="toggleCollapse('config')">
          <span class="title">配置</span>
          <span class="toggle-icon">{{ collapsed.config ? '+' : '-' }}</span>
        </div>
        <div v-show="!collapsed.config" class="box-content placeholder-content">
          <p>配置项预留位置...</p>
        </div>
      </div>

      <div class="player-body-placeholder">
        <div class="placeholder-text">音乐播放器本体区域</div>
      </div>

    </div>

    <div class="right-panel">
      <div ref="chartRef" class="chart-container"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import request from '../api/request'
import * as echarts from 'echarts'

// --- 状态管理 ---
const route = useRoute()
const chartRef = ref<HTMLElement | null>(null)
let myChart: echarts.ECharts | null = null

// 左侧面板状态
const collapsed = reactive({
  manual: false,
  config: true
})
const activeModule = ref('listen') // 'listen' | 'newListen' | 'delete' | 'deleteNode'

const forms = reactive({
  listenName: '',
  newListenName: '',
  delFirstName: '',
  delNextName: '',
  delNodeName: '' // 改为 Name
})

// --- 交互逻辑 ---

const toggleCollapse = (key: 'manual' | 'config') => {
  collapsed[key as keyof typeof collapsed] = !collapsed[key as keyof typeof collapsed]
}

// 1. 添加听歌记录
const handleAddListen = async () => {
  if (!forms.listenName.trim()) return alert('请输入歌曲名称')
  
  const graphId = route.params.id;
  if (!graphId) return alert('未找到图谱ID');

  try {
    await request.post('/music/listen', null, {
      params: { 
        name: forms.listenName,
        graphId: graphId 
      }
    })
    alert('添加听歌记录成功')
    refreshGraph()
  } catch (error: any) {
    console.error(error)
    alert('操作失败: ' + (error.response?.data || error.message))
  }
}

// 2. 添加新的记录 (断连)
const handleAddNewListen = async () => {
  if (!forms.newListenName.trim()) return alert('请输入歌曲名称')
  
  const graphId = route.params.id;
  if (!graphId) return alert('未找到图谱ID');

  try {
    await request.post('/music/newlisten', null, {
      params: { 
        name: forms.newListenName,
        graphId: graphId
      }
    })
    alert('添加新记录成功')
    refreshGraph()
  } catch (error: any) {
    console.error(error)
    alert('操作失败: ' + (error.response?.data || error.message))
  }
}

// 3. 删除歌曲关联
const clearDeleteForm = () => {
  forms.delFirstName = ''
  forms.delNextName = ''
}

const handleDeleteRelation = async () => {
  if (!forms.delFirstName.trim() || !forms.delNextName.trim()) {
    return alert('请输入两首歌曲的名称')
  }
  try {
    await request.post('/music/delete', null, {
      params: {
        firstname: forms.delFirstName,
        nextname: forms.delNextName
      }
    })
    alert('删除关联成功')
    refreshGraph()
  } catch (error) {
    console.error(error)
    alert('操作失败')
  }
}

// 4. 删除歌曲节点 (按名称)
const handleDeleteNode = async () => {
  if (!forms.delNodeName.trim()) return alert('请输入要删除的歌曲名称')
  
  const graphId = route.params.id;
  if (!graphId) return alert('未找到图谱ID');

  try {
    await request.delete('/music/node/delete', {
      params: {
        graphId: graphId,
        name: forms.delNodeName // 传 name 参数
      }
    })
    alert('删除歌曲成功')
    forms.delNodeName = '' // 清空输入框
    refreshGraph()
  } catch (error: any) {
    console.error(error)
    alert('操作失败: ' + (error.response?.data || error.message))
  }
}

// --- 图谱逻辑 ---

const initChart = () => {
  if (chartRef.value && !myChart) {
    myChart = echarts.init(chartRef.value)
    
    // 点击节点时，自动填充名字到删除框，方便操作
    myChart.on('click', (params) => {
      if (params.dataType === 'node') {
        const nodeName = params.data.name;
        console.log('Clicked Node Name:', nodeName);
        forms.delNodeName = nodeName;
        activeModule.value = 'deleteNode'; // 自动切换到删除 Tab
      }
    })
  }
}

const refreshGraph = () => {
  loadData()
}

const loadData = async () => {
  await nextTick()
  if (!myChart) initChart()
  
  const graphId = route.params.id || 'default' 
  
  try {
    myChart?.showLoading()
    const res = await request.get(`/graph/data/${graphId}`)
    const { nodes, links } = res.data
    
    const option = {
      title: { text: '音乐知识图谱', left: 'center' },
      tooltip: {
        formatter: (params: any) => {
          if (params.dataType === 'node') {
            return `<b>${params.data.name}</b><br/>ID: ${params.data.id}<br/>歌手: ${params.data.artist}`;
          }
          return `${params.data.source} -> ${params.data.target}`;
        }
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          zoom: 0.5,
          data: nodes.map((n: any) => ({
            ...n,
            symbolSize: n.symbolSize || 30,
            label: { show: true }
          })),
          links: links,
          roam: true,
          label: {
            position: 'right',
            formatter: '{b}'
          },
          force: {
            repulsion: 300,
            edgeLength: 100
          }
        }
      ]
    }
    myChart?.hideLoading()
    myChart?.setOption(option)
  } catch (error) {
    console.error('加载图谱失败', error)
    myChart?.hideLoading()
  }
}

const handleResize = () => {
  myChart?.resize()
}

onMounted(() => {
  initChart()
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  myChart?.dispose()
})
</script>

<style scoped>
/* 整体布局：左右各半 */
.page-container {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background-color: #f5f7fa;
}

.left-panel {
  width: 50%;
  height: 100%;
  padding: 20px;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
  background-color: #fff;
  box-sizing: border-box;
}

.right-panel {
  width: 50%;
  height: 100%;
  position: relative;
  background-color: #ffffff; 
}

.chart-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
}

/* 控制框样式 */
.control-box {
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background-color: #fff;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  overflow: hidden;
  transition: all 0.3s;
  flex-shrink: 0;
}

.box-header {
  padding: 15px 20px;
  background-color: #f8f9fa;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  user-select: none;
}

.box-header:hover {
  background-color: #f0f2f5;
}

.box-header .title {
  font-weight: bold;
  font-size: 16px;
  color: #303133;
}

.box-header .toggle-icon {
  font-size: 18px;
  font-weight: bold;
  color: #909399;
}

.box-content {
  padding: 20px;
}

/* 模块 Tab 样式 */
.module-tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.tab-item {
  padding: 8px 16px;
  background-color: #f0f2f5;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.tab-item:hover {
  color: #409eff;
}

.tab-item.active {
  background-color: #ecf5ff;
  color: #409eff;
  border-color: #b3d8ff;
  font-weight: 500;
}

/* 表单样式 */
.form-group {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.custom-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  outline: none;
  font-size: 14px;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.custom-input:focus {
  border-color: #409eff;
}

.mb-2 {
  margin-bottom: 10px;
}

.btn-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn {
  padding: 8px 20px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  border: none;
  transition: opacity 0.2s;
}

.btn:hover {
  opacity: 0.85;
}

.btn-secondary {
  background-color: #f4f4f5;
  color: #909399;
}

.btn-primary {
  background-color: #409eff;
  color: white;
}

/* 占位符样式 */
.placeholder-content {
  color: #909399;
  font-size: 14px;
  text-align: center;
  padding: 40px 0;
}

.player-body-placeholder {
  flex: 1; /* 占据剩余空间 */
  min-height: 200px;
  border: 2px dashed #e0e0e0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #fafafa;
  margin-bottom: 20px; /* 底部留白 */
}

.placeholder-text {
  color: #c0c4cc;
  font-size: 18px;
  font-weight: bold;
}
</style>