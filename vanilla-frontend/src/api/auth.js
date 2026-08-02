import request from './http'

// 用户名 + 密码登录，返回 JWT 与用户信息
export const login = (data) => request.post('/auth/api/v1/login', data)

// 当前登录用户信息（含是否全局管理员）
export const me = () => request.get('/auth/api/v1/me')