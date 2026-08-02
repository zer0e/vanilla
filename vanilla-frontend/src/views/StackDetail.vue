<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="router.push(`/clusters/${clusterId}/stacks`)">
          <el-icon><ArrowLeft /></el-icon>&nbsp;返回
        </el-button>
        <h2>栈：{{ stackName }} 详情</h2>
      </div>
      <div class="header-right">
        <el-button type="success" :loading="deploying" @click="handleDeploy">
          <el-icon><Upload /></el-icon>&nbsp;部署
        </el-button>
        <el-button type="warning" :loading="stopping" @click="handleStop">
          <el-icon><VideoPause /></el-icon>&nbsp;停止
        </el-button>
        <el-button type="danger" :loading="removing" @click="handleRemove">
          <el-icon><Delete /></el-icon>&nbsp;下架
        </el-button>
        <el-button @click="refreshStatus">
          <el-icon><Refresh /></el-icon>&nbsp;刷新状态
        </el-button>
        <el-tag :type="statusMeta(stackStatusName).type" size="large">
          {{ statusMeta(stackStatusName).label }}
        </el-tag>
      </div>
    </div>

    <el-card>
      <el-tabs v-model="activeTab">
        <!-- 服务管理 -->
        <el-tab-pane label="服务管理" name="services">
          <div class="toolbar">
            <el-input
              v-model="serviceSearch"
              placeholder="搜索服务名称"
              clearable
              style="width: 240px"
              @change="handleServiceSearch"
              @clear="handleServiceSearch"
            >
              <template #append>
                <el-button @click="handleServiceSearch"><el-icon><Search /></el-icon></el-button>
              </template>
            </el-input>
            <el-button type="primary" @click="openServiceDialog()">
              <el-icon><Plus /></el-icon>&nbsp;新建服务
            </el-button>
          </div>

          <el-table v-loading="servicesLoading" :data="services" row-key="id" stripe>
            <el-table-column type="expand">
              <template #default="{ row: service }">
                <div class="expanded">
                  <div class="expanded-section">
                    <div class="section-title">
                      <span>端口</span>
                      <el-button size="small" type="primary" @click="openPortDialog(service)">
                        添加端口
                      </el-button>
                    </div>
                    <el-table :data="service.ports || []" size="small" border>
                      <el-table-column prop="protocol" label="协议" width="90" align="center">
                        <template #default="{ row }">
                          <el-tag size="small">{{ row.protocol || 'tcp' }}</el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column prop="port" label="端口" width="120" align="center" />
                      <el-table-column label="操作" width="140">
                        <template #default="{ row: port }">
                          <el-button size="small" type="primary" link @click="openPortDialog(service, port)">
                            编辑
                          </el-button>
                          <el-button size="small" type="danger" link @click="handleDeletePort(service, port)">
                            删除
                          </el-button>
                        </template>
                      </el-table-column>
                      <template #empty>
                        <span class="text-muted">暂无端口</span>
                      </template>
                    </el-table>
                  </div>

                  <div class="expanded-section">
                    <div class="section-title">
                      <span>卷</span>
                      <el-button size="small" type="primary" @click="openVolumeDialog(service)">
                        添加卷
                      </el-button>
                    </div>
                    <el-table :data="service.volumes || []" size="small" border>
                      <el-table-column prop="volumeName" label="卷名称" width="140" />
                      <el-table-column prop="size" label="大小(GB)" width="100" align="center" />
                      <el-table-column prop="mountPath" label="挂载路径" min-width="160" />
                      <el-table-column label="操作" width="140">
                        <template #default="{ row: vol }">
                          <el-button size="small" type="primary" link @click="openVolumeDialog(service, vol)">
                            编辑
                          </el-button>
                          <el-button size="small" type="danger" link @click="handleDeleteVolume(service, vol)">
                            删除
                          </el-button>
                        </template>
                      </el-table-column>
                      <template #empty>
                        <span class="text-muted">暂无卷</span>
                      </template>
                    </el-table>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="serviceName" label="服务名称" min-width="120" />
            <el-table-column
              prop="image"
              label="镜像"
              min-width="140"
              show-overflow-tooltip
            />
            <el-table-column prop="replicas" label="副本" width="70" align="center" />
            <el-table-column prop="cpu" label="CPU" width="70" align="center" />
            <el-table-column prop="memory" label="内存(MB)" width="100" align="center" />
            <el-table-column label="环境变量" width="100" align="center">
              <template #default="{ row }">
                <el-tooltip :content="formatEnvs(row.envs)" placement="top">
                  <el-tag size="small">{{ (row.envs || []).length }} 项</el-tag>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="strategy" label="策略" width="110" />
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button type="info" link @click="openLogDialog(row)">日志</el-button>
                <el-button type="primary" link @click="openServiceDialog(row)">编辑</el-button>
                <el-button type="danger" link @click="handleDeleteService(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!servicesLoading && services.length === 0" description="暂无服务，点击右上角新建服务" />

          <el-pagination
            v-model:current-page="servicePage"
            v-model:page-size="serviceSize"
            :total="serviceCount"
            :page-sizes="[10, 15, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            class="pagination"
            @current-change="loadServices"
            @size-change="handleServiceSizeChange"
          />
        </el-tab-pane>

        <!-- 操作记录 -->
        <el-tab-pane label="操作记录" name="history">
          <el-table v-loading="historyLoading" :data="historyList" stripe>
            <el-table-column prop="event" label="事件" min-width="320" />
            <el-table-column prop="createUser" label="操作人" width="130" />
            <el-table-column prop="createTime" label="操作时间" width="180" />
          </el-table>
          <el-empty v-if="!historyLoading && historyList.length === 0" description="暂无操作记录" />
          <el-pagination
            v-model:current-page="historyPage"
            v-model:page-size="historySize"
            :total="historyCount"
            :page-sizes="[10, 15, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            class="pagination"
            @current-change="loadHistory"
            @size-change="handleHistorySizeChange"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 服务创建 / 编辑对话框 -->
    <el-dialog
      v-model="serviceDialogVisible"
      :title="serviceForm.id ? '编辑服务' : '新建服务'"
      width="660px"
      destroy-on-close
    >
      <el-form
        ref="serviceFormRef"
        :model="serviceForm"
        :rules="serviceFormRules"
        label-width="110px"
      >
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="服务名称" prop="serviceName">
              <el-input
                v-model="serviceForm.serviceName"
                :disabled="!!serviceForm.id"
                placeholder="如 nginx"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="镜像" prop="image">
              <el-input v-model="serviceForm.image" placeholder="nginx:latest" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="副本数">
              <el-input-number v-model="serviceForm.replicas" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="更新策略">
              <el-select v-model="serviceForm.strategy" style="width: 100%">
                <el-option label="Recreate（重建）" value="Recreate" />
                <el-option label="RollingUpdate（滚动更新）" value="RollingUpdate" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="CPU">
              <el-input-number v-model="serviceForm.cpu" :min="0" placeholder="CPU shares" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="内存(MB)">
              <el-input-number v-model="serviceForm.memory" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主机名">
              <el-input v-model="serviceForm.hostname" placeholder="容器主机名（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="停止宽限期">
              <el-input
                v-model="serviceForm.terminationGracePeriodSeconds"
                placeholder="如 30"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="健康检查命令">
              <el-input
                v-model="serviceForm.healthCheckCmd"
                placeholder="如 curl -f http://localhost:8080/health || exit 1（配置后容器启用 Docker HEALTHCHECK）"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="启动命令">
              <el-input v-model="serviceForm.command" placeholder="按空白拆分，如 nginx -g" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="命令参数">
              <el-input v-model="serviceForm.args" placeholder="如 daemon off;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="环境变量">
          <div class="env-list">
            <div v-for="(env, idx) in serviceForm.envs" :key="idx" class="env-item">
              <el-input v-model="env.name" placeholder="变量名" style="width: 200px" />
              <el-input v-model="env.value" placeholder="值" style="width: 220px" />
              <el-button type="danger" link @click="removeEnv(idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div class="env-add">
              <el-button size="small" type="primary" plain @click="addEnv">
                添加环境变量
              </el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingService" @click="saveService">保存</el-button>
      </template>
    </el-dialog>

    <!-- 端口对话框 -->
    <el-dialog
      v-model="portDialogVisible"
      :title="portForm.id ? '编辑端口' : '添加端口'"
      width="420px"
      destroy-on-close
    >
      <el-form ref="portFormRef" :model="portForm" :rules="portFormRules" label-width="90px">
        <el-form-item label="协议" prop="protocol">
          <el-select v-model="portForm.protocol" style="width: 100%">
            <el-option label="tcp" value="tcp" />
            <el-option label="udp" value="udp" />
          </el-select>
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="portForm.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="portDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPort" @click="savePort">保存</el-button>
      </template>
    </el-dialog>

    <!-- 卷对话框 -->
    <el-dialog
      v-model="volumeDialogVisible"
      :title="volumeForm.id ? '编辑卷' : '添加卷'"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="volumeFormRef"
        :model="volumeForm"
        :rules="volumeFormRules"
        label-width="90px"
      >
        <el-form-item label="卷名称" prop="volumeName">
          <el-input
            v-model="volumeForm.volumeName"
            :disabled="!!volumeForm.id"
            placeholder="卷名称（创建后不可修改）"
          />
        </el-form-item>
        <el-form-item label="大小(GB)" prop="size">
          <el-input-number v-model="volumeForm.size" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="挂载路径" prop="mountPath">
          <el-input v-model="volumeForm.mountPath" placeholder="如 /data" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="volumeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingVolume" @click="saveVolume">保存</el-button>
      </template>
    </el-dialog>

    <!-- 容器日志对话框 -->
    <el-dialog v-model="logDialogVisible" title="查看容器日志" width="840px" destroy-on-close>
      <template v-if="logService">
        <div class="log-toolbar">
          <span class="log-service">
            服务：{{ logService.serviceName }}
            <el-tag size="small" class="log-image">{{ logService.image }}</el-tag>
          </span>
          <el-select
            v-if="logReplicas > 1"
            v-model="logForm.replicaIndex"
            placeholder="副本"
            style="width: 120px"
          >
            <el-option v-for="i in logReplicas" :key="i - 1" :label="`副本 ${i - 1}`" :value="i - 1" />
          </el-select>
          <el-input-number v-model="logForm.tail" :min="1" :max="10000" style="width: 130px" />
          <span class="text-muted">行数</span>
          <el-button type="primary" :loading="logLoading" @click="refreshLog">
            <el-icon><Refresh /></el-icon>&nbsp;刷新
          </el-button>
          <el-button @click="copyLog">
            <el-icon><CopyDocument /></el-icon>&nbsp;复制
          </el-button>
        </div>
        <el-alert
          v-if="!logLoading && !logContent && (logError || logMeta)"
          :title="logError || `容器 ${logMeta} 暂无日志输出`"
          type="info"
          :closable="false"
          class="log-alert"
        />
        <pre class="log-body" v-loading="logLoading">{{ logContent }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getServices,
  createService,
  updateService,
  deleteService
} from '@/api/service'
import { createPort, updatePort, deletePort } from '@/api/port'
import { createVolume, updateVolume, deleteVolume } from '@/api/volume'
import { getHistory } from '@/api/history'
import {
  stackStatus,
  deployStack,
  stopStack,
  removeStack,
  getContainerLog
} from '@/api/stack'
import { statusMeta } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const clusterId = Number(route.params.clusterId)
const stackId = Number(route.params.stackId)
const stackName = ref(route.query.name || `#${stackId}`)

