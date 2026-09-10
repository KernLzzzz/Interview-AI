// ==================== 表单相关类型 ====================
export interface FormField {
  key: string
  type: 'input' | 'select' | 'number' | 'switch' | 'datetime' | 'textarea'
  label: string
  value: any
  rules?: Array<{ required?: boolean; message: string; trigger: string; validator?: any }>
  props?: Record<string, any>
  visible?: boolean
  options?: Array<{ label: string; value: any }>
  step?: number
}

export interface LinkageRule {
  trigger: string
  condition: (value: any) => boolean
  action: 'show' | 'hide'
  target: string
  rules?: any[]
}

// ==================== 表格相关类型 ====================
export interface TableColumn {
  prop: string
  label: string
  width?: string | number
  minWidth?: string | number
  sortable?: boolean
  render?: 'ellipsis' | 'tags' | 'stars' | 'progress' | 'status' | 'actions' | 'index' | 'datetime' | 'relativeTime'
  renderOptions?: any
  fixed?: 'left' | 'right'
  showOverflowTooltip?: boolean
}

export interface FilterOption {
  key: string
  label: string
  options: Array<{ label: string; value: any }>
  multiple?: boolean
}

export interface Pagination {
  currentPage: number
  pageSize: number
  total: number
  pageSizes: number[]
}

// ==================== 题库相关类型（对齐后端 interview_question） ====================
export interface Question {
  id: number
  scenarioId: number
  content: string
  type: '技术' | '行为' | 'HR面'      // 后端 technical / behavioral / hr
  difficulty: 1 | 2 | 3
  status: '启用' | '停用'              // 后端 1 / 0
  createTime: string
  expectedAnswer?: string
  keywords?: string
}

export interface QuestionFilter {
  keyword?: string
  type?: string[]
  difficulty?: number[]
  status?: string[]
}

// ==================== 用户相关类型（对齐后端 sys_user） ====================
export interface User {
  id: number
  username: string
  email: string
  nickname?: string
  phone?: string
  avatar?: string
  status: '正常' | '禁用'      // 后端 1 / 0
  createdAt: string
}

export interface UserFilter {
  keyword?: string
  status?: string[]
}

// ==================== 面试配置类型 ====================
export interface InterviewConfig {
  id: number
  interviewName: string
  interviewType: '技术面' | 'HR面' | '综合面'
  techDirection?: '前端' | '后端' | '算法' | '大数据'
  duration: number
  needBreak: boolean
  interviewers: string[]
  interviewTime: string
  remark?: string
  status: '草稿' | '已提交'
  createTime: string
}

// ==================== 通用响应类型 ====================
export interface PageResult<T> {
  list: T[]
  total: number
  currentPage: number
  pageSize: number
}

// ==================== 认证相关类型 ====================
export interface RegisterForm {
  username: string
  email: string
  password: string
  phone?: string
}

export interface LoginForm {
  username: string
  password: string
  remember: boolean
}

/** 后端登录返回体（LoginVO） */
export interface LoginResult {
  token: string
  userId: number
  username: string
  roles: string[]
}

export interface AuthUser {
  id: number
  username: string
  email: string
  role: '管理员' | '面试官' | '候选人'
  avatar?: string
  token: string
}

// ==================== 面试记录相关类型（对齐后端 interview_record） ====================
/** 抽到的面试题目（后端 questionData 快照） */
export interface AnswerQuestion {
  id: number
  content: string
  type?: string
  difficulty: number
}

export interface EvaluationTask {
  taskId: number
  recordId: number
  status: 'pending' | 'running' | 'success' | 'failed'
  retryCount: number
  maxRetries: number
  errorMessage?: string
  createdAt?: string
  updatedAt?: string
  finishedAt?: string
}

export interface InterviewContext {
  targetRole: string
  jobDescription: string
  candidateBackground: string
  selectionStrategy?: string
}

export interface InterviewRecord {
  id: number
  userId: number
  scenarioId: number
  scenarioName?: string         // 展示用，从场景列表解析
  interviewMode: 'text' | 'voice' | 'video'
  interviewContext?: InterviewContext
  questionData?: AnswerQuestion[]  // 开始面试时抽到的题
  status: '待开始' | '进行中' | '已完成' | '已取消'
  score: number | null          // 0-100，AI 评测完成后才有
  duration?: number             // 时长（分钟）
  mediaFileId?: number
  mediaDuration?: number
  answerData?: string           // 提交的答案（JSON 字符串）
  aiFeedback?: string           // AI 评测反馈
  startedAt?: string
  completedAt?: string
  createdAt: string
}

export interface RecordFilter {
  keyword?: string
  status?: string[]
}

// ==================== 仪表盘相关类型 ====================
export interface StatCard {
  key: string
  label: string
  value: number | string
  icon: string
  color: string        // 主色（用于图标背景/进度条）
  bgColor: string      // 卡片背景渐变色
  trend?: number       // 环比变化（正数上升，负数下降），可选
  unit?: string        // 单位，如 '%'、'人'
}

export interface TodoItem {
  id: number
  title: string
  description: string
  priority: '紧急' | '普通' | '低优先级'
  type: '面试安排' | '打分待办' | '系统通知' | '数据审核'
  status: '待处理' | '进行中' | '已完成'
  relatedId?: number     // 关联的面试记录 id
  dueDate?: string       // 截止日期
  createTime: string
  updateTime: string
}

// 仪表盘统计见 @/api/stats 的 StatsOverview（原 DashboardStats 已移除）
