<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { detail } from '@/api/course'
import AssistantChat from '@/components/AssistantChat.vue'

const route = useRoute()

// 两种模式：tutor 学习答疑（纯生成）/ job 岗位咨询（只按在架职位记录回答）
const mode = ref('tutor')
const isJob = computed(() => mode.value === 'job')

// 从课程学习页跳进来时带 ?courseId=。后端不再按课程检索，这一条现在退化成
// 「用户正在学这门课」的背景提示——保留入口，但语义从检索范围变成提示。
const courseId = ref(Number(route.query.courseId) || null)
const courseName = ref('')

// 岗位模式跟课程没关系：背景只在答疑里用得上
const chatContext = computed(() => (isJob.value ? '' : courseName.value))

const eyebrow = computed(() => (isJob.value ? '岗位顾问' : '学习助教'))

const subtitle = computed(() => (isJob.value
  ? '只按系统里在架的职位记录回答。记录里没有的，它会直说没有，不替你猜。'
  : '想到什么问什么。概念、写法、面试怎么答都行，不用先在课程资料里找到出处。'))

onMounted(async () => {
  if (!courseId.value) return
  try {
    const data = await detail(courseId.value)
    courseName.value = data.course?.courseName || ''
  } catch {
    // 课程查不到就当没有背景，不至于整页打不开
    courseId.value = null
  }
})
</script>

<template>
  <div class="container page">
    <header class="head">
      <div class="head-main">
        <p class="eyebrow">{{ eyebrow }}</p>
        <h1 class="font-display title">问渡口</h1>
        <p class="sub">{{ subtitle }}</p>
      </div>
      <div class="head-side">
        <div class="chips">
          <button class="chip" :class="{ active: !isJob }" @click="mode = 'tutor'">答疑</button>
          <button class="chip" :class="{ active: isJob }" @click="mode = 'job'">问岗位</button>
        </div>
        <router-link v-if="courseId" to="/assistant" class="btn btn-ghost btn-sm">不限定课程</router-link>
      </div>
    </header>

    <!-- key 跟着模式走：切模式就重建组件，旧的引用不会跟新的混在一起 -->
    <div class="panel shell">
      <AssistantChat :key="mode" :scope="mode" :context="chatContext" />
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.head-main { min-width: 0; }
.head-side { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.eyebrow { color: var(--text-faint); font-size: 12px; letter-spacing: .18em; text-transform: uppercase; }
.title { font-size: 2rem; line-height: 1.25; margin: 4px 0 6px; }
.sub { color: var(--text-dim); font-size: 14px; max-width: 62ch; }

.shell { padding: 18px 20px; height: calc(100vh - 250px); min-height: 520px; display: flex; }
.shell > * { flex: 1; min-height: 0; }

@media (max-width: 760px) {
  .head { flex-direction: column; align-items: flex-start; }
  .shell { height: calc(100vh - 210px); padding: 14px; }
}
</style>
