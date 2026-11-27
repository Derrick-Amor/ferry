<script setup>
import { ref, reactive, onMounted } from 'vue'
import { orderList } from '@/api/admin'
import StatusTag from '@/components/StatusTag.vue'
import Pagination from '@/components/Pagination.vue'

const data = ref({ list: [], total: 0 })
const q = reactive({ page: 1, size: 10, keyword: '', status: '' })
async function load() {
  const p = { page: q.page, size: q.size }
  if (q.keyword) p.keyword = q.keyword
  if (q.status !== '') p.status = q.status
  data.value = await orderList(p)
}
onMounted(load)
function change(page) { q.page = page; load() }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '—' }
</script>

<template>
  <div>
    <h1 class="font-display page-title">订单管理</h1>

    <div class="flex wrap" style="margin-bottom:18px">
      <input class="input" style="width:220px" v-model="q.keyword" placeholder="搜索订单号" @keyup.enter="q.page=1; load()" />
      <select class="select" style="width:140px" v-model="q.status" @change="q.page=1; load()">
        <option value="">全部状态</option>
        <option :value="0">待支付</option><option :value="1">已支付</option>
        <option :value="2">已取消</option><option :value="3">已退款</option>
      </select>
    </div>

    <div v-if="data.list.length">
      <table class="table">
        <thead><tr><th>订单号</th><th>金额</th><th>状态</th><th>支付时间</th><th>创建时间</th></tr></thead>
        <tbody>
          <tr v-for="o in data.list" :key="o.id">
            <td>{{ o.orderNo }}</td>
            <td>¥{{ o.totalAmount }}</td>
            <td><StatusTag :status="o.status" type="order" /></td>
            <td>{{ fmt(o.payTime) }}</td>
            <td>{{ fmt(o.createTime) }}</td>
          </tr>
        </tbody>
      </table>
      <Pagination :total="data.total" :page="q.page" :size="q.size" @change="change" />
    </div>
    <div v-else class="empty">暂无订单</div>
  </div>
</template>
