<!--
  经典后台管理系统布局：暗色左侧菜单 + 顶部功能栏 + 内容区。
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="admin-layout">
    <!-- ===== 左侧菜单栏 ===== -->
    <aside class="admin-sidebar" :class="{ collapsed: isCollapse }">
      <div class="sidebar-logo">
        <div class="logo-icon">W</div>
        <transition name="fade">
          <span v-show="!isCollapse" class="logo-text">智库 WMS</span>
        </transition>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/dashboard" class="nav-item"
          :class="{ active: $route.path === '/dashboard' }">
          <el-icon :size="18"><DataAnalysis /></el-icon>
          <span class="nav-label">智能看板</span>
        </router-link>
        <router-link to="/materials" class="nav-item"
          :class="{ active: $route.path.startsWith('/materials') }">
          <el-icon :size="18"><Document /></el-icon>
          <span class="nav-label">物料与基础数据</span>
        </router-link>
        <router-link to="/inbound-outbound" class="nav-item"
          :class="{ active: $route.path.startsWith('/inbound-outbound') }">
          <el-icon :size="18"><Switch /></el-icon>
          <span class="nav-label">入库与出库管理</span>
        </router-link>
        <router-link to="/stock-report" class="nav-item"
          :class="{ active: $route.path.startsWith('/stock-report') }">
          <el-icon :size="18"><TrendCharts /></el-icon>
          <span class="nav-label">库存报表与预警</span>
        </router-link>
        <router-link to="/ai-report" class="nav-item"
          :class="{ active: $route.path.startsWith('/ai-report') }">
          <el-icon :size="18"><Cpu /></el-icon>
          <span class="nav-label">AI 智能报告</span>
        </router-link>
        <router-link to="/inventory-trace" class="nav-item"
          :class="{ active: $route.path.startsWith('/inventory-trace') }">
          <el-icon :size="18"><Search /></el-icon>
          <span class="nav-label">库存追溯</span>
        </router-link>
      </nav>

      <div class="sidebar-footer" @click="isCollapse = !isCollapse">
        <el-icon :size="16">
          <DArrowLeft v-if="!isCollapse" /><DArrowRight v-else />
        </el-icon>
      </div>
    </aside>

    <!-- ===== 右侧主体 ===== -->
    <div class="admin-main" :class="{ expanded: isCollapse }">
      <header class="admin-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle !== '首页'">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="header-user">
            <el-icon :size="16" style="margin-right: 4px"><UserFilled /></el-icon>
            {{ userStore.username }}
          </span>
          <span class="header-divider">|</span>
          <span class="header-logout" @click="showLogout = true">退出</span>
        </div>
      </header>

      <div class="admin-content">
        <router-view />
      </div>
    </div>

    <!-- 退出登录确认对话框（模板式 Teleport to body，避免命令式渲染时序问题） -->
    <Teleport to="body">
      <el-dialog v-model="showLogout" title="提示" width="400px"
        align-center :close-on-click-modal="false" destroy-on-close>
        <p style="font-size: 15px; text-align: center; padding: 10px 0;">
          <el-icon :size="22" color="#e6a23c" style="vertical-align: middle; margin-right: 6px;">
            <WarningFilled />
          </el-icon>
          确定要退出登录吗？
        </p>
        <template #footer>
          <el-button @click="showLogout = false">取消</el-button>
          <el-button type="primary" @click="doLogout">确定</el-button>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
/**
 * 后台管理经典两栏布局。
 * 退出确认使用模板式 Teleport-to-body 对话框，彻底规避命令式 API 的渲染时序问题。
 */
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const showLogout = ref(false)

const currentTitle = computed(() => route.meta.title || '首页')

function doLogout() {
  showLogout.value = false
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
/* ===== 整体布局 ===== */
.admin-layout {
  display: flex;
  height: 100vh;
}

/* ===== 侧边栏 ===== */
.admin-sidebar {
  width: var(--sidebar-width);
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  transition: width 0.25s;
  flex-shrink: 0;
  overflow: hidden;
}
.admin-sidebar.collapsed {
  width: var(--sidebar-collapsed);
}

.sidebar-logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.logo-icon {
  width: 34px; height: 34px;
  background: var(--wms-primary);
  color: #fff;
  font-size: 18px; font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  flex-shrink: 0;
}
.logo-text {
  font-size: 16px; font-weight: 700;
  color: #fff;
  white-space: nowrap;
  letter-spacing: 1px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 20px;
  margin: 2px 8px;
  border-radius: 6px;
  color: var(--sidebar-text);
  text-decoration: none;
  font-size: 14px;
  transition: all 0.15s;
  white-space: nowrap;
}
.nav-item:hover { background: var(--sidebar-hover); color: #fff; }
.nav-item.active { background: var(--wms-primary); color: #fff; }
.nav-item .nav-label { overflow: hidden; text-overflow: ellipsis; }

.sidebar-footer {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid rgba(255,255,255,0.08);
  color: var(--sidebar-text);
  cursor: pointer;
  transition: color 0.15s;
}
.sidebar-footer:hover { color: #fff; }

/* ===== 右侧主体 ===== */
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.admin-header {
  height: var(--header-height);
  background: var(--header-bg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--header-border);
  flex-shrink: 0;
}
.header-left { display: flex; align-items: center; }
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}
.header-user { display: flex; align-items: center; color: var(--text-regular); }
.header-divider { color: var(--border-base); }
.header-logout { color: var(--text-secondary); cursor: pointer; }
.header-logout:hover { color: var(--wms-danger); }

.admin-content {
  flex: 1;
  overflow-y: auto;
  background: var(--main-bg);
}

/* ===== 过渡动画 ===== */
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
