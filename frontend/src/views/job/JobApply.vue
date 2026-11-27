<script setup>
import { reactive } from 'vue'
import { applyList } from '@/api/job'
import { toast } from '@/utils/toast'
import { JOB_CATEGORIES, EDUCATIONS, EXPERIENCES } from '@/utils/options'

const form = reactive({
  jobName: '', companyName: '', jobCategory: '', salaryMin: '', salaryMax: '',
  city: '', education: '', experience: '', description: '', reason: '', duties: []
})

function addDuty() { form.duties.push('') }
function removeDuty(i) { form.duties.splice(i, 1) }

async function submit() {
  if (!form.jobName) return toast('请填写职位名称', 'error')
  if (!form.jobCategory) return toast('请选择职位类别', 'error')
  const salaryMin = form.salaryMin === '' ? null : Number(form.salaryMin)
  const salaryMax = form.salaryMax === '' ? null : Number(form.salaryMax)
  if (salaryMin != null && salaryMax != null && salaryMin >= salaryMax) return toast('最低薪资必须小于最高薪资', 'error')
  const payload = { ...form, salaryMin, salaryMax }
  await applyList(payload)
  toast('申请已提交，等待审核', 'success')
  Object.assign(form, { jobName: '', companyName: '', jobCategory: '', salaryMin: '', salaryMax: '', city: '', education: '', experience: '', description: '', reason: '', duties: [] })
}
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">申请职位上架</h1>
    <div class="panel" style="max-width:680px">
      <div class="grid" style="grid-template-columns:1fr 1fr">
        <div class="field"><label>职位名称</label><input class="input" v-model="form.jobName" /></div>
        <div class="field"><label>公司名称</label><input class="input" v-model="form.companyName" /></div>
        <div class="field"><label>城市</label><input class="input" v-model="form.city" /></div>
        <div class="field"><label>薪资下限(K)</label><input class="input" type="number" v-model="form.salaryMin" /></div>
        <div class="field"><label>薪资上限(K)</label><input class="input" type="number" v-model="form.salaryMax" /></div>
      </div>

      <div class="field"><label>职位类别</label>
        <div class="chips">
          <button v-for="c in JOB_CATEGORIES" :key="c" class="chip" :class="{ active: form.jobCategory === c }" @click="form.jobCategory = c">{{ c }}</button>
        </div>
      </div>

      <div class="field"><label>学历要求</label>
        <div class="chips">
          <button v-for="e in EDUCATIONS" :key="e" class="chip" :class="{ active: form.education === (e === '不限' ? '' : e) }" @click="form.education = (e === '不限' ? '' : e)">{{ e }}</button>
        </div>
      </div>

      <div class="field"><label>经验要求</label>
        <div class="chips">
          <button v-for="x in EXPERIENCES" :key="x" class="chip" :class="{ active: form.experience === (x === '不限' ? '' : x) }" @click="form.experience = (x === '不限' ? '' : x)">{{ x }}</button>
        </div>
      </div>
      <div class="field">
        <label>工作内容要求（可添加多条）</label>
        <div v-for="(d, i) in form.duties" :key="i" class="duty-row">
          <input class="input" v-model="form.duties[i]" placeholder="例如：负责系统后端开发与维护…" />
          <button type="button" class="btn btn-ghost btn-sm" @click="removeDuty(i)">删除</button>
        </div>
        <button type="button" class="btn btn-ghost btn-sm" @click="addDuty">+ 添加一条</button>
      </div>

      <div class="field"><label>职位描述</label><textarea class="textarea" v-model="form.description"></textarea></div>
      <div class="field"><label>上架理由</label><textarea class="textarea" v-model="form.reason"></textarea></div>
      <button class="btn btn-primary" @click="submit">提交申请</button>
    </div>
  </div>
</template>

<style scoped>
.duty-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
</style>
