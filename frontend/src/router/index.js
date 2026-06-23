/**
 * 前端路由配置。
 *
 * @author Focus
 * @date 2026-06-03
 */
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', noAuth: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', noAuth: true }
  },
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '智能库存看板' }
      },
      {
        path: 'materials',
        name: 'Materials',
        component: () => import('@/views/Materials.vue'),
        meta: { title: '物料与基础数据' }
      },
      {
        path: 'inbound-outbound',
        name: 'InboundOutbound',
        component: () => import('@/views/InboundOutbound.vue'),
        meta: { title: '入库与出库管理' }
      },
      {
        path: 'stock-report',
        name: 'StockReport',
        component: () => import('@/views/StockReport.vue'),
        meta: { title: '库存报表与风险预警' }
      },
      {
        path: 'ai-report',
        name: 'AiReport',
        component: () => import('@/views/AiReport.vue'),
        meta: { title: 'AI 风险预测与智能报告' }
      },
      {
        path: 'inventory-trace',
        name: 'InventoryTrace',
        component: () => import('@/views/InventoryTrace.vue'),
        meta: { title: '库存追溯' }
      },
      {
        path: 'inbound-history',
        name: 'InboundHistory',
        component: () => import('@/views/InboundHistory.vue'),
        meta: { title: '入库历史' }
      },
      {
        path: 'scan',
        name: 'Scan',
        component: () => import('@/views/Scan.vue'),
        meta: { title: '扫码操作' }
      },
      {
        path: 'freeze',
        name: 'FreezeManagement',
        component: () => import('@/views/FreezeManagement.vue'),
        meta: { title: '封存管理' }
      }
    ]
  },
  {
    // 手机端
    path: '/mobile',
    component: () => import('@/components/MobileLayout.vue'),
    meta: { noAuth: false },
    children: [
      {
        path: '',
        name: 'MobileHome',
        component: () => import('@/views/MobileHome.vue'),
        meta: { title: '智库WMS' }
      },
      {
        path: 'scan/:mode',
        name: 'MobileScanner',
        component: () => import('@/views/MobileScanner.vue'),
        meta: { title: '扫码' },
        props: true
      }
    ]
  },
  {
    // 未匹配路由重定向到看板
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局路由守卫：非登录页无 Token 时强制跳转登录页。
 */
router.beforeEach((to, _from, next) => {
  // 检查 localStorage 中的 JWT
  const token = localStorage.getItem('token')
  if (!to.meta.noAuth && !token) {
    next('/login')
  } else if ((to.path === '/login' || to.path === '/register') && token) {
    // 已登录用户访问登录/注册页直接跳看板
    next('/dashboard')
  } else {
    next()
  }
})

export default router
