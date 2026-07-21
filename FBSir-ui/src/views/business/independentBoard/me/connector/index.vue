<template>
  <div class="app-container board-candidate-page" :aria-busy="loading">
    <header class="page-heading">
      <div>
        <p class="brand-line">福帮手 FBSir · 独董会</p>
        <h1>连接与授权</h1>
        <p>核验 WorkBuddy 兼容连接的服务端聚合状态；连接不是使用独董会首值的前提。</p>
      </div>
    </header>

    <el-alert
      v-if="!candidateEnabled"
      title="候选功能默认关闭"
      description="W4b.2 页面尚未发布，当前环境不会调用候选接口或改变任何连接状态。"
      type="info"
      show-icon
      :closable="false"
    />

    <el-alert
      v-else-if="!canViewPermission"
      title="当前账号缺少连接查看权限"
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
              @change="loadConnector"
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
            <el-button
              :loading="loading"
              :disabled="!selectedTenantId"
              @click="loadConnector(selectedTenantId)"
            >刷新安全状态</el-button>
          </el-form-item>
        </el-form>
        <p class="boundary-note">企业与成员身份由若依登录态和服务端 current-read 确认。</p>
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
      <el-skeleton v-if="loading" :rows="7" animated class="content-card" />
      <el-empty
        v-else-if="!contextLoading && contexts.length === 0"
        description="当前账号暂无可用企业"
        class="content-card"
      />

      <el-card v-else-if="connector" shadow="never" class="content-card">
        <template #header>
          <div class="card-heading">
            <div>
              <p class="eyebrow">当前连接</p>
              <h2>{{ selectedContext?.tenantName || '当前企业' }}</h2>
            </div>
            <el-tag :type="stateMeta.type" effect="light">{{ stateMeta.label }}</el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border class="connection-detail">
          <el-descriptions-item label="有效方案">{{ planLabel }}</el-descriptions-item>
          <el-descriptions-item label="证据级别">{{ connector.evidenceLevel }}</el-descriptions-item>
          <el-descriptions-item label="客户端">{{ formatBoardCandidateReference(connector.clientRef) }}</el-descriptions-item>
          <el-descriptions-item label="授权族">{{ formatBoardCandidateReference(connector.familyRef) }}</el-descriptions-item>
          <el-descriptions-item label="连接绑定">{{ formatBoardCandidateReference(connector.bindingRef) }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ connector.version }}</el-descriptions-item>
          <el-descriptions-item label="签发时间">{{ formatTime(connector.issuedAt) }}</el-descriptions-item>
          <el-descriptions-item label="到期时间">{{ formatTime(connector.expiresAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近访问" :span="2">{{ formatTime(connector.lastSeenAt) }}</el-descriptions-item>
        </el-descriptions>

        <section class="scope-section" aria-labelledby="scope-heading">
          <h3 id="scope-heading">授权范围</h3>
          <div v-if="connector.scopes.length" class="scope-list">
            <el-tag v-for="scope in connector.scopes" :key="scope" type="info">{{ scope }}</el-tag>
          </div>
          <p v-else>当前安全状态未确认任何 Scope。</p>
        </section>

        <el-alert
          title="候选页仅提供只读核验"
          description="断开、同意和显式重授权仍由后续事务、回执及并发门禁单独解锁。"
          type="warning"
          show-icon
          :closable="false"
        />
        <div class="actions">
          <el-button :disabled="!actionPolicy.canReturnToWorkBuddy" @click="explainReturn">
            返回 WorkBuddy
          </el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup name="IndependentBoardMeConnectorCandidate">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getIndependentBoardContexts } from '@/api/business/independentBoard'
import { getBoardConnectorCandidate } from '@/api/business/independentBoard/portalCandidate'
import { checkPermi } from '@/utils/permission'
import { parseBoardContexts } from '../reservationSafety'
import {
  connectorActionPolicy,
  formatBoardCandidateReference,
  isBoardPortalCandidateEnabled,
  parseBoardConnectorView
} from '../../portalCandidateModel'

const candidateEnabled = isBoardPortalCandidateEnabled(import.meta.env)
const canViewPermission = checkPermi(['my:independent-board:connector:view'])
const contexts = ref([])
const selectedTenantId = ref(null)
const connector = ref(null)
const contextLoading = ref(false)
const connectorLoading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

const loading = computed(() => contextLoading.value || connectorLoading.value)
const selectedContext = computed(() =>
  contexts.value.find(item => item.tenantId === selectedTenantId.value) || null)
const actionPolicy = computed(() => connectorActionPolicy(connector.value?.uiState || 'UNKNOWN'))
const planLabel = computed(() => connector.value?.effectivePlanCode === 'BOARD_VIP' ? 'VIP 版' : '免费版')
const stateMeta = computed(() => ({
  NOT_CONNECTED: { label: '尚未连接', type: 'info' },
  PENDING_ACTIVATION: { label: '等待安全激活', type: 'warning' },
  ACTIVE: { label: 'VIP 连接已生效', type: 'success' },
  REAUTH_REQUIRED: { label: '需要显式重新授权', type: 'danger' },
  UNKNOWN: { label: '状态待核验', type: 'danger' }
}[connector.value?.uiState] || { label: '状态待核验', type: 'danger' }))

onMounted(() => {
  if (candidateEnabled && canViewPermission) loadContexts()
})

async function loadContexts() {
  contextLoading.value = true
  errorMessage.value = ''
  connector.value = null
  try {
    const response = await getIndependentBoardContexts()
    contexts.value = [...parseBoardContexts(response?.data)]
    selectedTenantId.value = contexts.value[0]?.tenantId ?? null
    if (selectedTenantId.value) await loadConnector(selectedTenantId.value)
  } catch {
    contexts.value = []
    selectedTenantId.value = null
    errorMessage.value = '企业上下文加载失败或返回了非安全数据。'
  } finally {
    contextLoading.value = false
  }
}

async function loadConnector(tenantId = selectedTenantId.value) {
  const sequence = ++requestSequence
  connector.value = null
  errorMessage.value = ''
  if (!Number.isSafeInteger(tenantId) || tenantId <= 0) return
  connectorLoading.value = true
  try {
    const response = await getBoardConnectorCandidate(tenantId)
    const safeView = parseBoardConnectorView(response?.data, tenantId)
    if (sequence !== requestSequence || selectedTenantId.value !== tenantId) return
    connector.value = safeView
  } catch {
    if (sequence !== requestSequence) return
    errorMessage.value = '连接状态无法安全核验；页面不会猜测连接已生效。'
  } finally {
    if (sequence === requestSequence) connectorLoading.value = false
  }
}

function formatTime(value) {
  if (!value) return '未记录'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'medium'
  }).format(new Date(value))
}

function explainReturn() {
  ElMessage.info('请返回已验证的 WorkBuddy 窗口继续；候选页不会发明私有跳转地址。')
}
</script>

<style lang="scss" scoped>
.board-candidate-page { min-height: calc(100vh - 84px); background: #f6f8fb; }
.page-heading { margin-bottom: 18px; }
.page-heading h1 { margin: 4px 0 8px; color: #14233b; font-size: 28px; }
.page-heading p, .boundary-note, .scope-section p { color: #667085; }
.brand-line, .eyebrow { margin: 0; color: #246bfd; font-weight: 700; }
.context-card, .content-card, .page-alert { margin-bottom: 16px; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.card-heading h2 { margin: 4px 0 0; color: #14233b; }
.connection-detail { margin-bottom: 20px; }
.scope-section { margin: 18px 0; }
.scope-section h3 { margin-bottom: 10px; color: #14233b; }
.scope-list { display: flex; flex-wrap: wrap; gap: 8px; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
@media (max-width: 768px) {
  .card-heading { align-items: flex-start; flex-direction: column; }
  :deep(.el-descriptions__body) { overflow-x: auto; }
}
</style>
