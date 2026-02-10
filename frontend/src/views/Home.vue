<template>
  <div class="dashboard-container">
    <div class="action-section">
      <div class="action-card create-card" @click="openCreateModal">
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

      <n-empty v-if="myGraphs.length === 0" description="这里空空如也，快去创建一个吧" style="margin-top: 40px;" />

      <n-grid v-else x-gap="20" y-gap="20" cols="1 s:2 m:3 l:4" responsive="screen">
        <n-grid-item v-for="graph in myGraphs" :key="graph.id">
          <n-card hoverable class="graph-card" @click="enterGraph(graph.id)">
            <template #cover>
              <div class="card-cover" :style="{ background: graph.color }">
                <n-icon size="48" color="rgba(255,255,255,0.8)"><MusicalNotesOutline /></n-icon>
              </div>
            </template>
            
            <div class="card-meta">
              <div class="meta-header">
                <div class="graph-title">{{ graph.name }}</div>
                
                <div @click.stop>
                  <n-dropdown 
                    trigger="click" 
                    :options="menuOptions" 
                    @select="(key) => handleMenuSelect(key, graph.id)"
                  >
                    <n-button quaternary circle size="tiny" class="more-btn">
                      <template #icon>
                        <n-icon><EllipsisHorizontal /></n-icon>
                      </template>
                    </n-button>
                  </n-dropdown>
                </div>
              </div>
              
              <div class="graph-date">更新于 {{ graph.updatedAt }}</div>
            </div>
          </n-card>
        </n-grid-item>
      </n-grid>
    </div>

    <n-modal v-model:show="showCreateModal">
      <n-card style="width: 600px" title="创建新图谱" :bordered="false" size="huge" role="dialog" aria-modal="true">
        <div style="margin-bottom: 24px;">
          <n-text depth="3" style="margin-bottom: 8px; display: block;">给你的图谱起个名字：</n-text>
          <n-input 
            v-model:value="newGraphName" 
            placeholder="例如：周杰伦的音乐宇宙" 
            size="large"
            maxlength="20"
            show-count
          />
        </div>
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
        <n-upload directory-dnd action="#" :custom-request="handleUpload" max="1">
          <n-upload-dragger>
            <div style="margin-bottom: 12px">
              <n-icon size="48" :depth="3"><CloudUploadOutline /></n-icon>
            </div>
            <n-text style="font-size: 16px">点击或者拖拽文件到该区域来上传</n-text>
            <n-p depth="3" style="margin: 8px 0 0 0">支持 JSON 数据格式</n-p>
          </n-upload-dragger>
        </n-upload>
      </n-card>
    </n-modal>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { 
  NButton, NCard, NIcon, NDivider, NGrid, NGridItem, 
  NModal, NUpload, NUploadDragger, NText, NP, useMessage, 
  NInput, NEmpty, NDropdown, useDialog 
} from 'naive-ui'
import { 
  AddCircleOutline, CloudUploadOutline, MusicalNotesOutline,
  DiscOutline, AlbumsOutline, EllipsisHorizontal, TrashOutline 
} from '@vicons/ionicons5'
import request from '../api/request'

const router = useRouter()
const message = useMessage()
const dialog = useDialog() // 【新增】确认框

const showCreateModal = ref(false)
const showImportModal = ref(false)
const newGraphName = ref('')
const myGraphs = ref<any[]>([])

// 【新增】菜单选项定义
const renderIcon = (icon: any) => () => h(NIcon, null, { default: () => h(icon) })
const menuOptions = [
  {
    label: '删除图谱',
    key: 'delete',
    icon: renderIcon(TrashOutline),
    // 可以给删除加个红色样式
    props: {
      style: { color: '#d03050' }
    }
  }
]

// 【新增】处理菜单点击
const handleMenuSelect = (key: string, id: number) => {
  if (key === 'delete') {
    handleDeleteGraph(id)
  }
}

// 【新增】删除逻辑
const handleDeleteGraph = (id: number) => {
  dialog.warning({
    title: '确认删除',
    content: '确定要删除这个图谱吗？（图谱内的数据暂时保留，但入口将移除）',
    positiveText: '确认删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await request.delete(`/graph/delete/${id}`)
        message.success('删除成功')
        // 刷新列表
        loadGraphs()
      } catch (err) {
        message.error('删除失败')
      }
    }
  })
}

// ... 以下原有逻辑保持不变 ...

const openCreateModal = () => {
  newGraphName.value = ''
  showCreateModal.value = true
}

const loadGraphs = async () => {
  try {
    const res = await request.get('/graph/list')
    myGraphs.value = res.data.map((g: any) => ({
      id: g.id,
      name: g.name,
      updatedAt: g.updatedAt ? new Date(g.updatedAt).toLocaleDateString() : '刚刚', 
      color: g.coverColor,
      nodeLabel: g.nodeLabel
    }))
  } catch (err) {
    message.error('加载图谱列表失败')
  }
}

onMounted(() => {
  loadGraphs()
})

const enterGraph = (id: number) => {
  router.push(`/player/${id}`)
}

const createNewGraph = async (type: 'empty' | 'template') => {
  try {
    const params: any = { type }
    if (newGraphName.value.trim()) {
      params.name = newGraphName.value.trim()
    }
    const res = await request.post('/graph/create', null, { params: params })
    const newGraph = res.data
    message.success(type === 'empty' ? '已创建空白图谱' : '已应用模板')
    showCreateModal.value = false
    router.push(`/player/${newGraph.id}`)
  } catch (err) {
    message.error('创建失败')
  }
}

const handleUpload = ({ file, onFinish }: any) => {
  setTimeout(() => {
    message.success(`成功导入: ${file.name}`)
    onFinish()
    showImportModal.value = false
    loadGraphs()
  }, 1000)
}
</script>

<style scoped>
/* ... 原有样式保持不变 ... */
.dashboard-container {
  padding: 40px;
  max-width: 1200px;
  margin: 0 auto;
}
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
  background-color: var(--n-card-color);
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
}
.action-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
}
.action-card h3 { margin: 10px 0 5px; font-size: 1.2rem; }
.action-card p { margin: 0; opacity: 0.6; font-size: 0.9rem; }
.create-card .icon { color: #1db954; }
.import-card .icon { color: #646cff; }
.list-section { margin-top: 20px; }
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
.graph-card:hover { transform: scale(1.02); }
.card-cover {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-meta { padding: 12px 4px; }

/* 【新增】meta-header Flex布局，让标题和菜单左右对齐 */
.meta-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 4px;
}

.graph-title {
  font-weight: 600;
  font-size: 1rem;
  /* 限制宽度防止标题盖住按钮 */
  max-width: 85%; 
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 调整按钮样式 */
.more-btn {
  opacity: 0.6;
}
.more-btn:hover {
  opacity: 1;
  background-color: rgba(0,0,0,0.05);
}

.graph-date { font-size: 0.8rem; opacity: 0.6; }
.template-option {
  border: 2px solid transparent;
  border-radius: 12px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  background-color: rgba(128,128,128,0.08);
  transition: all 0.2s;
}
.template-option:hover {
  border-color: #1db954;
  background-color: rgba(128,128,128,0.15);
}
.template-option h4 { margin: 10px 0 5px; }
.template-option p { margin: 0; opacity: 0.6; font-size: 0.85rem; }
</style>