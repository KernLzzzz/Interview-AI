<template>
  <div class="page-container">
    <div class="page-header">
      <h2>用户管理</h2>
    </div>

    <!-- 搜索筛选 -->
    <el-card class="filter-card">
      <div class="filter-row">
        <el-input
          v-model="filterForm.keyword"
          placeholder="搜索用户名或邮箱..."
          clearable
          style="width: 280px"
          @input="handleFilterChange"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-select
          v-model="filterForm.status"
          placeholder="状态"
          multiple
          collapse-tags
          clearable
          style="width: 200px"
          @change="handleFilterChange"
        >
          <el-option v-for="s in statusOpts" :key="s" :label="s" :value="s" />
        </el-select>

        <el-button @click="resetFilter">重置</el-button>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table
        :data="userStore.pagedUsers"
        border
        stripe
        @sort-change="handleSortChange"
        style="width: 100%"
      >
        <el-table-column prop="id" label="用户ID" width="80" align="center" />

        <el-table-column prop="username" label="用户名" width="140" sortable="custom">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28" style="background:#409EFF">
                {{ row.username.charAt(0) }}
              </el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="email" label="邮箱" min-width="220" show-overflow-tooltip />

        <el-table-column prop="createdAt" label="注册时间" width="160" sortable="custom">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColorMap[row.status]" size="small" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="openAssignDialog(row)">
              <el-icon><UserFilled /></el-icon> 分配角色
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="userStore.pagination.currentPage"
          v-model:page-size="userStore.pagination.pageSize"
          :page-sizes="userStore.pagination.pageSizes"
          :total="userStore.pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="onPageSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="dialogVisible" title="分配角色" width="360px">
      <p class="role-hint">接口不返回当前角色，勾选后覆盖式保存；该用户需重新登录后新角色生效。</p>
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="r in userStore.roles" :key="r.id" :value="r.id">{{ r.name }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { statusColorMap, formatDateTime } from '@/utils/helpers'
import type { User } from '@/types'

const userStore = useUserStore()

const filterForm = reactive({
  keyword: '',
  status: [] as string[]
})

const statusOpts = ['正常', '禁用']

function handleFilterChange() {
  userStore.setFilter({
    keyword: filterForm.keyword || undefined,
    status: filterForm.status.length > 0 ? filterForm.status : undefined
  })
}

function resetFilter() {
  filterForm.keyword = ''
  filterForm.status = []
  userStore.setFilter({})
}

function handleSortChange({ prop, order }: { prop: string; order: any }) {
  userStore.setSort(prop, order)
}

function onPageChange(page: number) {
  userStore.pagination.currentPage = page
}

function onPageSizeChange(size: number) {
  userStore.pagination.pageSize = size
  userStore.pagination.currentPage = 1
}

function formatDate(str: string) {
  return formatDateTime(str)
}

// ===================== 分配角色 =====================
const dialogVisible = ref(false)
const currentUser = ref<User | null>(null)
const selectedRoleIds = ref<number[]>([])
const submitting = ref(false)

async function openAssignDialog(user: User) {
  currentUser.value = user
  selectedRoleIds.value = []
  dialogVisible.value = true
  if (userStore.roles.length === 0) {
    await userStore.fetchRoles()
  }
}

async function submitAssign() {
  if (!currentUser.value) return
  if (selectedRoleIds.value.length === 0) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  submitting.value = true
  try {
    await userStore.assignRoles(currentUser.value.id, selectedRoleIds.value)
    ElMessage.success(`已为用户「${currentUser.value.username}」分配角色`)
    dialogVisible.value = false
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await userStore.fetchUsers()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  h2 { margin: 0; color: #303133; }
}

.filter-card {
  :deep(.el-card__body) { padding: 16px; }
  .filter-row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
  }
}

.table-card {
  :deep(.el-card__body) { padding: 0; }
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
  border-top: 1px solid #EBEEF5;
}

.role-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