// ---------- 栈状态 ----------
const stackStatusName = ref('')
const refreshStatus = async () => {
  try {
    const res = await stackStatus(stackId, { silent: true })
    if (res?.status) {
      stackStatusName.value = res.status
    }
  } catch (e) {
    // 静默
  }
}

// ---------- 生命周期操作（部署 / 停止 / 下架） ----------
const deploying = ref(false)
const stopping = ref(false)
const removing = ref(false)

const handleDeploy = () => {
  ElMessageBox.confirm(
    `确定部署栈「${stackName.value}」吗？将拉取镜像并按策略创建/替换容器。`,
    '部署确认',
    { confirmButtonText: '部署', cancelButtonText: '取消' }
  )
    .then(async () => {
      deploying.value = true
      try {
        const res = await deployStack(stackId)
        if (res?.status) {
          stackStatusName.value = res.status
        }
        ElMessage.success(`部署成功，当前状态：${statusMeta(res?.status).label}`)
      } catch (e) {
        // 拦截器已提示（如端口冲突）
      } finally {
        deploying.value = false
      }
    })
    .catch(() => {})
}

const handleStop = () => {
  ElMessageBox.confirm(
    `确定停止栈「${stackName.value}」下所有容器吗？`,
    '停止确认',
    { confirmButtonText: '停止', cancelButtonText: '取消' }
  )
    .then(async () => {
      stopping.value = true
      try {
        await stopStack(stackId)
        ElMessage.success('栈已停止')
        await refreshStatus()
      } catch (e) {
        // 拦截器已提示
      } finally {
        stopping.value = false
      }
    })
    .catch(() => {})
}

