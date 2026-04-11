<template>
  <div class="app-container">
    <!-- 激活表单 -->
    <el-card shadow="hover" class="activate-card">
      <template #header>
        <div class="card-header">
          <el-icon><Ticket /></el-icon>
          <span>激活授权码</span>
        </div>
      </template>
      <el-form ref="activateRef" :model="activateForm" :rules="activateRules" label-width="100px">
        <el-form-item label="授权码" prop="authCode">
          <el-input
            v-model="activateForm.authCode"
            placeholder="请输入授权码"
            clearable
            style="width: 360px"
            @keyup.enter="handleActivate"
          >
            <template #prefix>
              <el-icon><Key /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            icon="CircleCheck"
            :loading="activating"
            @click="handleActivate"
          >立即激活</el-button>
          <el-button icon="Refresh" @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 激活结果 -->
    <el-card v-if="resultData" shadow="hover" class="result-card" :class="resultClass">
      <template #header>
        <div class="card-header">
          <el-icon><component :is="resultIcon" /></el-icon>
          <span>{{ resultTitle }}</span>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="场景包名称">{{ resultData.packName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="场景包ID">{{ resultData.packId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="到期时间">{{ parseTime(resultData.expiresAt) || '永久' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="resultData.code === 200 ? 'success' : 'danger'" size="small">
            {{ resultData.code === 200 ? '激活成功' : '激活失败' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 最近激活记录 -->
    <el-card shadow="hover" class="history-card" style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <el-icon><Clock /></el-icon>
          <span>最近激活记录</span>
        </div>
      </template>
      <el-table :data="activateHistory" style="width: 100%">
        <el-table-column label="授权码" align="center" prop="authCode" width="200" />
        <el-table-column label="场景包" align="center" prop="packName" min-width="160" show-overflow-tooltip />
        <el-table-column label="结果" align="center" prop="result" width="120">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.success ? 'success' : 'danger'">
              {{ scope.row.success ? '成功' : scope.row.message || '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" align="center" prop="time" width="170" />
      </el-table>
      <el-empty v-if="activateHistory.length === 0" description="暂无激活记录" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup name="FbsActivateAuthCode">
import { ref, reactive } from 'vue'
import { activateAuthCode } from "@/api/business/fbs/mySelfService"
import { parseTime } from '@/utils/WxFbsir'

const { proxy } = getCurrentInstance()

const STORAGE_KEY = 'fbs_activate_history'
const MAX_HISTORY = 50

const activating = ref(false)
const resultData = ref(null)
const resultTitle = ref('')
const resultClass = ref('')
const resultIcon = ref('SuccessFilled')

/** 从 localStorage 读取历史记录 */
function loadHistory() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch { return [] }
}

/** 保存历史记录到 localStorage */
function saveHistory(list) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list.slice(0, MAX_HISTORY)))
}

const activateHistory = ref(loadHistory())

const data = reactive({
  activateForm: {
    authCode: undefined
  },
  activateRules: {
    authCode: [{ required: true, message: "授权码不能为空", trigger: "blur" }]
  }
})

const { activateForm, activateRules } = toRefs(data)

/** 激活 */
function handleActivate() {
  proxy.$refs["activateRef"].validate(valid => {
    if (valid) {
      activating.value = true
      resultData.value = null
      activateAuthCode(activateForm.value).then(response => {
        // 激活成功
        resultData.value = response.data || {}
        resultData.value.code = response.code
        resultTitle.value = '激活成功'
        resultClass.value = 'result-success'
        resultIcon.value = 'SuccessFilled'
        // 记录历史
        activateHistory.value.unshift({
          authCode: activateForm.value.authCode,
          packName: (response.data && response.data.packName) || '-',
          success: true,
          time: parseTime(new Date())
        })
        saveHistory(activateHistory.value)
        proxy.$modal.msgSuccess("激活成功")
      }).catch(error => {
        // 激活失败（若依 request.js reject Error 对象，消息在 error.message 中）
        const msg = (error && error.message) || '激活失败'
        resultData.value = { code: 400, message: msg }
        resultTitle.value = msg
        resultClass.value = 'result-fail'
        resultIcon.value = 'CircleCloseFilled'
        // 记录历史
        activateHistory.value.unshift({
          authCode: activateForm.value.authCode,
          packName: '-',
          success: false,
          message: msg,
          time: parseTime(new Date())
        })
        saveHistory(activateHistory.value)
      }).finally(() => {
        activating.value = false
      })
    }
  })
}

/** 重置表单 */
function resetForm() {
  activateForm.value = { authCode: undefined }
  resultData.value = null
  proxy.resetForm("activateRef")
}
</script>

<style lang="scss" scoped>
.app-container {
  max-width: 900px;

  .mb8 {
    margin-bottom: 8px;
  }

  .card-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 600;
  }

  .activate-card {
    margin-bottom: 16px;
  }

  .result-card {
    margin-bottom: 16px;

    &.result-success {
      :deep(.el-card__header) {
        background: #f0f9eb;
        color: #67c23a;
      }
    }

    &.result-fail {
      :deep(.el-card__header) {
        background: #fef0f0;
        color: #f56c6c;
      }
    }
  }
}
</style>
