import request from './http'

export const getVolumes = (data) => request.post('/volume/api/v1/list', data)

export const createVolume = (data) => request.post('/volume/api/v1/create', data)

export const updateVolume = (data) => request.post('/volume/api/v1/update', data)

// 删除卷：{ stackId, id }
export const deleteVolume = (stackId, id) =>
  request.post('/volume/api/v1/delete', { stackId, id })
