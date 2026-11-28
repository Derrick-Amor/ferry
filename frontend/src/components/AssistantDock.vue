<script setup>
import { computed, ref } from 'vue'
import AssistantChat from '@/components/AssistantChat.vue'

const props = defineProps({
  // 问答类型：tutor 学习答疑 / job 岗位咨询
  scope: { type: String, default: 'tutor' },
  // 场景提示：课程名、职位名；面板副标题里会带出来，让人知道在问什么范围
  context: { type: String, default: '' }
})

const open = ref(false)

// 副标题把「在什么范围里问」讲清楚：答疑带课程名，岗位咨询带职位名
const subtitle = computed(() => {
  const label = props.scope === 'job' ? '岗位咨询' : '学习答疑'
  const ctx = (props.context || '').trim()
  return ctx ? `${label} · ${ctx}` : label
})
</script>

<template>
  <div class="dock">
    <transition name="pop">
      <section v-if="open" class="sheet panel">
        <header class="sheet-head">
          <span class="sheet-title font-display">问渡口</span>
          <span class="sheet-sub" :title="subtitle">{{ subtitle }}</span>
          <button class="close" aria-label="收起" @click="open = false">×</button>
        </header>
        <div class="sheet-body">
          <AssistantChat :scope="scope" :context="context" compact />
        </div>
      </section>
    </transition>

    <button class="fab" :class="{ on: open }" @click="open = !open">
      <span class="fab-mark font-display">{{ open ? '×' : '问' }}</span>
    </button>
  </div>
</template>

<style scoped>
.dock { position: fixed; right: 26px; bottom: 26px; z-index: 60; }

.fab {
  width: 54px; height: 54px; border-radius: 50%;
  border: 1px solid var(--firefly); background: var(--firefly); color: #fff;
  cursor: pointer; box-shadow: 0 12px 28px var(--glow);
  transition: transform .18s, box-shadow .18s;
}
.fab:hover { transform: translateY(-2px); box-shadow: 0 16px 34px var(--glow); }
.fab.on { background: var(--moss); color: var(--firefly-soft); }
.fab-mark { font-size: 20px; line-height: 1; }

.sheet {
  position: absolute; right: 0; bottom: 68px;
  width: 380px; height: min(66vh, 580px);
  display: flex; flex-direction: column; padding: 14px 16px;
  box-shadow: var(--shadow);
}
.sheet-head { display: flex; align-items: baseline; gap: 8px; padding-bottom: 8px; border-bottom: 1px solid var(--fern); }
.sheet-title { font-size: 1.05rem; }
.sheet-sub { flex: 1; color: var(--text-faint); font-size: 11.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.close { border: none; background: transparent; color: var(--text-faint); font-size: 20px; line-height: 1; cursor: pointer; }
.close:hover { color: var(--text); }
.sheet-body { flex: 1; min-height: 0; padding-top: 8px; display: flex; }
.sheet-body > * { flex: 1; min-height: 0; }

.pop-enter-active, .pop-leave-active { transition: opacity .16s, transform .16s; }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translateY(8px); }

@media (max-width: 520px) {
  .dock { right: 14px; bottom: 14px; }
  .sheet { width: calc(100vw - 28px); height: 72vh; }
}
</style>
