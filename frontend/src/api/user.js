import request from '@/utils/request'

export const register = data => request.post('/user/register', data)
export const login = data => request.post('/user/login', data)
export const forgotPassword = data => request.post('/user/forgotPassword', data)
export const resetPassword = data => request.post('/user/resetPassword', data)
export const getInfo = () => request.get('/user/info')
export const updateInfo = data => request.put('/user/info', data)
export const logout = () => request.post('/user/logout')
