import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

export const useAuth = () => {
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [username, setUsername] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    // 检查localStorage中的登录状态
    const loggedIn = localStorage.getItem('isLoggedIn') === 'true'
    const storedUsername = localStorage.getItem('username')
    
    setIsLoggedIn(loggedIn)
    setUsername(storedUsername || '')
  }, [])

  const login = (user) => {
    setIsLoggedIn(true)
    setUsername(user)
    localStorage.setItem('isLoggedIn', 'true')
    localStorage.setItem('username', user)
  }

  const logout = () => {
    setIsLoggedIn(false)
    setUsername('')
    localStorage.removeItem('isLoggedIn')
    localStorage.removeItem('username')
    localStorage.removeItem('token') // 确保删除token
    navigate('/login') // 退出后跳转到登录页
  }

  return {
    isLoggedIn,
    username,
    login,
    logout
  }
}
