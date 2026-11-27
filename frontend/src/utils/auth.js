const KEY = 'smart_edu_token'
export function getToken() { return localStorage.getItem(KEY) || '' }
export function setToken(t) { localStorage.setItem(KEY, t) }
export function clearAuth() { localStorage.removeItem(KEY) }
