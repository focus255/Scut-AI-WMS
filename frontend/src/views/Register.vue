<!--
  用户注册页
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="register-wrapper">
    <div class="register-box">
      <!-- 左侧品牌区 -->
      <div class="register-brand">
        <div class="brand-icon">W</div>
        <h1 class="brand-name">智库 WMS</h1>
        <p class="brand-sub">智能仓储管理系统</p>
      </div>

      <!-- 右侧注册表单 -->
      <div class="register-form-area">
        <h2 class="form-title">创建账号</h2>
        <el-form ref="formRef" :model="form" :rules="rules" size="large"
          label-width="72px" @keyup.enter="handleRegister">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入账号（至少3个字符）" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码（至少6个字符）"
              show-password />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码"
              show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" style="width: 100%"
              @click="handleRegister">
              注 册
            </el-button>
          </el-form-item>
        </el-form>
        <p class="login-link">
          已有账号？
          <router-link to="/login">立即登录</router-link>
        </p>
      </div>
    </div>
    <p class="register-copyright">Copyright © 2026 智库WMS</p>
  </div>
</template>

<script setup>
/**
 * 用户注册页 — 与登录页一致的品牌双栏风格。
 */
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

/**
 * 自定义校验：确认密码一致性。
 */
function validateConfirm(rule, value, callback) {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 50, message: '账号长度需在 3-50 个字符之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度需在 6-100 个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register(form.username, form.password)
    ElMessage.success('注册成功，请使用新账号登录')
    router.push('/login')
  } catch (err) {
    ElMessage.error(err.message || '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-wrapper {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.register-box {
  display: flex;
  width: 820px;
  min-height: 480px;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.register-brand {
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

.register-form-area {
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
/* 禁止表单标签换行 */
:deep(.el-form-item__label) {
  white-space: nowrap;
}
.login-link {
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
}
.login-link a {
  color: var(--wms-primary);
  text-decoration: none;
}
.login-link a:hover {
  text-decoration: underline;
}

.register-copyright {
  margin-top: 24px;
  font-size: 12px;
  color: var(--text-placeholder);
}

@media (max-width: 768px) {
  .register-wrapper {
    height: 100dvh;
    justify-content: flex-start;
    padding: 18px 12px;
    overflow-y: auto;
  }

  .register-box {
    width: 100%;
    max-width: 420px;
    min-height: 0;
    flex-direction: column;
  }

  .register-brand {
    width: 100%;
    padding: 24px 20px;
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

  .register-form-area {
    padding: 26px 22px 24px;
  }

  .form-title {
    margin-bottom: 22px;
  }

  .register-copyright {
    margin-top: 14px;
    text-align: center;
  }
}
</style>
