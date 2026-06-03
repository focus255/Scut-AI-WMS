/**
 * Axios 请求封装 — 统一 Token 注入、401 拦截与错误透传。
 * 业务消息由各组件自行通过 ElMessage 展示，避免双重通知。
 *
 * @author Focus
 * @date 2026-06-03
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

/**
 * 请求拦截器：自动注入 Authorization 头。
 */
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/**
 * 响应拦截器：
 * - code=0：剥离外层包装，返回 data
 * - code≠0：透传错误给调用方自行处理
 * - 401：清除 Token 并跳转登录页
 * - 网络异常：统一提示
 */
request.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 0) {
      return data
    }
    // 业务错误不在此处弹窗，由调用方决定是否展示
    return Promise.reject(new Error(message || '请求失败'))
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      ElMessage.error('登录凭证已失效，请重新登录')
      window.location.href = '/login'
    } else if (!error.response) {
      // 网络中断、超时等无响应场景
      ElMessage.error('网络连接异常，请检查网络后重试')
    }
    // 其他 HTTP 错误（500 等）透传
    return Promise.reject(error)
  }
)

export default request
