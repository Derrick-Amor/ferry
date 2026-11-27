<script setup>
import { ref, reactive, onMounted } from 'vue'
import { my } from '@/api/apply'
import StatusTag from '@/components/StatusTag.vue'
import Pagination from '@/components/Pagination.vue'
import { APPLY_TYPE_TEXT } from '@/utils/status'

const data = ref({ list: [], total: 0 })
const q = reactive({ page: 1, size: 10 })
async function load() { data.value = await my({ page: q.page, size: q.size }) }
onMounted(load)
function change(page) { q.page = page; load() }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '—' }
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">我的申请</h1>

    <div v-if="data.list.length">
      <table class="table">
        <thead>
          <tr><th>类型</th><th>申请理由</th><th>状态</th><th>审核意见</th><th>申请时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in data.list" :key="a.id">
            <td><span class="tag tag--violet">{{ APPLY_TYPE_TEXT[a.applyType] || '申请' }}</span></td>
            <td>{{ a.reason || '—' }}</td>
            <td><StatusTag :status="a.status" type="apply" /></td>
            <td>{{ a.auditComment || '—' }}</td>
            <td>{{ fmt(a.createTime) }}</td>
          </tr>
        </tbody>
      </table>
      <Pagination :total="data.total" :page="q.page" :size="q.size" @change="change" />
    </div>
    <div v-else class="empty">你还没有提交过申请</div>
  </div>
</template>
