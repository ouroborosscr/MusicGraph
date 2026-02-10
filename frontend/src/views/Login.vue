<template>
  <div class="login-container">
    <div class="bg-image"></div>
    <div class="login-box">
      <n-card :bordered="false" class="glass-card">
        <div class="header">
          <h1 class="title">MusicGraph</h1>
          <p class="subtitle">探索你的音乐思维轨迹</p>
        </div>

        <div class="form-area">
          <n-input
            v-model:value="inputName"
            size="large"
            placeholder="给自己起个代号..."
            @keyup.enter="handleLogin"
            class="custom-input"
          >
            <template #prefix>
              <n-icon :component="PersonOutline" />
            </template>
          </n-input>

          <n-button
            type="primary"
            size="large"
            block
            color="#1db954" 
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
            :disabled="!inputName"
          >
            进入音乐世界
          </n-button>
        </div>
      </n-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { NCard, NInput, NButton, NIcon, useMessage } from 'naive-ui'
import { PersonOutline } from '@vicons/ionicons5' // 确保安装了 @vicons/ionicons5

const router = useRouter()
const userStore = useUserStore()
const message = useMessage()

const inputName = ref('')
const loading = ref(false)

const handleLogin = () => {
  if (!inputName.value.trim()) {
    message.warning('请输入一个昵称')
    return
  }
  loading.value = true
  setTimeout(() => {
    userStore.setUsername(inputName.value)
    message.success(`欢迎回来，${inputName.value}`)
    router.push('/')
    loading.value = false
  }, 800)
}
</script>

<style scoped>
.login-container {
  position: relative;
  width: 100vw;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #121212;
  overflow: hidden;
}
.bg-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #1e1e2f 0%, #2a2a40 100%);
  filter: brightness(0.6) blur(5px);
  z-index: 0;
}
.login-box {
  z-index: 1;
  width: 400px;
  max-width: 90%;
}
.glass-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  padding: 20px;
}
.header { text-align: center; margin-bottom: 40px; }
.title {
  font-size: 2.5rem;
  font-weight: 700;
  background: linear-gradient(to right, #1db954, #1ed760);
  -webkit-background-clip: text;
  color: transparent;
}
.subtitle { color: rgba(255, 255, 255, 0.6); margin-top: 10px; }
.form-area { display: flex; flex-direction: column; gap: 20px; }
/* 强制覆盖 input 样式适配暗黑 */
:deep(.n-input) {
  background-color: rgba(0, 0, 0, 0.3) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}
:deep(.n-input__input-el) { color: white !important; }
</style>