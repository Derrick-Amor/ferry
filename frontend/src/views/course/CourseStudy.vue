<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { study, saveProgress } from '@/api/course'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const data = ref({ course: null, chapters: [], progress: 0, chapterId: null })
const current = ref(null)

// 课程没有真实章节时，用一套示例章节兜底，让学习页饱满一些（负 id 表示演示数据，不写库）
const DEFAULT_CHAPTERS = [
  { id: -1, chapterName: '第1讲 · 课程导学：从这里开始', duration: 12, videoUrl: '' },
  { id: -2, chapterName: '第2讲 · 核心概念与术语梳理', duration: 24, videoUrl: '' },
  { id: -3, chapterName: '第3讲 · 环境准备与工具安装', duration: 18, videoUrl: '' },
  { id: -4, chapterName: '第4讲 · 基础实操：第一个示例', duration: 30, videoUrl: '' },
  { id: -5, chapterName: '第5讲 · 进阶技巧与最佳实践', duration: 26, videoUrl: '' },
  { id: -6, chapterName: '第6讲 · 常见问题与避坑指南', duration: 20, videoUrl: '' },
  { id: -7, chapterName: '第7讲 · 综合案例演练', duration: 35, videoUrl: '' },
  { id: -8, chapterName: '第8讲 · 课程总结与下一步学习', duration: 15, videoUrl: '' }
]

const progressPercent = computed(() => Math.round(Number(data.value.progress || 0)))

onMounted(async () => {
  try {
    data.value = await study(route.params.id)
    if (!data.value.chapters || !data.value.chapters.length) {
      data.value.chapters = DEFAULT_CHAPTERS
    }
    current.value = data.value.chapters[0] || null
  } catch (e) {
    router.replace(`/course/${route.params.id}`)
  }
})

function play(ch) { current.value = ch }

async function markDone() {
  if (!current.value) return
  const idx = data.value.chapters.findIndex(c => c.id === current.value.id)
  const pct = Math.round(((idx + 1) / data.value.chapters.length) * 100)
  if (current.value.id < 0) {
    data.value.progress = pct
    data.value.chapterId = current.value.id
    toast('进度已更新', 'success')
    return
  }
  await saveProgress({ courseId: data.value.course.id, chapterId: current.value.id, progress: pct })
  data.value.progress = pct
  data.value.chapterId = current.value.id
  toast('进度已保存', 'success')
}
</script>

<template>
  <div class="container page">
    <div class="study-grid">
      <main>
        <div class="video" v-if="current">
          <video v-if="current.videoUrl" :src="current.videoUrl" controls style="width:100%; height:100%"></video>
          <div v-else class="video-empty">
            <span class="play-icon">▶</span>
            <p class="empty-title">示例章节 · 视频占位</p>
            <p class="empty-sub">{{ current.chapterName }}</p>
          </div>
        </div>
        <div class="flex between mt">
          <h2 class="font-display">{{ current?.chapterName || '暂无章节' }}</h2>
          <button class="btn btn-primary" @click="markDone">标记完成</button>
        </div>
        <div class="progress-bar mt">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
        <p class="text-muted" style="margin-top:8px">已学 {{ progressPercent }}%</p>
      </main>

      <aside>
        <div class="panel">
          <h3 class="font-display side-title">{{ data.course?.courseName }}</h3>
          <ol class="chapters">
            <li v-for="(ch, i) in data.chapters" :key="ch.id"
                :class="{ active: current?.id === ch.id }" @click="play(ch)">
              <span class="no">{{ i + 1 }}</span>
              <span class="ch-name">{{ ch.chapterName }}</span>
              <span class="ch-dur">{{ ch.duration }}'</span>
            </li>
          </ol>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.study-grid { display: grid; grid-template-columns: 1fr 320px; gap: 30px; }
.video { aspect-ratio: 16 / 9; background: #dfe8e1; border-radius: var(--radius-card); overflow: hidden; }
.video-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; gap: 8px; color: var(--text-faint); }
.play-icon { font-size: 2.4rem; color: var(--firefly-soft); opacity: .8; }
.empty-title { font-size: 1.1rem; color: var(--text-dim); }
.empty-sub { font-size: 13px; }
.progress-bar { height: 8px; border-radius: 999px; background: var(--fern); overflow: hidden; }
.progress-fill { height: 100%; background: var(--firefly); transition: width .3s; }

.side-title { font-size: 1.15rem; margin-bottom: 14px; }
.chapters { list-style: none; }
.chapters li { display: flex; gap: 12px; align-items: center; padding: 10px 6px; border-radius: var(--radius); cursor: pointer; color: var(--text-dim); }
.chapters li:hover { background: rgba(139,92,246,.06); }
.chapters li.active { background: rgba(139,92,246,.12); color: var(--text); }
.chapters .no { color: var(--text-faint); width: 1.4rem; }
.ch-name { flex: 1; }
.ch-dur { color: var(--text-faint); font-size: 12px; }
@media (max-width: 820px) { .study-grid { grid-template-columns: 1fr; } }
</style>
