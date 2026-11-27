<script setup>
import { ref, reactive, onMounted } from 'vue'
import { list } from '@/api/course'
import CourseCard from '@/components/CourseCard.vue'
import Pagination from '@/components/Pagination.vue'
import { COURSE_TYPES, COURSE_LEVELS } from '@/utils/options'
const data = ref({ list: [], total: 0 })
const q = reactive({ page: 1, size: 9, courseType: '', courseLevel: '', isFree: '', keyword: '', sortBy: 'time', order: 'desc' })
async function load() {
  const p = { ...q }
  Object.keys(p).forEach(k => { if (p[k] === '' || p[k] == null) delete p[k] })
  data.value = await list(p)
}
onMounted(load)
function change(page) { q.page = page; load() }
function setType(v) { q.courseType = v; q.page = 1; load() }
function setLevel(v) { q.courseLevel = v; q.page = 1; load() }
function setFree(v) { q.isFree = v; q.page = 1; load() }
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">课程中心</h1>

    <div class="filter">
      <input class="input" v-model="q.keyword" placeholder="搜索课程或讲师" @keyup.enter="q.page=1; load()" />
      <select class="select" v-model="q.sortBy" @change="load()">
        <option value="time">按上新</option><option value="enroll">按报名</option><option value="rating">按评分</option>
      </select>
    </div>

    <div class="chips">
      <button class="chip" :class="{ active: q.courseType === '' }" @click="setType('')">全部类型</button>
      <button v-for="t in COURSE_TYPES" :key="t.value" class="chip" :class="{ active: q.courseType === t.value }" @click="setType(t.value)">{{ t.label }}</button>
    </div>

    <div class="chips">
      <button class="chip" :class="{ active: q.courseLevel === '' }" @click="setLevel('')">全部等级</button>
      <button v-for="l in COURSE_LEVELS" :key="l" class="chip" :class="{ active: q.courseLevel === l }" @click="setLevel(l)">{{ l }}</button>
    </div>

    <div class="chips">
      <button class="chip" :class="{ active: q.isFree === '' }" @click="setFree('')">全部价格</button>
      <button class="chip" :class="{ active: q.isFree === 1 }" @click="setFree(1)">免费</button>
      <button class="chip" :class="{ active: q.isFree === 0 }" @click="setFree(0)">付费</button>
    </div>

    <div class="grid grid-3 mt" v-if="data.list.length">
      <CourseCard v-for="c in data.list" :key="c.id" :course="c" />
    </div>
    <div v-else class="empty">还没有课程，换个条件试试</div>

    <Pagination :total="data.total" :page="q.page" :size="q.size" @change="change" />
  </div>
</template>

<style scoped>
.filter { display: flex; gap: 12px; flex-wrap: wrap; }
.filter .input { flex: 1 1 220px; }
.chips { margin-top: 12px; }
</style>
