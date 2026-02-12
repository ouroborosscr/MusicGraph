import { useAuth } from '../hooks/useAuth'
import { LogOut, User } from 'lucide-react'

const Navbar = () => {
  const { isLoggedIn, username, logout } = useAuth()

  return (
    <nav className="bg-white shadow-sm border-b">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          <div className="flex items-center">
            <h1 className="text-xl font-semibold bg-gradient-to-r from-green-500 to-green-600 bg-clip-text text-transparent">MusicGraph</h1>
            <span className="ml-2 text-sm text-gray-500">探索你的音乐思维轨迹</span>
          </div>
          
          {isLoggedIn && (
            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2 text-gray-700">
                <User className="h-5 w-5" />
                <span>欢迎, {username}</span>
              </div>
              <button
                onClick={logout}
                className="flex items-center space-x-2 px-3 py-2 text-sm text-gray-700 hover:text-gray-900 hover:bg-gray-100 rounded-md transition duration-200"
              >
                <LogOut className="h-4 w-4" />
                <span>退出登录</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </nav>
  )
}

export default Navbar
