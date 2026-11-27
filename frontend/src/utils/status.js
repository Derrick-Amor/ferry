import { COURSE_TYPES } from './options'

// 状态码 -> 文案 + 色调（对应 components.css 里 .tag--<tone>）
const MAP = {
  course:    { 0: ['待审核', 'amber'], 1: ['已上架', 'emerald'], 2: ['已下架', 'rose'], 3: ['已驳回', 'amber'] },
  job:       { 0: ['待审核', 'amber'], 1: ['已上架', 'emerald'], 2: ['已下架', 'rose'], 3: ['已驳回', 'amber'] },
  apply:     { 0: ['待审核', 'amber'], 1: ['已通过', 'emerald'], 2: ['已驳回', 'rose'] },
  order:     { 0: ['待支付', 'amber'], 1: ['已支付', 'emerald'], 2: ['已取消', 'slate'], 3: ['已退款', 'violet'] },
  jobApply:  { 0: ['已投递', 'violet'], 1: ['已查看', 'info'], 2: ['面试邀请', 'emerald'], 3: ['已录用', 'emerald'], 4: ['已拒绝', 'rose'] },
  interview: { 0: ['待作答', 'amber'], 1: ['已提交', 'emerald'] },
  user:      { 0: ['已禁用', 'rose'], 1: ['正常', 'emerald'] }
}

export function statusMeta(type, s) {
  const m = MAP[type]?.[s] || ['未知', 'slate']
  return { text: m[0], tone: m[1] }
}

export const ROLE_TEXT = { 0: '学生', 1: '讲师/企业', 2: '管理员' }

export const APPLY_TYPE_TEXT = { 1: '课程上架', 2: '职位上架', 3: '课程下架', 4: '职位下架' }

export const COURSE_TYPE_TEXT = Object.fromEntries(COURSE_TYPES.map(t => [t.value, t.label]))
