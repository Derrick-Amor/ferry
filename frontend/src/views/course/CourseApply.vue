<script setup>
import { reactive } from 'vue'
import { applyList } from '@/api/course'
import { toast } from '@/utils/toast'
import { COURSE_TYPES, TECH_SYSTEMS, TECH_DIRECTIONS, COURSE_LEVELS } from '@/utils/options'

const form = reactive({
  courseName: '', courseType: 1, techSystem: '', techDirection: '',
  courseLevel: '初级', isFree: 0, price: '', coverImage: '', description: '', reason: '',
  chapters: []
})

function addChapter() { form.chapters.push({ chapterName: '', duration: 20, videoUrl: '' }) }
function removeChapter(i) { form.chapters.splice(i, 1) }

async function submit() {
  if (!form.courseName) return toast('请填写课程名称', 'error')
  if (!form.techSystem) return toast('请选择技术体系', 'error')
  if (!form.techDirection) return toast('请选择技术方向', 'error')
  const payload = { ...form, price: form.price === '' ? null : Number(form.price) }
  await applyList(payload)
  toast('申请已提交，等待审核', 'success')
  Object.assign(form, { courseName: '', techSystem: '', techDirection: '', price: '', coverImage: '', description: '', reason: '', chapters: [] })
}
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">申请课程上架</h1>
    <div class="panel" style="max-width:680px">
      <div class="grid" style="grid-template-columns:1fr 1fr">
        <div class="field"><label>课程名称</label><input class="input" v-model="form.courseName" /></div>
        <div class="field" v-if="form.isFree === 0"><label>价格(元)</label><input class="input" v-model="form.price" type="number" /></div>
      </div>

      <div class="field"><label>课程类型</label>
        <div class="chips">
          <button v-for="t in COURSE_TYPES" :key="t.value" class="chip" :class="{ active: form.courseType === t.value }" @click="form.courseType = t.value">{{ t.label }}</button>
        </div>
      </div>

      <div class="field"><label>技术体系</label>
        <div class="chips">
          <button v-for="s in TECH_SYSTEMS" :key="s" class="chip" :class="{ active: form.techSystem === s }" @click="form.techSystem = s">{{ s }}</button>
        </div>
      </div>

      <div class="field"><label>技术方向</label>
        <div class="chips">
          <button v-for="d in TECH_DIRECTIONS" :key="d" class="chip" :class="{ active: form.techDirection === d }" @click="form.techDirection = d">{{ d }}</button>
        </div>
      </div>

      <div class="field"><label>课程等级</label>
        <div class="chips">
          <button v-for="l in COURSE_LEVELS" :key="l" class="chip" :class="{ active: form.courseLevel === l }" @click="form.courseLevel = l">{{ l }}</button>
        </div>
      </div>

      <div class="field"><label>是否免费</label>
        <div class="chips">
          <button class="chip" :class="{ active: form.isFree === 1 }" @click="form.isFree = 1">免费</button>
          <button class="chip" :class="{ active: form.isFree === 0 }" @click="form.isFree = 0">付费</button>
        </div>
      </div>

      <div class="field"><label>封面图片地址</label><input class="input" v-model="form.coverImage" /></div>

      <div class="field">
        <label>课程章节（定制课程内容）</label>
        <div v-for="(ch, i) in form.chapters" :key="i" class="chapter-row">
          <input class="input" v-model="ch.chapterName" placeholder="章节名称" />
          <input class="input" type="number" v-model.number="ch.duration" placeholder="分钟" style="width:90px" />
          <input class="input" v-model="ch.videoUrl" placeholder="视频地址(可选)" />
          <button type="button" class="btn btn-ghost btn-sm" @click="removeChapter(i)">删除</button>
        </div>
        <button type="button" class="btn btn-ghost btn-sm" @click="addChapter">+ 添加章节</button>
      </div>

      <div class="field"><label>课程介绍</label><textarea class="textarea" v-model="form.description"></textarea></div>
      <div class="field"><label>上架理由</label><textarea class="textarea" v-model="form.reason"></textarea></div>
      <button class="btn btn-primary" @click="submit">提交申请</button>
    </div>
  </div>
</template>

<style scoped>
.chapter-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
</style>
