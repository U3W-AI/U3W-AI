<template>
  <div class="app-container board-security-page" :aria-busy="loading">
    <header class="page-heading">
      <p class="brand-line">福帮手 FBSir · 独董会</p>
      <h1>安全回执</h1>
      <p>仅展示当前登录成员可见的安全投影；技术动作完成不等于交付或业务完成。</p>
    </header>

    <el-alert
      v-if="!candidateEnabled"
      title="候选功能默认关闭"
      description="W4b.2 安全回执页尚未发布，当前环境不会调用候选接口。"
      type="info"
      show-icon
      :closable="false"
    />

    <el-alert
      v-else-if="!canViewPermission"
      title="当前账号缺少安全回执查看权限"
      description="页面不会通过手工企业、成员或用户编号绕过若依权限和服务端成员校验。"
      type="warning"
      show-icon
      :closable="false"
    />

    <template v-else>
      <el-card shadow="never" class="context-card">
        <el-form label-position="top" :inline="true">
          <el-form-item label="当前企业">
            <el-select
              v-model="selectedTenantId"
              filterable
              :disabled="contextLoading || contexts.length === 0"
              placeholder="请选择企业"
              aria-label="当前企业"
              @change="loadFirstPage"
            >
              <el-option
                v-for="context in contexts"
                :key="context.tenantId"
                :label="context.tenantName"
                :value="context.tenantId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="数据操作">
            <el-button :loading="loading" :disabled="!selectedTenantId" @click="loadFirstPage">
              刷新安全回执
            </el-button>
          </el-form-item>
        </el-form>
        <p class="boundary-note">身份与企业范围由服务端派生；页面不接受成员或用户编号旁路。</p>
      </el-card>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="page-alert"
        aria-live="assertive"
      />
      <el-alert
        v-if="truncated && !errorMessage"
        title="当前安全回执尚有下一页"
        description="继续加载仍会逐页执行字段白名单、重复记录和企业边界校验。"
        type="warning"
        show-icon
        :closable="false"
        class="page-alert"
      />
      <el-skeleton v-if="loading && events.length === 0" :rows="8" animated class="content-card" />
      <el-empty
        v-else-if="!contextLoading && selectedTenantId && events.length === 0 && !errorMessage"
        description="当前企业暂无可展示的安全回执"
        class="content-card"
      />

      <el-card v-else-if="events.length" shadow="never" class="content-card">
        <template #header>
          <div class="card-heading">
            <div>
              <h2>{{ selectedContext?.tenantName || '当前企业' }}</h2>
              <p>已安全核验 {{ events.length }} 条不可变动作回执。</p>
            </div>
            <el-button :loading="loading" :disabled="!truncated || !nextCursor" @click="loadNextPage">
              加载下一页
            </el-button>
          </div>
        </template>

        <el-table :data="events" class="security-table" empty-text="暂无安全回执">
          <el-table-column label="时间" min-width="168">
            <template #default="scope">{{ formatTime(scope.row.occurredAt) }}</template>
          </el-table-column>
          <el-table-column prop="action" label="动作" min-width="220" />
          <el-table-column prop="actorType" label="触发方" width="100" />
          <el-table-column label="客户端" min-width="140">
            <template #default="scope">{{ formatBoardCandidateReference(scope.row.clientRef) }}</template>
          </el-table-column>
          <el-table-column label="授权族" min-width="140">
            <template #default="scope">{{ formatBoardCandidateReference(scope.row.familyRef) }}</template>
          </el-table-column>
          <el-table-column label="连接绑定" min-width="140">
            <template #default="scope">{{ formatBoardCandidateReference(scope.row.bindingRef) }}</template>
          </el-table-column>
          <el-table-column label="关联编号" min-width="150">
            <template #default="scope">{{ formatBoardCandidateReference(scope.row.correlationRef) }}</template>
          </el-table-column>
          <el-table-column label="证据" min-width="140">
            <template #default="scope">
              <el-tag type="info">{{ scope.row.evidenceLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="固定原因码" min-width="190">
            <template #default="scope">{{ scope.row.reasonCode || '该回执模型未提供' }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup name="IndependentBoardMeSecurityCandidate">
import { computed, onMounted, ref } from 'vue'
import { getIndependentBoardContexts } from '@/api/business/independentBoard'
import { listBoardSecurityReceiptsCandidate } from '@/api/business/independentBoard/portalCandidate'
import { checkPermi } from '@/utils/permission'
import { parseBoardContexts } from '../reservationSafety'
import {
  formatBoardCandidateReference,
  isBoardPortalCandidateEnabled,
  parseBoardCandidateEnvelope,
  parseBoardSecurityEvent
} from '../../portalCandidateModel'

const candidateEnabled = isBoardPortalCandidateEnabled(import.meta.env)
const canViewPermission = checkPermi(['my:independent-board:security:view'])
const MAX_SECURITY_RECEIPTS = 5000
const contexts = ref([])
const selectedTenantId = ref(null)
const events = ref([])
const nextCursor = ref(null)
const truncated = ref(false)
const contextLoading = ref(false)
const receiptLoading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

const loading = computed(() => contextLoading.value || receiptLoading.value)
const selectedContext = computed(() =>
  contexts.value.find(item => item.tenantId === selectedTenantId.value) || null)

onMounted(() => {
  if (candidateEnabled && canViewPermission) loadContexts()
})

async function loadContexts() {
  contextLoading.value = true
  errorMessage.value = ''
  try {
    const response = await getIndependentBoardContexts()
    contexts.value = [...parseBoardContexts(response?.data)]
    selectedTenantId.value = contexts.value[0]?.tenantId ?? null
    if (selectedTenantId.value) await loadFirstPage()
  } catch {
    contexts.value = []
    selectedTenantId.value = null
    clearReceipts()
    errorMessage.value = '企业上下文加载失败或返回了非安全数据。'
  } finally {
    contextLoading.value = false
  }
}

function loadFirstPage() {
  return loadReceipts(null, false)
}

function loadNextPage() {
  if (!truncated.value || !nextCursor.value) return Promise.resolve()
  return loadReceipts(nextCursor.value, true)
}

async function loadReceipts(cursor, append) {
  const tenantId = selectedTenantId.value
  const sequence = ++requestSequence
  if (!Number.isSafeInteger(tenantId) || tenantId <= 0) {
    clearReceipts()
    return
  }
  receiptLoading.value = true
  errorMessage.value = ''
  if (!append) clearReceipts()
  try {
    const response = await listBoardSecurityReceiptsCandidate(tenantId, cursor)
    const safePage = parseBoardCandidateEnvelope(
      response?.data, parseBoardSecurityEvent, tenantId
    )
    if (sequence !== requestSequence || selectedTenantId.value !== tenantId) return
    const combined = append ? [...events.value, ...safePage.records] : [...safePage.records]
    if (combined.length > MAX_SECURITY_RECEIPTS) {
      throw new Error('安全回执超出浏览器安全展示上限。')
    }
    if (new Set(combined.map(item => item.eventRef)).size !== combined.length) {
      throw new Error('安全回执分页包含重复记录。')
    }
    events.value = combined
    truncated.value = safePage.truncated
    nextCursor.value = safePage.nextCursor
  } catch {
    if (sequence !== requestSequence) return
    clearReceipts()
    errorMessage.value = '安全回执加载失败或返回了非安全数据，页面已停止展示。'
  } finally {
    if (sequence === requestSequence) receiptLoading.value = false
  }
}

function clearReceipts() {
  events.value = []
  truncated.value = false
  nextCursor.value = null
}

function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'medium'
  }).format(new Date(value))
}
</script>

<style lang="scss" scoped>
.board-security-page { min-height: calc(100vh - 84px); background: #f6f8fb; }
.page-heading { margin-bottom: 18px; }
.page-heading h1 { margin: 4px 0 8px; color: #14233b; font-size: 28px; }
.page-heading p, .boundary-note, .card-heading p { color: #667085; }
.brand-line { margin: 0; color: #246bfd !important; font-weight: 700; }
.context-card, .content-card, .page-alert { margin-bottom: 16px; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.card-heading h2 { margin: 0 0 4px; color: #14233b; }
.card-heading p { margin: 0; }
.security-table { width: 100%; }
@media (max-width: 768px) {
  .card-heading { align-items: flex-start; flex-direction: column; }
  .content-card { overflow-x: auto; }
}
</style>
