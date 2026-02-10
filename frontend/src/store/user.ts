import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
    const username = ref(localStorage.getItem('username') || '')

    function setUsername(name: string) {
        username.value = name
        localStorage.setItem('username', name)
    }

    function logout() {
        username.value = ''
        localStorage.removeItem('username')
    }

    return { username, setUsername, logout }
})