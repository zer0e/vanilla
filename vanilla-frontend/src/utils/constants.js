// 栈 / 服务运行状态元信息（类型对应 Element Plus el-tag 的 type）
export const STACK_STATUS = {
  RUNNING: { label: '运行中', type: 'success' },
  STOPPED: { label: '已停止', type: 'info' },
  PARTIAL: { label: '部分运行', type: 'warning' },
  NONE: { label: '未部署', type: 'info' }
}

export const statusMeta = (status) =>
  STACK_STATUS[status] || { label: status || '未知', type: 'info' }

// 全局角色
export const GLOBAL_ROLES = [
  { value: 'admin', label: '管理员' },
  { value: 'user', label: '普通用户' }
]

// 集群角色（需 clusterId）
export const CLUSTER_ROLES = [
  { value: 'cluster_admin', label: '集群管理员' },
  { value: 'cluster_user', label: '集群用户' }
]

// 栈角色（需 stackId）
export const STACK_ROLES = [
  { value: 'stack_admin', label: '栈管理员' },
  { value: 'stack_member', label: '栈成员' },
  { value: 'stack_readonly', label: '栈只读' }
]

export const ALL_ROLES = [...GLOBAL_ROLES, ...CLUSTER_ROLES, ...STACK_ROLES]

export const isClusterRole = (name) => CLUSTER_ROLES.some((r) => r.value === name)
export const isStackRole = (name) => STACK_ROLES.some((r) => r.value === name)

export const roleLabel = (name) => {
  const found = ALL_ROLES.find((r) => r.value === name)
  return found ? found.label : name
}
