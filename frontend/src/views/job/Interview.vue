<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { interviewReport, interviewSubmit } from '@/api/job'
import { toast } from '@/utils/toast'

const route = useRoute(), router = useRouter()
const report = ref(null)
const loading = ref(true)

// 面试题目由前端给出（后端按「已答 / 总数」计分）
const questions = [
  '请做一个简短的自我介绍。',
  '你最有代表性的一个项目是什么？在其中你承担了什么角色？',
  '遇到技术难题时，你通常如何定位和解决？',
  '你如何看待持续学习与职业成长？',
  '你为什么想加入我们？'
]
const answers = ref(questions.map(() => ''))

onMounted(async () => {
  try {
    report.value = await interviewReport(route.params.id)
    if (report.value.status === 1) {
      loading.value = false
    } else {
      loading.value = false
    }
  } catch (e) {
    loading.value = false
  }
})

async function submit() {
  await interviewSubmit({ interviewId: Number(route.params.id), answers: answers.value })
  report.value = await interviewReport(route.params.id)
  toast('已提交', 'success')
}
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">AI 面试</h1>

    <div v-if="report && report.status === 1" class="panel result">
      <h2 class="font-display">面试结果</h2>
      <p class="score">得分 <strong>{{ report.score }}</strong> / 100</p>
      <p class="text-muted">你的回答已记录，招聘方将据此评估。</p>
      <button class="btn btn-ghost mt" @click="router.push('/job')">返回职位列表</button>
    </div>

    <div v-else class="panel" style="max-width:760px">
      <p class="text-muted" style="margin-bottom:20px">请如实作答，共 {{ questions.length }} 题。</p>
      <div v-for="(q, i) in questions" :key="i" class="field">
        <label>{{ i + 1 }}. {{ q }}</label>
        <textarea class="textarea" v-model="answers[i]"></textarea>
      </div>
      <button class="btn btn-primary" @click="submit">提交回答</button>
    </div>
  </div>
</template>

<style scoped>
.result { max-width: 560px; text-align: center; padding: 40px; }
.score { font-size: 1.3rem; margin: 16px 0; }
.score strong { color: var(--firefly-soft); font-size: 2.4rem; }
</style>
