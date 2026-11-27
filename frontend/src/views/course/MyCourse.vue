<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { mine } from '@/api/course'
import { COURSE_TYPE_TEXT } from '@/utils/status'

const router = useRouter()
const courses = ref([])
onMounted(async () => { courses.value = await mine() })
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">我的课程</h1>

    <div v-if="courses.length">
      <ul class="course-list">
        <li v-for="c in courses" :key="c.id" class="course-item">
          <div class="cover" :style="c.coverImage ? { backgroundImage: `url(${c.coverImage})` } : {}"></div>
          <div class="main">
            <p class="name">{{ c.courseName }}</p>
            <p class="meta">{{ c.teacherName || '—' }} · {{ c.courseLevel || '不限' }} · {{ COURSE_TYPE_TEXT[c.courseType] || '课程' }}</p>
          </div>
          <button class="btn btn-primary btn-sm" @click="router.push(`/course/study/${c.id}`)">去学习</button>
        </li>
      </ul>
    </div>
    <div v-else class="empty">还没有已购课程，去挑一门吧</div>
  </div>
</template>

<style scoped>
.course-list { list-style: none; }
.course-item { display: flex; align-items: center; gap: 16px; padding: 14px; background: var(--moss); border: 1px solid var(--fern); border-radius: var(--radius-card); margin-bottom: 12px; }
.cover { width: 110px; height: 66px; border-radius: var(--radius); background: linear-gradient(135deg, #d7e7db, #bfd6c5) center/cover; flex-shrink: 0; }
.main { flex: 1; }
.name { font-weight: 600; }
.meta { color: var(--text-dim); font-size: 13px; margin-top: 4px; }
</style>
