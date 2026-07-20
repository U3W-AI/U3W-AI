<template>
  <div
    class="board-page"
    v-loading="contextLoading"
    :aria-busy="contextLoading || dashboardLoading"
  >
    <span class="sr-only" aria-live="polite">{{ loadingAnnouncement }}</span>
    <section class="board-hero">
      <div class="brand-lockup" aria-label="福帮手独董会 FBSir">
        <span class="brand-mark" aria-hidden="true">福</span>
        <div>
          <div class="brand-line">福帮手 <span>FBSir</span></div>
          <h1>独董会</h1>
          <p>把关键议题交给一组独立视角，在有限时间内形成可执行结论。</p>
        </div>
      </div>

      <div class="context-panel">
        <label for="board-context">当前企业</label>
        <el-select
          id="board-context"
          v-model="selectedTenantId"
          class="context-select"
          placeholder="请选择企业"
          aria-label="当前企业"
          filterable
          :disabled="contextLoading || contexts.length === 0"
          @change="handleContextChange"
        >
          <el-option
            v-for="context in contexts"
            :key="String(context.tenantId)"
            :label="context.tenantName"
            :value="context.tenantId"
          />
        </el-select>
        <el-button
          :icon="Refresh"
          circle
          plain
          aria-label="刷新独董会数据"
          :disabled="!selectedTenantId"
          :loading="dashboardLoading"
          @click="loadDashboard()"
        />
      </div>
    </section>

    <el-alert
      v-if="contextError"
      class="page-alert"
      type="error"
      title="企业上下文加载失败"
      :closable="false"
      show-icon
      aria-live="assertive"
    >
      <template #default>
        <div class="alert-content">
          <span>{{ contextError }}</span>
          <el-button type="primary" link @click="loadContexts">重新加载</el-button>
        </div>
      </template>
    </el-alert>

    <el-empty
      v-else-if="!contextLoading && contexts.length === 0"
      class="context-empty"
      description="当前账号暂无可用企业，请联系管理员完成企业成员配置。"
    />

    <template v-else-if="selectedTenantId">
      <el-alert
        v-if="dashboardError"
        class="page-alert"
        type="error"
        title="独董会数据加载失败"
        :closable="false"
        show-icon
        aria-live="assertive"
      >
        <template #default>
          <div class="alert-content">
            <span>{{ dashboardError }}</span>
            <el-button type="primary" link @click="loadDashboard()">重新加载</el-button>
          </div>
        </template>
      </el-alert>

      <el-skeleton
        v-if="dashboardLoading && !dashboardReady"
        class="dashboard-skeleton"
        :rows="8"
        animated
      />

      <div v-if="dashboardReady" v-loading="dashboardLoading" class="dashboard-content">
        <section class="summary-grid" aria-label="独董会权益与额度概览">
          <el-card class="summary-card entitlement-card" shadow="never">
            <div class="card-eyebrow">当前权益</div>
            <div class="plan-row">
              <div>
                <div class="plan-name">{{ planName }}</div>
                <div class="plan-caption">实际生效方案</div>
              </div>
              <el-tag :type="activationTag.type" :class="activationTag.className" effect="light" round>
                {{ activationTag.label }}
              </el-tag>
            </div>
            <div v-if="isPendingConnection" class="connector-pending">
              <el-icon><WarningFilled /></el-icon>
              <div>
                <strong>待连接</strong>
                <p>VIP 权益已授予，完成 WorkBuddy Connector 验证后生效；当前继续按免费版额度运行。</p>
              </div>
            </div>
            <div v-else-if="isRevoked" class="entitlement-revoked">
              <el-icon><WarningFilled /></el-icon>
              <div>
                <strong>权益已撤销</strong>
                <p>原授予已由管理员撤销，当前明确按免费版额度运行；如有疑问请联系管理员核验权益回执。</p>
              </div>
            </div>
            <div v-else class="plan-features">
              <span>{{ entitlement.secretaryEnabled ? '已含独董会秘书' : '暂不含独董会秘书' }}</span>
              <span>{{ seatLimitText }}</span>
            </div>
          </el-card>

          <el-card class="summary-card quota-card" shadow="never">
            <div class="card-eyebrow">今日会议额度</div>
            <div class="quota-main">
              <strong>{{ numberOrZero(entitlement.remainingCount) }}</strong>
              <span>/ {{ numberOrZero(entitlement.dailyMeetingLimit) }} 次</span>
            </div>
            <el-progress
              :percentage="quotaPercentage"
              :stroke-width="8"
              :show-text="false"
              :color="quotaPercentage >= 100 ? '#f56c6c' : '#246bfd'"
            />
            <div class="quota-details">
              <span>已使用 {{ numberOrZero(entitlement.usedCount) }}</span>
              <span>已预留 {{ numberOrZero(entitlement.reservedCount) }}</span>
            </div>
          </el-card>

          <el-card class="summary-card meeting-card" shadow="never">
            <div class="card-eyebrow">开始一次独董会</div>
            <div class="meeting-copy">
              每次最多 {{ numberOrZero(entitlement.agendaLimit) }} 个议题，{{ seatLimitText }}。
            </div>
            <el-button
              class="reserve-button"
              type="primary"
              :disabled="!canReserve"
              @click="openReservationDialog"
            >
              {{ canReserve ? '预约会议' : '今日额度已用完' }}
            </el-button>
          </el-card>
        </section>

        <section class="content-grid">
          <el-card class="history-card" shadow="never">
            <template #header>
              <div class="section-header">
                <div>
                  <div class="section-title">最近会议</div>
                  <div class="section-subtitle">仅展示当前企业下由你发起的会议预约</div>
                </div>
                <el-button type="primary" link :disabled="dashboardLoading" @click="loadDashboard()">
                  刷新
                </el-button>
              </div>
            </template>

            <el-table v-if="recentMeetings.length" :data="recentMeetings" class="history-table">
              <el-table-column label="预约时间" min-width="166">
                <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
              </el-table-column>
              <el-table-column prop="operationId" label="会议编号" min-width="190" show-overflow-tooltip />
              <el-table-column label="议题 / 席位" min-width="120">
                <template #default="scope">
                  {{ numberOrZero(scope.row.agendaCount) }} / {{ numberOrZero(scope.row.seatCount) }}
                </template>
              </el-table-column>
              <el-table-column label="方案" min-width="100">
                <template #default="scope">{{ formatPlan(scope.row.effectivePlanCode) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="108" align="right">
                <template #default="scope">
                  <el-tag :type="meetingStatus(scope.row.status).type" effect="light" round>
                    {{ meetingStatus(scope.row.status).label }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="还没有会议记录，预约第一场独董会吧。" :image-size="76" />
          </el-card>

          <aside class="future-column" aria-label="后续能力">
            <el-card class="future-card" shadow="never">
              <div class="future-icon watch-icon"><el-icon><Watch /></el-icon></div>
              <div class="future-copy">
                <div class="future-title">Apple Watch</div>
                <p>云上资产的轻量操作入口</p>
              </div>
              <el-tag type="info" effect="plain" round>{{ capabilityLabel(watchState) }}</el-tag>
            </el-card>
            <el-card class="future-card" shadow="never">
              <div class="future-icon webhook-icon"><el-icon><Connection /></el-icon></div>
              <div class="future-copy">
                <div class="future-title">Webhook 订阅</div>
                <p>会议进展与独董会内参推送</p>
              </div>
              <el-tag type="info" effect="plain" round>{{ capabilityLabel(webhookState) }}</el-tag>
            </el-card>
            <div class="future-note">能力状态仅作路线说明，当前页面不提供操作入口。</div>
          </aside>
        </section>
      </div>
    </template>

    <el-dialog
      v-model="reservationDialogVisible"
      title="预约独董会"
      width="min(520px, calc(100vw - 32px))"
      destroy-on-close
      append-to-body
    >
      <div class="dialog-context">
        <span>当前企业</span>
        <strong>{{ selectedContextName }}</strong>
      </div>
      <div class="draft-reference">
        <span>预约编号</span>
        <code>{{ reservationForm.operationId || '--' }}</code>
      </div>
      <el-alert
        v-if="reservationOutcomeState === 'UNKNOWN'"
        class="reservation-alert"
        type="warning"
        title="预约结果待确认"
        :closable="false"
        show-icon
      >
        <template #default>
          {{ reservationReconciliationMessage || '本次编号已保留。你可以刷新回读，或按原编号安全重试；请勿直接新建另一笔预约。' }}
        </template>
      </el-alert>
      <el-alert
        v-else-if="reservationOutcomeState === 'NOT_FOUND'"
        class="reservation-alert"
        type="info"
        title="尚未发现预约记录"
        :closable="false"
        show-icon
      >
        <template #default>
          服务端当前读未发现该编号。原编号和原载荷已保留，可以按同一编号安全重试。
        </template>
      </el-alert>
      <el-alert
        v-if="reservationStorageError"
        class="reservation-alert"
        type="error"
        title="无法安全保存预约草稿"
        :closable="false"
        show-icon
      >
        <template #default>{{ reservationStorageError }}</template>
      </el-alert>
      <el-form ref="reservationFormRef" :model="reservationForm" :rules="reservationRules" label-position="top">
        <el-form-item label="议题数量" prop="agendaCount">
          <el-input-number
            v-model="reservationForm.agendaCount"
            :min="1"
            :max="agendaLimit"
            :disabled="reservationSubmitting || reservationOutcomeState !== 'DRAFT'"
            controls-position="right"
            @change="persistCurrentReservationDraft"
          />
          <span class="field-hint">本方案每次最多 {{ agendaLimit }} 个议题</span>
        </el-form-item>
        <el-form-item label="参会席位" prop="seatCount">
          <el-input-number
            v-model="reservationForm.seatCount"
            :min="1"
            :max="seatSafetyLimit"
            :disabled="reservationSubmitting || reservationOutcomeState !== 'DRAFT'"
            controls-position="right"
            @change="persistCurrentReservationDraft"
          />
          <span class="field-hint">{{ seatDialogHint }}</span>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="`预约成功后，今日剩余额度将减少 1 次。当前剩余 ${numberOrZero(entitlement.remainingCount)} 次。`"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button
            v-if="reservationForm.operationId"
            type="danger"
            link
            :disabled="reservationSubmitting"
            @click="abandonAndCreateReservationDraft"
          >
            放弃并新建
          </el-button>
          <div class="dialog-footer-actions">
            <el-button :disabled="reservationSubmitting" @click="reservationDialogVisible = false">
              {{ reservationOutcomeState === 'DRAFT' ? '取消' : '稍后处理' }}
            </el-button>
            <el-button type="primary" :loading="reservationSubmitting" @click="submitReservation">
              {{ reservationOutcomeState === 'DRAFT' ? '确认预约' : '按原编号重试' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="IndependentBoardMe">
import { computed, onMounted, reactive, ref } from 'vue'
import { Connection, Refresh, WarningFilled, Watch } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getIndependentBoardContexts,
  getIndependentBoardDashboard,
  getIndependentBoardMeetingReservation,
  reserveIndependentBoardMeeting
} from '@/api/business/independentBoard'
import {
  parseBoardContext,
  parseBoardContexts,
  parseBoardDashboardEnvelope,
  parseBoardEntitlementSnapshot,
  parseBoardRecentMeetings,
  parseStoredDraft,
  persistDraftWithReadback,
  reservationMatchesSubmitted
} from './reservationSafety'

const CONTEXT_STORAGE_KEY = 'independent-board-tenant'
const RESERVATION_DRAFT_STORAGE_PREFIX = 'independent-board-reservation-draft'
const INITIAL_SAFETY_MAX_SEAT_COUNT = 100

const contextLoading = ref(false)
const dashboardLoading = ref(false)
const reservationSubmitting = ref(false)
const contextError = ref('')
const dashboardError = ref('')
const dashboardReady = ref(false)
const contexts = ref([])
const selectedTenantId = ref(null)
const entitlement = ref(emptyEntitlement())
const recentMeetings = ref([])
const connectorState = ref('NOT_CONNECTED')
const webhookState = ref('COMING_SOON')
const watchState = ref('COMING_SOON')

const reservationDialogVisible = ref(false)
const reservationFormRef = ref(null)
const reservationForm = reactive({
  tenantId: null,
  userId: null,
  operationId: '',
  agendaCount: 1,
  seatCount: 1
})
const reservationOutcomeState = ref('DRAFT')
const reservationReconciliationMessage = ref('')
const reservationStorageError = ref('')
let dashboardRequestSequence = 0

const selectedContextName = computed(() => {
  return contexts.value.find(item => String(item.tenantId) === String(selectedTenantId.value))?.tenantName || '--'
})

const loadingAnnouncement = computed(() => {
  if (contextLoading.value) return '正在加载企业列表'
  if (dashboardLoading.value) return '正在加载独董会数据'
  return ''
})

const planName = computed(() => formatPlan(entitlement.value.effectivePlanCode))
const isPendingConnection = computed(() => {
  return connectorState.value === 'PENDING_CONNECTION' || entitlement.value.activationState === 'PENDING_CONNECTOR'
})
const isRevoked = computed(() => entitlement.value.activationState === 'REVOKED')
const agendaLimit = computed(() => Math.max(1, Number(entitlement.value.agendaLimit || 1)))
const seatSafetyLimit = computed(() => {
  const planLimit = Number(entitlement.value.seatLimit)
  return planLimit > 0 ? Math.min(planLimit, INITIAL_SAFETY_MAX_SEAT_COUNT) : INITIAL_SAFETY_MAX_SEAT_COUNT
})
const seatLimitText = computed(() => {
  const limit = Number(entitlement.value.seatLimit)
  return limit > 0 ? `最多 ${limit} 个席位` : '支持全员参与'
})
const seatDialogHint = computed(() => {
  return entitlement.value.seatLimit
    ? `本方案最多 ${entitlement.value.seatLimit} 个席位`
    : `全员方案首期单次最多 ${INITIAL_SAFETY_MAX_SEAT_COUNT} 个席位`
})
const canReserve = computed(() => {
  return dashboardReady.value
    && !dashboardError.value
    && Number(entitlement.value.remainingCount || 0) > 0
    && !dashboardLoading.value
})
const quotaPercentage = computed(() => {
  const limit = Number(entitlement.value.dailyMeetingLimit || 0)
  const consumed = Number(entitlement.value.usedCount || 0) + Number(entitlement.value.reservedCount || 0)
  return limit > 0 ? Math.min(100, Math.round((consumed / limit) * 100)) : 0
})
const activationTag = computed(() => {
  const state = entitlement.value.activationState
  if (isPendingConnection.value) {
    return { label: '待连接', type: 'warning', className: 'pending-tag' }
  }
  if (state === 'ACTIVE') return { label: 'VIP 已生效', type: 'success', className: '' }
  if (state === 'REVOKED') return { label: '权益已撤销', type: 'danger', className: '' }
  if (state === 'EXPIRED') return { label: '权益已过期', type: 'danger', className: '' }
  if (state === 'INVALID_ENTITLEMENT') return { label: '权益待核验', type: 'danger', className: '' }
  return { label: '免费版', type: 'info', className: '' }
})

const reservationRules = {
  agendaCount: [{ validator: validateAgendaCount, trigger: 'change' }],
  seatCount: [{ validator: validateSeatCount, trigger: 'change' }]
}

onMounted(loadContexts)

async function loadContexts() {
  contextLoading.value = true
  contextError.value = ''
  dashboardReady.value = false
  try {
    const response = await getIndependentBoardContexts()
    contexts.value = validateContexts(response?.data)
    const remembered = readRememberedTenant()
    const matched = contexts.value.find(item => String(item.tenantId) === remembered)
    selectedTenantId.value = matched?.tenantId ?? contexts.value[0]?.tenantId ?? null
    if (selectedTenantId.value) await loadDashboard()
  } catch (error) {
    contexts.value = []
    selectedTenantId.value = null
    contextError.value = error?.message || '暂时无法获取可用企业。'
  } finally {
    contextLoading.value = false
  }
}

async function handleContextChange(tenantId) {
  rememberTenant(tenantId)
  await loadDashboard(tenantId)
}

async function loadDashboard(tenantId = selectedTenantId.value) {
  if (!isPositiveSafeInteger(tenantId)) return false
  const requestedTenantId = tenantId
  const requestSequence = ++dashboardRequestSequence
  dashboardLoading.value = true
  dashboardReady.value = false
  dashboardError.value = ''
  try {
    const response = await getIndependentBoardDashboard(requestedTenantId)
    const data = validateDashboard(response?.data, requestedTenantId)
    if (requestSequence !== dashboardRequestSequence
        || String(selectedTenantId.value) !== String(requestedTenantId)) {
      return false
    }
    entitlement.value = data.entitlement
    recentMeetings.value = data.recentMeetings
    connectorState.value = data.connectorState
    webhookState.value = data.webhookState
    watchState.value = data.watchState
    dashboardReady.value = true
    return true
  } catch (error) {
    if (requestSequence !== dashboardRequestSequence
        || String(selectedTenantId.value) !== String(requestedTenantId)) {
      return false
    }
    entitlement.value = emptyEntitlement()
    recentMeetings.value = []
    connectorState.value = 'NOT_CONNECTED'
    webhookState.value = 'COMING_SOON'
    watchState.value = 'COMING_SOON'
    dashboardReady.value = false
    dashboardError.value = error?.message || '暂时无法获取权益和会议数据。'
    return false
  } finally {
    if (requestSequence === dashboardRequestSequence) {
      dashboardLoading.value = false
    }
  }
}

async function openReservationDialog() {
  if (!canReserve.value) return
  if (reservationForm.operationId
      && String(reservationForm.tenantId) !== String(selectedTenantId.value)) {
    const abandoned = await confirmAbandonDraft('另一企业仍有预约草稿，是否明确放弃并为当前企业新建？')
    if (!abandoned) return
    discardCurrentReservationDraft()
    createReservationDraft(selectedTenantId.value)
  } else if (!reservationForm.operationId) {
    const restored = readReservationDraft(selectedTenantId.value, entitlement.value.userId)
    if (restored) {
      Object.assign(reservationForm, restored)
      reservationOutcomeState.value = restored.outcomeState
    } else {
      createReservationDraft(selectedTenantId.value)
    }
  }
  reservationDialogVisible.value = true
}

function createReservationDraft(tenantId) {
  reservationForm.tenantId = tenantId
  reservationForm.userId = entitlement.value.userId
  reservationForm.operationId = createOperationId()
  reservationForm.agendaCount = 1
  reservationForm.seatCount = Math.min(3, seatSafetyLimit.value)
  reservationOutcomeState.value = 'DRAFT'
  reservationReconciliationMessage.value = ''
  reservationStorageError.value = ''
  reservationFormRef.value?.clearValidate()
  persistCurrentReservationDraft()
}

async function abandonAndCreateReservationDraft() {
  const abandoned = await confirmAbandonDraft(
    reservationOutcomeState.value !== 'DRAFT'
      ? '当前预约结果仍待确认。放弃后新建可能造成重复预约，确定继续吗？'
      : '确定放弃当前预约草稿并生成新编号吗？'
  )
  if (abandoned) {
    discardCurrentReservationDraft()
    createReservationDraft(selectedTenantId.value)
  }
}

async function confirmAbandonDraft(message) {
  try {
    await ElMessageBox.confirm(message, '放弃预约草稿', {
      confirmButtonText: '明确放弃并新建',
      cancelButtonText: '保留原草稿',
      type: 'warning'
    })
    return true
  } catch {
    return false
  }
}

async function submitReservation() {
  if (!reservationFormRef.value || reservationSubmitting.value) return
  const valid = await reservationFormRef.value.validate().catch(() => false)
  if (!valid) return

  const previousOutcomeState = reservationOutcomeState.value
  reservationOutcomeState.value = 'UNKNOWN'
  reservationReconciliationMessage.value = ''
  reservationStorageError.value = ''
  if (!persistCurrentReservationDraft()) {
    reservationOutcomeState.value = previousOutcomeState
    reservationStorageError.value = '浏览器未能可靠保存并回读当前草稿。为避免重复预约，本次请求已被阻止。请检查浏览器存储设置后重试。'
    return
  }

  reservationSubmitting.value = true
  const submittedPayload = Object.freeze({
    tenantId: reservationForm.tenantId,
    operationId: reservationForm.operationId,
    agendaCount: reservationForm.agendaCount,
    seatCount: reservationForm.seatCount
  })
  try {
    const response = await reserveIndependentBoardMeeting({
      ...submittedPayload
    })
    const reservation = validateReservationResponse(response?.data, submittedPayload)
    completeReservation(reservation.remainingCount)
    await loadDashboard(submittedPayload.tenantId)
  } catch {
    let exactReadback = null
    let exactReadFailed = false
    try {
      const lookupResponse = await getIndependentBoardMeetingReservation(
        submittedPayload.operationId, submittedPayload.tenantId)
      exactReadback = validateReservationLookup(lookupResponse?.data, submittedPayload.operationId)
    } catch {
      exactReadFailed = true
    }

    if (exactReadback?.found
        && reservationMatchesSubmitted(exactReadback.meeting, submittedPayload)) {
      completeReservation(exactReadback.meeting.remainingCount)
      await loadDashboard(submittedPayload.tenantId)
      return
    }

    const reconciliationConflict = exactReadback?.found === true
    const dashboardReadSucceeded = await loadDashboard(submittedPayload.tenantId)
    const dashboardReadback = dashboardReadSucceeded
      ? recentMeetings.value.find(item => item.operationId === submittedPayload.operationId)
      : null
    if (dashboardReadback
        && reservationMatchesSubmitted(dashboardReadback, submittedPayload)) {
      completeReservation(dashboardReadback.remainingCount)
    } else {
      const hasConflict = reconciliationConflict || Boolean(dashboardReadback)
      reservationOutcomeState.value = hasConflict || exactReadFailed ? 'UNKNOWN' : 'NOT_FOUND'
      reservationReconciliationMessage.value = hasConflict
        ? '服务端回读记录与本次冻结的编号、状态或议题/席位载荷不一致。页面不会把它视为成功，也不会轮换原编号；请保留草稿并联系管理员核验。'
        : ''
      persistCurrentReservationDraft()
    }
  } finally {
    reservationSubmitting.value = false
  }
}

function completeReservation(remainingCount) {
  reservationDialogVisible.value = false
  discardCurrentReservationDraft()
  reservationForm.tenantId = null
  reservationForm.userId = null
  reservationForm.operationId = ''
  reservationOutcomeState.value = 'DRAFT'
  reservationReconciliationMessage.value = ''
  reservationStorageError.value = ''
  ElMessage.success(`会议预约成功，今日还可预约 ${remainingCount} 次`)
}

function validateContexts(value) {
  return parseBoardContexts(value)
}

function validateDashboard(value, requestedTenantId) {
  const dashboard = parseBoardDashboardEnvelope(value)
  const context = parseBoardContext(dashboard.context)
  const entitlementView = parseBoardEntitlementSnapshot(dashboard.entitlement)
  const listedContext = contexts.value.find(item => String(item.tenantId) === String(requestedTenantId))
  if (String(context.tenantId) !== String(requestedTenantId)
      || String(entitlementView.tenantId) !== String(requestedTenantId)
      || String(context.memberId) !== String(entitlementView.memberId)
      || !listedContext
      || String(context.memberId) !== String(listedContext.memberId)
      || context.tenantName !== listedContext.tenantName
      || context.memberRole !== listedContext.memberRole) {
    throw new Error('企业数据绑定校验失败，页面已停止展示。')
  }

  const allowedConnectorStates = ['ACTIVE', 'PENDING_CONNECTION', 'NOT_CONNECTED']
  if (!allowedConnectorStates.includes(dashboard.connectorState)
      || dashboard.webhookState !== 'COMING_SOON'
      || dashboard.watchState !== 'COMING_SOON') {
    throw new Error('能力状态数据不受支持，请刷新后重试。')
  }
  if (dashboard.connectorState === 'PENDING_CONNECTION'
      && entitlementView.activationState !== 'PENDING_CONNECTOR') {
    throw new Error('连接状态与权益状态不一致，页面已停止展示。')
  }
  if (entitlementView.activationState === 'PENDING_CONNECTOR'
      && dashboard.connectorState !== 'PENDING_CONNECTION') {
    throw new Error('待连接权益缺少连接状态证据，页面已停止展示。')
  }
  if (dashboard.connectorState === 'ACTIVE'
      && (!entitlementView.connectorVerified || entitlementView.activationState !== 'ACTIVE')) {
    throw new Error('连接状态缺少有效权益证据，页面已停止展示。')
  }

  return Object.freeze({
    context,
    entitlement: entitlementView,
    recentMeetings: parseBoardRecentMeetings(dashboard.recentMeetings),
    connectorState: dashboard.connectorState,
    webhookState: dashboard.webhookState,
    watchState: dashboard.watchState
  })
}

function validateReservationResponse(value, submitted) {
  if (!isPlainObject(value)
      || !reservationMatchesSubmitted(value, submitted)
      || !['BOARD_FREE', 'BOARD_VIP'].includes(value.effectivePlanCode)
  ) {
    throw new Error('预约响应无法确认，正在保留原编号回读。')
  }
  return value
}

function validateReservationLookup(value, operationId) {
  if (!isPlainObject(value) || typeof value.found !== 'boolean') {
    throw new Error('预约回读响应格式不正确。')
  }
  if (!value.found) {
    if (value.meeting !== null) throw new Error('预约回读状态不一致。')
    return { found: false, meeting: null }
  }
  const meeting = parseBoardRecentMeetings([value.meeting])[0]
  if (meeting.operationId !== operationId) throw new Error('预约回读编号不一致。')
  return { found: true, meeting }
}

function validateAgendaCount(rule, value, callback) {
  if (!isPositiveSafeInteger(value) || value > agendaLimit.value) {
    callback(new Error(`议题数量应为 1 至 ${agendaLimit.value}`))
    return
  }
  callback()
}

function validateSeatCount(rule, value, callback) {
  if (!isPositiveSafeInteger(value) || value > seatSafetyLimit.value) {
    callback(new Error(`参会席位应为 1 至 ${seatSafetyLimit.value}`))
    return
  }
  callback()
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isPositiveSafeInteger(value) {
  return Number.isSafeInteger(value) && value > 0
}

function isValidOperationId(value) {
  return typeof value === 'string'
    && /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/.test(value)
}

function readRememberedTenant() {
  try {
    return window.localStorage.getItem(CONTEXT_STORAGE_KEY)
  } catch {
    return null
  }
}

function rememberTenant(tenantId) {
  try {
    window.localStorage.setItem(CONTEXT_STORAGE_KEY, String(tenantId))
  } catch {
    // Browser privacy settings may disable storage; selection remains valid in memory.
  }
}

function reservationDraftStorageKey(tenantId, userId) {
  return `${RESERVATION_DRAFT_STORAGE_PREFIX}:${userId}:${tenantId}`
}

function readReservationDraft(tenantId, userId) {
  if (!isPositiveSafeInteger(tenantId) || !isPositiveSafeInteger(userId)) return null
  try {
    return parseStoredDraft(
      window.sessionStorage.getItem(reservationDraftStorageKey(tenantId, userId)),
      tenantId,
      userId
    )
  } catch {
    return null
  }
}

function persistCurrentReservationDraft() {
  if (!isPositiveSafeInteger(reservationForm.tenantId)
      || !isPositiveSafeInteger(reservationForm.userId)
      || !isValidOperationId(reservationForm.operationId)) return false
  const snapshot = {
    tenantId: reservationForm.tenantId,
    userId: reservationForm.userId,
    operationId: reservationForm.operationId,
    agendaCount: reservationForm.agendaCount,
    seatCount: reservationForm.seatCount,
    outcomeState: reservationOutcomeState.value
  }
  try {
    persistDraftWithReadback(
      window.sessionStorage,
      reservationDraftStorageKey(reservationForm.tenantId, reservationForm.userId),
      snapshot
    )
    reservationStorageError.value = ''
    return true
  } catch {
    reservationStorageError.value = '浏览器无法可靠保存预约草稿；在存储恢复前不会发出预约请求。'
    return false
  }
}

function discardCurrentReservationDraft() {
  if (!isPositiveSafeInteger(reservationForm.tenantId)
      || !isPositiveSafeInteger(reservationForm.userId)) return
  try {
    window.sessionStorage.removeItem(
      reservationDraftStorageKey(reservationForm.tenantId, reservationForm.userId))
  } catch {
    // No recovery action is needed when storage is unavailable.
  }
}

function emptyEntitlement() {
  return {
    grantedPlanCode: null,
    effectivePlanCode: 'BOARD_FREE',
    activationState: 'FREE',
    dailyMeetingLimit: 0,
    agendaLimit: 0,
    seatLimit: 0,
    secretaryEnabled: false,
    connectorRequired: false,
    connectorVerified: false,
    usedCount: 0,
    reservedCount: 0,
    remainingCount: 0
  }
}

function createOperationId() {
  const random = window.crypto?.randomUUID
    ? window.crypto.randomUUID()
    : Math.random().toString(36).slice(2, 12)
  return `board-${Date.now()}-${random}`
}

function formatPlan(code) {
  if (code === 'BOARD_VIP') return 'VIP 版'
  if (code === 'BOARD_FREE') return '免费版'
  return code || '--'
}

function meetingStatus(status) {
  if (status === 'RESERVED') return { label: '已预约', type: 'success' }
  if (status === 'COMPLETED') return { label: '已完成', type: 'primary' }
  if (status === 'PENDING') return { label: '处理中', type: 'warning' }
  if (status === 'REJECTED') return { label: '未通过', type: 'danger' }
  return { label: status || '未知', type: 'info' }
}

function capabilityLabel(state) {
  return state === 'COMING_SOON' ? '后续能力' : '暂不可用'
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false
  }).format(date)
}

function numberOrZero(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}
</script>

<style lang="scss" scoped>
.board-page {
  --board-blue: #246bfd;
  --board-navy: #12223d;
  --board-muted: #667085;
  min-height: calc(100vh - 84px);
  padding: 24px;
  background:
    radial-gradient(circle at 92% 2%, rgba(36, 107, 253, 0.1), transparent 28%),
    #f5f7fb;
  color: var(--board-navy);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.board-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 30px;
  border-radius: 20px;
  color: #fff;
  background: linear-gradient(125deg, #10213f 0%, #183965 62%, #246bfd 130%);
  box-shadow: 0 18px 50px rgba(18, 34, 61, 0.16);
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 18px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  flex: 0 0 58px;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.13);
  font-size: 26px;
  font-weight: 800;
}

.brand-line {
  margin-bottom: 3px;
  font-size: 14px;
  font-weight: 650;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.88);

  span {
    margin-left: 8px;
    padding-left: 10px;
    border-left: 1px solid rgba(255, 255, 255, 0.35);
    font-weight: 500;
    letter-spacing: 0.03em;
  }
}

