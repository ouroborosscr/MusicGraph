<template>
  <div class="dashboard-container">
    <div class="action-section">
      <div class="action-card create-card" @click="showCreateModal = true">
        <n-icon size="40" class="icon"><AddCircleOutline /></n-icon>
        <h3>创建音乐图谱</h3>
        <p>从零开始或使用模板</p>
      </div>
      
      <div class="action-card import-card" @click="showImportModal = true">
        <n-icon size="40" class="icon"><CloudUploadOutline /></n-icon>
        <h3>导入音乐图谱</h3>
        <p>支持 .json / .csv 格式</p>
      </div>
    </div>

    <n-divider />

    <div class="list-section">
      <div class="section-header">
        <h2>我的图谱</h2>
        <span class="count">{{ myGraphs.length }} 个项目</span>
      </div>

      <n-grid x-gap="20" y-gap="20" cols="1 s:2 m:3 l:4" responsive="screen">
        <n-grid-item v-for="graph in myGraphs" :key="graph.id">
          <n-card hoverable class="graph-card" @click="enterGraph(graph.id)">
            <template #cover>
              <div class="card-cover" :style="{ background: graph.color }">
                <n-icon size="48" color="rgba(255,255,255,0.8)"><MusicalNotesOutline /></n-icon>
              </div>
            </template>
            <div class="card-meta">
              <div class="graph-title">{{ graph.name }}</div>
              <div class="graph-date">{{ graph.updatedAt }}</div>
            </div>
          </n-card>
        </n-grid-item>
      </n-grid>
    </div>

    <n-modal v-model:show="showCreateModal">
      <n-card style="width: 600px" title="创建新图谱" :bordered="false" size="huge" role="dialog" aria-modal="true">
        <n-grid x-gap="12" cols="2">
          <n-grid-item>
            <div class="template-option" @click="createNewGraph('empty')">
              <n-icon size="40" color="#1db954"><DiscOutline /></n-icon>
              <h4>空图谱</h4>
              <p>一张白纸，自由创作</p>
            </div>
          </n-grid-item>
          <n-grid-item>
            <div class="template-option" @click="createNewGraph('template')">
              <n-icon size="40" color="#646cff"><AlbumsOutline /></n-icon>
              <h4>官方推荐模板</h4>
              <p>包含基础流派分类</p>
            </div>
          </n-grid-item>
        </n-grid>
      </n-card>
    </n-modal>

    <n-modal v-model:show="showImportModal">
      <n-card style="width: 500px" title="导入本地文件" :bordered="false" size="huge" role="dialog" aria-modal="true">
        <n-upload
          directory-dnd
          action="#" 
          :custom-request="handleUpload"
          max="1"
        >
          <n-upload-dragger>
            <div style="margin-bottom: 12px">
              <n-icon size="48" :depth="3">
                <CloudUploadOutline />
              </n-icon>
            </div>
            <n-text style="font-size: 16px">
              点击或者拖拽文件到该区域来上传
            </n-text>
            <n-p depth="3" style="margin: 8px 0 0 0">
              支持 JSON 数据格式，将自动解析节点与关系
            </n-p>
          </n-upload-dragger>
        </n-upload>
      </n-card>
    </n-modal>

  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { 
  NButton, NCard, NIcon, NDivider, NGrid, NGridItem, 
  NModal, NUpload, NUploadDragger, NText, NP, useMessage 
} from 'naive-ui'
import { 
  AddCircleOutline, 
  CloudUploadOutline, 
  MusicalNotesOutline,
  DiscOutline,
  AlbumsOutline
} from '@vicons/ionicons5'

const router = useRouter()
const message = useMessage()

// 状态控制
const showCreateModal = ref(false)
const showImportModal = ref(false)

// 模拟数据：我的图谱列表
// 未来这里应该调用 API: await request.get('/api/user/graphs')
const myGraphs = ref([
  { id: 101, name: '周杰伦的音乐宇宙', updatedAt: '2026-02-10 14:20', color: 'linear-gradient(135deg, #FF9A9E 0%, #FECFEF 100%)' },
  { id: 102, name: '2000年代华语金曲', updatedAt: '2026-02-09 09:30', color: 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)' },
  { id: 103, name: '摇滚编年史', updatedAt: '2026-02-08 18:15', color: 'linear-gradient(135deg, #84fab0 0%, #8fd3f4 100%)' },
  { id: 104, name: '深夜Emo歌单', updatedAt: '2026-02-05 22:00', color: 'linear-gradient(135deg, #cfd9df 0%, #e2ebf0 100%)' }
])

// 跳转进播放页面
const enterGraph = (id: number) => {
  router.push(`/player/${id}`)
}

// 创建图谱逻辑
const createNewGraph = (type: 'empty' | 'template') => {
  // TODO: 调用后端 API 创建图谱
  // const res = await request.post('/api/graph/create', { type })
  // const newId = res.data.id
  
  // 模拟创建成功
  const newId = Date.now()
  message.success(type === 'empty' ? '已创建空白图谱' : '已应用模板')
  showCreateModal.value = false
  router.push(`/player/${newId}`)
}

// 导入逻辑 (模拟)
const handleUpload = ({ file, onFinish }: any) => {
  // 模拟上传延迟
  setTimeout(() => {
    message.success(`成功导入: ${file.name}`)
    onFinish()
    showImportModal.value = false
    // 模拟刷新列表
    myGraphs.value.unshift({
      id: Date.now(),
      name: file.name.replace('.json', ''),
      updatedAt: '刚刚',
      color: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
    })
  }, 1000)
}
</script>

<style scoped>
.dashboard-container {
  padding: 40px;
  max-width: 1200px;
  margin: 0 auto;
}

/* 1. 操作区样式 */
.action-section {
  display: flex;
  gap: 24px;
  margin-bottom: 40px;
}

.action-card {
  flex: 1;
  height: 160px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid rgba(255,255,255,0.1);
  background: rgba(255,255,255,0.02);
}

.action-card:hover {
  transform: translateY(-5px);
  background: rgba(255,255,255,0.05);
  border-color: rgba(255,255,255,0.2);
}

.action-card h3 { margin: 10px 0 5px; font-size: 1.2rem; }
.action-card p { margin: 0; opacity: 0.6; font-size: 0.9rem; }

.create-card .icon { color: #1db954; }
.import-card .icon { color: #646cff; }

/* 2. 列表区样式 */
.list-section {
  margin-top: 20px;
}

.section-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}

.section-header h2 { margin: 0; }
.count { opacity: 0.5; font-size: 0.9rem; }

.graph-card {
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s;
  border: none;
}

.graph-card:hover {
  transform: scale(1.02);
}

.card-cover {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-meta {
  padding: 12px 4px;
}

.graph-title {
  font-weight: 600;
  font-size: 1rem;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.graph-date {
  font-size: 0.8rem;
  opacity: 0.6;
}

/* 弹窗样式 */
.template-option {
  border: 2px solid transparent;
  border-radius: 12px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  background: rgba(128,128,128,0.05);
  transition: all 0.2s;
}

.template-option:hover {
  background: rgba(128,128,128,0.1);
  border-color: #1db954;
}

.template-option h4 { margin: 10px 0 5px; }
.template-option p { margin: 0; opacity: 0.6; font-size: 0.85rem; }
</style>