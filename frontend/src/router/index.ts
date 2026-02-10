import { createRouter, createWebHistory } from 'vue-router'

// 懒加载导入页面
const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue')
    },
    {
        path: '/',
        name: 'Home',
        component: () => import('../views/Home.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/player/:id',
        name: 'Player',
        component: () => import('../views/Player.vue'),
        meta: { requiresAuth: true }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// 简单的路由守卫：没登录就踢回登录页
router.beforeEach((to, from, next) => {
    // 检查本地有没有存用户名
    const isAuthenticated = localStorage.getItem('username')
    
    if (to.name !== 'Login' && !isAuthenticated) {
        next({ name: 'Login' })
    } else {
        next()
    }
})

export default router