const handleRemove = () => {
  ElMessageBox.confirm(
    `确定下架栈「${stackName.value}」吗？将删除栈下所有容器（含停止的）。`,
    '下架确认',
    { confirmButtonText: '下架', cancelButtonText: '取消', type: 'warning' }
  )
    .then(async () => {
      removing.value = true
      try {
        await removeStack(stackId)
        ElMessage.success('栈已下架')
        await refreshStatus()
      } catch (e) {
        // 拦截器已提示
      } finally {
        removing.value = false
      }
    })
    .catch(() => {})
}

// ---------- 容器日志 ----------
const logDialogVisible = ref(false)
const logLoading = ref(false)
const logContent = ref('')
const logMeta = ref('')
const logError = ref('')
const logService = ref(null)
const logForm = reactive({ replicaIndex: 0, tail: 500 })
const logReplicas = computed(() => Math.max(1, logService.value?.replicas ?? 1))

const openLogDialog = (service) => {
  logService.value = service
  logForm.replicaIndex = 0
  logForm.tail = 500
  logContent.value = ''
  logMeta.value = ''
  logError.value = ''
  logDialogVisible.value = true
  refreshLog()
}

const refreshLog = async () => {
  if (!logService.value) return
  logLoading.value = true
  try {
    const res = await getContainerLog({
      stackId,
      serviceId: logService.value.id,
      replicaIndex: logReplicas.value > 1 ? logForm.replicaIndex : undefined,
      tail: logForm.tail
    })
    logContent.value = res?.log ?? ''
    logMeta.value = res?.containerName ? `${res.containerName}（${res.containerId}）` : ''
    logError.value = ''
  } catch (e) {
    logContent.value = ''
    logMeta.value = ''
    logError.value = e?.message || '读取日志失败'
  } finally {
    logLoading.value = false
  }
}

