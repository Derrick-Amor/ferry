<script setup>
import { ref, reactive, onMounted } from 'vue'
import { pending, audited, audit } from '@/api/apply'
import StatusTag from '@/components/StatusTag.vue'
import Pagination from '@/components/Pagination.vue'
import { APPLY_TYPE_TEXT } from '@/utils/status'
import { toast } from '@/utils/toast'

const tab = ref('pending')
const data = ref({ list: [], total: 0 })
const q = reactive({ page: 1, size: 10, status: '' })
const comment = reactive({})

async function load() {
  if (tab.value === 'pending') {
    data.value = await pending({ page: q.page, size: q.size })
  } else {
    const p = { page: q.page, size: q.size }
    if (q.status !== '') p.status = q.status
    data.value = await audited(p)
  }
}
onMounted(load)
function switchTab(t) { tab.value = t; q.page = 1; load() }
function change(page) { q.page = page; load() }

async function doAudit(a, pass) {
  await audit({ id: a.id, pass, auditComment: comment[a.id] || '' })
  toast(pass ? '已通过' : '已驳回', 'success')
  load()
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '—' }
</script>

<template>
  <div>
    <h1 class="font-display page-title">申请审核</h1>

    <div class="flex" style="margin-bottom:18px">
      <button class="btn btn-sm" :class="tab === 'pending' ? 'btn-primary' : 'btn-ghost'" @click="switchTab('pending')">待审核</button>
      <button class="btn btn-sm" :class="tab === 'audited' ? 'btn-primary' : 'btn-ghost'" @click="switchTab('audited')">已处理</button>
      <select v-if="tab === 'audited'" class="select" style="width:140px" v-model="q.status" @change="q.page=1; load()">
        <option value="">全部</option><option :value="1">已通过</option><option :value="2">已驳回</option>
      </select>
    </div>

    <div v-if="data.list.length">
      <table class="table">
        <thead>
          <tr><th>类型</th><th>申请理由</th><th>状态</th><th>申请时间</th><th v-if="tab === 'pending'">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in data.list" :key="a.id">
            <td><span class="tag tag--violet">{{ APPLY_TYPE_TEXT[a.applyType] || '申请' }}</span></td>
            <td>{{ a.reason || '—' }}</td>
            <td><StatusTag :status="a.status" type="apply" /></td>
            <td>{{ fmt(a.createTime) }}</td>
            <td v-if="tab === 'pending'">
              <div class="flex wrap">
                <input class="input" style="width:180px" v-model="comment[a.id]" placeholder="审核意见(可选)" />
                <button class="btn btn-primary btn-sm" @click="doAudit(a, true)">通过</button>
                <button class="btn btn-danger btn-sm" @click="doAudit(a, false)">驳回</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <Pagination :total="data.total" :page="q.page" :size="q.size" @change="change" />
    </div>
    <div v-else class="empty">暂无申请记录</div>
  </div>
</template>
