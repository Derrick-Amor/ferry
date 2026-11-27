<script setup>
import { ref, reactive, onMounted } from 'vue'
import { jobList, jobStatus } from '@/api/admin'
import StatusTag from '@/components/StatusTag.vue'
import Pagination from '@/components/Pagination.vue'
import { toast } from '@/utils/toast'

const data = ref({ list: [], total: 0 })
const q = reactive({ page: 1, size: 10, keyword: '', status: '' })
async function load() {
  const p = { page: q.page, size: q.size }
  if (q.keyword) p.keyword = q.keyword
  if (q.status !== '') p.status = q.status
  data.value = await jobList(p)
}
onMounted(load)
function change(page) { q.page = page; load() }
async function offline(j) { await jobStatus(j.id, 2); toast('已下架', 'success'); load() }
async function online(j) { await jobStatus(j.id, 1); toast('已上架', 'success'); load() }
</script>

<template>
  <div>
    <h1 class="font-display page-title">职位管理</h1>

    <div class="flex wrap" style="margin-bottom:18px">
      <input class="input" style="width:220px" v-model="q.keyword" placeholder="搜索职位/公司" @keyup.enter="q.page=1; load()" />
      <select class="select" style="width:140px" v-model="q.status" @change="q.page=1; load()">
        <option value="">全部状态</option>
        <option :value="0">待审核</option><option :value="1">已上架</option>
        <option :value="2">已下架</option><option :value="3">已驳回</option>
      </select>
    </div>

    <div v-if="data.list.length">
      <table class="table">
        <thead><tr><th>职位</th><th>公司</th><th>薪资</th><th>投递</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="j in data.list" :key="j.id">
            <td>{{ j.jobName }}</td>
            <td>{{ j.companyName || '—' }}</td>
            <td>{{ j.salaryMin }}–{{ j.salaryMax }}K</td>
            <td>{{ j.applyCount }}</td>
            <td><StatusTag :status="j.status" type="job" /></td>
            <td>
              <div class="flex">
                <button v-if="j.status === 1" class="btn btn-danger btn-sm" @click="offline(j)">下架</button>
                <button v-if="j.status === 2" class="btn btn-primary btn-sm" @click="online(j)">上架</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <Pagination :total="data.total" :page="q.page" :size="q.size" @change="change" />
    </div>
    <div v-else class="empty">暂无职位</div>
  </div>
</template>
