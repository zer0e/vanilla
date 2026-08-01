import request from './http'

export const getServices = (data) => request.post('/service/api/v1/list', data)

export const createService = (data) => request.post('/service/api/v1/create', data)

export const updateService = (data) => request.post('/service/api/v1/update', data)

export const deleteService = (stackId, serviceId) =>
  request.post('/service/api/v1/delete', { stackId, serviceId })
