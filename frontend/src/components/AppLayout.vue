<!--
  经典后台管理系统布局：暗色左侧菜单 + 顶部功能栏 + 内容区。
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="admin-layout">
    <div v-if="mobileMenuOpen" class="mobile-sidebar-mask" @click="mobileMenuOpen = false"></div>

    <!-- ===== 左侧菜单栏 ===== -->
    <aside class="admin-sidebar" :class="{ collapsed: isCollapse, 'mobile-open': mobileMenuOpen }">
      <div class="sidebar-logo">
        <div class="logo-icon">W</div>
        <transition name="fade">
          <span v-show="!isCollapse" class="logo-text">智库 WMS</span>
        </transition>
        <button class="mobile-sidebar-close" type="button" aria-label="关闭菜单" @click="mobileMenuOpen = false">
          <el-icon :size="18"><Close /></el-icon>
        </button>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/dashboard" class="nav-item"
          :class="{ active: $route.path === '/dashboard' }" @click="mobileMenuOpen = false">
          <el-icon :size="18"><DataAnalysis /></el-icon>
          <span class="nav-label">仪表盘</span>
        </router-link>
        <router-link to="/materials" class="nav-item"
          :class="{ active: $route.path.startsWith('/materials') }" @click="mobileMenuOpen = false">
          <el-icon :size="18"><Document /></el-icon>
          <span class="nav-label">物料与基础数据</span>
        </router-link>
        <router-link to="/inbound-outbound" class="nav-item"
          :class="{ active: $route.path.startsWith('/inbound-outbound') }" @click="mobileMenuOpen = false">
          <el-icon :size="18"><Switch /></el-icon>
          <span class="nav-label">出入库管理</span>
        </router-link>
        <router-link to="/stock-report" class="nav-item"
          :class="{ active: $route.path.startsWith('/stock-report') }" @click="mobileMenuOpen = false">
          <el-icon :size="18"><TrendCharts /></el-icon>
          <span class="nav-label">库存报表与预警</span>
        </router-link>
        <router-link to="/ai-report" class="nav-item"
          :class="{ active: $route.path.startsWith('/ai-report') }" @click="mobileMenuOpen = false">
          <el-icon :size="18"><Cpu /></el-icon>
          <span class="nav-label">AI 智能报告</span>
        </router-link>
        <router-link to="/inventory-trace" class="nav-item"
          :class="{ active: $route.path.startsWith('/inventory-trace') }" @click="mobileMenuOpen = false">
          <el-icon :size="18"><Search /></el-icon>
          <span class="nav-label">库存与看板监控</span>
        </router-link>
        <router-link to="/inbound-history" class="nav-item"
          :class="{ active: $route.path.startsWith('/inbound-history') }" @click="mobileMenuOpen = false">
          <el-icon :size="18"><Clock /></el-icon>
          <span class="nav-label">出入库历史</span>
        </router-link>
        <router-link to="/scan" class="nav-item"
          :class="{ active: $route.path.startsWith('/scan') }" @click="mobileMenuOpen = false">
          <el-icon :size="18"><Camera /></el-icon>
          <span class="nav-label">扫码操作</span>
        </router-link>
        <router-link to="/freeze" class="nav-item"
          :class="{ active: $route.path.startsWith('/freeze') }" @click="mobileMenuOpen = false">
          <el-icon :size="18"><Lock /></el-icon>
          <span class="nav-label">封存管理</span>
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
          <button class="mobile-menu-button" type="button" aria-label="打开菜单" @click="mobileMenuOpen = true">
            <el-icon :size="20"><Menu /></el-icon>
          </button>
          <span class="mobile-page-title">{{ currentTitle }}</span>
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
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { DataAnalysis, Document, Switch, TrendCharts, Cpu, Search, Camera, Clock, Lock, DArrowLeft, DArrowRight, UserFilled, WarningFilled, Menu, Close } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const mobileMenuOpen = ref(false)
const showLogout = ref(false)

const currentTitle = computed(() => route.meta.title || '首页')

watch(() => route.fullPath, () => {
  mobileMenuOpen.value = false
})

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
  min-width: 0;
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
  z-index: 30;
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
  position: relative;
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

.mobile-sidebar-mask,
.mobile-sidebar-close,
.mobile-menu-button,
.mobile-page-title {
  display: none;
}

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
.header-left { display: flex; align-items: center; min-width: 0; }
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

@media (max-width: 768px) {
  .admin-layout {
    height: 100dvh;
    overflow: hidden;
  }

  .admin-sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    width: min(82vw, 300px);
    transform: translateX(-100%);
    transition: transform 0.22s ease;
    box-shadow: 8px 0 24px rgba(0, 0, 0, 0.18);
  }

  .admin-sidebar.collapsed {
    width: min(82vw, 300px);
  }

  .admin-sidebar.mobile-open {
    transform: translateX(0);
  }

  .mobile-sidebar-mask {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(15, 23, 42, 0.42);
    z-index: 20;
  }

  .mobile-sidebar-close {
    display: inline-flex;
    position: absolute;
    right: 12px;
    top: 50%;
    width: 34px;
    height: 34px;
    transform: translateY(-50%);
    align-items: center;
    justify-content: center;
    border: 0;
    border-radius: 4px;
    color: var(--sidebar-text);
    background: rgba(255, 255, 255, 0.08);
  }

  .sidebar-footer {
    display: none;
  }

  .admin-main {
    width: 100%;
  }

  .admin-header {
    height: 52px;
    padding: 0 12px;
    gap: 12px;
  }

  .mobile-menu-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border: 1px solid var(--border-light);
    border-radius: 4px;
    color: var(--text-regular);
    background: #fff;
    flex-shrink: 0;
  }

  .mobile-page-title {
    display: block;
    color: var(--text-primary);
    font-size: 15px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .header-left :deep(.el-breadcrumb) {
    display: none;
  }

  .header-right {
    gap: 6px;
    flex-shrink: 0;
  }

  .header-user {
    max-width: 96px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .header-divider {
    display: none;
  }

  .admin-content {
    -webkit-overflow-scrolling: touch;
  }
}
</style>
