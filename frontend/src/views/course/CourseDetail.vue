<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { detail, collect } from '@/api/course'
import { add as addCart } from '@/api/cart'
import { useAuthStore } from '@/store/auth'
import { toast } from '@/utils/toast'
import { COURSE_TYPE_TEXT } from '@/utils/status'

const route = useRoute(), router = useRouter(), auth = useAuthStore()
const data = ref({ course: null, chapters: [] })
onMounted(async () => { data.value = await detail(route.params.id) })

async function onCollect() {
  if (!auth.isLogin) return router.push('/login')
  await collect(route.params.id)
  toast('已收藏', 'success')
}
async function onStudy() {
  if (!auth.isLogin) return router.push('/login')
  if (auth.role !== 0) return toast('仅学生可学习课程', 'info')
  router.push(`/course/study/${route.params.id}`)
}
async function onCart() {
  if (!auth.isLogin) return router.push('/login')
  if (auth.role !== 0) return toast('仅学生可购买课程', 'info')
  await addCart(route.params.id)
  toast('已加入购物车，请结算支付后学习', 'success')
  router.push('/cart')
}
</script>

<template>
  <div class="container page" v-if="data.course">
    <div class="detail-grid">
      <main>
        <div class="tags">
          <span class="tag tag--violet">{{ COURSE_TYPE_TEXT[data.course.courseType] }}</span>
          <span class="tag tag--slate">{{ data.course.courseLevel || '不限' }}</span>
          <span class="tag" :class="data.course.isFree ? 'tag--emerald' : 'tag--amber'">{{ data.course.isFree ? '免费' : '付费' }}</span>
        </div>
        <h1 class="font-display title">{{ data.course.courseName }}</h1>
        <p class="meta">{{ data.course.teacherName || '—' }} · 评分 {{ data.course.rating }} · {{ data.course.enrollCount }} 人报名</p>

        <h2 class="font-display sec">课程说明</h2>
        <p class="desc">{{ data.course.description || '暂无说明' }}</p>

        <h2 class="font-display sec">章节</h2>
        <ol class="chapters">
          <li v-for="(ch, i) in data.chapters" :key="ch.id">
            <span class="no font-display">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="ch-name">{{ ch.chapterName }}</span>
            <span class="ch-dur">{{ ch.duration }} 分钟</span>
          </li>
        </ol>
      </main>

      <aside>
        <div class="panel side-card">
          <div class="plate" :style="data.course.coverImage ? { backgroundImage: `url(${data.course.coverImage})` } : {}"></div>
          <p class="side-price">{{ data.course.isFree ? '免费' : '¥' + data.course.price }}</p>
          <button v-if="data.purchased" class="btn btn-primary" style="width:100%" @click="onStudy">开始学习</button>
          <button v-else class="btn btn-primary" style="width:100%" @click="onCart">加入购物车</button>
          <button class="btn btn-ghost" style="width:100%; margin-top:10px" @click="onCollect">收藏</button>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.detail-grid { display: grid; grid-template-columns: 1fr 300px; gap: 40px; }
.tags { display: flex; gap: 8px; margin-bottom: 12px; }
.title { font-size: 2.2rem; line-height: 1.3; }
.meta { color: var(--text-dim); font-size: 14px; margin: 10px 0 26px; }
.sec { font-size: 1.4rem; margin: 28px 0 14px; }
.desc { color: var(--text-dim); max-width: 68ch; }

.chapters { list-style: none; }
.chapters li { display: flex; align-items: center; gap: 16px; padding: 13px 4px; border-bottom: 1px solid var(--fern); }
.chapters .no { color: var(--text-faint); width: 2rem; font-size: 1.1rem; }
.ch-name { flex: 1; }
.ch-dur { color: var(--text-dim); font-size: 13px; }

.side-card { position: sticky; top: 90px; }
.plate { height: 150px; border-radius: var(--radius); background: linear-gradient(135deg, #d7e7db, #bfd6c5) center/cover; margin-bottom: 14px; }
.side-price { font-size: 1.6rem; font-weight: 700; color: var(--firefly-soft); margin-bottom: 14px; }
@media (max-width: 820px) { .detail-grid { grid-template-columns: 1fr; } .side-card { position: static; } }
</style>
