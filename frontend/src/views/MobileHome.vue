<!--
  手机端首页 — 四个操作按钮。
  @author Focus
  @date 2026-06-24
-->
<template>
  <div class="home-page">
    <div class="btn-list">
      <div class="action-btn" @click="$router.push('/mobile/scan/inbound')">
        <el-icon :size="28"><Box /></el-icon>
        <span>入库</span>
      </div>
      <div class="action-btn" @click="$router.push('/mobile/scan/outbound')">
        <el-icon :size="28"><Sell /></el-icon>
        <span>出库</span>
      </div>
      <div class="action-btn" @click="$router.push('/mobile/scan/seal')">
        <el-icon :size="28"><Lock /></el-icon>
        <span>封存</span>
      </div>
      <div class="action-btn" @click="$router.push('/mobile/scan/unseal')">
        <el-icon :size="28"><Unlock /></el-icon>
        <span>解封</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Box, Sell, Lock, Unlock } from '@element-plus/icons-vue'
</script>

<style scoped>
.home-page {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-height: calc(100vh - 80px); padding: 20px;
}
.btn-list { display: flex; flex-direction: column; gap: 16px; width: 100%; }
.action-btn {
  display: flex; align-items: center; justify-content: center; gap: 12px;
  padding: 22px; border-radius: 12px; font-size: 18px; font-weight: 600;
  color: #fff; cursor: pointer; transition: transform 0.1s;
  background: #409eff;
}
.action-btn:active { transform: scale(0.97); opacity: 0.9; }
</style>
