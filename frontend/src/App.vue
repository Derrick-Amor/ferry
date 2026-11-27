<script setup>
import AppHeader from '@/components/AppHeader.vue'
import BackButton from '@/components/BackButton.vue'
import Toast from '@/components/Toast.vue'
import { useRoute } from 'vue-router'
import { computed } from 'vue'
const route = useRoute()
const isAdmin = computed(() => route.path.startsWith('/admin'))
// 顶级页面不需要返回按钮，其余子页面统一显示
const TOP_LEVEL = ['/', '/course', '/job', '/login', '/register', '/forgot']
const showBack = computed(() => !isAdmin.value && !TOP_LEVEL.includes(route.path))
</script>

<template>
  <div class="app">
    <AppHeader v-if="!isAdmin" />
    <BackButton v-if="showBack" />
    <router-view />
    <Toast />
  </div>
</template>
