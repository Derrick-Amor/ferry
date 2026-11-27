<script setup>
import { ref, onMounted } from 'vue'
import { my, myApplicants, updateApplicantStatus } from '@/api/job'
import StatusTag from '@/components/StatusTag.vue'
import { toast } from '@/utils/toast'

const APPLICANT_STATUS = [
  { value: 0, label: '已投递' },
  { value: 1, label: '已查看' },
  { value: 2, label: '面试邀请' },
  { value: 3, label: '已录用' },
  { value: 4, label: '已拒绝' }
]

const jobs = ref([])
const jobId = ref(null)
const applicants = ref([])

async function loadJobs() {
  jobs.value = await my()
  if (jobs.value.length) {
    jobId.value = jobs.value[0].id
    await loadApplicants()
  }
}
async function loadApplicants() {
  if (!jobId.value) { applicants.value = []; return }
  applicants.value = await myApplicants(jobId.value)
}
async function onChangeStatus(a, status) {
  await updateApplicantStatus(a.applyId, status)
  a.applyStatus = status
  toast('已更新候选人状态', 'success')
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '—' }
onMounted(loadJobs)
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">候选人管理</h1>

    <div v-if="jobs.length">
      <div class="flex wrap" style="margin-bottom:18px">
        <label class="text-muted">选择职位</label>
        <select class="select" style="width:300px" v-model="jobId" @change="loadApplicants">
          <option v-for="j in jobs" :key="j.id" :value="j.id">{{ j.jobName }}</option>
        </select>
      </div>

      <div v-if="applicants.length">
        <table class="table">
          <thead>
            <tr><th>候选人</th><th>联系方式</th><th>简历</th><th>投递状态</th><th>AI 面试</th><th>投递时间</th><th>处理</th></tr>
          </thead>
          <tbody>
            <tr v-for="a in applicants" :key="a.applyId">
              <td>{{ a.nickname || a.username }}</td>
              <td>
                <div>{{ a.phone || '—' }}</div>
                <div class="text-muted" style="font-size:12px">{{ a.email || '—' }}</div>
              </td>
              <td>
                <a v-if="a.resumeFileUrl" :href="a.resumeFileUrl" target="_blank" class="link">{{ a.resumeFileName || '查看简历' }}</a>
                <span v-else-if="a.resumeFileName">{{ a.resumeFileName }}</span>
                <span v-else>—</span>
              </td>
              <td><StatusTag :status="a.applyStatus" type="jobApply" /></td>
              <td>
                <template v-if="a.interviewStatus != null">
                  <StatusTag :status="a.interviewStatus" type="interview" />
                  <span v-if="a.interviewScore != null" class="text-muted"> {{ a.interviewScore }}分</span>
                </template>
                <span v-else>—</span>
              </td>
              <td>{{ fmt(a.applyTime) }}</td>
              <td>
                <select class="select" style="width:112px" :value="a.applyStatus" @change="onChangeStatus(a, Number($event.target.value))">
                  <option v-for="s in APPLICANT_STATUS" :key="s.value" :value="s.value">{{ s.label }}</option>
                </select>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty">该职位还没有人投递</div>
    </div>
    <div v-else class="empty">你还没有发布过职位</div>
  </div>
</template>

<style scoped>
.link { color: var(--firefly-soft); text-decoration: underline; }
</style>
