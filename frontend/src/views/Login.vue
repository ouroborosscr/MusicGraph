<template>
  <div class="login-container">
    <div class="login-box">
      <n-card class="auth-card" size="large" hoverable>
        <div class="header">
          <h1 class="title">MusicGraph</h1>
          <p class="subtitle">探索你的音乐思维轨迹</p>
        </div>

        <n-tabs 
          v-model:value="activeTab" 
          size="large" 
          justify-content="space-evenly"
          type="segment"
          animated
        >
          <n-tab-pane name="login" tab="登录" />
          <n-tab-pane name="register" tab="注册" />
        </n-tabs>

        <div class="form-area">
          <n-input
            v-model:value="form.username"
            size="large"
            placeholder="请输入账号"
          >
            <template #prefix>
              <n-icon :component="PersonOutline" />
            </template>
          </n-input>

          <n-input
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            size="large"
            placeholder="请输入密码"
            @keyup.enter="handleAuth"
          >
            <template #prefix>
              <n-icon :component="LockClosedOutline" />
            </template>
          </n-input>

          <n-button
            type="primary"
            size="large"
            block
            :loading="loading"
            @click="handleAuth"
            :disabled="!form.username || !form.password"
            class="submit-btn"
          >
            {{ activeTab === 'login' ? '立即登录' : '注册账号' }}
          </n-button>
        </div>
      </n-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { NCard, NInput, NButton, NIcon, NTabs, NTabPane, useMessage } from 'naive-ui'
import { PersonOutline, LockClosedOutline } from '@vicons/ionicons5'
import request from '../api/request'

const router = useRouter()
const userStore = useUserStore()
const message = useMessage()

const activeTab = ref('login')
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const handleAuth = async () => {
  if (!form.username.trim() || !form.password.trim()) {
    message.warning('账号和密码不能为空')
    return
  }

  loading.value = true

  try {
    if (activeTab.value === 'login') {
      const res = await request.post('/auth/login', null, {
        params: {
          username: form.username,
          password: form.password
        }
      })
      
      const { token, username } = res.data
      localStorage.setItem('token', token)
      userStore.setUsername(username)
      
      message.success('登录成功，欢迎回来！')
      router.push('/')

    } else {
      await request.post('/auth/register', null, {
        params: {
          username: form.username,
          password: form.password
        }
      })
      
      message.success('注册成功，请登录')
      activeTab.value = 'login'
    }
  } catch (error: any) {
    const msg = error.response?.data || '请求失败，请检查网络'
    message.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  /* 使用 100% 充满父容器（App.vue 的 content 区域） */
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  /* 移除写死的背景色，依靠透明背景显示 App.vue 的底色 */
}

.login-box {
  width: 400px;
  max-width: 90%;
  z-index: 1;
}

.auth-card {
  border-radius: 16px;
  /* 增加一点阴影让它在浅色模式下更立体 */
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.1);
}

.header {
  text-align: center;
  margin-bottom: 24px;
}

.title {
  font-size: 2rem;
  font-weight: 700;
  /* 使用渐变色作为品牌标识 */
  background: linear-gradient(to right, #1db954, #1ed760);
  -webkit-background-clip: text;
  color: transparent;
  margin: 0;
}

.subtitle {
  /* 使用 Naive UI 的变量让副标题颜色自动适配深浅 */
  color: var(--n-text-color-3); 
  margin-top: 8px;
}

.form-area {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 24px;
}

.submit-btn {
  font-weight: bold;
  letter-spacing: 1px;
}

/* 移除所有 :deep 的 !important 样式覆盖 */
/* 让 Naive UI 自动处理 Input 和 Tabs 的颜色 */
</style>