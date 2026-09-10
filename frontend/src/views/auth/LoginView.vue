<template>
  <div class="auth-page">
    <section class="brand-panel" aria-label="Interview AI 产品介绍">
      <div class="brand-lockup">
        <span class="brand-mark">IA</span>
        <span><strong>INTERVIEW AI</strong><small>候选人评测工作台</small></span>
      </div>

      <div class="brand-thesis">
        <span class="eyebrow">PRACTICE WITH EVIDENCE</span>
        <h1>让每一次回答，<br><em>都成为下一次面试的证据。</em></h1>
        <p>从岗位定制抽题到多模态作答，再到可追溯的 AI 评测，把模糊的“面试感觉”变成明确的改进动作。</p>
      </div>

      <div class="signal-board" aria-label="面试评测流程">
        <div class="signal-head">
          <span>INTERVIEW TRACE</span>
          <span class="signal-state"><i></i> READY</span>
        </div>
        <div class="signal-row">
          <span class="signal-code">01</span>
          <span><strong>理解岗位</strong><small>JD 与候选人背景形成面试上下文</small></span>
          <span class="signal-bars bars-one" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></span>
        </div>
        <div class="signal-row">
          <span class="signal-code">02</span>
          <span><strong>完成表达</strong><small>文本、语音、视频三种面试方式</small></span>
          <span class="signal-bars bars-two" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></span>
        </div>
        <div class="signal-row">
          <span class="signal-code">03</span>
          <span><strong>获得反馈</strong><small>能力维度、证据片段与行动建议</small></span>
          <span class="signal-bars bars-three" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></span>
        </div>
      </div>

      <div class="trust-line">
        <span>岗位定制</span><span>多模态面试</span><span>结构化评测</span>
      </div>
    </section>

    <main class="form-panel">
      <div class="auth-card">
        <div class="mobile-brand"><span>IA</span> INTERVIEW AI</div>
        <div class="auth-heading">
          <span class="eyebrow">WELCOME BACK</span>
          <h2>继续你的面试训练</h2>
          <p>登录后查看练习记录与下一步建议</p>
        </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="auth-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <div class="form-options">
          <el-checkbox v-model="form.remember">记住登录状态</el-checkbox>
          <el-dropdown trigger="click" @command="fillQuickAccount">
            <el-link type="primary" :underline="false" class="demo-link">
              切换角色
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-link>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="acc in quickAccounts"
                  :key="acc.username"
                  :command="acc"
                >
                  {{ acc.label }}（{{ acc.username }}）
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <el-form-item>
          <el-button
            type="primary"
            :loading="authStore.loading"
            class="submit-btn"
            @click="handleLogin"
          >
            {{ authStore.loading ? '正在进入工作台...' : '进入工作台' }}
          </el-button>
        </el-form-item>

        <div class="auth-footer">
          还没有账号？
          <el-link type="primary" @click="router.push('/register')">立即注册</el-link>
        </div>
      </el-form>

        <div class="demo-access">
          <span>DEMO ACCESS</span>
          <p>候选人 <code>test01 / candidate123</code></p>
          <small>也可从“体验不同角色”切换面试官或管理员。</small>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  remember: false
})

// 快速填入后端测试账号（密码须含字母+数字，后端 LoginDTO 校验要求）
const quickAccounts = [
  { label: '管理员', username: 'admin', password: 'admin123' },
  { label: '面试官', username: 'interviewer', password: 'interview123' },
  { label: '候选人', username: 'test01', password: 'candidate123' }
] as const

function fillQuickAccount(acc: { username: string; password: string }) {
  form.username = acc.username
  form.password = acc.password
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const result = await authStore.login(form)
  if (result.success) {
    ElMessage.success(result.message)
    router.push('/')
  } else {
    ElMessage.error(result.message)
  }
}
</script>

<style lang="scss" scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(430px, .82fr);
  background: #f4f7f8;
}

