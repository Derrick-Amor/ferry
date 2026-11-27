<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { toast } from '@/utils/toast'
const auth = useAuthStore()
const router = useRouter()
const form = reactive({ username: '', password: '' })
async function submit() {
  if (!form.username || !form.password) return toast('请输入用户名和密码', 'error')
  await auth.login(form)
  toast('登录成功', 'success')
  router.push('/')
}
</script>
<template>
  <div class="auth-wrap">
    <div class="panel auth-card">
      <h1 class="font-display auth-title">欢迎登录</h1>
      <div class="field"><label>用户名</label><input class="input" v-model="form.username" placeholder="用户名" /></div>
      <div class="field"><label>密码</label><input class="input" type="password" v-model="form.password" placeholder="密码" @keyup.enter="submit" /></div>
      <button class="btn btn-primary" style="width:100%" @click="submit">登录</button>
      <div class="flex between mt">
        <router-link to="/forgot" class="text-muted">忘记密码</router-link>
        <router-link to="/register">注册账号</router-link>
      </div>
    </div>
  </div>
</template>
<style scoped>
.auth-wrap { min-height: 80vh; display: flex; align-items: center; justify-content: center; }
.auth-card { width: 380px; }
.auth-title { font-size: 1.7rem; margin-bottom: 22px; }
</style>
