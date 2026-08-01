import request from './http'

export const getStacks = (data) => request.post('/stack/api/v1/list', data)

export const createStack = (data) => request.post('/stack/api/v1/create', data)

export const updateStack = (data) => request.post('/stack/api/v1/update', data)

export const deleteStack = (id) => request.post('/stack/api/v1/delete', { id })

export const deployStack = (stackId) => request.post('/stack/api/v1/deploy', { stackId })

// status 支持 silent 选项，用于批量刷新状态时静默失败
export const stackStatus = (stackId, options = {}) =>
  request.post('/stack/api/v1/status', { stackId }, options)

export const stopStack = (stackId) => request.post('/stack/api/v1/stop', { stackId })

export const removeStack = (stackId) => request.post('/stack/api/v1/remove', { stackId })