.brand-panel {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: clamp(32px, 5vw, 70px);
  color: #edf6f7;
  background:
    radial-gradient(circle at 84% 16%, rgba(36, 144, 157, .22), transparent 28%),
    linear-gradient(145deg, #0b1d2c 0%, #102b3d 58%, #123342 100%);
}

.brand-panel::after {
  position: absolute;
  right: -14%;
  bottom: -27%;
  width: 64%;
  aspect-ratio: 1;
  border: 1px solid rgba(166, 214, 219, .16);
  border-radius: 50%;
  box-shadow: 0 0 0 70px rgba(166, 214, 219, .035), 0 0 0 150px rgba(166, 214, 219, .022);
  content: '';
}

.brand-lockup {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 13px;
  width: fit-content;

  > span:last-child { display: flex; flex-direction: column; gap: 4px; }
  strong { font: 700 13px/1 "Bahnschrift", "Arial Narrow", sans-serif; letter-spacing: .18em; }
  small { color: #88a8b5; font-size: 10px; letter-spacing: .12em; }
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(219, 241, 242, .5);
  border-radius: 50%;
  font: 700 12px/1 ui-monospace, monospace;
  letter-spacing: .1em;
}

.eyebrow {
  font: 700 10px/1.2 ui-monospace, SFMono-Regular, Consolas, monospace;
  color: #55bec5;
  letter-spacing: .2em;
}

.brand-thesis {
  position: relative;
  z-index: 1;
  max-width: 700px;
  margin: clamp(50px, 10vh, 120px) 0 46px;

  h1 {
    max-width: 680px;
    margin: 18px 0 22px;
    font: 600 clamp(38px, 4vw, 56px)/1.16 "Bahnschrift", "Noto Sans SC", sans-serif;
    letter-spacing: -.045em;
  }
  em { color: #9ed7da; font-style: normal; }
  p { max-width: 590px; color: #a9bec8; font-size: 15px; line-height: 1.9; }
}

.signal-board {
  position: relative;
  z-index: 1;
  width: min(100%, 660px);
  border-top: 1px solid rgba(196, 225, 228, .22);
  border-bottom: 1px solid rgba(196, 225, 228, .22);
}

.signal-head, .signal-row {
  display: grid;
  align-items: center;
}

.signal-head {
  grid-template-columns: 1fr auto;
  padding: 12px 0;
  color: #7293a0;
  font: 700 9px/1 ui-monospace, monospace;
  letter-spacing: .18em;
}

.signal-state { color: #72c5b1; i { display: inline-block; width: 6px; height: 6px; margin-right: 6px; border-radius: 50%; background: #72c5b1; } }

.signal-row {
  grid-template-columns: 38px 1fr 90px;
  gap: 14px;
  min-height: 72px;
  border-top: 1px solid rgba(196, 225, 228, .1);
  .signal-code { color: #55bec5; font: 600 11px/1 ui-monospace, monospace; }
  > span:nth-child(2) { display: flex; flex-direction: column; gap: 5px; }
  strong { font-size: 13px; font-weight: 600; }
  small { color: #76939f; font-size: 11px; }
}

.signal-bars {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
  height: 24px;
  i { width: 3px; border-radius: 2px; background: #3e9ba3; }
  i:nth-child(1) { height: 8px; } i:nth-child(2) { height: 17px; } i:nth-child(3) { height: 11px; }
  i:nth-child(4) { height: 22px; } i:nth-child(5) { height: 14px; }
}
.bars-two { opacity: .72; transform: scaleY(.78); }
.bars-three { opacity: .5; transform: scaleY(.62); }

.trust-line {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 22px;
  margin-top: 38px;
  color: #7f9ba6;
  font-size: 11px;
  span::before { margin-right: 7px; color: #4fadb4; content: '◆'; font-size: 7px; }
}

.form-panel {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 48px clamp(30px, 5vw, 76px);
  background-color: #f4f7f8;
  background-image: linear-gradient(rgba(34, 63, 79, .035) 1px, transparent 1px);
  background-size: 100% 28px;
}

.auth-card {
  width: min(100%, 430px);
}

.mobile-brand { display: none; }

.auth-heading {
  margin-bottom: 34px;
  h2 { margin: 12px 0 8px; color: #102238; font: 650 30px/1.25 "Bahnschrift", "Noto Sans SC", sans-serif; letter-spacing: -.02em; }
  p { color: #718594; font-size: 13px; }
}

.auth-form {
  :deep(.el-form-item__label) {
    color: #354d5d;
    font-size: 12px;
    font-weight: 650;
    padding-bottom: 7px;
  }
  :deep(.el-input__wrapper) {
    min-height: 46px;
    border-radius: 4px;
    background: rgba(255, 255, 255, .76);
    box-shadow: 0 0 0 1px #d5e0e4 inset;
    &:focus-within { box-shadow: 0 0 0 1px #167487 inset, 0 0 0 4px rgba(22, 116, 135, .08); }
  }
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -6px 0 18px;
  font-size: 12px;
}

.demo-link { font-size: 12px; }

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 650;
  letter-spacing: .04em;
}

.auth-footer {
  text-align: center;
  font-size: 13px;
  color: #718594;
  margin-top: 14px;
}

.demo-access {
  margin-top: 34px;
  padding: 15px 17px;
  border: 1px solid #d6e1e4;
  border-left: 3px solid #d89542;
  background: rgba(255, 255, 255, .64);
  > span { color: #9a713a; font: 700 9px/1 ui-monospace, monospace; letter-spacing: .18em; }
  p { margin: 8px 0 4px; color: #334c5c; font-size: 12px; }
  small { color: #7e909b; font-size: 11px; }
  code {
    padding: 2px 5px;
    color: #126879;
    background: #e4eff0;
    font-family: ui-monospace, monospace;
  }
}

@media (max-width: 980px) {
  .auth-page { grid-template-columns: 1fr; }
  .brand-panel { display: none; }
  .form-panel { padding: 32px 22px; }
  .mobile-brand { display: flex; align-items: center; gap: 10px; margin-bottom: 54px; color: #102238; font: 700 12px/1 ui-monospace, monospace; letter-spacing: .14em; }
  .mobile-brand span { display: grid; place-items: center; width: 34px; height: 34px; border: 1px solid #75909d; border-radius: 50%; font-size: 10px; }
}

@media (prefers-reduced-motion: no-preference) {
  .signal-bars i { animation: signal-pulse 2.8s ease-in-out infinite alternate; }
  .signal-bars i:nth-child(2) { animation-delay: -.7s; }
  .signal-bars i:nth-child(4) { animation-delay: -1.3s; }
  @keyframes signal-pulse { to { transform: scaleY(.55); opacity: .48; } }
}
</style>
