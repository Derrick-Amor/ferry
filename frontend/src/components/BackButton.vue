<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
function goBack() {
  if (history.state && history.state.back) router.back()
  else router.push('/')
}
</script>

<template>
  <div class="container backbar">
    <button class="back-btn" @click="goBack">
      <svg viewBox="0 0 24 24" width="15" height="15">
        <path d="M15 18l-6-6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      返回
    </button>
  </div>
</template>

<style scoped>
.backbar { margin-top: 16px; margin-bottom: -8px; }
.back-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--fern);
  border-radius: 999px;
  background: var(--moss);
  color: var(--text-dim);
  font-size: 13px;
  cursor: pointer;
  transition: .18s;
}
.back-btn:hover { color: var(--firefly-soft); border-color: var(--firefly); }
.back-btn svg { display: block; }
</style>
