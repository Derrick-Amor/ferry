<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useAuthStore } from '@/store/auth'
import { updateInfo, resetPassword } from '@/api/user'
import { toast } from '@/utils/toast'
import { ROLE_TEXT } from '@/utils/status'
import StatusTag from '@/components/StatusTag.vue'
import { mine as myCourses } from '@/api/course'
import { list as orderList } from '@/api/order'

const auth = useAuthStore()
const info = reactive({ nickname: '', realName: '', avatar: '' })
const pwd = reactive({ oldPassword: '', newPassword: '' })
const courses = ref([])
const orders = ref([])

const u = computed(() => auth.userInfo || {})
const initial = computed(() => (u.value.nickname || u.value.username || '?').slice(0, 1))
const roleText = computed(() => ROLE_TEXT[auth.role] || '用户')

onMounted(async () => {
  info.nickname = u.value.nickname || ''
  info.realName = u.value.realName || ''
  info.avatar = u.value.avatar || ''
  if (auth.role === 0) {
    courses.value = await myCourses().catch(() => [])
    const o = await orderList({ page: 1, size: 5 }).catch(() => null)
    orders.value = o?.list || []
  }
})

async function saveInfo() {
  await updateInfo(info)
  await auth.fetchInfo()
  toast('资料已更新', 'success')
}
async function savePwd() {
  if (!pwd.oldPassword || !pwd.newPassword) return toast('请填写原密码和新密码', 'error')
  await resetPassword(pwd)
  pwd.oldPassword = pwd.newPassword = ''
  toast('密码已修改', 'success')
}
</script>

<template>
  <div class="container page">
    <h1 class="font-display page-title">个人中心</h1>

    <!-- 用户信息卡 -->
    <section class="user-card">
      <div class="avatar">{{ initial }}</div>
      <div class="u-main">
        <div class="u-name-row">
          <p class="name">{{ u.nickname || u.username }}</p>
          <span class="role-badge">{{ roleText }}</span>
        </div>
        <p class="u-sub">@{{ u.username }}</p>
        <div class="u-meta">
          <span class="u-meta-item">
            <svg viewBox="0 0 24 24"><path d="M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm0 3v1.6l8 5 8-5V7l-8 5-8-5Z"/></svg>
            {{ u.email || '未填写邮箱' }}
          </span>
          <span class="u-meta-item">
            <svg viewBox="0 0 24 24"><path d="M7 3a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2H7Zm0 2h10v11H7V5Zm4 14a1 1 0 1 0 0 2 1 1 0 0 0 0-2Z"/></svg>
            {{ u.phone || '未填写手机' }}
          </span>
        </div>
      </div>
      <div class="u-actions">
        <router-link to="/user/my-apply" class="btn btn-ghost btn-sm">我的申请</router-link>
        <router-link to="/user/resume" class="btn btn-ghost btn-sm">我的简历</router-link>
      </div>
    </section>

    <!-- 账户设置 -->
    <section class="section">
      <h2 class="section-title">账户设置</h2>
      <div class="grid-2">
        <div class="panel form-card">
          <div class="card-head">
            <h3 class="font-display card-title">编辑资料</h3>
            <span class="card-hint">昵称 · 姓名 · 头像</span>
          </div>
          <div class="field"><label>昵称</label><input class="input" v-model="info.nickname" /></div>
          <div class="field"><label>真实姓名</label><input class="input" v-model="info.realName" /></div>
          <div class="field"><label>头像地址</label><input class="input" v-model="info.avatar" /></div>
          <button class="btn btn-primary" @click="saveInfo">保存资料</button>
        </div>

        <div class="panel form-card">
          <div class="card-head">
            <h3 class="font-display card-title">修改密码</h3>
            <span class="card-hint">保障账户安全</span>
          </div>
          <div class="field"><label>原密码</label><input class="input" type="password" v-model="pwd.oldPassword" /></div>
          <div class="field"><label>新密码</label><input class="input" type="password" v-model="pwd.newPassword" /></div>
          <button class="btn btn-primary" @click="savePwd">更新密码</button>
        </div>
      </div>
    </section>

    <!-- 学习与订单 -->
    <section v-if="auth.role === 0" class="section">
      <h2 class="section-title">学习与订单</h2>
      <div class="grid-2">
        <div class="panel list-card">
          <div class="card-head">
            <h3 class="font-display card-title">报名课程</h3>
            <router-link to="/user/my-course" class="link">全部 →</router-link>
          </div>
          <ul v-if="courses.length" class="list">
            <li v-for="c in courses.slice(0, 5)" :key="c.id" @click="$router.push(`/course/study/${c.id}`)">
              <span class="li-dot"></span>
              <span class="li-name">{{ c.courseName }}</span>
              <span class="li-meta">{{ c.teacherName || '—' }}</span>
            </li>
          </ul>
          <p v-else class="empty">还没有报名课程，去 <router-link to="/course" class="link">课程中心</router-link> 逛逛吧</p>
        </div>

        <div class="panel list-card">
          <div class="card-head">
            <h3 class="font-display card-title">订单记录</h3>
            <router-link to="/order" class="link">全部 →</router-link>
          </div>
          <ul v-if="orders.length" class="list">
            <li v-for="o in orders" :key="o.id" @click="$router.push(`/order/${o.id}`)">
              <span class="li-dot"></span>
              <span class="li-name">{{ o.orderNo }}</span>
              <span class="li-meta price">¥{{ o.totalAmount }}</span>
              <StatusTag :status="o.status" type="order" />
            </li>
          </ul>
          <p v-else class="empty">还没有订单记录</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 用户信息卡 */
