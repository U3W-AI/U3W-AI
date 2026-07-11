<template>
  <div class="app-container">
    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleCreate"
        >创建 API Key</el-button>
      </el-col>
    </el-row>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="keyList">
      <el-table-column label="名称" prop="name" />
      <el-table-column label="密钥" prop="apiKey">
        <template #default="scope">
          <code style="color: #409EFF; font-family: monospace;">{{ scope.row.apiKey }}</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" align="center" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
            {{ scope.row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" align="center" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最后使用" prop="lastUsedAt" align="center" width="160">
        <template #default="scope">
          <span v-if="scope.row.lastUsedAt">{{ parseTime(scope.row.lastUsedAt) }}</span>
          <span v-else style="color: #909399;">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="180" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            type="primary"
            link
            icon="Edit"
            @click="handleToggle(scope.row)"
          >{{ scope.row.status === 1 ? '禁用' : '启用' }}</el-button>
          <el-button
            type="primary"
            link
            icon="Delete"
            @click="handleDelete(scope.row)"
            style="color: #F56C6C;"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建对话框 -->
    <el-dialog :title="createDialogTitle" v-model="createDialogVisible" width="500px" append-to-body>
      <!-- 创建表单 -->
      <el-form v-if="!showKey" ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" placeholder="请输入 API Key 名称" maxlength="50" />
        </el-form-item>
      </el-form>

      <!-- 显示密钥（创建成功后） -->
      <div v-else>
        <el-alert
          type="warning"
          title="请立即复制保存，关闭后无法再次查看完整密钥！"
          :closable="false"
          show-icon
          style="margin-bottom: 20px;"
        />

        <el-form label-width="80px">
          <el-form-item label="名称">
            <el-input :value="createdKey.name" disabled />
          </el-form-item>
          <el-form-item label="密钥">
            <el-input :value="createdKey.apiKey" readonly>
              <template #append>
                <el-button icon="DocumentCopy" @click="handleCopyKey">复制</el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="!showKey" @click="createDialogVisible = false">取 消</el-button>
          <el-button v-if="!showKey" type="primary" @click="submitCreate" :loading="createLoading">确 定</el-button>
          <el-button v-else type="primary" @click="closeShowKey">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="MyApikey">
import { listMyApiKeys, createApiKey, toggleApiKey, deleteApiKey } from '@/api/business/fbs/myApikey'

const { proxy } = getCurrentInstance()

// 数据
const loading = ref(false)
const keyList = ref([])

// 创建对话框
const createDialogVisible = ref(false)
const createLoading = ref(false)
const showKey = ref(false)
const createFormRef = ref()
const createForm = ref({
  name: undefined
})
const createRules = {
  name: [
    { required: true, message: '名称不能为空', trigger: 'blur' }
  ]
}
const createdKey = ref({
  name: undefined,
  apiKey: undefined
})

// 计算属性
const createDialogTitle = computed(() => {
  return showKey.value ? 'API Key 创建成功' : '创建 API Key'
})

// 方法
/** 查询列表 */
function getList() {
  loading.value = true
  listMyApiKeys().then(response => {
    keyList.value = response.data || []
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 创建按钮操作 */
function handleCreate() {
  resetCreateForm()
  showKey.value = false
  createDialogVisible.value = true
}

/** 提交创建 */
function submitCreate() {
  proxy.$refs.createFormRef.validate(valid => {
    if (valid) {
      createLoading.value = true
      createApiKey(createForm.value).then(response => {
        createdKey.value = {
          name: response.data.name,
          apiKey: response.data.apiKey
        }
        showKey.value = true
        getList()
      }).catch(() => {
        createLoading.value = false
      }).finally(() => {
        createLoading.value = false
      })
    }
  })
}

/** 复制密钥 */
function handleCopyKey() {
  navigator.clipboard.writeText(createdKey.value.apiKey).then(() => {
    proxy.$modal.msgSuccess('已复制到剪贴板')
  }).catch(() => {
    proxy.$modal.msgError('复制失败，请手动复制')
  })
}

/** 关闭显示密钥对话框 */
function closeShowKey() {
  createDialogVisible.value = false
  showKey.value = false
}

/** 禁用/启用操作 */
function handleToggle(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  
  proxy.$modal.confirm(`确认要${action} API Key "${row.name}" 吗？`).then(() => {
    toggleApiKey(row.id, newStatus).then(() => {
      proxy.$modal.msgSuccess(`${action}成功`)
      getList()
    })
  }).catch(() => {})
}

/** 删除按钮操作 */
function handleDelete(row) {
  proxy.$modal.confirm(`确认删除 API Key "${row.name}" 吗？删除后无法恢复，使用此 Key 的 Skill 将无法调用后端 API。`).then(() => {
    deleteApiKey(row.id).then(() => {
      proxy.$modal.msgSuccess('删除成功')
      getList()
    })
  }).catch(() => {})
}

/** 重置创建表单 */
function resetCreateForm() {
  createForm.value = {
    name: undefined
  }
  if (proxy.$refs.createFormRef) {
    proxy.$refs.createFormRef.resetFields()
  }
}

// 生命周期
getList()
</script>
