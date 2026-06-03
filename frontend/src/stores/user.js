/**
 * 用户状态管理（Pinia Store）。
 *
 * @author Focus
 * @date 2026-06-03
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')

  /**
   * 登录成功后保存状态。
   */
  function setLogin(user, t) {
    token.value = t
    username.value = user
    localStorage.setItem('token', t)
    localStorage.setItem('username', user)
  }

  /**
   * 登出，清除所有状态。
   */
  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
  }

  /**
   * 是否已登录。
   */
  function isLoggedIn() {
    return !!token.value
  }

  return { token, username, setLogin, logout, isLoggedIn }
})
