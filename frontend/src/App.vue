<script setup lang="ts">
import { ref, computed, h, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { 
  darkTheme, 
  useOsTheme, 
  NConfigProvider, 
  NMessageProvider, 
  NGlobalStyle,
  NLayout,
  NLayoutHeader,
  NLayoutContent,
  NButton,
  NAvatar,
  NDropdown,
  NIcon,
  NSpace,
  NDivider,
  type GlobalTheme
} from 'naive-ui'
import { 
  SunnyOutline, 
  MoonOutline, 
  DesktopOutline, 
  LogInOutline,
  LogOutOutline,
  ColorPaletteOutline
} from '@vicons/ionicons5'
import { useUserStore } from './store/user'

const router = useRouter()
const userStore = useUserStore()
const osTheme = useOsTheme()

// ================= 主题管理逻辑 =================
type ThemeMode = 'light' | 'dark' | 'system'

// 从本地存储读取主题偏好，默认为 'system'
const themeMode = ref<ThemeMode>((localStorage.getItem('themeMode') as ThemeMode) || 'system')

// 计算当前实际应用的主题 (Naive UI 的 darkTheme 或 null)
const activeTheme = computed<GlobalTheme | null>(() => {
  if (themeMode.value === 'system') {
    return osTheme.value === 'dark' ? darkTheme : null
  }
  return themeMode.value === 'dark' ? darkTheme : null
})

// 渲染图标辅助函数
const renderIcon = (icon: any) => () => h(NIcon, null, { default: () => h(icon) })

// 风格下拉菜单选项
const themeOptions = [
  { label: '浅色', key: 'light', icon: renderIcon(SunnyOutline) },
  { label: '深色', key: 'dark', icon: renderIcon(MoonOutline) },
  { label: '跟随系统', key: 'system', icon: renderIcon(DesktopOutline) }
]

const handleThemeSelect = (key: ThemeMode) => {
  themeMode.value = key
  localStorage.setItem('themeMode', key)
}

// ================= 用户交互逻辑 =================
// 简单的字符串转颜色算法（给头像生成背景色）
const stringToColor = (str: string) => {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  const c = (hash & 0x00ffffff).toString(16).toUpperCase()
  return '#' + '00000'.substring(0, 6 - c.length) + c
}

const handleLogin = () => {
  router.push('/login')
}

const handleLogout = () => {
  userStore.logout()
  localStorage.removeItem('token')
  router.push('/login')
}

const goHome = () => {
  router.push('/')
}
</script>

<template>
  <n-config-provider :theme="activeTheme">
    <n-global-style />
    <n-message-provider>
      <n-layout class="app-layout" position="absolute">
        
        <n-layout-header bordered class="nav-header">
          <div class="nav-content">
            <div class="brand" @click="goHome">
              <span class="logo-text">MusicGraph</span>
            </div>

            <div class="controls">
              <n-dropdown trigger="hover" :options="themeOptions" @select="handleThemeSelect">
                <n-button quaternary circle size="medium">
                  <template #icon>
                    <n-icon>
                      <ColorPaletteOutline />
                    </n-icon>
                  </template>
                </n-button>
              </n-dropdown>

              <n-divider vertical />

              <template v-if="userStore.username">
                <div class="user-profile">
                  <n-avatar 
                    round 
                    size="small" 
                    :style="{ backgroundColor: stringToColor(userStore.username), color: '#fff' }"
                  >
                    {{ userStore.username[0]?.toUpperCase() }}
                  </n-avatar>
                  <span class="username-text">{{ userStore.username }}</span>
                </div>
                
                <n-button quaternary size="small" @click="handleLogout">
                  <template #icon>
                    <n-icon><LogOutOutline /></n-icon>
                  </template>
                  登出
                </n-button>
              </template>

              <template v-else>
                <n-button type="primary" size="small" @click="handleLogin">
                  <template #icon>
                    <n-icon><LogInOutline /></n-icon>
                  </template>
                  登录
                </n-button>
              </template>
            </div>
          </div>
        </n-layout-header>

        <n-layout-content class="main-content" :native-scrollbar="false">
          <router-view />
        </n-layout-content>

      </n-layout>
    </n-message-provider>
  </n-config-provider>
</template>

<style scoped>
.app-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.nav-header {
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: center; /* 内容居中 */
  background-color: var(--n-color); /* 跟随主题背景 */
  transition: background-color 0.3s;
  z-index: 100;
}

.nav-content {
  width: 100%;
  max-width: 1400px; /* 限制最大宽度，像 Gemini 一样居中 */
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.brand {
  cursor: pointer;
  font-size: 1.5rem;
  font-weight: 700;
  background: linear-gradient(90deg, #1db954, #1ed760);
  -webkit-background-clip: text;
  color: transparent;
  user-select: none;
}

.controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 20px;
  background-color: rgba(128, 128, 128, 0.1);
}

.username-text {
  font-weight: 500;
  font-size: 0.9rem;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  flex: 1;
  position: relative;
}
</style>

<style>
/* 全局样式覆盖，确保背景色跟随主题 */
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  width: 100%;
  /* 移除之前的硬编码背景色，改用 Naive UI 注入的变量 */
  background-color: var(--n-body-color);
  color: var(--n-text-color);
  transition: background-color 0.3s, color 0.3s;
}
</style>