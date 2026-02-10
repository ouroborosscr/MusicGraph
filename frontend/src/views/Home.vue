<template>
  <div class="home-container">
    <h1>欢迎来到 MusicGraph</h1>
    <p>当前用户: {{ userStore.username }}</p>
    </div>
</template>

<script setup lang="ts">
import { useUserStore } from '../store/user'
const userStore = useUserStore()
</script>

<style scoped>
.home-container {
  color: white;
  padding: 50px;
  text-align: center;
}
</style>