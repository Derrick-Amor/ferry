import request from '@/utils/request'

export const my = params => request.get('/apply/my', { params })
export const pending = params => request.get('/apply/pending', { params })
export const audited = params => request.get('/apply/audited', { params })
export const audit = data => request.post('/apply/audit', data)
