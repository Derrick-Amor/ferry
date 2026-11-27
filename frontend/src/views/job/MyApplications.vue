<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { myApplications } from '@/api/job'
import StatusTag from '@/components/StatusTag.vue'

const router = useRouter()
const list = ref([])
const loading = ref(true)

onMounted(async () => {
  list.value = await myApplications()
  loading.value = false
})

const STEPS = ['已投递', '已查看', '面试邀请', '已录用']

const stats = computed(() => {
  const total = list.value.length
  const invites = list.value.filter(a => a.applyStatus === 2).length
  const offers = list.value.filter(a => a.applyStatus === 3).length
  const scores = list.value.filter(a => a.interviewScore != null).map(a => Number(a.interviewScore))
  const avg = scores.length ? Math.round(scores.reduce((s, x) => s + x, 0) / scores.length) : null
  return { total, invites, offers, avg }
})

function stepCls(a, i) {
  if (a.applyStatus === 4) {
    if (i <= 1) return 'done'
    if (i === 2) return 'rejected'
    return ''
  }
  return i <= a.applyStatus ? 'done' : ''
}

function goDetail(id) { if (id) router.push(`/job/${id}`) }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '—' }
function salary(j) { return j.salaryMin != null && j.salaryMax != null ? `${j.salaryMin}–${j.salaryMax}K` : '面议' }
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">我的投递</h1>
    <p class="sub">追踪每一次投递的进展，不错过任何一个面试机会。</p>

    <!-- 统计概览 -->
    <div class="stats">
      <div class="stat-card">
        <p class="stat-num">{{ stats.total }}</p>
        <p class="stat-label">累计投递</p>
      </div>
      <div class="stat-card">
        <p class="stat-num accent">{{ stats.invites }}</p>
        <p class="stat-label">面试邀请</p>
      </div>
      <div class="stat-card">
        <p class="stat-num good">{{ stats.offers }}</p>
        <p class="stat-label">已录用</p>
      </div>
      <div class="stat-card">
        <p class="stat-num warm">{{ stats.avg != null ? stats.avg : '—' }}</p>
        <p class="stat-label">AI 面试均分</p>
      </div>
    </div>

    <div v-if="loading" class="empty">加载中…</div>

    <template v-else-if="list.length">
      <article v-for="a in list" :key="a.applyId" class="app-card">
        <div class="card-top">
          <div class="card-main">
            <h3 class="job-name">{{ a.jobName || '已下架职位' }}</h3>
            <p class="meta">
              <span>{{ a.companyName || '未知公司' }}</span>
              <span class="dot-sep">·</span>
              <span>{{ a.city || '城市不限' }}</span>
              <span class="dot-sep">·</span>
              <span>{{ a.jobCategory || '类别不限' }}</span>
            </p>
          </div>
          <div class="card-right">
            <span class="salary">{{ salary(a) }}</span>
            <StatusTag :status="a.applyStatus" type="jobApply" />
          </div>
        </div>

        <!-- 投递进度步骤条 -->
        <ol class="steps">
          <li v-for="(s, i) in STEPS" :key="s" :class="stepCls(a, i)">
            <span class="dot"></span>
            <span class="step-label">{{ s }}</span>
          </li>
        </ol>
        <p v-if="a.applyStatus === 4" class="rejected-hint">很遗憾，该职位暂未通过，继续投递下一个目标吧。</p>
        <p v-else-if="a.applyStatus === 3" class="offer-hint">恭喜！你已被录用，请留意企业方的后续通知。</p>

        <div class="card-foot">
          <div class="interview">
            <span class="foot-label">AI 面试</span>
            <template v-if="a.interviewStatus != null">
              <StatusTag :status="a.interviewStatus" type="interview" />
              <span v-if="a.interviewScore != null" class="score">{{ a.interviewScore }} 分</span>
            </template>
            <span v-else class="text-muted">未参加</span>
          </div>
          <div class="spacer"></div>
          <span class="time">{{ fmt(a.applyTime) }}</span>
          <a v-if="a.jobId" class="link" @click="goDetail(a.jobId)">查看职位 →</a>
        </div>
      </article>
    </template>

    <div v-else class="empty">
      <p>你还没有投递过职位</p>
      <router-link to="/job"><button class="btn btn-primary" style="margin-top:16px">去逛一逛职位</button></router-link>
    </div>
  </div>
</template>

<style scoped>
.sub { color: var(--text-dim); margin-top: -10px; margin-bottom: 24px; }

.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 28px; }
.stat-card {
  padding: 18px 20px; background: var(--moss); border: 1px solid var(--fern);
  border-radius: var(--radius-card);
}
.stat-num { font-family: "Noto Serif SC", "Songti SC", serif; font-size: 2.1rem; font-weight: 600; color: var(--text); line-height: 1.1; }
.stat-num.accent { color: var(--firefly-soft); }
.stat-num.good { color: var(--lichen); }
.stat-num.warm { color: #d97706; }
.stat-label { color: var(--text-dim); font-size: 13px; margin-top: 6px; }

.app-card {
  padding: 20px; background: var(--moss); border: 1px solid var(--fern);
  border-left: 3px solid var(--fern); border-radius: var(--radius-card);
  margin-bottom: 16px; transition: border-color .18s, transform .18s;
}
.app-card:hover { border-left-color: var(--firefly); transform: translateY(-2px); }

.card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.card-main { flex: 1; }
.job-name { font-size: 1.25rem; font-weight: 600; }
.meta { color: var(--text-dim); font-size: 13px; margin-top: 4px; }
.dot-sep { color: var(--text-faint); margin: 0 2px; }
.card-right { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.salary { color: var(--lichen); font-weight: 700; font-size: 1.1rem; }

.steps { display: flex; list-style: none; margin: 20px 0 8px; }
.steps li { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 7px; position: relative; }
.steps li:not(:last-child)::after {
  content: ''; position: absolute; top: 7px; left: 50%; width: 100%; height: 2px; background: var(--fern); z-index: 0;
}
.steps li.done:not(:last-child)::after { background: var(--lichen); }
.steps .dot {
  width: 14px; height: 14px; border-radius: 50%; background: var(--moss);
  border: 2px solid var(--fern); position: relative; z-index: 1;
}
.steps li.done .dot { border-color: var(--lichen); background: var(--lichen); box-shadow: 0 0 10px var(--lichen); }
.steps li.rejected .dot { border-color: #dc2626; background: #dc2626; }
.steps .step-label { font-size: 12px; color: var(--text-faint); }
.steps li.done .step-label { color: var(--lichen); }
.steps li.rejected .step-label { color: #dc2626; }

.rejected-hint { color: #b91c1c; font-size: 13px; margin-top: 6px; }
.offer-hint { color: var(--lichen); font-size: 13px; margin-top: 6px; }

.card-foot { display: flex; align-items: center; gap: 14px; margin-top: 14px; padding-top: 14px; border-top: 1px dashed var(--fern); }
.interview { display: flex; align-items: center; gap: 8px; }
.foot-label { color: var(--text-faint); font-size: 13px; }
.score { color: var(--firefly-soft); font-weight: 600; }
.spacer { flex: 1; }
.time { color: var(--text-faint); font-size: 12px; }
.link { color: var(--firefly-soft); cursor: pointer; font-size: 14px; }
.link:hover { text-decoration: underline; }

@media (max-width: 900px) { .stats { grid-template-columns: 1fr 1fr; } }
@media (max-width: 560px) {
  .stats { grid-template-columns: 1fr 1fr; }
  .card-top { flex-direction: column; }
  .card-right { flex-direction: row; align-items: center; }
  .steps .step-label { display: none; }
}
</style>
