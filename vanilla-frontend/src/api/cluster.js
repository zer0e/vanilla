import request from './http'

export const getClusters = () => request.get('/cluster/api/v1/list')

export const createCluster = (data) => request.post('/cluster/api/v1/create', data)

export const updateCluster = (data) => request.post('/cluster/api/v1/update', data)

export const deleteCluster = (id) => request.post('/cluster/api/v1/delete', { id })