const copyLog = async () => {
  try {
    await navigator.clipboard.writeText(logContent.value)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

// ---------- 服务列表 ----------
const activeTab = ref('services')
const servicesLoading = ref(false)
const services = ref([])
const serviceCount = ref(0)
const servicePage = ref(1)
const serviceSize = ref(15)
const serviceSearch = ref('')

const loadServices = async () => {
  servicesLoading.value = true
  try {
    const res = await getServices({
      stackId,
      page: servicePage.value,
      size: serviceSize.value,
      search: serviceSearch.value || undefined
    })
    services.value = res?.data || []
    serviceCount.value = res?.count || 0
  } catch (e) {
    // 拦截器已提示
  } finally {
    servicesLoading.value = false
  }
}

const handleServiceSearch = () => {
  servicePage.value = 1
  loadServices()
}

const handleServiceSizeChange = () => {
  servicePage.value = 1
  loadServices()
}

const formatEnvs = (envs) => {
  if (!envs || envs.length === 0) return '无环境变量'
  return envs.map((e) => `${e.name || ''}=${e.value || ''}`).join('；')
}

// ---------- 服务创建/编辑 ----------
const serviceDialogVisible = ref(false)
const savingService = ref(false)
const serviceFormRef = ref()
const serviceForm = ref(defaultServiceForm())
const serviceFormRules = {
  serviceName: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  image: [{ required: true, message: '请输入镜像', trigger: 'blur' }]
}

function defaultServiceForm() {
  return {
    id: null,
    stackId,
    serviceName: '',
    image: '',
    replicas: 1,
    command: '',
    args: '',
    cpu: null,
    memory: null,
    hostname: '',
    terminationGracePeriodSeconds: '',
    healthCheckCmd: '',
    strategy: 'Recreate',
    envs: []
  }
}

const openServiceDialog = (row) => {
  if (row) {
    serviceForm.value = {
      ...defaultServiceForm(),
      id: row.id,
      serviceName: row.serviceName,
      image: row.image,
      replicas: row.replicas ?? 1,
      command: row.command || '',
      args: row.args || '',
      cpu: row.cpu,
      memory: row.memory,
      hostname: row.hostname || '',
      terminationGracePeriodSeconds: row.terminationGracePeriodSeconds || '',
      healthCheckCmd: row.healthCheckCmd || '',
      strategy: row.strategy || 'Recreate',
      envs: (row.envs || []).map((e) => ({ ...e }))
    }
  } else {
    serviceForm.value = defaultServiceForm()
  }
  serviceDialogVisible.value = true
}

const addEnv = () => {
  serviceForm.value.envs.push({ name: '', value: '' })
}

const removeEnv = (idx) => {
  serviceForm.value.envs.splice(idx, 1)
}

const saveService = () => {
  serviceFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingService.value = true
    try {
      const payload = {
        ...serviceForm.value,
        envs: serviceForm.value.envs.filter((e) => e.name || e.value)
      }
      if (payload.id) {
        // 更新服务：image 必填，serviceName 不可修改（后端 UpdateServiceDto 无该字段）
        const { serviceName, ...rest } = payload
        await updateService(rest)
        ElMessage.success('服务已更新')
      } else {
        await createService(payload)
        ElMessage.success('服务已创建')
      }
      serviceDialogVisible.value = false
      await loadServices()
    } catch (e) {
      // 拦截器已提示
    } finally {
      savingService.value = false
    }
  })
}

