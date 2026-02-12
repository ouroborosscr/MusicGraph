import { User, Lock, Eye, EyeOff } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import request from '../utils/request';
import { useAuth } from '../hooks/useAuth';
import { useState } from 'react';
const Login = () => {
  const [activeTab, setActiveTab] = useState('login');
  const [form, setForm] = useState({
    username: '',
    password: ''
  });
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleAuth = async (e) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    if (!form.username.trim() || !form.password.trim()) {
      setError('账号和密码不能为空');
      setIsLoading(false);
      return;
    }

    try {
      if (activeTab === 'login') {
        const response = await request.post('/auth/login', null, {
          params: {
            username: form.username,
            password: form.password
          }
        });
        
        const { token, username } = response.data;
        localStorage.setItem('token', token);
        localStorage.setItem('isLoggedIn', 'true'); // 显式设置登录状态
        login(username);
        
        // 显示登录成功提示
        toast.success('登录成功，欢迎回来！');
        
        // 延迟跳转，确保状态更新完成
        setTimeout(() => {
          navigate('/', { replace: true });
        }, 100);
      } else {
        await request.post('/auth/register', null, {
          params: {
            username: form.username,
            password: form.password
          }
        });
        
        // 显示注册成功提示
        toast.success('注册成功，请登录');
        setActiveTab('login');
      }
    } catch (error) {
      // 确保错误信息是字符串类型
      let errorMessage = '';
      if (error.response?.data) {
        errorMessage = typeof error.response.data === 'string' 
          ? error.response.data 
          : JSON.stringify(error.response.data);
      } else if (error.message) {
        errorMessage = error.message;
      } else {
        errorMessage = '请求失败，请检查网络';
      }
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({
      ...prev,
      [name]: value
    }));
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100">
      <div className="max-w-md w-full space-y-8 p-8">
        <div className="bg-white rounded-lg shadow-xl p-8">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-bold bg-gradient-to-r from-green-500 to-green-600 bg-clip-text text-transparent">
              MusicGraph
            </h1>
            <p className="mt-2 text-sm text-gray-600">探索你的音乐思维轨迹</p>
          </div>

          <div className="flex mb-6 bg-gray-100 rounded-lg p-1">
            <button
              className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors ${
                activeTab === 'login'
                  ? 'bg-white text-blue-600 shadow-sm'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
              onClick={() => setActiveTab('login')}
            >
              登录
            </button>
            <button
              className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors ${
                activeTab === 'register'
                  ? 'bg-white text-blue-600 shadow-sm'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
              onClick={() => setActiveTab('register')}
            >
              注册
            </button>
          </div>

          <form onSubmit={handleAuth} className="space-y-6">
            <div>
              <div className="relative">
                <User className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5 text-gray-400" />
                <input
                  type="text"
                  name="username"
                  value={form.username}
                  onChange={handleInputChange}
                  className="w-full pl-10 pr-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="请输入账号"
                  required
                />
              </div>
            </div>

            <div>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5 text-gray-400" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  value={form.password}
                  onChange={handleInputChange}
                  className="w-full pl-10 pr-12 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="请输入密码"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 transform -translate-y-1/2 h-5 w-5 text-gray-400 hover:text-gray-600"
                >
                  {showPassword ? <EyeOff /> : <Eye />}
                </button>
              </div>
            </div>

            {error && (
              <div className={`text-sm text-center p-3 rounded-lg ${
                (typeof error === 'string' && error.includes('成功'))
                  ? 'text-green-600 bg-green-50' 
                  : 'text-red-600 bg-red-50'
              }`}>
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={isLoading || !form.username || !form.password}
              className="w-full bg-blue-600 text-white py-3 px-4 rounded-lg hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition duration-200 font-bold tracking-wide"
            >
              {isLoading ? '处理中...' : activeTab === 'login' ? '立即登录' : '注册账号'}
            </button>
          </form>
        </div>
      </div>
      <div className="fixed bottom-4 right-4 text-xs text-gray-500 text-right">
        前端使用美团nocode生成，<br />
        前后端及本地部署代码已开源至<br />
        <a 
          href="https://github.com/ouroborosscr/MusicGraph" 
          target="_blank" 
          rel="noopener noreferrer"
          className="text-blue-600 hover:underline"
        >
          https://github.com/ouroborosscr/MusicGraph
        </a>
      </div>
    </div>
  );
};

export default Login;
