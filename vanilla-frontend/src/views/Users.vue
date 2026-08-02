<template>
  <div class="page-container">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新建用户
      </el-button>
    </div>

    <el-alert
      v-if="loadError"
      type="warning"
      show-icon
      :closable="false"
      title="该页面仅管理员可访问"
      description="当前登录用户可能不具备 admin 权限，或角色尚未生效（Redis 角色缓存 24 小时）。"
      class="alert"
    />

    <el-card v-loading="loading">
      <div class="toolbar">
        <el-input
          v-model="search"
          placeholder="搜索登录名/昵称"
          clearable
          style="width: 240px"
          @change="handleSearch"
          @clear="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch"><el-icon><Search /></el-icon></el-button>
          </template>
        </el-input>
      </div>

      <el-table :data="users" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="nikeName" label="昵称" min-width="120" />
        <el-table-column prop="loginName" label="登录名" min-width="120" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="240">
          <template #default="{ row }">
            <el-tag
              v-for="role in row.roles || []"
              :key="role.roleId"
              size="small"
              class="role-tag"
            >
              {{ formatRole(role) }}
            </el-tag>
            <span v-if="!row.roles || row.roles.length === 0" class="text-muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !loadError && users.length === 0" description="暂无用户" />

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="count"
        :page-sizes="[10, 15, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="loadUsers"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 创建 / 编辑用户 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑用户' : '新建用户'"
      width="700px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="昵称" prop="nikeName">
              <el-input v-model="form.nikeName" placeholder="如 Dev One" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="登录名" prop="loginName">
              <el-input v-model="form.loginName" :disabled="!!form.id" placeholder="如 dev1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                :placeholder="form.id ? '留空则不修改' : '初始登录密码'"
                type="password"
                show-password
                :minlength="6"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="0"
            :inactive-value="1"
            active-text="正常"
            inactive-text="禁用"
          />
        </el-form-item>

        <el-form-item label="角色">
          <div class="role-binding-list">
            <div v-for="(role, idx) in form.roles" :key="idx" class="role-binding-item">
              <el-select
                v-model="role.roleName"
                placeholder="选择角色"
                style="width: 180px"
                @change="onRoleChange(role)"
              >
                <el-option-group label="全局">
                  <el-option
                    v-for="r in GLOBAL_ROLES"
                    :key="r.value"
                    :label="r.label"
                    :value="r.value"
                  />
                </el-option-group>
                <el-option-group label="集群">
                  <el-option
                    v-for="r in CLUSTER_ROLES"
                    :key="r.value"
                    :label="r.label"
                    :value="r.value"
                  />
                </el-option-group>
                <el-option-group label="栈">
                  <el-option
                    v-for="r in STACK_ROLES"
                    :key="r.value"
                    :label="r.label"
                    :value="r.value"
                  />
                </el-option-group>
              </el-select>

              <el-select
                v-if="isClusterRole(role.roleName)"
                v-model="role.clusterId"
                placeholder="选择集群"
                style="width: 160px"
              >
                <el-option
                  v-for="c in clusters"
                  :key="c.id"
                  :label="c.clusterName"
                  :value="c.id"
                />
              </el-select>

              <el-select
                v-if="isStackRole(role.roleName)"
                v-model="role.clusterId"
                placeholder="选择集群"
                style="width: 140px"
                @change="loadStacksForRole(role)"
              >
                <el-option
                  v-for="c in clusters"
                  :key="c.id"
                  :label="c.clusterName"
                  :value="c.id"
                />
              </el-select>
              <el-select
                v-if="isStackRole(role.roleName)"
                v-model="role.stackId"
                placeholder="选择栈"
                style="width: 150px"
              >
                <el-option
                  v-for="s in stackOptionsByCluster(role.clusterId)"
                  :key="s.id"
                  :label="s.stackName"
                  :value="s.id"
                />
              </el-select>

              <el-button type="danger" link @click="removeRole(idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div class="role-add">
              <el-button size="small" type="primary" plain @click="addRole">
                添加角色
              </el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUsers, createUser, updateUser, deleteUser } from '@/api/user'
