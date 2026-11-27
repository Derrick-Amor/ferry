const KEY = 'ferry_token'
export function getToken() { return localStorage.getItem(KEY) || '' }
export function setToken(t) { localStorage.setItem(KEY, t) }
export function clearAuth() { localStorage.removeItem(KEY) }
