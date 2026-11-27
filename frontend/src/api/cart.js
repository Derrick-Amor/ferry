import request from '@/utils/request'

export const list = () => request.get('/cart/list')
export const add = courseId => request.post('/cart/add', { courseId })
export const update = (id, data) => request.put(`/cart/${id}`, data)
export const remove = id => request.delete(`/cart/${id}`)
