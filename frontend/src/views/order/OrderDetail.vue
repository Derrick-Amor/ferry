<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { detail, pay, cancel } from '@/api/order'
import StatusTag from '@/components/StatusTag.vue'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const order = ref(null), items = ref([])

async function load() {
  const d = await detail(route.params.id)
  order.value = d.order
  items.value = d.items
}
onMounted(load)

async function onPay() { await pay(order.value.id); toast('支付成功', 'success'); load() }
async function onCancel() { await cancel(order.value.id); toast('已取消', 'success'); load() }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '—' }
</script>

<template>
  <div class="container page" v-if="order">
    <h1 class="font-display page-title">订单详情</h1>

    <div class="panel" style="max-width:760px">
      <div class="flex between">
        <p class="order-no">{{ order.orderNo }}</p>
        <StatusTag :status="order.status" type="order" />
      </div>
      <p class="text-muted">下单时间 {{ fmt(order.createTime) }}</p>
      <p v-if="order.payTime" class="text-muted">支付时间 {{ fmt(order.payTime) }}</p>

      <table class="table mt">
        <thead><tr><th>课程</th><th>单价</th><th>数量</th><th>小计</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="it in items" :key="it.id">
            <td>{{ it.courseName }}</td>
            <td>¥{{ it.price }}</td>
            <td>{{ it.quantity }}</td>
            <td>¥{{ (Number(it.price) * it.quantity).toFixed(2) }}</td>
            <td>
              <button v-if="order.status === 1" class="btn btn-primary btn-sm" @click="router.push(`/course/study/${it.courseId}`)">去学习</button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="flex between mt">
        <p>合计 <strong class="total">¥{{ order.totalAmount }}</strong></p>
        <div class="flex">
          <button v-if="order.status === 0" class="btn btn-primary" @click="onPay">立即支付</button>
          <button v-if="order.status === 0" class="btn btn-danger" @click="onCancel">取消订单</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-no { color: var(--firefly-soft); font-weight: 600; }
.total { color: var(--firefly-soft); font-size: 1.5rem; }
</style>
