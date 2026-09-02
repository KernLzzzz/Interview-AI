<template>
  <div class="page-container">
    <el-row :gutter="16">
      <!-- 基本资料 -->
      <el-col :xs="24" :md="12">
        <el-card class="profile-card">
          <template #header><span class="card-title">基本资料</span></template>
          <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="90px">
            <el-form-item label="用户名">
              <el-input :model-value="profile?.username ?? ''" disabled />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" placeholder="登录邮箱" />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" placeholder="昵称" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="profileForm.phone" placeholder="手机号" />
            </el-form-item>
            <el-form-item label="头像URL" prop="avatar">
              <el-input v-model="profileForm.avatar" placeholder="https://...（阶段D接上传）" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 修改密码 -->
      <el-col :xs="24" :md="12">
        <el-card class="profile-card">
          <template #header><span class="card-title">修改密码</span></template>
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-30位，含字母和数字" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" :loading="changingPwd" @click="changePassword">修改密码</el-button>
              <span class="pwd-hint">修改后需重新登录</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { profileApi, changePasswordApi, type UserProfile } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()

const profile = ref<UserProfile | null>(null)
const profileFormRef = ref<FormInstance>()
const profileForm = reactive({ email: '', nickname: '', phone: '', avatar: '' })
const savingProfile = ref(false)

const profileRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

onMounted(async () => {
  try {
    profile.value = await profileApi()
    if (profile.value) {
      Object.assign(profileForm, {
        email: profile.value.email ?? '',
        nickname: profile.value.nickname ?? '',
        phone: profile.value.phone ?? '',
        avatar: profile.value.avatar ?? ''
      })
    }
  } catch {
    // 401 由拦截器兜底跳登录
  }
})

async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingProfile.value = true
  try {
    const res = await authStore.updateProfile({ ...profileForm })
    if (res.success) {
      ElMessage.success(res.message)
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    savingProfile.value = false
  }
}

// ===================== 修改密码 =====================
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const changingPwd = ref(false)

const validateNewPwd = (_rule: any, value: string, callback: any) => {
  if (!value) return callback(new Error('请输入新密码'))
  if (value.length < 6 || value.length > 30) return callback(new Error('密码长度6-30个字符'))
  if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) return callback(new Error('密码必须包含字母和数字'))
  callback()
}

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (!value) return callback(new Error('请再次输入新密码'))
  if (value !== pwdForm.newPassword) return callback(new Error('两次输入的密码不一致'))
  callback()
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ validator: validateNewPwd, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }]
}

async function changePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  changingPwd.value = true
  try {
    await changePasswordApi({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码已修改，请重新登录')
    await authStore.logout()
    router.push('/login')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '修改失败')
  } finally {
    changingPwd.value = false
  }
}
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}

.profile-card {
  margin-bottom: 16px;
  .card-title { font-size: 15px; font-weight: 600; color: #303133; }
}

.pwd-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
