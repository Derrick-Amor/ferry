import request from '@/utils/request'

export const userList = params => request.get('/admin/user/list', { params })
export const userStatus = (id, status) => request.put(`/admin/user/status/${id}`, null, { params: { status } })
export const orderList = params => request.get('/admin/order/list', { params })
export const orderDetail = id => request.get(`/admin/order/${id}`)
export const courseList = params => request.get('/admin/course/list', { params })
export const courseStatus = (id, status) => request.put(`/admin/course/status/${id}`, null, { params: { status } })
export const jobList = params => request.get('/admin/job/list', { params })
export const jobStatus = (id, status) => request.put(`/admin/job/status/${id}`, null, { params: { status } })
