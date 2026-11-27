<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/user'
import { toast } from '@/utils/toast'
const router = useRouter()
const form = reactive({ username: '', password: '', nickname: '', phone: '', email: '', role: 0 })
function validate() {
  if (!form.username) return '用户名不能为空'
  if (form.username.length < 3 || form.username.length > 20) return '用户名长度需在3-20个字符之间'
  if (!/^[a-zA-Z0-9_]+$/.test(form.username)) return '用户名只能包含字母、数字和下划线'
  if (!form.password) return '密码不能为空'
  if (form.password.length < 6 || form.password.length > 20) return '密码长度需在6-20个字符之间'
  if (form.nickname && form.nickname.length > 20) return '昵称不能超过20个字符'
  if (!form.phone) return '手机号不能为空'
  if (!/^1[0-9]{10}$/.test(form.phone)) return '手机号必须为11位数字'
  if (!form.email) return '邮箱不能为空'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return '邮箱格式不正确'
  return ''
}
async function submit() {
  const msg = validate()
  if (msg) return toast(msg, 'error')
  await register(form)
  toast('注册成功，请登录', 'success')
  router.push('/login')
}
</script>
<template>
  <div class="auth-wrap">
    <div class="panel auth-card">
      <h1 class="font-display auth-title">注册账号</h1>
      <p class="req-tip">带 <span class="req">*</span> 为必填项，其余为选填</p>
      <div class="field"><label>用户名 <span class="req">*</span></label><input class="input" v-model="form.username" /></div>
      <div class="field"><label>密码 <span class="req">*</span></label><input class="input" type="password" v-model="form.password" /></div>
      <div class="field"><label>邮箱 <span class="req">*</span></label><input class="input" v-model="form.email" /></div>
      <div class="field"><label>手机号 <span class="req">*</span></label><input class="input" v-model="form.phone" /></div>
      <div class="field"><label>身份 <span class="req">*</span></label>
        <select class="select" v-model="form.role">
          <option :value="0">学生</option><option :value="1">讲师/企业</option>
        </select>
      </div>
      <div class="field"><label>昵称 <span class="opt">选填</span></label><input class="input" v-model="form.nickname" /></div>
      <button class="btn btn-primary" style="width:100%" @click="submit">注册</button>
      <p class="text-muted mt">已有账号，<router-link to="/login">去登录</router-link></p>
    </div>
  </div>
</template>
<style scoped>
.auth-wrap { min-height: 80vh; display: flex; align-items: center; justify-content: center; }
.auth-card { width: 380px; }
.auth-title { font-size: 1.7rem; margin-bottom: 10px; }
.req-tip { color: var(--text-faint); font-size: 13px; margin-bottom: 16px; }
.req { color: #dc2626; }
.opt { color: var(--text-faint); font-size: 12px; font-weight: 400; }
</style>
