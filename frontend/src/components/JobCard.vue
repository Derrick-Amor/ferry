<script setup>
defineProps({ job: Object })
</script>
<template>
  <router-link :to="`/job/${job.id}`" class="specimen">
    <h3 class="name">{{ job.jobName }}</h3>
    <p class="company">{{ job.companyName || '—' }}</p>
    <p class="salary">{{ job.salaryMin }}–{{ job.salaryMax }}K</p>
    <div class="meta">
      <span>{{ job.city || '不限' }}</span>
      <span>{{ job.education || '不限' }}</span>
      <span>{{ job.experience || '不限' }}</span>
    </div>
  </router-link>
</template>

<style scoped>
.specimen {
  display: flex; flex-direction: column; gap: 8px;
  padding: 16px; border-left: 2px solid var(--fern);
  background: var(--moss); border-radius: 0 var(--radius-card) var(--radius-card) 0;
  transition: border-color .18s, transform .18s;
}
.specimen:hover { border-left-color: var(--firefly); transform: translateY(-2px); }
.name { font-size: 16px; }
.company { color: var(--text-dim); font-size: 13px; }
.salary { color: var(--lichen); font-weight: 700; font-size: 16px; }
.meta { display: flex; gap: 12px; color: var(--text-faint); font-size: 13px; }
</style>
