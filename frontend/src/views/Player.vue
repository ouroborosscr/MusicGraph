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
            <div class="tab-item" :class="{ active: activeModule === 'listen' }" @click="activeModule = 'listen'">添加听歌记录</div>
            <div class="tab-item" :class="{ active: activeModule === 'newListen' }" @click="activeModule = 'newListen'">添加新的记录</div>
            <div class="tab-item" :class="{ active: activeModule === 'delete' }" @click="activeModule = 'delete'">删除歌曲关联</div>
            <div class="tab-item" :class="{ active: activeModule === 'deleteNode' }" @click="activeModule = 'deleteNode'">删除歌曲</div>
          </div>

          <div class="module-body">
            <div v-if="activeModule === 'listen'" class="form-group">
              <input v-model="forms.listenName" type="text" placeholder="请输入歌曲名称" class="custom-input" />
              <div class="btn-row"><button class="btn btn-secondary" @click="forms.listenName = ''">清空</button><button class="btn btn-primary" @click="handleAddListen">确定</button></div>
            </div>
            <div v-if="activeModule === 'newListen'" class="form-group">
              <input v-model="forms.newListenName" type="text" placeholder="请输入歌曲名称" class="custom-input" />
              <div class="btn-row"><button class="btn btn-secondary" @click="forms.newListenName = ''">清空</button><button class="btn btn-primary" @click="handleAddNewListen">确定</button></div>
            </div>
            <div v-if="activeModule === 'delete'" class="form-group">
              <input v-model="forms.delFirstName" type="text" placeholder="歌曲 1" class="custom-input mb-2" />
              <input v-model="forms.delNextName" type="text" placeholder="歌曲 2" class="custom-input" />
              <div class="btn-row"><button class="btn btn-secondary" @click="clearDeleteForm">清空</button><button class="btn btn-primary" @click="handleDeleteRelation">确定</button></div>
            </div>
            <div v-if="activeModule === 'deleteNode'" class="form-group">
              <input v-model="forms.delNodeName" type="text" placeholder="请输入要删除的歌曲名称" class="custom-input" />
              <div class="btn-row"><button class="btn btn-secondary" @click="forms.delNodeName = ''">清空</button><button class="btn btn-primary" @click="handleDeleteNode">确定</button></div>
            </div>
          </div>
        </div>
      </div>

      <div class="control-box">
        <div class="box-header" @click="toggleCollapse('config')">
          <span class="title">配置</span>
          <span class="toggle-icon">{{ collapsed.config ? '+' : '-' }}</span>
        </div>
        <div v-show="!collapsed.config" class="box-content">
          <div class="form-group">
            <label class="form-label">QQ音乐Cookie</label>
            <textarea v-model="forms.cookie" placeholder="请粘贴您的 Cookie" class="custom-input cookie-input" rows="4"></textarea>
            <div class="btn-row"><button class="btn btn-secondary" @click="forms.cookie = ''">清空</button><button class="btn btn-primary" @click="handleUpdateCookie">确定</button></div>
          </div>
        </div>
      </div>

      <div class="player-wrapper">
        <div class="search-bar">
          <div class="search-input-wrapper">
            <Search class="search-icon" />
            <input 
              v-model="player.searchKeyword" 
              type="text" 
              placeholder="搜索你想听的歌曲..." 
              class="search-input"
              @keyup.enter="handleSearch"
            />
          </div>
          <button class="search-btn" @click="handleSearch">搜索</button>
        </div>

        <div class="song-list-container">
          <div v-if="player.loading" class="loading-state">搜索中...</div>
          <div v-else-if="player.searchList.length === 0" class="empty-state">
            暂无搜索结果
          </div>
          <div v-else class="song-list">
            <div 
              v-for="song in player.searchList" 
              :key="song.songmid" 
              class="song-item"
              :class="{ 'active': player.currentSong?.songmid === song.songmid }"
              @click="playSong(song)"
            >
              <div class="song-info-left">
                <div class="song-name" :title="song.songname">{{ song.songname }}</div>
                <div class="song-singer">{{ song.singer?.[0]?.name || '未知歌手' }}</div>
              </div>
              <div class="song-action">
                <PlayCircle class="play-icon-small" />
              </div>
            </div>
          </div>
        </div>

        <div class="play-bar">
          <audio 
            ref="audioRef" 
            :src="player.audioUrl" 
            @timeupdate="onTimeUpdate" 
            @ended="onEnded"
            @error="onAudioError"
          ></audio>

          <div class="controls">
            <button class="control-btn play-toggle" @click="togglePlay">
              <div class="icon-wrapper" :class="{ 'rotating': player.isPlaying }">
                <Disc v-if="player.isPlaying" class="icon-main" />
                <Play v-else class="icon-main" />
              </div>
            </button>
            
            <button class="control-btn next-btn" @click="playNext" title="下一首 (智能推荐)">
              <SkipForward class="icon-sub" />
            </button>
          </div>

          <div class="progress-container">
            <div class="time-info">
              <span>{{ formatTime(player.currentTime) }}</span>
              <span class="song-title-display">{{ player.currentSong?.songname || '未播放' }}</span>
              <span>{{ formatTime(player.duration) }}</span>
            </div>
            <div class="progress-track" @click="seekAudio">
              <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
            </div>
          </div>
        </div>
      </div>

    </div>

    <div class="right-panel">
      <div ref="chartRef" class="chart-container"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import request from '../api/request'