import { getClusters } from '@/api/cluster'
import { getStacks } from '@/api/stack'
import {
  GLOBAL_ROLES,
  CLUSTER_ROLES,
  STACK_ROLES,
  isClusterRole,
  isStackRole,
  roleLabel
} from '@/utils/constants'

const loading = ref(false)
const saving = ref(false)
const loadError = ref(false)
const users = ref([])
const count = ref(0)
const page = ref(1)
const size = ref(15)
const search = ref('')

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive(defaultForm())
const formRules = {
  nikeName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  loginName: [{ required: true, message: '请输入登录名', trigger: 'blur' }]
}

// 角色选择用的集群 / 栈数据
const clusters = ref([])
const stackOptions = reactive({})

function defaultForm() {
  return {
    id: null,
    nikeName: '',
    loginName: '',
    password: '',
    status: 0,
    roles: []
  }
}

const loadClusters = async () => {
  try {
    clusters.value = (await getClusters()) || []
  } catch (e) {
    // 拦截器已提示
  }
}

const loadStacksForRole = async (role) => {
  if (!role.clusterId) return
  if (stackOptions[role.clusterId]) return
  try {
    const res = await getStacks({ clusterId: role.clusterId, page: 1, size: 100 })
    stackOptions[role.clusterId] = res?.data || []
  } catch (e) {
    stackOptions[role.clusterId] = []
  }
}

const stackOptionsByCluster = (clusterId) => stackOptions[clusterId] || []

const formatRole = (role) => {
  const label = roleLabel(role.roleName)
  if (role.clusterId) return `${label}（集群#${role.clusterId}）`
  if (role.stackId) return `${label}（栈#${role.stackId}）`
  return label
}

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await getUsers({
      page: page.value,
      size: size.value,
      search: search.value || undefined
    })
    users.value = res?.data || []
    count.value = res?.count || 0
    loadError.value = false
  } catch (e) {
    // 403 等已由拦截器提示；非管理员列表请求会失败
    users.value = []
    count.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadUsers()
}

const handleSizeChange = () => {
  page.value = 1
  loadUsers()
}

const openDialog = (row) => {
  if (row) {
    Object.assign(form, defaultForm(), {
      id: row.id,
      nikeName: row.nikeName,
      loginName: row.loginName,
      status: row.status ?? 0,
      roles: (row.roles || []).map((r) => ({
        roleName: r.roleName,
        clusterId: r.clusterId ?? null,
        stackId: r.stackId ?? null
      }))
    })
  } else {
    Object.assign(form, defaultForm())
  }
  dialogVisible.value = true
}

const addRole = () => {
  form.roles.push({ roleName: '', clusterId: null, stackId: null })
}

const removeRole = (idx) => {
  form.roles.splice(idx, 1)
}

const onRoleChange = (role) => {
  if (!isClusterRole(role.roleName) && !isStackRole(role.roleName)) {
    role.clusterId = null
  }
  if (!isStackRole(role.roleName)) {
    role.stackId = null
  }
}

const handleSave = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const roles = form.roles.filter((r) => r.roleName)
      const payload = { nikeName: form.nikeName, status: form.status, roles }
      // 密码留空不提交（创建时表示不设密码，编辑时表示不修改）
      if (form.password) {
        payload.password = form.password
      }
      if (form.id) {
        // 更新：roles 非 null 时全量替换
        await updateUser({ id: form.id, ...payload })
        ElMessage.success('用户已更新')
      } else {
        await createUser({ ...payload, loginName: form.loginName })
        ElMessage.success('用户已创建')
      }
      dialogVisible.value = false
      await loadUsers()
    } catch (e) {
      // 拦截器已提示
    } finally {
      saving.value = false
    }
  })
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定删除用户「${row.nikeName}」吗？删除后账号即刻禁用并清空角色绑定。`,
    '删除确认',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteUser(row.id)
        ElMessage.success('用户已删除')
        await loadUsers()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

onMounted(() => {
  loadUsers()
  loadClusters()
})
</script>

<style scoped>
.alert {
  margin-bottom: 14px;
}

.role-binding-list .role-binding-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  width: 100%;
}

.role-binding-list .role-add {
  margin-top: 4px;
}
</style>
