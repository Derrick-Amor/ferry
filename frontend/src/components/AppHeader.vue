<script setup>
import { useAuthStore } from '@/store/auth'
import { useRouter } from 'vue-router'
import { ROLE_TEXT } from '@/utils/status'

const auth = useAuthStore()
const router = useRouter()
async function onLogout() { await auth.logout(); router.push('/') }
</script>

<template>
  <header class="header">
    <div class="container header-inner">
      <router-link to="/" class="logo">渡口<span class="dot">·</span>Ferry</router-link>
      <nav class="nav">
        <router-link to="/">首页</router-link>
        <router-link to="/course">课程</router-link>
        <router-link to="/job">职位</router-link>
        <router-link to="/assistant">问渡口</router-link>
        <template v-if="auth.role === 0">
          <router-link to="/user/my-course">我的课程</router-link>
          <router-link to="/job/my-applications">我的投递</router-link>
          <router-link to="/cart">购物车</router-link>
          <router-link to="/order">我的订单</router-link>
        </template>
        <template v-if="auth.role === 1">
          <router-link to="/course/apply">申请课程</router-link>
          <router-link to="/job/apply">申请职位</router-link>
          <router-link to="/job/applicants">候选人</router-link>
        </template>
        <router-link v-if="auth.isAdmin" to="/admin">后台管理</router-link>
      </nav>
      <div class="user-area">
        <template v-if="auth.isLogin">
          <router-link to="/user" class="user">{{ auth.userInfo?.nickname || auth.userInfo?.username }}</router-link>
          <span v-if="auth.role != null" class="tag tag--violet">{{ ROLE_TEXT[auth.role] }}</span>
          <button class="btn btn-ghost btn-sm" @click="onLogout">退出</button>
        </template>
        <template v-else>
          <router-link to="/login"><button class="btn btn-ghost btn-sm">登录</button></router-link>
          <router-link to="/register"><button class="btn btn-primary btn-sm">注册</button></router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header { position: sticky; top: 0; z-index: 50; background: rgba(255,255,255,.78); backdrop-filter: blur(12px); border-bottom: 1px solid var(--fern); }
.header-inner { display: flex; align-items: center; justify-content: space-between; padding: 14px 0; }
.logo { font-family: "Noto Serif SC", "Songti SC", serif; font-size: 19px; font-weight: 600; color: var(--text); }
.logo .dot { color: var(--firefly); }
.nav { display: flex; gap: 24px; }
.nav a { color: var(--text-dim); font-size: 15px; }
.nav a:hover { color: var(--firefly-soft); }
.nav a.router-link-active { color: var(--firefly-soft); }
.user-area { display: flex; align-items: center; gap: 12px; }
.user { color: var(--text); }
@media (max-width: 760px) { .nav { display: none; } }
</style>
