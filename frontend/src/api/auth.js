/**
 * 用户认证 API。
 *
 * @author Focus
 * @date 2026-06-03
 */
import request from './request'

/**
 * 用户注册。
 * @param {string} username 账号
 * @param {string} password 密码
 */
export function register(username, password) {
  return request.post('/auth/register', { username, password })
}

/**
 * 用户登录。
 * @param {string} username 账号
 * @param {string} password 密码
 * @returns {Promise<{token: string, expiresIn: number}>}
 */
export function login(username, password) {
  return request.post('/auth/login', { username, password })
}