import * as echarts from 'echarts'
import { Search, Play, SkipForward, PlayCircle, Disc } from 'lucide-vue-next'

const route = useRoute()
const chartRef = ref<HTMLElement | null>(null)
let myChart: echarts.ECharts | null = null

// 左侧面板状态
const collapsed = reactive({ manual: false, config: true })
const activeModule = ref('listen')

const forms = reactive({
  listenName: '', newListenName: '', delFirstName: '', delNextName: '', delNodeName: '', cookie: ''
})

// 播放器状态
const audioRef = ref<HTMLAudioElement | null>(null)
const player = reactive({
  searchKeyword: '',
  searchList: [] as any[],
  loading: false,
  currentSong: null as any,
  currentNodeId: null as number | null, // 【新增】保存 Neo4j 中的节点 ID
  audioUrl: '',
  isPlaying: false,
  currentTime: 0,
  duration: 0
})

const progressPercent = computed(() => {
  if (!player.duration) return 0
  return (player.currentTime / player.duration) * 100
})

// --- 播放器逻辑 ---

// 1. 搜索
const handleSearch = async () => {
  if (!player.searchKeyword.trim()) return
  player.loading = true
  try {
    const res = await request.get('/music/search', { params: { key: player.searchKeyword } })
    player.searchList = res.data?.data?.song?.list || res.data?.data?.list || []
  } catch (error) {
    console.error('搜索失败', error)
    alert('搜索失败，请检查 Cookie 是否过期')
  } finally {
    player.loading = false
  }
}

// 2. 播放歌曲 (核心入口)
const playSong = async (song: any) => {
  if (!song.songmid) return
  const graphId = route.params.id
  if (!graphId) return alert('缺少图谱ID')

  try {
    // A. 获取播放链接
    const urlRes = await request.get('/music/song/urls', { params: { id: song.songmid } })
    const urlMap = urlRes.data?.data || {}
    const playUrl = urlMap[song.songmid]

    if (!playUrl) {
      alert(`无法播放《${song.songname}》，可能是VIP歌曲或Cookie失效`)
      // 如果是自动播放下一首失败，尝试递归跳过（慎用，防止死循环）
      return
    }

    // B. 更新状态并播放
    player.currentSong = song
    player.audioUrl = playUrl
    player.isPlaying = true
    
    await nextTick()
    audioRef.value?.play()

    // C. 添加听歌记录并 【保存 Node ID】
    const listenRes = await request.post('/music/listen', null, {
      params: { 
        name: song.songname, 
        artist: song.singer?.[0]?.name || 'Unknown',
        graphId: graphId 
      }
    })
    
    // 关键：保存后端返回的 ID，用于下次推荐
    if (listenRes.data && listenRes.data.id) {
      player.currentNodeId = listenRes.data.id
    }

    // D. 刷新图谱
    refreshGraph()

  } catch (error) {
    console.error('播放失败', error)
  }
}

