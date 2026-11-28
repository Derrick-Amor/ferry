<script setup>
import { ref, reactive, onMounted } from 'vue'
import { list } from '@/api/job'
import JobCard from '@/components/JobCard.vue'
import Pagination from '@/components/Pagination.vue'
import AssistantDock from '@/components/AssistantDock.vue'
import { JOB_CATEGORIES } from '@/utils/options'
const data = ref({ list: [], total: 0 })
const q = reactive({ page: 1, size: 9, jobCategory: '', keyword: '' })
async function load() {
  const p = { ...q }
  Object.keys(p).forEach(k => { if (p[k] === '' || p[k] == null) delete p[k] })
  data.value = await list(p)
}
onMounted(load)
function change(page) { q.page = page; load() }
function setCategory(v) { q.jobCategory = v; q.page = 1; load() }
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">职位中心</h1>

    <div class="filter">
      <input class="input" v-model="q.keyword" placeholder="搜索职位或公司" @keyup.enter="q.page=1; load()" />
    </div>

    <div class="chips">
      <button class="chip" :class="{ active: q.jobCategory === '' }" @click="setCategory('')">全部类别</button>
      <button v-for="c in JOB_CATEGORIES" :key="c" class="chip" :class="{ active: q.jobCategory === c }" @click="setCategory(c)">{{ c }}</button>
    </div>

    <div class="grid grid-3 mt" v-if="data.list.length">
      <JobCard v-for="j in data.list" :key="j.id" :job="j" />
    </div>
    <div v-else class="empty">暂无职位，换个条件试试</div>

    <Pagination :total="data.total" :page="q.page" :size="q.size" @change="change" />

    <!-- 职位页的浮标走岗位咨询：只按系统里在架的职位记录回答 -->
    <AssistantDock scope="job" />
  </div>
</template>

<style scoped>
.filter { display: flex; gap: 12px; flex-wrap: wrap; }
.filter .input { flex: 1 1 260px; }
.chips { margin-top: 12px; }
</style>
