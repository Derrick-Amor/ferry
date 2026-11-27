<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { list, pay, cancel } from '@/api/order'
import StatusTag from '@/components/StatusTag.vue'
import Pagination from '@/components/Pagination.vue'
import { toast } from '@/utils/toast'

const router = useRouter()
const data = ref({ list: [], total: 0 })
const q = reactive({ page: 1, size: 10, status: '' })
async function load() {
  const p = { page: q.page, size: q.size }
  if (q.status !== '') p.status = q.status
  data.value = await list(p)
}
onMounted(load)
function change(page) { q.page = page; load() }

async function onPay(o) { await pay(o.id); toast('支付成功', 'success'); load() }
async function onCancel(o) { await cancel(o.id); toast('已取消', 'success'); load() }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '—' }
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">我的订单</h1>

    <div class="flex wrap" style="margin-bottom:18px">
      <select class="select" style="width:160px" v-model="q.status" @change="q.page=1; load()">
        <option value="">全部状态</option>
        <option :value="0">待支付</option><option :value="1">已支付</option>
        <option :value="2">已取消</option><option :value="3">已退款</option>
      </select>
    </div>

    <div v-if="data.list.length">
      <table class="table">
        <thead>
          <tr><th>订单号</th><th>金额</th><th>状态</th><th>创建时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="o in data.list" :key="o.id">
            <td @click="router.push(`/order/${o.id}`)" style="cursor:pointer; color:var(--firefly-soft)">{{ o.orderNo }}</td>
            <td>¥{{ o.totalAmount }}</td>
            <td><StatusTag :status="o.status" type="order" /></td>
            <td>{{ fmt(o.createTime) }}</td>
            <td>
              <div class="flex">
                <button class="btn btn-ghost btn-sm" @click="router.push(`/order/${o.id}`)">详情</button>
                <button v-if="o.status === 0" class="btn btn-primary btn-sm" @click="onPay(o)">支付</button>
                <button v-if="o.status === 0" class="btn btn-danger btn-sm" @click="onCancel(o)">取消</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <Pagination :total="data.total" :page="q.page" :size="q.size" @change="change" />
    </div>
    <div v-else class="empty">还没有订单</div>
  </div>
</template>