// 3. 智能下一首 (使用推荐算法)
const playNext = async () => {
  const graphId = route.params.id
  if (!graphId || !player.currentNodeId) {
    alert('请先手动播放一首歌曲，以便生成推荐种子')
    return
  }

  try {
    // A. 获取推荐列表
    const res = await request.get('/music/recommend', {
      params: {
        graphId: graphId,
        currentId: player.currentNodeId
      }
    })

    const recommendations = res.data || []
    if (recommendations.length === 0) {
      alert('暂无推荐歌曲，去搜索听点新的吧！')
      return
    }

    // B. 取分数最高的推荐歌曲
    const topRec = recommendations[0].song // 拿到 {id, name, artist}
    console.log('推荐歌曲:', topRec.name, '推荐理由:', recommendations[0].reason)

    // C. [桥接] 因为 Neo4j 没存 songmid，我们需要用名字搜一下来获取播放源
    // 注意：这里可能会搜到同名不同版的歌，暂且取第一个
    const searchRes = await request.get('/music/search', { 
      params: { key: topRec.name + ' ' + topRec.artist } 
    })
    
    const list = searchRes.data?.data?.song?.list || searchRes.data?.data?.list || []
    
    if (list.length > 0) {
      // 找到了！播放它
      // 提示用户正在切歌
      // alert(`为您推荐：${topRec.name}`) // 可选，体验更好可以做成 Toast
      playSong(list[0])
    } else {
      console.warn('推荐了歌曲但在曲库没搜到:', topRec.name)
      // 容错：如果搜不到，尝试推荐列表的下一个（这里简单处理，直接提示）
      alert(`推荐歌曲《${topRec.name}》暂时无法播放`)
    }

  } catch (error) {
    console.error('推荐失败', error)
  }
}

// 4. 播放控制
const togglePlay = () => {
  if (!audioRef.value) return
  if (player.isPlaying) audioRef.value.pause()
  else audioRef.value.play()
  player.isPlaying = !player.isPlaying
}

// 5. 进度条与事件
const onTimeUpdate = () => {
  if (audioRef.value) {
    player.currentTime = audioRef.value.currentTime
    player.duration = audioRef.value.duration || 0
  }
}

const onEnded = () => {
  player.isPlaying = false
  // 自动播放下一首
  playNext()
}

const onAudioError = () => {
  player.isPlaying = false
  console.error('音频加载出错')
}

const seekAudio = (e: MouseEvent) => {
  if (!audioRef.value || !player.duration) return
  const track = e.currentTarget as HTMLElement
  const rect = track.getBoundingClientRect()
  const clickX = e.clientX - rect.left
  const newTime = (clickX / rect.width) * player.duration
  audioRef.value.currentTime = newTime
}

const formatTime = (seconds: number) => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

// --- 其他逻辑 (保持不变) ---
const toggleCollapse = (key: any) => collapsed[key] = !collapsed[key]
const handleUpdateCookie = async () => {
  if (!forms.cookie.trim()) return alert('请输入 Cookie')
  const username = localStorage.getItem('username')
  try {
    await request.post('/user/cookie', null, { params: { username, cookie: forms.cookie } })
    alert('Cookie 配置成功')
  } catch (e:any) { alert('配置失败: ' + (e.response?.data || e.message)) }
}
const handleAddListen = async () => { /* 调用通用接口 */
  const graphId = route.params.id;
  await request.post('/music/listen', null, { params: { name: forms.listenName, graphId } })
  refreshGraph()
}
const handleAddNewListen = async () => { 
  const graphId = route.params.id;
  await request.post('/music/newlisten', null, { params: { name: forms.newListenName, graphId } })
  refreshGraph()
}
const clearDeleteForm = () => { forms.delFirstName = ''; forms.delNextName = '' }
const handleDeleteRelation = async () => {
  await request.post('/music/delete', null, { params: { firstname: forms.delFirstName, nextname: forms.delNextName } })
  refreshGraph()
}
const handleDeleteNode = async () => {
  const graphId = route.params.id;
  await request.delete('/music/node/delete', { params: { graphId, name: forms.delNodeName } })
  refreshGraph()
}

