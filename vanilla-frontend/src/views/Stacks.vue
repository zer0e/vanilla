<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="router.push('/clusters')">
          <el-icon><ArrowLeft /></el-icon>&nbsp;返回
        </el-button>
        <h2>集群：{{ clusterName }} / 栈管理</h2>
      </div>
      <div>
        <el-button @click="refreshStatuses">
          <el-icon><Refresh /></el-icon>&nbsp;刷新状态
        </el-button>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>&nbsp;新建栈
        </el-button>
      </div>
    </div>

    <el-card v-loading="loading">
      <div class="toolbar">
        <el-input
          v-model="search"
          placeholder="搜索栈名称"
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

      <el-table :data="stacks" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="stackName" label="栈名称" min-width="120" />
        <el-table-column
          prop="description"
          label="描述"
          min-width="120"
          show-overflow-tooltip
        />
        <el-table-column prop="owner" label="负责人" width="100" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(statusMap[row.id]).type" size="small">
              {{ statusMeta(statusMap[row.id]).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row)">服务</el-button>
            <el-button
              type="success"
              link
              :loading="deployingId === row.id"
              @click="handleDeploy(row)"
            >
              部署
            </el-button>
            <el-button type="warning" link @click="showStatus(row)">状态</el-button>
            <el-button
              type="info"
              link
              :loading="stoppingId === row.id"
              @click="handleStop(row)"
            >
              停止
            </el-button>
            <el-button
              type="danger"
              link
              :loading="removingId === row.id"
              @click="handleRemove(row)"
            >
              下架
            </el-button>
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && stacks.length === 0" description="暂无栈，点击右上角新建栈" />

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="count"
        :page-sizes="[10, 15, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="loadStacks"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 创建 / 编辑栈 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑栈' : '新建栈'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="栈名称" prop="stackName">
          <el-input v-model="form.stackName" placeholder="如 web" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="栈描述（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 状态详情 -->
    <el-dialog v-model="statusVisible" title="栈运行状态" width="540px">
      <template v-if="statusData">
        <div class="status-summary">
          整体状态：
          <el-tag :type="statusMeta(statusData.status).type">
            {{ statusMeta(statusData.status).label }}
          </el-tag>
        </div>
        <el-table :data="statusData.services || []" size="small" style="margin-top: 12px">
          <el-table-column prop="serviceName" label="服务" min-width="120" />
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" size="small">
                {{ statusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="runningCount" label="运行数" width="80" align="center" />
          <el-table-column prop="replicas" label="副本数" width="80" align="center" />
          <el-table-column label="暴露地址" min-width="200">
            <template #default="{ row }">
              <span class="text-muted">{{ (row.exposedAddresses || []).join(' · ') || '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="暂无状态数据" />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getStacks,
  createStack,
  updateStack,
  deleteStack,
  deployStack,
  stackStatus,
  stopStack,
  removeStack
} from '@/api/stack'
import { getClusters } from '@/api/cluster'
import { statusMeta } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const clusterId = Number(route.params.clusterId)

const loading = ref(false)
const saving = ref(false)
const stacks = ref([])
const count = ref(0)
const page = ref(1)
const size = ref(15)
const search = ref('')
const statusMap = reactive({})

const deployingId = ref(null)
const stoppingId = ref(null)
const removingId = ref(null)

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive(defaultForm())
const formRules = {
  stackName: [{ required: true, message: '请输入栈名称', trigger: 'blur' }]
}

const statusVisible = ref(false)
const statusData = ref(null)

const clusterName = ref(`#${clusterId}`)

function defaultForm() {
  return {
    id: null,
    clusterId,
    stackName: '',
    description: ''
  }
}

const loadClusterName = async () => {
  try {
    const list = (await getClusters()) || []
    const found = list.find((c) => c.id === clusterId)
    if (found) {
      clusterName.value = found.clusterName
    }
  } catch (e) {
    // 忽略
  }
}

const loadStacks = async () => {
  loading.value = true
  try {
    const res = await getStacks({
      clusterId,
      page: page.value,
      size: size.value,
      search: search.value || undefined
    })
    stacks.value = res?.data || []
    count.value = res?.count || 0
    await refreshStatuses()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadStacks()
}

const handleSizeChange = () => {
  page.value = 1
  loadStacks()
}

// 批量刷新所有栈状态（静默，避免因个别栈 403 弹一堆提示）
const refreshStatuses = async () => {
  const tasks = stacks.value.map((s) =>
    stackStatus(s.id, { silent: true })
      .then((res) => {
        statusMap[s.id] = res?.status
      })
      .catch(() => {
        statusMap[s.id] = undefined
      })
  )
  await Promise.allSettled(tasks)
}

const goDetail = (row) => {
  router.push({
    path: `/clusters/${clusterId}/stacks/${row.id}`,
    query: { name: row.stackName }
  })
}

const openDialog = (row) => {
  if (row) {
    Object.assign(form, defaultForm(), {
      id: row.id,
      stackName: row.stackName,
      description: row.description
    })
  } else {
    Object.assign(form, defaultForm())
  }
  dialogVisible.value = true
}

const handleSave = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) {
        await updateStack({ id: form.id, stackName: form.stackName, description: form.description })
        ElMessage.success('栈已更新')
      } else {
        await createStack({ clusterId, stackName: form.stackName, description: form.description })
        ElMessage.success('栈已创建')
      }
      dialogVisible.value = false
      await loadStacks()
    } catch (e) {
      // 拦截器已提示
    } finally {
      saving.value = false
    }
  })
}

