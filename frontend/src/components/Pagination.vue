<script setup>
import { computed } from 'vue'
const props = defineProps({ total: Number, page: Number, size: Number })
const emit = defineEmits(['change'])
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
function go(p) { if (p >= 1 && p <= pages.value && p !== props.page) emit('change', p) }
</script>
<template>
  <div class="pagination" v-if="total > 0">
    <button class="btn btn-ghost btn-sm" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
    <span class="info">{{ page }} / {{ pages }}</span>
    <button class="btn btn-ghost btn-sm" :disabled="page >= pages" @click="go(page + 1)">下一页</button>
  </div>
</template>
<style scoped>
.pagination { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 28px; }
.info { color: var(--text-dim); font-size: 14px; }
</style>
