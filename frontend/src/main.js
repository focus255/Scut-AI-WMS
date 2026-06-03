/**
 * 智库WMS 前端应用入口。
 *
 * @author Focus
 * @date 2026-06-03
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

import './assets/global.css'

// Element Plus 命令式 API 的消息组件样式必须放在 global.css 之后
// 确保其 z-index 不会被后续动态注入的路由组件 scoped 样式覆盖
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
