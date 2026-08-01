import request from './http'

export const getHistory = (data) => request.post('/history/api/v1/list', data)
