<!--
  移动端布局壳 — 顶部标题栏 + 内容区（无底部TabBar）。
  @author Focus
  @date 2026-06-24
-->
<template>
  <div class="mobile-shell">
    <header class="mobile-header">
      <el-icon v-if="showBack" :size="20" class="back-btn" @click="goBack"><ArrowLeft /></el-icon>
      <span class="mobile-title">{{ title }}</span>
      <span v-if="showBack" style="width:20px"></span>
    </header>
    <main class="mobile-body">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const showBack = computed(() => route.path !== '/mobile')
const title = computed(() => {
  if (route.path === '/mobile') return '智库WMS'
  const map = { inbound: '入库扫码', outbound: '出库扫码', seal: '封存扫码', unseal: '解封扫码' }
  return map[route.params.mode] || '智库WMS'
})

function goBack() { router.back() }
</script>

<style scoped>
/* 桌面端：手机模拟区域居中，两侧透明 */
.mobile-shell {
  display: flex; flex-direction: column; height: 100vh; height: 100dvh;
  background: #f0f2f5; overflow: hidden;
  /* 桌面端限制宽度≈0.5*高度，模拟手机比例；手机端全宽 */
  width: 100%; max-width: 420px; margin: 0 auto;
  box-shadow: 0 0 20px rgba(0,0,0,0.3);
}
.mobile-header {
  flex-shrink: 0; height: 48px; display: flex; align-items: center;
  background: #2C2F39; color: #fff; font-size: 16px; font-weight: 600;
  padding: 0 12px; gap: 8px; padding-top: env(safe-area-inset-top);
}
.mobile-title { flex: 1; text-align: center; }
.mobile-title::before { display: none !important; }
.back-btn::before { display: none !important; }
.back-btn { cursor: pointer; flex-shrink: 0; }
.mobile-body {
  flex: 1; overflow-y: auto; padding: 12px;
  -webkit-overflow-scrolling: touch;
}
</style>