const handleDeleteService = (row) => {
  ElMessageBox.confirm(
    `确定删除服务「${row.serviceName}」吗？`,
    '删除确认',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  )
    .then(async () => {
      try {
        await deleteService(stackId, row.id)
        ElMessage.success('服务已删除')
        await loadServices()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

// ---------- 端口 ----------
const portDialogVisible = ref(false)
const savingPort = ref(false)
const portFormRef = ref()
const portForm = ref(defaultPortForm())
const portFormRules = {
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }]
}

function defaultPortForm() {
  return {
    id: null,
    stackId,
    serviceId: null,
    protocol: 'tcp',
    port: undefined
  }
}

const openPortDialog = (service, port) => {
  if (port) {
    portForm.value = {
      id: port.id,
      stackId,
      serviceId: service.id,
      protocol: port.protocol || 'tcp',
      port: port.port
    }
  } else {
    portForm.value = { ...defaultPortForm(), serviceId: service.id }
  }
  portDialogVisible.value = true
}

const savePort = () => {
  portFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingPort.value = true
    try {
      if (portForm.value.id) {
        await updatePort({ ...portForm.value })
        ElMessage.success('端口已更新')
      } else {
        await createPort({ ...portForm.value })
        ElMessage.success('端口已添加')
      }
      portDialogVisible.value = false
      await loadServices()
    } catch (e) {
      // 拦截器已提示（如端口重复）
    } finally {
      savingPort.value = false
    }
  })
}

