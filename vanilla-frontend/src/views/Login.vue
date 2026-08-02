<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">Vanilla 服务部署平台</div>
      <div class="login-subtitle">基于 Docker 的服务部署与运维</div>

      <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="handleLogin">
        <el-form-item prop="loginName">
          <el-input
            v-model="form.loginName"
            placeholder="请输入登录用户名"
            size="large"
            clearable
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            placeholder="请输入密码"
            size="large"
            type="password"
            show-password
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
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

      <div class="login-tip">默认管理员账号 admin / admin123</div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { login } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ loginName: '', password: '' })
const rules = {
  loginName: [{ required: true, message: '请输入登录用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const data = await login({
        loginName: form.loginName.trim(),
        password: form.password
      })
      authStore.setAuth({ token: data.token, loginName: data.loginName })
      ElMessage.success(`欢迎回来，${data.nikeName || data.loginName}`)
      router.push(route.query.redirect || '/clusters')
    } catch (e) {
      // 错误提示已由拦截器处理
    } finally {
      loading.value = false
    }
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
