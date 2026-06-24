<!--
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="login-wrapper">
    <div class="login-box">
      <!-- 左侧品牌区 -->
      <div class="login-brand">
        <div class="brand-icon">W</div>
        <h1 class="brand-name">智库 WMS</h1>
        <p class="brand-sub">智能仓储管理系统</p>
      </div>

      <!-- 右侧表单区 -->
      <div class="login-form-area">
        <h2 class="form-title">账号登录</h2>
        <el-form ref="formRef" :model="form" :rules="rules" size="large"
          label-width="56px" @keyup.enter="handleLogin">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码"
              show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" style="width: 100%"
              @click="handleLogin">
              登 录
            </el-button>
          </el-form-item>
        </el-form>
        <p class="register-link">
          还没有账号？
          <router-link to="/register">立即注册</router-link>
        </p>
      </div>
    </div>
    <p class="login-copyright">Copyright © 2026 智库WMS</p>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { login } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = await login(form.username, form.password)
    userStore.setLogin(form.username, data.token)
    ElMessage.success('登录成功，欢迎回来')
    router.push('/dashboard')
  } catch {
    ElMessage.error('用户名或密码错误')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.login-box {
  display: flex;
  width: 820px;
  min-height: 420px;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

/* 左侧品牌区 */
.login-brand {
  width: 340px;
  background: #2b2f3a;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  padding: 40px;
}
.brand-icon {
  width: 64px;
  height: 64px;
  background: var(--wms-primary);
  font-size: 32px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  margin-bottom: 20px;
}
.brand-name {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 3px;
  margin-bottom: 8px;
}
.brand-sub {
  font-size: 13px;
  color: rgba(255,255,255,0.6);
}

/* 右侧表单区 */
.login-form-area {
  flex: 1;
  padding: 48px 52px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.form-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 28px;
}

:deep(.el-form-item__label) {
  white-space: nowrap;
}
.register-link {
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
}
.register-link a {
  color: var(--wms-primary);
  text-decoration: none;
}
.register-link a:hover {
  text-decoration: underline;
}

/* 底部版权 */
.login-copyright {
  margin-top: 24px;
  font-size: 12px;
  color: var(--text-placeholder);
}

@media (max-width: 768px) {
  .login-wrapper {
    height: 100dvh;
    justify-content: flex-start;
    padding: 18px 12px;
    overflow-y: auto;
  }

  .login-box {
    width: 100%;
    max-width: 420px;
    min-height: 0;
    flex-direction: column;
  }

  .login-brand {
    width: 100%;
    padding: 28px 20px;
  }

  .brand-icon {
    width: 52px;
    height: 52px;
    font-size: 26px;
    margin-bottom: 14px;
  }

  .brand-name {
    font-size: 20px;
  }

  .login-form-area {
    padding: 28px 22px 24px;
  }

  .form-title {
    margin-bottom: 22px;
  }

  .login-copyright {
    margin-top: 14px;
    text-align: center;
  }
}
</style>
