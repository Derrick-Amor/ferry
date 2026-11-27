import request from '@/utils/request'

export const upload = file => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/resume/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export const list = () => request.get('/resume/list')
export const remove = id => request.delete(`/resume/${id}`)
