<script setup>
import { ref, onMounted } from 'vue'
import { list as courseList } from '@/api/course'
import { list as jobList } from '@/api/job'
const courses = ref([]), jobs = ref([])
onMounted(async () => {
  courses.value = (await courseList({ page: 1, size: 5, sortBy: 'enroll', order: 'desc' })).list
  jobs.value = (await jobList({ page: 1, size: 4 })).list
})
</script>

<template>
  <div class="container page">
    <section class="hero">
      <div class="hero-copy">
        <h1 class="font-display">智慧教育平台</h1>
        <p>汇聚优质课程、实训、实习与 AI 面试，一站式智慧学习与成长平台。</p>
        <div class="flex">
          <router-link to="/course"><button class="btn btn-primary">开始探索</button></router-link>
          <router-link to="/job"><button class="btn btn-ghost">寻找职位</button></router-link>
        </div>
      </div>
      <div class="hero-light" aria-hidden="true"></div>
      <span class="hero-spark" aria-hidden="true"></span>
    </section>

    <section class="mt panel">
      <div class="flex between section-head">
        <h2 class="font-display">热门课程</h2>
        <router-link to="/course" class="more">查看全部课程</router-link>
      </div>
      <ol class="rank">
        <li v-for="(c, i) in courses" :key="c.id">
          <span class="no font-display">{{ String(i + 1).padStart(2, '0') }}</span>
          <router-link :to="`/course/${c.id}`" class="rank-main">
            <span class="rank-name">{{ c.courseName }}</span>
            <span class="rank-meta">{{ c.teacherName || '—' }} · {{ c.enrollCount }} 人报名</span>
          </router-link>
          <span class="rank-price">{{ c.isFree ? '免费' : '¥' + c.price }}</span>
        </li>
      </ol>
    </section>

    <section class="mt panel">
      <div class="flex between section-head">
        <h2 class="font-display">最新职位</h2>
        <router-link to="/job" class="more">查看全部职位</router-link>
      </div>
      <ul class="jobs">
        <li v-for="j in jobs" :key="j.id">
          <router-link :to="`/job/${j.id}`" class="job-row">
            <span class="job-name">{{ j.jobName }}</span>
            <span class="job-company">{{ j.companyName || '—' }}</span>
            <span class="job-city">{{ j.city || '—' }}</span>
            <span class="job-salary">{{ j.salaryMin }}–{{ j.salaryMax }}K</span>
          </router-link>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.hero {
  position: relative; display: flex; align-items: center; overflow: hidden;
  padding: 44px 40px; background: var(--moss); border: 1px solid var(--fern);
  border-radius: var(--radius-card); min-height: 320px;
}
.hero-copy { position: relative; z-index: 2; max-width: 36rem; text-align: left; }
.hero h1 { font-size: 3.2rem; line-height: 1.2; margin-bottom: 1rem; }
.hero p { color: var(--text-dim); font-size: 1.05rem; margin-bottom: 2rem; }
.hero-light {
  position: absolute; left: -8%; bottom: -34%; width: 72%; height: 135%;
  background: linear-gradient(110deg, transparent 42%, rgba(139,92,246,.10) 62%, rgba(139,92,246,.04) 78%, transparent 100%);
  filter: blur(2px); transform: skewX(-8deg);
}
.hero-spark {
  position: absolute; right: 12%; top: 22%; width: 6px; height: 6px; border-radius: 50%;
  background: var(--firefly); box-shadow: 0 0 14px var(--firefly); opacity: .8;
}

.section-head { margin-bottom: 18px; }
.section-head h2 { font-size: 1.6rem; }
.more { color: var(--text-dim); font-size: 14px; }
.more:hover { color: var(--firefly-soft); }

.rank { list-style: none; }
.rank li { display: flex; align-items: center; gap: 20px; padding: 16px 6px; border-bottom: 1px solid var(--fern); }
.rank li:last-child { border-bottom: none; }
.rank .no { font-size: 1.5rem; color: var(--text-faint); width: 2.4rem; }
.rank li:first-child .no { color: var(--firefly); }
.rank-main { flex: 1; display: flex; flex-direction: column; }
.rank-name { font-size: 16px; font-weight: 600; }
.rank-meta { font-size: 13px; color: var(--text-dim); }
.rank-price { color: var(--firefly-soft); font-weight: 600; }

.jobs { list-style: none; }
.jobs li { border-bottom: 1px solid var(--fern); }
.jobs li:last-child { border-bottom: none; }
.job-row { display: flex; align-items: center; gap: 16px; padding: 16px 6px; transition: .18s; }
.job-row:hover { background: rgba(139,92,246,.06); }
.job-name { flex: 1; font-weight: 600; }
.job-company { color: var(--text-dim); width: 200px; }
.job-city { color: var(--text-faint); width: 90px; }
.job-salary { color: var(--lichen); font-weight: 600; width: 110px; text-align: right; }
@media (max-width: 700px) { .job-company, .job-city { display: none; } .hero h1 { font-size: 2.3rem; } }
</style>
