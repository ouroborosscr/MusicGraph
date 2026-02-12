import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import Navbar from '../components/Navbar'
import { Shield, Users, BarChart3, Settings } from 'lucide-react'

const Index = () => {
  const { isLoggedIn } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    // 双重检查：既检查内存状态，也检查localStorage
    const hasToken = localStorage.getItem('token')
    const isLoggedInStorage = localStorage.getItem('isLoggedIn') === 'true'
    
    // 如果内存状态和localStorage状态都不为已登录，则重定向
    if (!isLoggedIn && !hasToken && !isLoggedInStorage) {
      navigate('/login')
    }
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
    return null // 在重定向期间显示空白页面
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      
      <div className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <div className="text-center mb-12">
            <h1 className="text-4xl font-bold bg-gradient-to-r from-green-500 to-green-600 bg-clip-text text-transparent mb-4">欢迎使用 MusicGraph</h1>
            <p className="text-xl text-gray-600">探索你的音乐思维轨迹</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-12">
            <div className="bg-white rounded-lg shadow p-6 hover:shadow-lg transition-shadow">
              <div className="flex items-center justify-center w-12 h-12 bg-blue-100 rounded-lg mb-4">
                <Shield className="h-6 w-6 text-blue-600" />
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">安全管理</h3>
              <p className="text-gray-600">保护您的数据和隐私安全</p>
            </div>

            <div className="bg-white rounded-lg shadow p-6 hover:shadow-lg transition-shadow">
              <div className="flex items-center justify-center w-12 h-12 bg-green-100 rounded-lg mb-4">
                <Users className="h-6 w-6 text-green-600" />
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">用户管理</h3>
              <p className="text-gray-600">管理用户权限和访问控制</p>
            </div>

            <div className="bg-white rounded-lg shadow p-6 hover:shadow-lg transition-shadow">
              <div className="flex items-center justify-center w-12 h-12 bg-purple-100 rounded-lg mb-4">
                <BarChart3 className="h-6 w-6 text-purple-600" />
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">数据分析</h3>
              <p className="text-gray-600">查看详细的统计报告</p>
            </div>

            <div className="bg-white rounded-lg shadow p-6 hover:shadow-lg transition-shadow">
              <div className="flex items-center justify-center w-12 h-12 bg-orange-100 rounded-lg mb-4">
                <Settings className="h-6 w-6 text-orange-600" />
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">系统设置</h3>
              <p className="text-gray-600">配置系统参数和选项</p>
            </div>
          </div>

          <div className="bg-white rounded-lg shadow p-8">
            <h2 className="text-2xl font-bold text-gray-900 mb-6">快速开始</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="border border-gray-200 rounded-lg p-6">
                <h3 className="text-lg font-semibold text-gray-900 mb-3">创建新项目</h3>
                <p className="text-gray-600 mb-4">开始构建您的下一个应用</p>
                <button className="w-full bg-blue-600 text-white py-2 px-4 rounded hover:bg-blue-700 transition duration-200">
                  立即开始
                </button>
              </div>
              
              <div className="border border-gray-200 rounded-lg p-6">
                <h3 className="text-lg font-semibold text-gray-900 mb-3">查看文档</h3>
                <p className="text-gray-600 mb-4">学习如何使用平台功能</p>
                <button className="w-full bg-gray-600 text-white py-2 px-4 rounded hover:bg-gray-700 transition duration-200">
                  阅读文档
                </button>
              </div>
              
              <div className="border border-gray-200 rounded-lg p-6">
                <h3 className="text-lg font-semibold text-gray-900 mb-3">社区支持</h3>
                <p className="text-gray-600 mb-4">获取帮助和分享经验</p>
                <button className="w-full bg-green-600 text-white py-2 px-4 rounded hover:bg-green-700 transition duration-200">
                  加入社区
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Index
