<script setup>
import { ref, onMounted } from 'vue'
import { upload, list, remove } from '@/api/resume'
import { toast } from '@/utils/toast'

const resumes = ref([])
const uploading = ref(false)

async function load() { resumes.value = await list().catch(() => []) }
onMounted(load)

async function onPick(e) {
  const file = e.target.files[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.pdf')) return toast('仅支持 PDF 简历', 'error')
  uploading.value = true
  try {
    await upload(file)
    toast('简历上传成功', 'success')
    await load()
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

async function onRemove(r) {
  if (!confirm(`确定删除简历「${r.fileName}」吗？`)) return
  await remove(r.id)
  toast('简历已删除', 'success')
  await load()
}

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '—' }
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">我的简历</h1>
    <p class="sub">上传多份 PDF 简历，投递不同职位时可分别选择对应的简历。</p>

    <div class="panel" style="max-width:680px">
      <label class="upload-zone">
        <input type="file" accept=".pdf" hidden @change="onPick" />
        <span v-if="uploading">上传中…</span>
        <template v-else>
          <span class="icon">📄</span>
          <span>点击上传新的 PDF 简历</span>
        </template>
      </label>

      <ul v-if="resumes.length" class="resume-list">
        <li v-for="r in resumes" :key="r.id" class="resume-item">
          <span class="icon">📄</span>
          <div class="main">
            <p class="name">{{ r.fileName }}</p>
            <p class="time">上传于 {{ fmt(r.createTime) }}</p>
          </div>
          <a class="btn btn-ghost btn-sm" :href="r.fileUrl" target="_blank" rel="noopener">预览</a>
          <button class="btn btn-danger btn-sm" @click="onRemove(r)">删除</button>
        </li>
      </ul>
      <p v-else class="empty">还没有简历，先上传一份吧</p>
    </div>
  </div>
</template>

<style scoped>
.sub { color: var(--text-dim); margin-top: -10px; margin-bottom: 20px; }
.upload-zone {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  padding: 30px; border: 1px dashed var(--fern); border-radius: var(--radius-card);
  color: var(--text-dim); cursor: pointer; transition: .18s;
}
.upload-zone:hover { border-color: var(--firefly); color: var(--firefly-soft); }
.icon { font-size: 1.3rem; }
.resume-list { list-style: none; margin-top: 18px; }
.resume-item {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 14px; background: var(--moss);
  border: 1px solid var(--fern); border-radius: var(--radius-card);
  margin-bottom: 10px;
}
.main { flex: 1; min-width: 0; }
.name { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.time { color: var(--text-faint); font-size: 12px; margin-top: 3px; }
.empty { color: var(--text-faint); padding: 12px 0; }
</style>
