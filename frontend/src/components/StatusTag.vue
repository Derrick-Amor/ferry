<script setup>
import { computed } from 'vue'
import { statusMeta } from '@/utils/status'
const props = defineProps({ status: [Number, String], type: { type: String, default: 'course' } })
const meta = computed(() => statusMeta(props.type, props.status))
</script>
<template>
  <span class="tag" :class="`tag--${meta.tone}`">{{ meta.text }}</span>
</template>
