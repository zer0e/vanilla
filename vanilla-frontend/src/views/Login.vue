<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">Vanilla 服务部署平台</div>
      <div class="login-subtitle">基于 Docker 的服务部署与运维</div>

      <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入登录用户名"
            size="large"
            clearable
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>

      <div class="login-tip">平台通过请求头 x-auth-user 识别用户，无需密码</div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '' })
const rules = {
  username: [{ required: true, message: '请输入登录用户名', trigger: 'blur' }]
}

const handleLogin = () => {
  formRef.value.validate((valid) => {
    if (!valid) return
    const name = form.username.trim()
    if (!name) {
      ElMessage.warning('请输入登录用户名')
      return
    }
    loading.value = true
    authStore.setUsername(name)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.push(redirect || '/clusters')
    loading.value = false
  })
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1677ff 0%, #3c6fe6 100%);
}

.login-card {
  width: 380px;
  padding: 12px 6px;
  border-radius: 8px;
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  text-align: center;
  color: #303133;
}

.login-subtitle {
  font-size: 13px;
  color: #999;
  text-align: center;
  margin: 8px 0 24px;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
}

.login-tip {
  margin-top: 16px;
  font-size: 12px;
  color: #aaa;
  text-align: center;
}
</style>
