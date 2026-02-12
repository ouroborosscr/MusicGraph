import { useEffect, useState, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ArrowLeft, Play, Plus, Minus, MoveDiagonal } from 'lucide-react';
import Navbar from '../components/Navbar';
import * as echarts from 'echarts';
import request from '../utils/request';

const Player = () => {
  const { id } = useParams()
  const navigate = useNavigate()
  const { isLoggedIn } = useAuth()
  
  // ================= 1. 使用 useRef 存储实例 (关键优化) =================
  const chartRef = useRef(null)
  const boxRef = useRef(null)
  const chartInstance = useRef(null) // 不会触发重渲染

  const [loading, setLoading] = useState(true)
  const [graphName, setGraphName] = useState('加载中...')
  const [nodeCount, setNodeCount] = useState(0)
  const [currentZoom, setCurrentZoom] = useState(0.3)

  // 拖拽状态
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

  // ================= 2. 渲染图表逻辑 (初始化 + 更新) =================
  const renderChart = useCallback((nodes, links) => {
    if (!chartRef.current) return
    
    // A. 懒初始化：只有实例不存在时才 init
    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current)
      
      // 事件监听只绑定一次
      chartInstance.current.on('click', (params) => {
        if (params.dataType === 'node') {
          console.log(`选中歌曲: ${params.data.name}`)
        }
      })
      
      chartInstance.current.on('graphRoam', (params) => {
        if (params.zoom) {
          setCurrentZoom(prev => prev * params.zoom)
        }
      })
    }
    
    // B. 数据更新
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

    // 设置配置项
    chartInstance.current.setOption(option, { notMerge: true })
    
    // 【核心修复】数据加载完成后，强制触发一次 resize
    // 这能确保图表立即填满容器，不需要等待下一次交互
    setTimeout(() => {
        chartInstance.current?.resize();
    }, 10);
    
  }, [currentZoom]) 

  // ================= 3. 数据获取 =================
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
      
      // 数据准备好后渲染
      setTimeout(() => {
        renderChart(processedNodes, links)
      }, 50)
    } catch (error) {
      console.error('无法加载图谱数据:', error)
    } finally {
      setLoading(false)
    }
  }, [id, renderChart])

  // ================= 4. 生命周期管理 =================
  useEffect(() => {
    const hasToken = localStorage.getItem('token');
    const isLoggedInStorage = localStorage.getItem('isLoggedIn') === 'true';

    if (!isLoggedIn && !hasToken && !isLoggedInStorage) {
      navigate('/login');
      return;
    }
    
    fetchData();
    
    return () => {
      if (chartInstance.current) {
        chartInstance.current.dispose();
        chartInstance.current = null;
      }
    }
  }, [isLoggedIn, navigate, fetchData])

  // ================= 5. 拖拽缩放逻辑 =================
  const startResize = (e, mode) => {
    e.preventDefault();
    if (!boxRef.current) return;
    const rect = boxRef.current.getBoundingClientRect();
    setResizeState({
      isResizing: true, resizeMode: mode, startX: e.clientX, startY: e.clientY,
      startWidth: rect.width, startHeight: rect.height,
      width: resizeState.width || rect.width, height: resizeState.height || rect.height
    });
  };

  useEffect(() => {
    const handleMouseMove = (e) => {
      if (!resizeState.isResizing) return;
      const dx = e.clientX - resizeState.startX;
      const dy = e.clientY - resizeState.startY;
      const minSize = 300;
      let newWidth = resizeState.width;
      let newHeight = resizeState.height;

      if (resizeState.resizeMode === 'width' || resizeState.resizeMode === 'both') 
        newWidth = Math.max(minSize, resizeState.startWidth + dx);
      if (resizeState.resizeMode === 'height' || resizeState.resizeMode === 'both') 
        newHeight = Math.max(minSize, resizeState.startHeight + dy);

      setResizeState(prev => ({ ...prev, width: newWidth, height: newHeight }));
    };

    const handleMouseUp = () => {
      if (resizeState.isResizing) {
        setResizeState(prev => ({ ...prev, isResizing: false }));
        chartInstance.current?.resize(); // 拖拽结束重置图表大小
      }
    };

    if (resizeState.isResizing) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.userSelect = 'none';
      document.body.style.cursor = 'nwse-resize';
    }
    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.userSelect = '';
      document.body.style.cursor = '';
    };
  }, [resizeState]);

  // 监听容器大小变化 (自动修正显示问题的关键)
  useEffect(() => {
    if (!boxRef.current) return;
    const resizeObserver = new ResizeObserver(() => {
      chartInstance.current?.resize();
    });
    resizeObserver.observe(boxRef.current);
    return () => resizeObserver.disconnect();
  }, []);

  const boxStyle = {
    minHeight: '600px',
    position: 'relative',
    ...(resizeState.width > 0 ? { width: `${resizeState.width}px`, flex: 'none' } : { flex: 1 }),
    ...(resizeState.height > 0 ? { height: `${resizeState.height}px` } : {}),
  };

  const handleZoom = (ratio) => {
    const newZoom = currentZoom * ratio
    if (newZoom >= 0.1 && newZoom <= 10) {
      setCurrentZoom(newZoom)
      if (chartInstance.current) {
        chartInstance.current.setOption({ series: [{ zoom: newZoom }] })
      }
    }
  }

  const goBack = () => navigate('/')

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

  if (!isLoggedIn) return null;

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <Navbar />
      <div className="flex-1 flex flex-col p-4 overflow-hidden">
        <div className="bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between rounded-lg shadow-sm mb-4 shrink-0">
          <div className="flex items-center space-x-4">
            <button onClick={goBack} className="p-2 hover:bg-gray-100 rounded-full transition-colors">
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

        {/* 这一层 div 是关键，包含了 ref={boxRef} 和拖拽手柄 */}
        <div 
          ref={boxRef}
          className={`bg-white rounded-xl shadow-sm border-2 border-dashed border-gray-300 relative transition-colors duration-200 ${resizeState.isResizing ? 'border-blue-400' : 'hover:border-blue-300'}`}
          style={boxStyle}
        >
          {loading && (
            <div className="absolute inset-0 bg-white bg-opacity-90 flex items-center justify-center z-20 rounded-xl">
              <div className="text-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
                <p className="text-gray-600">正在加载星图...</p>
              </div>
            </div>
          )}
          
          <div ref={chartRef} className="w-full h-full rounded-xl overflow-hidden"></div>

          <div className="absolute bottom-6 right-6 flex flex-col space-y-2 z-10">
            <button onClick={() => handleZoom(1.2)} className="bg-white border border-gray-300 rounded-lg p-2 hover:bg-gray-50 transition-colors shadow-sm">
              <Plus className="h-4 w-4 text-gray-600" />
            </button>
            <button onClick={() => handleZoom(0.8)} className="bg-white border border-gray-300 rounded-lg p-2 hover:bg-gray-50 transition-colors shadow-sm">
              <Minus className="h-4 w-4 text-gray-600" />
            </button>
          </div>

          {/* 拖拽手柄 (Resize Handles) - 之前你的代码里缺了这些 */}
          <div className="absolute top-0 right-0 bottom-6 w-4 cursor-ew-resize hover:bg-blue-500/10 z-20" onMouseDown={(e) => startResize(e, 'width')}></div>
          <div className="absolute bottom-0 left-0 right-6 h-4 cursor-ns-resize hover:bg-blue-500/10 z-20" onMouseDown={(e) => startResize(e, 'height')}></div>
          <div className="absolute bottom-0 right-0 w-8 h-8 cursor-nwse-resize bg-white border-t border-l border-gray-200 rounded-tl-lg flex items-center justify-center hover:bg-blue-50 z-30" onMouseDown={(e) => startResize(e, 'both')}>
            <MoveDiagonal className="h-4 w-4 text-gray-400" />
          </div>
        </div>
      </div>
    </div>
  )
}

export default Player;
