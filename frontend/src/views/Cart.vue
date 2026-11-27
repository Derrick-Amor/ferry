<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { list, update, remove } from '@/api/cart'
import { create as createOrder } from '@/api/order'
import { toast } from '@/utils/toast'

const router = useRouter()
const items = ref([])

const checkedItems = computed(() => items.value.filter(i => i.checked))
const total = computed(() => checkedItems.value.reduce((s, i) => s + Number(i.price) * i.quantity, 0).toFixed(2))

async function load() { items.value = await list() }
onMounted(load)

async function toggle(item) {
  await update(item.cartId, { quantity: item.quantity, checked: item.checked ? 1 : 0 })
}
async function changeQty(item, d) {
  item.quantity = Math.max(1, item.quantity + d)
  await update(item.cartId, { quantity: item.quantity, checked: item.checked ? 1 : 0 })
}
async function removeItem(item) {
  await remove(item.cartId)
  toast('已移除', 'success')
  load()
}
async function checkout() {
  if (!checkedItems.value.length) return toast('请先勾选要结算的课程', 'info')
  const id = await createOrder(checkedItems.value.map(i => i.cartId))
  toast('订单已创建', 'success')
  router.push(`/order/${id}`)
}
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">购物车</h1>

    <div v-if="items.length">
      <ul class="cart-list">
        <li v-for="it in items" :key="it.cartId" class="cart-item">
          <!-- 必须用 v-model 双向绑定：:checked 是单向的，用户点击不会回写 it.checked，
               导致「已选 N 件」不更新、checkout 的未勾选校验永远走不到。
               服务端 checked 是 0/1 数字，Vue 默认按 true 比较（looseEqual(1, true) 为 false），
               初始勾选态会丢失，所以要显式指定 true-value / false-value。 -->
          <input type="checkbox" v-model="it.checked" :true-value="1" :false-value="0" @change="toggle(it)" />
          <div class="cover" :style="it.coverImage ? { backgroundImage: `url(${it.coverImage})` } : {}"></div>
          <div class="main">
            <p class="name">{{ it.courseName }}</p>
            <p class="price">¥{{ it.price }}</p>
          </div>
          <div class="qty">
            <button class="btn btn-ghost btn-sm" @click="changeQty(it, -1)">−</button>
            <span>{{ it.quantity }}</span>
            <button class="btn btn-ghost btn-sm" @click="changeQty(it, 1)">+</button>
          </div>
          <button class="btn btn-danger btn-sm" @click="removeItem(it)">移除</button>
        </li>
      </ul>

      <div class="panel checkout">
        <p class="text-muted">已选 {{ checkedItems.length }} 件</p>
        <p class="total">合计 <strong>¥{{ total }}</strong></p>
        <button class="btn btn-primary" @click="checkout">去结算</button>
      </div>
    </div>
    <div v-else class="empty">购物车还是空的，去挑几门课吧</div>
  </div>
</template>

<style scoped>
.cart-list { list-style: none; }
.cart-item { display: flex; align-items: center; gap: 16px; padding: 14px; background: var(--moss); border: 1px solid var(--fern); border-radius: var(--radius-card); margin-bottom: 12px; }
.cover { width: 96px; height: 60px; border-radius: var(--radius); background: linear-gradient(135deg, #d7e7db, #bfd6c5) center/cover; flex-shrink: 0; }
.main { flex: 1; }
.name { font-weight: 600; }
.price { color: var(--firefly-soft); font-weight: 600; }
.qty { display: flex; align-items: center; gap: 10px; }
.checkout { display: flex; align-items: center; gap: 20px; justify-content: flex-end; margin-top: 20px; }
.total strong { color: var(--firefly-soft); font-size: 1.5rem; }
</style>