const handleDeletePort = (service, port) => {
  ElMessageBox.confirm('确定删除该端口吗？', '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      try {
        await deletePort({ id: port.id, stackId, serviceId: service.id })
        ElMessage.success('端口已删除')
        await loadServices()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

// ---------- 卷 ----------
const volumeDialogVisible = ref(false)
const savingVolume = ref(false)
const volumeFormRef = ref()
const volumeForm = ref(defaultVolumeForm())
const volumeFormRules = {
  volumeName: [{ required: true, message: '请输入卷名称', trigger: 'blur' }],
  size: [{ required: true, message: '请输入卷大小', trigger: 'blur' }],
  mountPath: [{ required: true, message: '请输入挂载路径', trigger: 'blur' }]
}

function defaultVolumeForm() {
  return {
    id: null,
    stackId,
    serviceId: null,
    volumeName: '',
    size: 1,
    mountPath: ''
  }
}

const openVolumeDialog = (service, vol) => {
  if (vol) {
    volumeForm.value = {
      id: vol.id,
      stackId,
      serviceId: service.id,
      volumeName: vol.volumeName,
      size: vol.size ?? 1,
      mountPath: vol.mountPath || ''
    }
  } else {
    volumeForm.value = { ...defaultVolumeForm(), serviceId: service.id }
  }
  volumeDialogVisible.value = true
}

const saveVolume = () => {
  volumeFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingVolume.value = true
    try {
      if (volumeForm.value.id) {
        // 卷名称创建后不可修改
        const { volumeName, ...rest } = volumeForm.value
        await updateVolume(rest)
        ElMessage.success('卷已更新')
      } else {
        await createVolume({ ...volumeForm.value })
        ElMessage.success('卷已添加')
      }
      volumeDialogVisible.value = false
      await loadServices()
    } catch (e) {
      // 拦截器已提示
    } finally {
      savingVolume.value = false
    }
  })
}

const handleDeleteVolume = (service, vol) => {
  ElMessageBox.confirm(`确定删除卷「${vol.volumeName}」吗？`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      try {
        await deleteVolume(stackId, vol.id)
        ElMessage.success('卷已删除')
        await loadServices()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

// ---------- 操作记录 ----------
const historyLoading = ref(false)
const historyList = ref([])
const historyCount = ref(0)
const historyPage = ref(1)
const historySize = ref(15)

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getHistory({
      stackId,
      page: historyPage.value,
      size: historySize.value
    })
    historyList.value = res?.data || []
    historyCount.value = res?.count || 0
  } catch (e) {
    // 拦截器已提示
  } finally {
    historyLoading.value = false
  }
}

const handleHistorySizeChange = () => {
  historyPage.value = 1
  loadHistory()
}

// 切换到操作记录时加载
watch(activeTab, (tab) => {
  if (tab === 'history') {
    loadHistory()
  }
})

onMounted(() => {
  loadServices()
  refreshStatus()
})
</script>

<style scoped>
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.expanded {
  padding: 8px 16px;
}

.expanded-section {
  margin-bottom: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 8px;
}

.env-list .env-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.env-list .env-add {
  margin-top: 4px;
}

.log-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.log-service {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.log-image {
  max-width: 220px;
}

.log-alert {
  margin-bottom: 10px;
}

.log-body {
  min-height: 320px;
  max-height: 480px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  background-color: #0d1117;
  color: #c9d1d9;
  border-radius: 4px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