.brand-lockup h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.18;
}

.brand-lockup p {
  margin: 7px 0 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.72);
}

.context-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 330px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(8px);

  label {
    flex: 0 0 auto;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.75);
  }
}

.context-select {
  flex: 1;
}

.page-alert,
.context-empty {
  margin-top: 20px;
}

.alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.dashboard-skeleton {
  margin-top: 20px;
  padding: 24px;
  border-radius: 16px;
  background: #fff;
}

.dashboard-content {
  min-height: 360px;
}

.summary-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.9fr 0.9fr;
  gap: 18px;
  margin-top: 20px;
}

.summary-card {
  border: 1px solid #e7ebf2;
  border-radius: 16px;

  :deep(.el-card__body) {
    height: 100%;
    padding: 22px;
  }
}

.card-eyebrow {
  margin-bottom: 15px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--board-muted);
}

.plan-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.plan-name {
  font-size: 27px;
  font-weight: 750;
}

.plan-caption {
  margin-top: 3px;
  font-size: 12px;
  color: #98a2b3;
}

.pending-tag {
  --el-tag-bg-color: #fff4d6;
  --el-tag-border-color: #f4c35a;
  --el-tag-text-color: #9a6700;
  font-weight: 700;
}

.connector-pending {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid #f3cb70;
  border-radius: 11px;
  background: #fff8e8;
  color: #9a6700;

  .el-icon {
    margin-top: 2px;
    font-size: 18px;
  }

  strong {
    font-size: 14px;
  }

  p {
    margin: 3px 0 0;
    color: #805c16;
    font-size: 12px;
    line-height: 1.55;
  }
}

