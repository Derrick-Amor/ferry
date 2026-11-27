import axios from 'axios'
import { getToken, clearAuth } from './auth'
import { toast } from './toast'

const request = axios.create({ baseURL: '/api', timeout: 15000 })

request.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  res => {
    const body = res.data
    if (body && body.code === 200) return body.data
    const msg = (body && body.message) || '请求失败'
    toast(msg, 'error')
    return Promise.reject(new Error(msg))
  },
  err => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message || '网络错误'
    if (status === 401) {
      clearAuth()
      toast('登录已过期，请重新登录', 'error')
      setTimeout(() => { window.location.href = '/login' }, 600)
    } else {
      toast(msg, 'error')
    }
    return Promise.reject(new Error(msg))
  }
)

export default request
