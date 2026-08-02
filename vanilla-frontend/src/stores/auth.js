import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    username: localStorage.getItem('vanilla_username') || '',
    token: localStorage.getItem('vanilla_token') || ''
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
    logout() {
      this.token = ''
      this.username = ''
      localStorage.removeItem('vanilla_token')
      localStorage.removeItem('vanilla_username')
    }
  }
})