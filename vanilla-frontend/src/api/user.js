import request from './http'

export const getUsers = (data) => request.post('/user/api/v1/list', data)

export const createUser = (data) => request.post('/user/api/v1/create', data)

export const updateUser = (data) => request.post('/user/api/v1/update', data)

export const deleteUser = (id) => request.post('/user/api/v1/delete', { id })
