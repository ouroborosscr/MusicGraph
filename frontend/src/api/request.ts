import axios from 'axios'

// 创建实例
const service = axios.create({
  baseURL: '/api', // 这里利用 Vite 的代理转发，后面会讲配置
  timeout: 5000
})

// 请求拦截器：每次发请求前，自动往 Header 里塞 Token
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token') // 注意：要和 Store 里存的 Key 一致
    if (token) {
      config.headers['Authorization'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：处理 401
service.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Token 过期或无效，强制登出
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default service