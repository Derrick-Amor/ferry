import request from '@/utils/request'

export const list = params => request.get('/course/list', { params })
export const detail = id => request.get(`/course/${id}`)
export const collect = id => request.post(`/course/collect/${id}`)
export const study = id => request.get(`/course/study/${id}`)
export const saveProgress = data => request.post('/course/progress', data)
export const applyList = data => request.post('/course/applyList', data)
export const offline = (courseId, reason) => request.post('/course/offline', null, { params: { courseId, reason } })
export const mine = () => request.get('/course/mine')
