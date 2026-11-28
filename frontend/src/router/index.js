import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes = [
  { path: '/login', component: () => import('@/views/Login.vue') },
  { path: '/register', component: () => import('@/views/Register.vue') },
  { path: '/forgot', component: () => import('@/views/Forgot.vue') },
  { path: '/', component: () => import('@/views/Home.vue') },
  { path: '/course', component: () => import('@/views/course/CourseList.vue') },
  { path: '/course/apply', component: () => import('@/views/course/CourseApply.vue'), meta: { requiresAuth: true, roles: [1] } },
  { path: '/course/study/:id', component: () => import('@/views/course/CourseStudy.vue'), meta: { requiresAuth: true, roles: [0] } },
  { path: '/course/:id', component: () => import('@/views/course/CourseDetail.vue') },
  { path: '/assistant', component: () => import('@/views/assistant/AssistantPage.vue'), meta: { requiresAuth: true } },
  { path: '/job', component: () => import('@/views/job/JobList.vue') },
  { path: '/job/apply', component: () => import('@/views/job/JobApply.vue'), meta: { requiresAuth: true, roles: [1] } },
  { path: '/job/applicants', component: () => import('@/views/job/JobApplicants.vue'), meta: { requiresAuth: true, roles: [1] } },
  { path: '/job/my-applications', component: () => import('@/views/job/MyApplications.vue'), meta: { requiresAuth: true, roles: [0] } },
  { path: '/job/interview/:id', component: () => import('@/views/job/Interview.vue'), meta: { requiresAuth: true, roles: [0] } },
  { path: '/job/:id', component: () => import('@/views/job/JobDetail.vue') },
  { path: '/cart', component: () => import('@/views/Cart.vue'), meta: { requiresAuth: true, roles: [0] } },
  { path: '/order', component: () => import('@/views/order/OrderList.vue'), meta: { requiresAuth: true, roles: [0] } },
  { path: '/order/:id', component: () => import('@/views/order/OrderDetail.vue'), meta: { requiresAuth: true, roles: [0] } },
  { path: '/user', component: () => import('@/views/user/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/user/my-apply', component: () => import('@/views/user/MyApply.vue'), meta: { requiresAuth: true } },
  { path: '/user/my-course', component: () => import('@/views/course/MyCourse.vue'), meta: { requiresAuth: true, roles: [0] } },
  { path: '/user/resume', component: () => import('@/views/user/MyResume.vue'), meta: { requiresAuth: true } },
  {
    path: '/admin', component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, roles: [2] },
    children: [
      { path: '', redirect: '/admin/apply' },
      { path: 'apply', component: () => import('@/views/admin/ApplyAudit.vue') },
      { path: 'users', component: () => import('@/views/admin/UserManage.vue') },
      { path: 'courses', component: () => import('@/views/admin/CourseManage.vue') },
      { path: 'jobs', component: () => import('@/views/admin/JobManage.vue') },
      { path: 'orders', component: () => import('@/views/admin/OrderManage.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFound.vue') }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (auth.isLogin && !auth.userInfo) await auth.fetchInfo().catch(() => {})
  if (to.meta.requiresAuth && !auth.isLogin) return '/login'
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) return '/'
  if ((to.path === '/login' || to.path === '/register' || to.path === '/forgot') && auth.isLogin) return '/'
})

export default router
