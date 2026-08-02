import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  // 统一响应结构：{ success, code, msg, data }
  baseURL: (import.meta.env.VITE_API_BASE || '') + '/vanilla',
  timeout: 60000
})

// 请求拦截：附加 JWT（后端通过 Authorization: Bearer <token> 识别登录用户）
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('vanilla_token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：解包统一响应结构，成功直接返回 data；失败统一提示并按 401/403 处理
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 非标准结构（如文件流等）直接返回
    if (!res || typeof res !== 'object' || !('success' in res)) {
      return res
    }
    if (res.success) {
      return res.data
    }
    const silent = response.config?.silent === true
    if (res.code === 401) {
      // 未登录 / token 失效或账号已禁用
      if (!silent) {
        ElMessage.error('未登录或登录已过期，请重新登录')
      }
      localStorage.removeItem('vanilla_token')
      localStorage.removeItem('vanilla_username')
      // 强制跳转登录页（全量刷新，重置内存状态）
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    } else if (res.code === 403) {
      // 无权限：可能是角色未生效（Redis 角色缓存 24h）或确实无权限
      if (!silent) {
        ElMessage.error('权限尚未生效，请稍后重试')
      }
    } else {
      if (!silent) {
        ElMessage.error(res.msg || '操作失败')
      }
    }
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  (error) => {
    const silent = error?.config?.silent === true
    if (!silent) {
      ElMessage.error(error?.response?.data?.msg || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default service