const handleDeploy = (row) => {
  ElMessageBox.confirm(
    `确定部署栈「${row.stackName}」到集群吗？将拉取镜像并按策略创建容器。`,
    '部署确认',
    { confirmButtonText: '部署', cancelButtonText: '取消' }
  )
    .then(async () => {
      deployingId.value = row.id
      try {
        const res = await deployStack(row.id)
        if (res?.status) {
          statusMap[row.id] = res.status
        }
        ElMessage.success(`部署成功，当前状态：${statusMeta(res?.status).label}`)
      } catch (e) {
        // 拦截器已提示（如端口冲突等）
      } finally {
        deployingId.value = null
      }
    })
    .catch(() => {})
}

const showStatus = async (row) => {
  try {
    const res = await stackStatus(row.id)
    statusData.value = res
    if (res?.status) {
      statusMap[row.id] = res.status
    }
    statusVisible.value = true
  } catch (e) {
    // 拦截器已提示
  }
}

const handleStop = (row) => {
  ElMessageBox.confirm(
    `确定停止栈「${row.stackName}」下所有容器吗？`,
    '停止确认',
    { confirmButtonText: '停止', cancelButtonText: '取消' }
  )
    .then(async () => {
      stoppingId.value = row.id
      try {
        await stopStack(row.id)
        ElMessage.success('栈已停止')
        await refreshStatuses()
      } catch (e) {
        // 拦截器已提示
      } finally {
        stoppingId.value = null
      }
    })
    .catch(() => {})
}

const handleRemove = (row) => {
  ElMessageBox.confirm(
    `确定下架栈「${row.stackName}」吗？将删除栈下所有容器（含停止的）。`,
    '下架确认',
    { confirmButtonText: '下架', cancelButtonText: '取消', type: 'warning' }
  )
    .then(async () => {
      removingId.value = row.id
      try {
        await removeStack(row.id)
        ElMessage.success('栈已下架')
        await refreshStatuses()
      } catch (e) {
        // 拦截器已提示
      } finally {
        removingId.value = null
      }
    })
    .catch(() => {})
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定删除栈「${row.stackName}」吗？该操作不可恢复。`,
    '删除确认',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteStack(row.id)
        ElMessage.success('栈已删除')
        await loadStacks()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

onMounted(() => {
  loadClusterName()
  loadStacks()
})
</script>

<style scoped>
.status-summary {
  font-size: 14px;
}
</style>