// --- 图谱逻辑 ---
const initChart = () => {
  if (chartRef.value && !myChart) {
    myChart = echarts.init(chartRef.value)
    myChart.on('click', (params: any) => {
      if (params.dataType === 'node') forms.delNodeName = params.data.name
    })
  }
}
const refreshGraph = () => loadData()
const loadData = async () => {
  await nextTick()
  if (!myChart) initChart()
  const graphId = route.params.id || 'default'
  try {
    myChart?.showLoading()
    const [graphRes, historyRes] = await Promise.all([
      request.get(`/graph/data/${graphId}`),
      request.get('/music/listenhistory', { params: { graphId } })
    ])
    const { nodes, links } = graphRes.data
    const historyList = historyRes.data || []
    const lastSongId = historyList.length > 0 ? String(historyList[0].id) : null

    const option = {
      title: { text: '音乐知识图谱', left: 'center' },
      tooltip: { formatter: (p:any) => p.dataType==='node' ? `<b>${p.data.name}</b><br/>${p.data.artist}` : `${p.data.source}->${p.data.target}` },
      series: [{
        type: 'graph', layout: 'force', zoom: 0.5,
        data: nodes.map((n:any) => {
          const isCurrent = lastSongId && String(n.id) === lastSongId
          return {
            ...n,
            symbolSize: isCurrent ? 40 : (n.symbolSize || 30),
            label: { show: true, color: isCurrent ? '#52c41a' : undefined, fontWeight: isCurrent ? 'bold' : 'normal', fontSize: isCurrent ? 14 : 12 },
            itemStyle: isCurrent ? { color: '#52c41a', borderColor: '#fff', borderWidth: 3, shadowBlur: 20, shadowColor: '#52c41a' } : undefined
          }
        }),
        links, roam: true, label: { position: 'right', formatter: '{b}' }, force: { repulsion: 300, edgeLength: 100 }
      }]
    }
    myChart?.hideLoading()
    myChart?.setOption(option)
  } catch (e) { console.error(e); myChart?.hideLoading() }
}
const handleResize = () => myChart?.resize()
onMounted(() => { initChart(); loadData(); window.addEventListener('resize', handleResize) })
onUnmounted(() => { window.removeEventListener('resize', handleResize); myChart?.dispose() })
</script>