.user-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 30px 34px;
  margin-bottom: 30px;
  border: 1px solid var(--fern);
  border-radius: 18px;
  background: linear-gradient(135deg, #ffffff 0%, #eef4ef 100%);
  box-shadow: 0 1px 2px rgba(23, 48, 31, .04);
}
.avatar {
  width: 76px;
  height: 76px;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--firefly), var(--firefly-soft));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 32px;
  box-shadow: 0 6px 16px rgba(124, 58, 237, .28);
}
.u-main { flex: 1; min-width: 0; }
.u-name-row { display: flex; align-items: center; gap: 12px; }
.name { font-size: 1.5rem; font-weight: 700; color: var(--text); }
.role-badge {
  padding: 3px 12px;
  border-radius: 999px;
  background: rgba(139, 92, 246, .12);
  color: var(--firefly-soft);
  font-size: 12px;
  font-weight: 600;
}
.u-sub { color: var(--text-faint); font-size: 13px; margin: 4px 0 14px; }
.u-meta { display: flex; flex-wrap: wrap; gap: 22px; }
.u-meta-item { display: flex; align-items: center; gap: 7px; color: var(--text-dim); font-size: 14px; }
.u-meta-item svg { width: 16px; height: 16px; fill: var(--firefly-soft); opacity: .85; }
.u-actions { display: flex; flex-direction: column; gap: 10px; flex-shrink: 0; }

/* 分区标题 */
.section { margin-bottom: 30px; }
.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 16px;
}
.section-title::before {
  content: '';
  width: 4px;
  height: 18px;
  border-radius: 2px;
  background: var(--firefly);
}

/* 两列网格 */
.grid-2 { display: grid; grid-template-columns: 1.618fr 1fr; gap: 20px; }

/* 卡片通用 */
.panel { border: 1px solid var(--fern); border-radius: 16px; background: var(--moss); padding: 22px; }
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.card-title { font-size: 1.15rem; font-weight: 700; color: var(--text); }
.card-hint { font-size: 12px; color: var(--text-faint); }

/* 列表 */
.list { list-style: none; }
.list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 2px;
  border-bottom: 1px solid var(--fern);
  cursor: pointer;
  transition: background .15s;
}
.list li:last-child { border-bottom: none; }
.list li:hover .li-name { color: var(--firefly-soft); }
.li-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--fern);
  flex-shrink: 0;
}
.list li:hover .li-dot { background: var(--firefly); }
.li-name { flex: 1; font-weight: 500; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.li-meta { color: var(--text-dim); font-size: 13px; flex-shrink: 0; }
.li-meta.price { color: var(--lichen); font-weight: 600; }

.empty { color: var(--text-faint); font-size: 14px; padding: 8px 2px; }
.link { color: var(--firefly-soft); font-size: 13px; font-weight: 500; }
.link:hover { text-decoration: underline; }

@media (max-width: 900px) {
  .user-card { flex-direction: column; align-items: flex-start; }
  .u-actions { flex-direction: row; }
  .grid-2 { grid-template-columns: 1fr; }
}
</style>
