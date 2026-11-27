<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { detail, collect, apply, aiInterview } from '@/api/job'
import { list as resumeList, upload as resumeUpload } from '@/api/resume'
import { useAuthStore } from '@/store/auth'
import { toast } from '@/utils/toast'

const route = useRoute(), router = useRouter(), auth = useAuthStore()
const job = ref(null)
const showResume = ref(false)
const resumes = ref([])
const selectedResumeId = ref(null)
const resumeUploading = ref(false)
onMounted(async () => { job.value = await detail(route.params.id) })

function needLogin() { if (!auth.isLogin) { router.push('/login'); return false } return true }

async function onCollect() {
  if (!needLogin()) return
  await collect(route.params.id)
  toast('已收藏', 'success')
}
async function onApply() {
  if (!needLogin()) return
  if (auth.role !== 0) return toast('仅学生可投递职位', 'info')
  showResume.value = true
  resumes.value = await resumeList().catch(() => [])
  selectedResumeId.value = resumes.value.length ? resumes.value[0].id : null
}
async function confirmApply() {
  if (!selectedResumeId.value) return toast('请先上传并选择一份简历', 'error')
  await apply(route.params.id, selectedResumeId.value)
  toast('投递成功', 'success')
  showResume.value = false
}
async function onResumePick(e) {
  const file = e.target.files[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.pdf')) return toast('仅支持 PDF 简历', 'error')
  resumeUploading.value = true
  try {
    const res = await resumeUpload(file)
    selectedResumeId.value = res.resumeId
    resumes.value = await resumeList().catch(() => [])
    toast('简历上传成功', 'success')
  } finally {
    resumeUploading.value = false
    e.target.value = ''
  }
}
async function onInterview() {
  if (!needLogin()) return
  if (auth.role !== 0) return toast('仅学生可参加 AI 面试', 'info')
  const interviewId = await aiInterview(route.params.id)
  router.push(`/job/interview/${interviewId}`)
}
</script>

<template>
  <div class="container page" v-if="job">
    <div class="detail-grid">
      <main>
        <div class="tags">
          <span class="tag tag--violet">{{ job.jobCategory || '不限' }}</span>
          <span class="tag tag--slate">{{ job.education || '不限' }}</span>
          <span class="tag tag--slate">{{ job.experience || '不限' }}</span>
        </div>
        <h1 class="font-display title">{{ job.jobName }}</h1>
        <p class="meta">{{ job.companyName || '—' }} · {{ job.city || '不限' }} · {{ job.applyCount }} 人已投递</p>

        <h2 class="font-display sec">职位描述</h2>
        <p class="desc">{{ job.description || '暂无描述' }}</p>
      </main>

      <aside>
        <div class="panel side-card">
          <p class="side-salary">{{ job.salaryMin }}–{{ job.salaryMax }}K</p>
          <button class="btn btn-primary" style="width:100%" @click="onInterview">参加 AI 面试</button>
          <button class="btn btn-ghost" style="width:100%; margin-top:10px" @click="onApply">投递简历</button>
          <button class="btn btn-ghost" style="width:100%; margin-top:10px" @click="onCollect">收藏</button>
        </div>
      </aside>
    </div>

    <!-- 投递简历弹窗 -->
    <div v-if="showResume" class="modal-mask" @click.self="showResume = false">
      <div class="modal">
        <div class="modal-head">
          <h3 class="font-display">投递简历</h3>
          <button class="close" @click="showResume = false">×</button>
        </div>
        <p class="modal-tip">为「{{ job.jobName }}」选择一份简历</p>

        <div v-if="resumes.length" class="resume-opts">
          <label v-for="r in resumes" :key="r.id" class="opt" :class="{ active: selectedResumeId === r.id }">
            <input type="radio" :value="r.id" v-model="selectedResumeId" hidden />
            <span class="opt-icon">📄</span>
            <span class="opt-name">{{ r.fileName }}</span>
            <a :href="r.fileUrl" target="_blank" rel="noopener" class="opt-preview" @click.stop>预览</a>
          </label>
        </div>
        <p v-else class="modal-empty">还没有简历，请先上传一份</p>

        <label class="upload-inline">
          <input type="file" accept=".pdf" hidden @change="onResumePick" />
          <span>{{ resumeUploading ? '上传中…' : '+ 上传新简历' }}</span>
        </label>

        <div class="modal-foot">
          <button class="btn btn-ghost" @click="showResume = false">取消</button>
          <button class="btn btn-primary" @click="confirmApply">确认投递</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-grid { display: grid; grid-template-columns: 1fr 300px; gap: 40px; }
.tags { display: flex; gap: 8px; margin-bottom: 12px; }
.title { font-size: 2.2rem; line-height: 1.3; }
.meta { color: var(--text-dim); font-size: 14px; margin: 10px 0 26px; }
.sec { font-size: 1.4rem; margin: 28px 0 14px; }
.desc { color: var(--text-dim); max-width: 68ch; white-space: pre-line; }

.side-card { position: sticky; top: 90px; }
.side-salary { font-size: 1.7rem; font-weight: 700; color: var(--lichen); margin-bottom: 14px; }
@media (max-width: 820px) { .detail-grid { grid-template-columns: 1fr; } .side-card { position: static; } }

.modal-mask { position: fixed; inset: 0; background: rgba(23,48,31,.35); display: flex; align-items: center; justify-content: center; z-index: 100; padding: 20px; }
.modal { width: 100%; max-width: 480px; background: #fff; border-radius: 16px; padding: 22px; box-shadow: 0 20px 60px rgba(0,0,0,.2); }
.modal-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.modal-head h3 { font-size: 1.15rem; }
.close { border: none; background: none; font-size: 24px; color: var(--text-faint); cursor: pointer; line-height: 1; }
.modal-tip { color: var(--text-dim); font-size: 13px; margin-bottom: 14px; }
.resume-opts { display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; max-height: 240px; overflow: auto; }
.opt { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--fern); border-radius: 10px; cursor: pointer; transition: .15s; }
.opt.active { border-color: var(--firefly); background: rgba(139,92,246,.06); }
.opt-icon { font-size: 1.2rem; }
.opt-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.opt-preview { color: var(--firefly-soft); font-size: 13px; }
.modal-empty { color: var(--text-faint); padding: 16px 0; font-size: 14px; }
.upload-inline { display: inline-flex; color: var(--firefly-soft); font-size: 13px; cursor: pointer; margin-bottom: 16px; }
.upload-inline:hover { text-decoration: underline; }
.modal-foot { display: flex; justify-content: flex-end; gap: 10px; }
</style>
