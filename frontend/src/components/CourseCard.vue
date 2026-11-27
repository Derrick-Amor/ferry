<script setup>
import { COURSE_TYPE_TEXT } from '@/utils/status'
defineProps({ course: Object })
</script>
<template>
  <router-link :to="`/course/${course.id}`" class="specimen">
    <div class="plate" :style="course.coverImage ? { backgroundImage: `url(${course.coverImage})` } : {}">
      <span class="tag tag--violet">{{ COURSE_TYPE_TEXT[course.courseType] || '课程' }}</span>
    </div>
    <div class="body">
      <h3 class="name">{{ course.courseName }}</h3>
      <p class="meta">{{ course.teacherName || '—' }} · {{ course.courseLevel || '不限' }}</p>
      <p class="price">{{ course.isFree ? '免费' : '¥' + course.price }}</p>
    </div>
  </router-link>
</template>

<style scoped>
.specimen {
  display: flex; flex-direction: column; gap: 12px;
  padding: 14px; border-left: 2px solid var(--fern);
  background: var(--moss); border-radius: 0 var(--radius-card) var(--radius-card) 0;
  transition: border-color .18s, transform .18s;
}
.specimen:hover { border-left-color: var(--firefly); transform: translateY(-2px); }
.plate { height: 140px; border-radius: var(--radius); background: linear-gradient(135deg, #d7e7db, #bfd6c5) center/cover; position: relative; }
.plate .tag { position: absolute; top: 10px; left: 10px; }
.name { font-size: 16px; line-height: 1.4; }
.meta { font-size: 13px; color: var(--text-dim); }
.price { color: var(--firefly-soft); font-weight: 600; }
</style>
