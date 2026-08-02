<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">Vanilla</div>

      <!-- 进入集群后显示当前集群上下文 -->
      <div v-if="inCluster" class="cluster-context">
        <span class="cluster-name" :title="clusterName">☁ {{ clusterName }}</span>
        <span class="cluster-sub">当前集群</span>
      </div>

      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/clusters">
          <el-icon><Platform /></el-icon>
          <span>集群列表</span>
        </el-menu-item>
        <el-menu-item v-if="inCluster" :index="`/clusters/${clusterId}/stacks`">
          <el-icon><Box /></el-icon>
          <span>栈列表</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">
          Vanilla 服务部署平台
          <span v-if="clusterName && inCluster" class="header-sub">/ {{ clusterName }}</span>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              <span>{{ authStore.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="authStore.isAdmin" @click="goUsers">
                  <el-icon><User /></el-icon>&nbsp;用户管理
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>&nbsp;退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getClusters } from '@/api/cluster'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 是否处于某个集群上下文内（/clusters/:clusterId/...）
const clusterId = computed(() => {
  const id = Number(route.params.clusterId)
  return Number.isFinite(id) && id > 0 ? id : null
})
const inCluster = computed(() => clusterId.value !== null)

const activeMenu = computed(() => {
  if (inCluster.value) return `/clusters/${clusterId.value}/stacks`
  return '/clusters'
})

// 集群名解析（进入集群后侧边栏/顶栏展示，按需拉取一次）
const clusterName = ref('')
const clustersCache = ref([])
const resolveClusterName = async (id) => {
  if (!id) {
    clusterName.value = ''
    return
  }
  if (clustersCache.value.length === 0) {
    try {
      clustersCache.value = (await getClusters()) || []
    } catch (e) {
      return
    }
  }
  const found = clustersCache.value.find((c) => c.id === id)
  clusterName.value = found ? found.clusterName : `#${id}`
}
watch(inCluster, (v) => resolveClusterName(v ? clusterId.value : null), { immediate: true })

const goUsers = () => router.push('/users')

const handleLogout = () => {
  authStore.logout()
  router.push('/login')
}

onMounted(() => {
  // 登录后拉取当前用户信息（admin 标记决定「用户管理」入口是否可见）
  authStore.loadProfile()
})
</script>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background-color: #001529;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}

.cluster-context {
  padding: 4px 16px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 6px;
}

.cluster-name {
  display: block;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cluster-sub {
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
}

.menu {
  border-right: none;
  background-color: #001529;
}

.menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.7);
}

.menu :deep(.el-menu-item:hover) {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.08);
}

.menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background-color: #1677ff;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fff;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-sub {
  font-size: 13px;
  font-weight: 400;
  color: #909399;
}

.header-right .user-info {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #333;
  outline: none;
}

.main {
  background-color: #f5f7fa;
  overflow: auto;
}
</style>