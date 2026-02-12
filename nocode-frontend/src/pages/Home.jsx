import { toast } from 'sonner';
import { useNavigate } from 'react-router-dom';
import { Album, Music, Trash2, Disc, FileJson, Upload, MoreHorizontal, Plus, X } from 'lucide-react';
import request from '../utils/request';
import Navbar from '../components/Navbar';
import { useAuth } from '../hooks/useAuth';
import { useEffect, useState } from 'react';

const Home = () => {
  const { isLoggedIn } = useAuth()
  const navigate = useNavigate()
  const [myGraphs, setMyGraphs] = useState([])
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showImportModal, setShowImportModal] = useState(false)
  const [newGraphName, setNewGraphName] = useState('')
  const [loading, setLoading] = useState(false)

  const loadGraphs = async () => {
    try {
      const response = await request.get('/graph/list')
      const graphs = response.data.map(g => ({
        id: g.id,
        name: g.name,
        updatedAt: g.updatedAt ? new Date(g.updatedAt).toLocaleDateString() : '刚刚',
        color: g.coverColor || '#1db954',
        nodeLabel: g.nodeLabel
      }))
      setMyGraphs(graphs)
    } catch (error) {
      console.error('加载图谱列表失败:', error)
    }
  }

  useEffect(() => {
    // 双重检查：既检查内存状态，也检查localStorage
    const hasToken = localStorage.getItem('token')
    const isLoggedInStorage = localStorage.getItem('isLoggedIn') === 'true'

    // 如果内存状态和localStorage状态都不为已登录，则重定向
    if (!isLoggedIn && !hasToken && !isLoggedInStorage) {
      navigate('/login')
      return
    }

    loadGraphs()
  }, [isLoggedIn, navigate])

  // 如果localStorage显示已登录但内存状态未更新，显示加载中
  if (!isLoggedIn && localStorage.getItem('isLoggedIn') === 'true') {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">正在加载...</p>
        </div>
      </div>
    )
  }

  if (!isLoggedIn) {
    return null
  }

  const enterGraph = (id) => {
    navigate(`/player/${id}`)
  }

  const createNewGraph = async (type) => {
    try {
      const params = { type }
      if (newGraphName.trim()) {
        params.name = newGraphName.trim()
      }
      const response = await request.post('/graph/create', null, { params })
      const newGraph = response.data
      setShowCreateModal(false)
      setNewGraphName('')
      navigate(`/player/${newGraph.id}`)
    } catch (error) {
      console.error('创建失败:', error)
    }
  }

  const handleDeleteGraph = async (id) => {
    if (window.confirm('确定要删除这个图谱吗？（图谱内的数据暂时保留，但入口将移除）')) {
      try {
        await request.delete(`/graph/delete/${id}`)
        loadGraphs()
      } catch (error) {
        console.error('删除失败:', error)
      }
    }
  }

  const handleFileUpload = (event) => {
    const file = event.target.files[0]
    if (file) {
      setTimeout(() => {
        setShowImportModal(false)
        loadGraphs()
      }, 1000)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />

      <div className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          {/* 操作区域 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
            <div
              className="bg-white rounded-xl shadow-sm p-8 hover:shadow-lg transition-all duration-300 cursor-pointer border border-gray-100"
              onClick={() => setShowCreateModal(true)}
            >
              <div className="text-center">
                <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                  <Plus className="h-8 w-8 text-green-600" />
                </div>
                <h3 className="text-xl font-semibold text-gray-900 mb-2">创建音乐图谱</h3>
                <p className="text-gray-600">从零开始或使用模板</p>
              </div>
            </div>

            <div
              className="bg-white rounded-xl shadow-sm p-8 hover:shadow-lg transition-all duration-300 cursor-pointer border border-gray-100"
              onClick={() => setShowImportModal(true)}
            >
              <div className="text-center">
                <div className="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
                  <Upload className="h-8 w-8 text-blue-600" />
                </div>
                <h3 className="text-xl font-semibold text-gray-900 mb-2">导入音乐图谱</h3>
                <p className="text-gray-600">支持 .json / .csv 格式</p>
              </div>
            </div>
          </div>

          <div className="border-t border-gray-200 my-8"></div>

          {/* 图谱列表 */}
          <div>
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-2xl font-bold text-gray-900">我的图谱</h2>
              <span className="text-sm text-gray-500">{myGraphs.length} 个项目</span>
            </div>

            {myGraphs.length === 0 ? (
              <div className="text-center py-12">
                <Music className="h-16 w-16 text-gray-300 mx-auto mb-4" />
                <p className="text-gray-500">这里空空如也，快去创建一个吧</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                {myGraphs.map((graph) => (
                  <div
                    key={graph.id}
                    className="bg-white rounded-xl shadow-sm hover:shadow-lg transition-all duration-200 cursor-pointer overflow-hidden border border-gray-100"
                    onClick={() => enterGraph(graph.id)}
                  >
                    <div
                      className="h-32 flex items-center justify-center"
                      style={{ background: graph.color }}
                    >
                      <Music className="h-12 w-12 text-white opacity-80" />
                    </div>
                    <div className="p-4">
                      <div className="flex items-start justify-between mb-2">
                        <h3 className="font-semibold text-gray-900 truncate flex-1 mr-2">
                          {graph.name}
                        </h3>
                        <div onClick={(e) => e.stopPropagation()}>
                          <button
                            onClick={() => handleDeleteGraph(graph.id)}
                            className="p-1 text-gray-400 hover:text-red-500 transition-colors"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </div>
                      <p className="text-sm text-gray-500">更新于 {graph.updatedAt}</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 创建图谱模态框 */}
      {showCreateModal && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={() => setShowCreateModal(false)}
        >
          <div
            className="bg-white rounded-xl p-8 max-w-2xl w-full mx-4 relative"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              onClick={() => setShowCreateModal(false)}
              className="absolute top-4 right-4 text-gray-400 hover:text-gray-600 transition-colors"
            >
              <X className="h-6 w-6" />
            </button>

            <h2 className="text-2xl font-bold text-gray-900 mb-6">创建新图谱</h2>

            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                给你的图谱起个名字：
              </label>
              <input
                type="text"
                value={newGraphName}
                onChange={(e) => setNewGraphName(e.target.value)}
                placeholder="例如：周杰伦的音乐宇宙"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                maxLength={20}
              />
              <div className="text-right text-sm text-gray-500 mt-1">
                {newGraphName.length}/20
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div
                className="border-2 border-gray-200 rounded-xl p-6 text-center cursor-pointer hover:border-green-500 hover:bg-green-50 transition-all"
                onClick={() => createNewGraph('empty')}
              >
                <Disc className="h-10 w-10 text-green-500 mx-auto mb-3" />
                <h4 className="font-semibold text-gray-900 mb-2">空图谱</h4>
                <p className="text-sm text-gray-600">一张白纸，自由创作</p>
              </div>

              <div
                className="border-2 border-gray-200 rounded-xl p-6 text-center cursor-pointer hover:border-blue-500 hover:bg-blue-50 transition-all"
                onClick={() => createNewGraph('template')}
              >
                <Album className="h-10 w-10 text-blue-500 mx-auto mb-3" />
                <h4 className="font-semibold text-gray-900 mb-2">官方推荐模板</h4>
                <p className="text-sm text-gray-600">包含基础流派分类</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 导入文件模态框 */}
      {showImportModal && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={() => setShowImportModal(false)}
        >
          <div
            className="bg-white rounded-xl p-8 max-w-md w-full mx-4 relative"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              onClick={() => setShowImportModal(false)}
              className="absolute top-4 right-4 text-gray-400 hover:text-gray-600 transition-colors"
            >
              <X className="h-6 w-6" />
            </button>

            <h2 className="text-2xl font-bold text-gray-900 mb-6">导入本地文件</h2>

            <div className="border-2 border-dashed border-gray-300 rounded-xl p-8 text-center">
              <FileJson className="h-12 w-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-600 mb-4">点击或者拖拽文件到该区域来上传</p>
              <p className="text-sm text-gray-500 mb-4">支持 JSON 数据格式</p>
              <input
                type="file"
                accept=".json,.csv"
                onChange={handleFileUpload}
                className="hidden"
                id="file-upload"
              />
              <label
                htmlFor="file-upload"
                className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 cursor-pointer transition-colors"
              >
                选择文件
              </label>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Home
