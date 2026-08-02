import { defineStore } from 'pinia'
import { me } from '@/api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    username: localStorage.getItem('vanilla_username') || '',
    token: localStorage.getItem('vanilla_token') || '',
    nikeName: '',
    isAdmin: false
  }),
  actions: {
    setAuth({ token, loginName }) {
      this.token = token
      this.username = loginName
      localStorage.setItem('vanilla_token', token)
      if (loginName) {
        localStorage.setItem('vanilla_username', loginName)
      }
    },
    // 拉取当前用户信息（含 admin 标记），登录后由布局层调用
    async loadProfile() {
      if (!this.token) return
      try {
        const info = await me()
        this.loginName = info.loginName || this.username
        this.nikeName = info.nikeName || ''
        this.isAdmin = !!info.isAdmin
      } catch (e) {
        // 401 等已由拦截器处理（清 token 跳登录）
      }
    },
    logout() {
      this.token = ''
      this.username = ''
      this.nikeName = ''
      this.isAdmin = false
      localStorage.removeItem('vanilla_token')
      localStorage.removeItem('vanilla_username')
    }
  }
})