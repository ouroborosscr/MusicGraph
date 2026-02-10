import { createApp } from 'vue'
//import './style.css' // Vite 默认样式，保留即可
import App from './App.vue'
import router from './router' // 稍后创建
import { createPinia } from 'pinia'

// 引入 Naive UI 推荐的通用字体 (可选，不装 vfonts 也没事)
import 'vfonts/Lato.css'
import 'vfonts/FiraCode.css'

const app = createApp(App)

// 【关键步骤】挂载插件
app.use(createPinia()) // 启用状态管理
app.use(router)        // 启用路由

// 挂载到 index.html 的 #app 节点上
app.mount('#app')