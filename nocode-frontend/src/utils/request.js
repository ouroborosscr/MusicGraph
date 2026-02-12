import axios from 'axios'

// 创建axios实例
const service = axios.create({
  baseURL: 'https://scrnocode.top:62012/api', // 使用正确的后端地址
  timeout: 5000
})

// 请求拦截器：自动添加Token
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：处理401错误
service.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Token过期或无效，强制登出
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('isLoggedIn')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default service
