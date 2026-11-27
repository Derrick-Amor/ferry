// 平台所有「类型/分类」下拉选项，申请页与列表筛选共用，保证一致。

// 课程类型（后端存 Integer）
export const COURSE_TYPES = [
  { value: 1, label: '理论' },
  { value: 2, label: '实训' },
  { value: 3, label: '认证' },
  { value: 4, label: '实战项目' },
  { value: 5, label: '公开课' },
  { value: 6, label: '直播课' },
  { value: 7, label: '企业内训' },
  { value: 8, label: '竞赛训练' }
]

// 技术体系（后端存 String）
export const TECH_SYSTEMS = [
  'Java', 'Python', '前端', '大数据', '人工智能', '云计算',
  '网络安全', '嵌入式', '数据库', '测试运维', '移动开发',
  '区块链', '物联网', '游戏开发'
]

// 技术方向（后端存 String）
export const TECH_DIRECTIONS = [
  'Web后端', 'Web前端', '移动端', '桌面端', '算法', '数据分析',
  '云原生', 'DevOps', '信息安全', '嵌入式系统', '游戏引擎', '软件测试'
]

// 课程等级（后端存 String）
export const COURSE_LEVELS = ['入门', '初级', '中级', '高级', '专家']

// 职位类别（后端存 String）
export const JOB_CATEGORIES = [
  '前端', '后端', '算法', '数据', '设计', '产品', '运营', '市场',
  '销售', '测试', '运维', '职能/HR', '财务', '客服', '教育'
]

// 学历要求（后端存 String，空串表示不限）
export const EDUCATIONS = ['不限', '大专', '本科', '硕士', '博士']

// 经验要求（后端存 String，空串表示不限）
export const EXPERIENCES = ['不限', '应届', '1-3年', '3-5年', '5-10年', '10年以上']