.entitlement-revoked {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid #f2b8b5;
  border-radius: 11px;
  background: #fff2f1;
  color: #b42318;

  .el-icon {
    margin-top: 2px;
    font-size: 18px;
  }

  strong {
    font-size: 14px;
  }

  p {
    margin: 3px 0 0;
    color: #912018;
    font-size: 12px;
    line-height: 1.55;
  }
}

.plan-features {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 21px;
  font-size: 13px;
  color: var(--board-muted);
}

.quota-main {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 13px;

  strong {
    font-size: 34px;
    line-height: 1;
  }

  span {
    color: var(--board-muted);
  }
}

.quota-details {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  color: var(--board-muted);
  font-size: 12px;
}

.meeting-copy {
  min-height: 47px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--board-muted);
}

.reserve-button {
  width: 100%;
  margin-top: 14px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  margin-top: 18px;
}

.history-card,
.future-card {
  border: 1px solid #e7ebf2;
  border-radius: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
}

.section-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #98a2b3;
}

.history-table {
  width: 100%;
}

.future-column {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.future-card {
  :deep(.el-card__body) {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) auto;
    align-items: center;
    gap: 12px;
    padding: 17px;
  }
}

.future-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  font-size: 20px;
}

.watch-icon {
  background: #eef4ff;
  color: #246bfd;
}