<style scoped>
/* 样式保持不变 */
.page-container { display: flex; width: 100vw; height: 100vh; overflow: hidden; background-color: #f5f7fa; }
.left-panel { width: 50%; height: 100%; padding: 20px; border-right: 1px solid #e0e0e0; display: flex; flex-direction: column; gap: 20px; background-color: #fff; box-sizing: border-box; overflow-y: hidden; }
.right-panel { width: 50%; height: 100%; position: relative; background-color: #fff; }
.chart-container { width: 100%; height: 100%; min-height: 400px; }

/* Control Box */
.control-box { border: 1px solid #dcdfe6; border-radius: 8px; background-color: #fff; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05); overflow: hidden; flex-shrink: 0; }
.box-header { padding: 12px 20px; background-color: #f8f9fa; cursor: pointer; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #ebeef5; }
.box-header .title { font-weight: bold; font-size: 15px; color: #303133; }
.box-content { padding: 15px; }
.module-tabs { display: flex; gap: 8px; margin-bottom: 15px; flex-wrap: wrap; }
.tab-item { padding: 6px 12px; background-color: #f0f2f5; border-radius: 4px; cursor: pointer; font-size: 12px; color: #606266; }
.tab-item.active { background-color: #ecf5ff; color: #409eff; font-weight: 500; }
.form-group { display: flex; flex-direction: column; gap: 8px; }
.custom-input { padding: 8px; border: 1px solid #dcdfe6; border-radius: 4px; outline: none; font-size: 13px; }
.custom-input:focus { border-color: #409eff; }
.btn-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 5px; }
.btn { padding: 6px 16px; border-radius: 4px; font-size: 13px; cursor: pointer; border: none; }
.btn-secondary { background-color: #f4f4f5; color: #909399; }
.btn-primary { background-color: #409eff; color: white; }

/* 播放器样式 */
.player-wrapper { flex: 1; display: flex; flex-direction: column; border: 1px solid #e0e0e0; border-radius: 8px; background-color: #fff; overflow: hidden; }
.search-bar { padding: 15px; border-bottom: 1px solid #f0f0f0; display: flex; gap: 10px; background: #fff; }
.search-input-wrapper { flex: 1; position: relative; display: flex; align-items: center; }
.search-icon { position: absolute; left: 10px; width: 16px; height: 16px; color: #999; }
.search-input { width: 100%; padding: 8px 10px 8px 32px; border: 1px solid #dcdfe6; border-radius: 20px; outline: none; font-size: 14px; background-color: #f9f9f9; }
.search-input:focus { background-color: #fff; border-color: #409eff; }
.search-btn { padding: 0 20px; background-color: #409eff; color: white; border: none; border-radius: 20px; cursor: pointer; font-size: 14px; }
.song-list-container { flex: 1; overflow-y: auto; background-color: #fafafa; }
.loading-state, .empty-state { text-align: center; padding: 40px; color: #909399; font-size: 14px; }
.song-item { display: flex; justify-content: space-between; align-items: center; padding: 12px 15px; border-bottom: 1px solid #f0f0f0; cursor: pointer; transition: background 0.2s; }
.song-item:hover { background-color: #e6f7ff; }
.song-item.active { background-color: #e6f7ff; border-left: 3px solid #1890ff; }
.song-info-left { flex: 1; min-width: 0; }
.song-name { font-size: 14px; color: #333; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.song-singer { font-size: 12px; color: #888; margin-top: 4px; }
.song-action { margin-left: 10px; color: #409eff; opacity: 0; transition: opacity 0.2s; }
.song-item:hover .song-action { opacity: 1; }
.play-bar { height: 70px; background-color: #fff; border-top: 1px solid #e0e0e0; display: flex; align-items: center; padding: 0 15px; gap: 15px; }
.controls { display: flex; align-items: center; gap: 15px; }
.control-btn { background: none; border: none; cursor: pointer; padding: 0; display: flex; align-items: center; justify-content: center; transition: transform 0.1s; }
.control-btn:active { transform: scale(0.9); }
.play-toggle .icon-wrapper { width: 40px; height: 40px; border-radius: 50%; background-color: #333; color: #fff; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
.next-btn .icon-sub { width: 24px; height: 24px; color: #666; }
.next-btn:hover .icon-sub { color: #333; }
.rotating { animation: rotate 3s linear infinite; }
@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.progress-container { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 6px; }
.time-info { display: flex; justify-content: space-between; font-size: 11px; color: #999; }
.song-title-display { color: #333; font-weight: 500; font-size: 12px; }
.progress-track { height: 4px; background-color: #e0e0e0; border-radius: 2px; cursor: pointer; position: relative; }
.progress-fill { height: 100%; background-color: #409eff; border-radius: 2px; position: absolute; top: 0; left: 0; transition: width 0.1s linear; }
.progress-track::after { content: ''; position: absolute; top: -5px; bottom: -5px; left: 0; right: 0; }
</style>