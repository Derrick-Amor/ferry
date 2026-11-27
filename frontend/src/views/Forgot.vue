<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { forgotPassword } from '@/api/user'
import { toast } from '@/utils/toast'
const router = useRouter()
const form = reactive({ phone: '', newPassword: '' })
function validate() {
  if (!form.phone) return '手机号不能为空'
  if (!/^1[0-9]{10}$/.test(form.phone)) return '手机号必须为11位数字'
  if (!form.newPassword) return '新密码不能为空'
  if (form.newPassword.length < 6 || form.newPassword.length > 20) return '密码长度需在6-20个字符之间'
  return ''
}
async function submit() {
  const msg = validate()
  if (msg) return toast(msg, 'error')
  await forgotPassword(form)
  toast('密码已重置，请登录', 'success')
  router.push('/login')
}
</script>
<template>
  <div class="auth-wrap">
    <div class="panel auth-card">
      <h1 class="font-display auth-title">找回密码</h1>
      <p class="text-muted" style="margin-bottom:16px">通过注册手机号直接重置密码</p>
      <div class="field"><label>手机号</label><input class="input" v-model="form.phone" /></div>
      <div class="field"><label>新密码</label><input class="input" type="password" v-model="form.newPassword" /></div>
      <button class="btn btn-primary" style="width:100%" @click="submit">重置密码</button>
      <p class="text-muted mt"><router-link to="/login">返回登录</router-link></p>
    </div>
  </div>
</template>
<style scoped>
.auth-wrap { min-height: 80vh; display: flex; align-items: center; justify-content: center; }
.auth-card { width: 380px; }
.auth-title { font-size: 1.7rem; margin-bottom: 22px; }
</style>
