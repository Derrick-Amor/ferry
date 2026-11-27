<script setup>
import { toasts } from '@/utils/toast'
</script>
<template>
  <div class="toast-wrap">
    <div v-for="t in toasts" :key="t.id" class="toast" :class="`toast--${t.type}`">{{ t.message }}</div>
  </div>
</template>
<style scoped>
.toast-wrap { position: fixed; top: 20px; left: 50%; transform: translateX(-50%); z-index: 999; display: flex; flex-direction: column; gap: 10px; }
.toast { padding: 10px 20px; border-radius: var(--radius); background: var(--moss); border: 1px solid var(--fern); box-shadow: var(--shadow); font-size: 14px; }
.toast--error { border-color: rgba(220,38,38,.35); color: #dc2626; }
.toast--success { border-color: rgba(22,163,74,.35); color: var(--lichen); }
.toast--info { border-color: rgba(139,92,246,.35); color: var(--firefly-soft); }
</style>
