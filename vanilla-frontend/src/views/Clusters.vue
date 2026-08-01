<template>
  <div class="page-container">
    <div class="page-header">
      <h2>集群管理</h2>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新建集群
      </el-button>
    </div>

    <el-card v-loading="loading">
      <el-table :data="clusters" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="clusterName" label="集群名称" min-width="130" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 'K8S' ? 'warning' : 'success'">
              {{ row.type || 'DOCKER' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="endpoint"
          label="连接地址"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          prop="description"
          label="描述"
          min-width="120"
          show-overflow-tooltip
        />
        <el-table-column label="TLS" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.tlsVerify ? 'danger' : 'info'" size="small">
              {{ row.tlsVerify ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goStacks(row)">栈管理</el-button>
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && clusters.length === 0"
        description="暂无集群，点击右上角新建集群"
      />
    </el-card>

    <!-- 创建 / 编辑集群 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑集群' : '新建集群'"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="集群名称" prop="clusterName">
          <el-input v-model="form.clusterName" placeholder="如 docker-1" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="集群描述（可选）"
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="Docker" value="DOCKER" />
            <el-option label="K8s" value="K8S" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接地址" prop="endpoint">
          <el-input
            v-model="form.endpoint"
            placeholder="unix:///var/run/docker.sock"
          />
        </el-form-item>
        <el-form-item label="TLS 校验">
          <el-switch v-model="form.tlsVerify" />
        </el-form-item>
        <el-form-item v-if="form.tlsVerify" label="证书目录">
          <el-input v-model="form.dockerCertPath" placeholder="Docker TLS 证书目录" />
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getClusters,
  createCluster,
  updateCluster,
  deleteCluster
} from '@/api/cluster'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const clusters = ref([])

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive(defaultForm())
const formRules = {
  clusterName: [{ required: true, message: '请输入集群名称', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入连接地址', trigger: 'blur' }]
}

function defaultForm() {
  return {
    id: null,
    clusterName: '',
    description: '',
    type: 'DOCKER',
    endpoint: '',
    tlsVerify: false,
    dockerCertPath: ''
  }
}

const loadClusters = async () => {
  loading.value = true
  try {
    clusters.value = (await getClusters()) || []
  } catch (e) {
    // 错误提示已在拦截器处理
  } finally {
    loading.value = false
  }
}

const goStacks = (row) => {
  router.push(`/clusters/${row.id}/stacks`)
}

const openDialog = (row) => {
  if (row) {
    Object.assign(form, defaultForm(), {
      id: row.id,
      clusterName: row.clusterName,
      description: row.description,
      type: row.type || 'DOCKER',
      endpoint: row.endpoint,
      tlsVerify: !!row.tlsVerify,
      dockerCertPath: row.dockerCertPath
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
        await updateCluster({ ...form })
        ElMessage.success('集群已更新')
      } else {
        await createCluster({ ...form })
        ElMessage.success('集群已创建')
      }
      dialogVisible.value = false
      await loadClusters()
    } catch (e) {
      // 拦截器已提示（如 403 无权限 / 业务错误）
    } finally {
      saving.value = false
    }
  })
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定删除集群「${row.clusterName}」吗？该操作不可恢复。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
    .then(async () => {
      try {
        await deleteCluster(row.id)
        ElMessage.success('集群已删除')
        await loadClusters()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

onMounted(loadClusters)
</script>
