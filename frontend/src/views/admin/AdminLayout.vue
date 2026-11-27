<script setup>
import { useAuthStore } from '@/store/auth'
import { useRouter } from 'vue-router'
const auth = useAuthStore()
const router = useRouter()
async function onLogout() { await auth.logout(); router.push('/') }
const links = [
  { to: '/admin/apply', text: '申请审核' },
  { to: '/admin/users', text: '用户管理' },
  { to: '/admin/courses', text: '课程管理' },
  { to: '/admin/jobs', text: '职位管理' },
  { to: '/admin/orders', text: '订单管理' }
]
</script>

<template>
  <div class="admin">
    <aside class="side">
      <router-link to="/" class="logo">智慧教育<span class="dot">·</span>平台</router-link>
      <nav>
        <router-link v-for="l in links" :key="l.to" :to="l.to" class="link">{{ l.text }}</router-link>
      </nav>
      <div class="bottom">
        <p class="user">{{ auth.userInfo?.nickname }}</p>
        <button class="btn btn-ghost btn-sm" @click="onLogout">退出</button>
      </div>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin { display: flex; min-height: 100vh; }
.side { width: 220px; background: var(--moss); border-right: 1px solid var(--fern); padding: 20px 14px; display: flex; flex-direction: column; gap: 20px; position: sticky; top: 0; height: 100vh; }
.logo { font-family: "Noto Serif SC", "Songti SC", serif; font-size: 17px; font-weight: 600; color: var(--text); }
.logo .dot { color: var(--firefly); }
nav { display: flex; flex-direction: column; gap: 6px; flex: 1; }
.link { padding: 10px 14px; border-radius: var(--radius); color: var(--text-dim); font-size: 14px; }
.link:hover { background: rgba(139,92,246,.08); color: var(--text); }
.link.router-link-active { background: rgba(139,92,246,.14); color: var(--firefly-soft); }
.bottom { border-top: 1px solid var(--fern); padding-top: 14px; display: flex; flex-direction: column; gap: 10px; }
.user { color: var(--text); font-size: 14px; }
.main { flex: 1; padding: 28px; }
@media (max-width: 760px) { .side { position: static; width: 100%; height: auto; } .admin { flex-direction: column; } }
</style>
