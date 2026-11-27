<script setup>
import { ref, reactive, onMounted } from 'vue'
import { userList, userStatus } from '@/api/admin'
import StatusTag from '@/components/StatusTag.vue'
import Pagination from '@/components/Pagination.vue'
import { ROLE_TEXT } from '@/utils/status'
import { toast } from '@/utils/toast'

const data = ref({ list: [], total: 0 })
const q = reactive({ page: 1, size: 10, keyword: '', role: '', status: '' })
async function load() {
  const p = { page: q.page, size: q.size }
  if (q.keyword) p.keyword = q.keyword
  if (q.role !== '') p.role = q.role
  if (q.status !== '') p.status = q.status
  data.value = await userList(p)
}
onMounted(load)
function change(page) { q.page = page; load() }
async function toggle(u) {
  await userStatus(u.id, u.status === 1 ? 0 : 1)
  toast('已更新状态', 'success')
  load()
}
</script>

<template>
  <div>
    <h1 class="font-display page-title">用户管理</h1>

    <div class="flex wrap" style="margin-bottom:18px">
      <input class="input" style="width:220px" v-model="q.keyword" placeholder="搜索用户名/昵称" @keyup.enter="q.page=1; load()" />
      <select class="select" style="width:140px" v-model="q.role" @change="q.page=1; load()">
        <option value="">全部身份</option><option :value="0">学生</option><option :value="1">讲师/企业</option><option :value="2">管理员</option>
      </select>
      <select class="select" style="width:140px" v-model="q.status" @change="q.page=1; load()">
        <option value="">全部状态</option><option :value="1">正常</option><option :value="0">已禁用</option>
      </select>
    </div>

    <div v-if="data.list.length">
      <table class="table">
        <thead><tr><th>用户名</th><th>昵称</th><th>身份</th><th>手机</th><th>邮箱</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="u in data.list" :key="u.id">
            <td>{{ u.username }}</td>
            <td>{{ u.nickname }}</td>
            <td>{{ ROLE_TEXT[u.role] }}</td>
            <td>{{ u.phone || '—' }}</td>
            <td>{{ u.email || '—' }}</td>
            <td><StatusTag :status="u.status" type="user" /></td>
            <td>
              <button v-if="u.role !== 2" class="btn btn-sm" :class="u.status === 1 ? 'btn-danger' : 'btn-primary'" @click="toggle(u)">
                {{ u.status === 1 ? '禁用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <Pagination :total="data.total" :page="q.page" :size="q.size" @change="change" />
    </div>
    <div v-else class="empty">暂无用户</div>
  </div>
</template>
