import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as userApi from '@/api/user'
import { getToken, setToken, clearAuth } from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken() || '')
  const userInfo = ref(null)
  const role = computed(() => userInfo.value?.role ?? null)
  const isLogin = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 2)

  async function login(payload) {
    const data = await userApi.login(payload)
    token.value = data.token
    userInfo.value = data.user
    setToken(data.token)
    return data
  }
  async function fetchInfo() {
    if (token.value) userInfo.value = await userApi.getInfo()
  }
  async function logout() {
    try { await userApi.logout() } catch (e) { /* 忽略 */ }
    clearAuth()
    token.value = ''
    userInfo.value = null
  }
  return { token, userInfo, role, isLogin, isAdmin, login, fetchInfo, logout }
})
