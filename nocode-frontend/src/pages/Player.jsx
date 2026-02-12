import { useEffect, useState, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ArrowLeft, Play, Plus, Minus } from 'lucide-react';
import Navbar from '../components/Navbar';
import * as echarts from 'echarts';
import request from '../utils/request';

const Player = () => {
  const { id } = useParams()
  const navigate = useNavigate()
  const { isLoggedIn } = useAuth()
  const chartRef = useRef(null)
  const containerRef = useRef(null)
  const [loading, setLoading] = useState(true)
  const [graphName, setGraphName] = useState('加载中...')
  const [nodeCount, setNodeCount] = useState(0)
  const [currentZoom, setCurrentZoom] = useState(0.3)
  const [myChart, setMyChart] = useState(null)
  const [resizeState, setResizeState] = useState({
    isResizing: false,
    width: 0,
    height: 0,
    startX: 0,
    startY: 0,
    startWidth: 0,
    startHeight: 0,
    resizeMode: ''
  })

  const initChart = useCallback((nodes, links) => {
    if (!chartRef.current) return
    
    const chart = echarts.init(chartRef.current)
    setMyChart(chart)
    
    const option = {
      backgroundColor: 'transparent',
      title: { show: false },
      tooltip: {
        trigger: 'item',
        backgroundColor: '#ffffff',
        borderColor: '#e5e7eb',
        textStyle: { color: '#374151' },
        formatter: (params) => {
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
          zoom: currentZoom,
          label: { 
            show: true, 
            position: 'right', 
            color: '#66ccff', 
            fontSize: 12 
          },
          data: nodes.map(n => ({
            id: n.id,
            name: n.name,
            symbolSize: n.symbolSize || 20,
            value: n.artist,
            artist: n.artist,
            x: n.x,
            y: n.y,
            itemStyle: { 
              color: '#66ccff', 
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
              color: '#66ccff', 
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

    chart.setOption(option)
    
    chart.on('click', (params) => {
      if (params.dataType === 'node') {
        console.log(`选中歌曲: ${params.data.name}`)
      }
    })
    
    chart.on('graphRoam', (params) => {
      if (params.zoom) {
        setCurrentZoom(prev => prev * params.zoom)
      }
    })
  }, [currentZoom])

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const response = await request.get(`/graph/data/${id}`)
      const { nodes, links } = response.data
      setNodeCount(nodes.length)
      setGraphName(`图谱 #${id}`)
      
      const processedNodes = nodes.map(n => ({
        ...n,
        x: Math.random() * 800,
        y: Math.random() * 600
      }))
      
      setTimeout(() => {
        initChart(processedNodes, links)
      }, 100)
    } catch (error) {
      console.error('无法加载图谱数据:', error)
    } finally {
      setLoading(false)
    }
  }, [id, initChart])

  useEffect(() => {
    // 检查持久化状态
    const hasToken = localStorage.getItem('token');
    const isLoggedInStorage = localStorage.getItem('isLoggedIn') === 'true';

    // 如果内存状态、Token和本地存储标志位都没有，才重定向
    if (!isLoggedIn && !hasToken && !isLoggedInStorage) {
      navigate('/login');
      return;
    }
    
    // 只要有其中一个证明已登录，就允许加载数据
    fetchData();
    
    return () => {
      if (myChart) {
        myChart.dispose();
      }
    }
  }, [isLoggedIn, navigate, fetchData, myChart])

  // 同样增加一个"正在加载"的保护层
  if (!isLoggedIn && localStorage.getItem('isLoggedIn') === 'true') {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">身份同步中...</p>
        </div>
      </div>
    );
  }

  if (!isLoggedIn) {
    return null
  }

  const handleZoom = (ratio) => {
    const newZoom = currentZoom * ratio
    if (newZoom >= 0.1 && newZoom <= 10) {
      setCurrentZoom(newZoom)
      if (myChart) {
        myChart.setOption({ series: [{ zoom: newZoom }] })
      }
    }
  }

  const goBack = () => {
    navigate('/')
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <Navbar />
      
      <div className="flex-1 flex flex-col">
        {/* 头部 */}
        <div className="bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
          <div className="flex items-center space-x-4">
            <button
              onClick={goBack}
              className="p-2 hover:bg-gray-100 rounded-full transition-colors"
            >
              <ArrowLeft className="h-5 w-5 text-gray-600" />
            </button>
            <div>
              <h2 className="text-xl font-semibold text-gray-900">{graphName}</h2>
              <span className="text-sm text-gray-500">包含 {nodeCount} 首歌曲</span>
            </div>
          </div>
          <button className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors flex items-center space-x-2">
            <Play className="h-4 w-4" />
            <span>随机播放</span>
          </button>
        </div>

        {/* 图表容器 */}
        <div className="flex-1 p-6">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 h-full relative">
            {loading && (
              <div className="absolute inset-0 bg-white bg-opacity-90 flex items-center justify-center z-10">
                <div className="text-center">
                  <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
                  <p className="text-gray-600">正在加载星图...</p>
                </div>
              </div>
            )}
            
            <div 
              ref={chartRef}
              className="w-full h-full rounded-xl"
              style={{ minHeight: '600px' }}
            ></div>

            {/* 缩放控制 */}
            <div className="absolute bottom-6 right-6 flex flex-col space-y-2">
              <button
                onClick={() => handleZoom(1.2)}
                className="bg-white border border-gray-300 rounded-lg p-2 hover:bg-gray-50 transition-colors shadow-sm"
              >
                <Plus className="h-4 w-4 text-gray-600" />
              </button>
              <button
                onClick={() => handleZoom(0.8)}
                className="bg-white border border-gray-300 rounded-lg p-2 hover:bg-gray-50 transition-colors shadow-sm"
              >
                <Minus className="h-4 w-4 text-gray-600" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Player;
