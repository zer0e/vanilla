import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    username: localStorage.getItem('vanilla_username') || ''
  }),
  actions: {
    setUsername(name) {
      this.username = name
      localStorage.setItem('vanilla_username', name)
    },
    logout() {
      this.username = ''
      localStorage.removeItem('vanilla_username')
    }
  }
})