.webhook-icon {
  background: #eefaf5;
  color: #169c66;
}

.future-title {
  font-size: 14px;
  font-weight: 700;
}

.future-copy p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--board-muted);
}

.future-note {
  padding: 2px 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #98a2b3;
}

.dialog-context {
  display: flex;
  justify-content: space-between;
  margin: -4px 0 20px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f5f7fb;
  color: var(--board-muted);

  strong {
    color: var(--board-navy);
  }
}

.draft-reference {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -8px 0 18px;
  color: var(--board-muted);
  font-size: 12px;

  code {
    max-width: 72%;
    overflow: hidden;
    color: #475467;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.reservation-alert {
  margin-bottom: 18px;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dialog-footer-actions {
  display: flex;
  gap: 10px;
}

.field-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #98a2b3;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: 1fr 1fr;
  }

  .entitlement-card {
    grid-column: 1 / -1;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .future-column {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .future-note {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  .board-page {
    padding: 14px;
  }

  .board-hero {
    align-items: stretch;
    flex-direction: column;
    padding: 22px;
  }

  .brand-lockup {
    align-items: flex-start;
  }

  .brand-lockup p {
    line-height: 1.5;
  }

  .context-panel {
    min-width: 0;
  }

  .summary-grid,
  .future-column {
    grid-template-columns: 1fr;
  }

  .entitlement-card,
  .future-note {
    grid-column: auto;
  }

  .future-card :deep(.el-card__body) {
    grid-template-columns: 44px minmax(0, 1fr);

    .el-tag {
      grid-column: 2;
      justify-self: start;
    }
  }

  .alert-content,
  .dialog-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .dialog-footer-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;

    .el-button {
      width: 100%;
      margin-left: 0;
    }
  }

  .field-hint {
    display: block;
    width: 100%;
    margin: 7px 0 0;
  }
}
</style>
