import request from './http'

export const createPort = (data) => request.post('/port/api/v1/create', data)

export const updatePort = (data) => request.post('/port/api/v1/update', data)

// 删除端口：{ id, stackId, serviceId }
export const deletePort = (data) => request.post('/port/api/v1/delete', data)
