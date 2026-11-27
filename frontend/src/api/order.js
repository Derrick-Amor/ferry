import request from '@/utils/request'

export const create = cartIds => request.post('/order/create', { cartIds })
export const list = params => request.get('/order/list', { params })
export const detail = id => request.get(`/order/${id}`)
export const pay = id => request.post(`/order/pay/${id}`)
export const cancel = id => request.post(`/order/cancel/${id}`)
