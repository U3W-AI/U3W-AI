<template>
  <el-card shadow="never" class="policy-card">
    <div class="policy-heading">
      <div>
        <div class="heading-line">
          <h3>套餐策略治理</h3>
          <el-tag type="warning" effect="plain">候选能力</el-tag>
        </div>
        <p>仅管理当前两套独董会策略；每次修订都是完整替换并生成不可变回执。</p>
      </div>
      <el-button
        v-if="canAudit"
        icon="Refresh"
        :loading="auditLoading"
        :disabled="auditLoading || mutating || externalBusy"
        @click="loadAudit"
        v-hasPermi="['board:plan:audit']"
      >刷新审计回执</el-button>
    </div>

    <el-alert
      v-if="!crossTabLockAvailable && canRevise"
      title="当前浏览器已降级为只读策略治理"
      description="浏览器不支持跨标签 Web Lock，页面仍可读取当前策略与审计回执，但会禁止写入，避免并发双写。"
      type="warning"
      show-icon
      :closable="false"
      class="policy-alert"
    />
    <el-alert
      v-if="pendingRecoveryBlocked"
      title="未决请求恢复数据无效"
      description="页面已停止新的套餐策略写入；请保留现场并由管理员清理当前浏览器的操作者未决槽。"
      type="error"
      show-icon
      :closable="false"
      aria-live="assertive"
      class="policy-alert"
    />
    <el-alert
      v-if="mutationError"
      :title="mutationError"
      type="error"
      show-icon
      :closable="false"
      aria-live="assertive"
      class="policy-alert"
    />
    <el-alert
      v-if="mutationConflict && manualRefreshRequired"
      title="409 冲突：旧意图已保留，必须手动刷新"
      description="页面不会自动套用新版本，也不会把旧表单改写后重试。请先手动刷新当前策略，对比后再明确放弃旧意图。"
      type="warning"
      show-icon
      :closable="false"
      class="policy-alert"
    />
    <el-alert
      v-else-if="mutationConflict && catalogManuallyRefreshed"
      title="当前策略已手动刷新"
      description="旧意图、旧预期版本和原请求仍保持锁定；页面没有自动套用新版本。确认差异后可明确放弃旧意图，再从最新版本重新发起。"
      type="info"
      show-icon
      :closable="false"
      class="policy-alert"
    />

    <div v-if="pendingMutation" class="pending-toolbar" aria-live="polite">
      <span>
        当前操作者存在一笔未决{{ pendingMutation.payload.rollbackOfReceiptId ? '补偿回滚' : '完整修订' }}，
        新策略写入已锁定。
      </span>
      <el-button
        type="warning"
        plain
        :disabled="mutating || externalBusy"
        @click="resumePendingMutation"
      >{{ mutationConflict ? '查看冲突意图' : '使用原幂等键继续' }}</el-button>
    </div>

    <section class="policy-section" aria-labelledby="current-policy-title">
      <div class="section-heading">
        <div>
          <h4 id="current-policy-title">当前两套餐表</h4>
          <p>版本与更新时间来自服务端已提交 head；界面不推断未提交状态。</p>
        </div>
      </div>

      <el-skeleton v-if="plansLoading" :rows="3" animated class="section-state" />
      <el-alert
        v-else-if="plansError"
        :title="plansError"
        type="error"
        show-icon
        :closable="false"
        class="section-state"
      />
      <el-empty
        v-else-if="plans.length !== 2"
        description="当前两套餐表不可用，已停止策略修订"
        class="section-state"
      />
      <div v-else class="table-scroll">
        <el-table :data="plans" row-key="planCode" empty-text="暂无套餐策略">
          <el-table-column label="套餐" min-width="160">
            <template #default="scope">
              <div class="plan-cell">
                <strong>{{ scope.row.planName }}</strong>
                <span>{{ scope.row.planCode === 'BOARD_VIP' ? 'VIP' : '免费版' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="80" align="center">
            <template #default="scope">v{{ scope.row.version }}</template>
          </el-table-column>
          <el-table-column prop="dailyMeetingLimit" label="每日会议" width="100" align="center" />
          <el-table-column prop="agendaLimit" label="单次议题" width="100" align="center" />
          <el-table-column label="席位" width="90" align="center">
            <template #default="scope">{{ seatLabel(scope.row.seatLimit) }}</template>
          </el-table-column>
          <el-table-column label="秘书" width="90" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.secretaryEnabled ? 'success' : 'info'" effect="plain">
                {{ scope.row.secretaryEnabled ? '开启' : '关闭' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="180">
            <template #default="scope">{{ formatPlanPolicyDateTime(scope.row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column v-if="canRevise" label="操作" width="105" fixed="right" align="center">
            <template #default="scope">
              <el-button
                link
                type="primary"
                :disabled="!canStartMutation"
                @click="openRevision(scope.row)"
                v-hasPermi="['board:plan:revise']"
              >完整修订</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section
      v-if="canAudit"
      class="policy-section audit-section"
      aria-labelledby="policy-audit-title"
      v-hasPermi="['board:plan:audit']"
    >
      <div class="section-heading">
        <div>
          <h4 id="policy-audit-title">策略审计回执</h4>
          <p>固定窗口最多返回 100 条；表格仅展示运营判断所需的安全字段。</p>
        </div>
      </div>
      <el-alert
        v-if="audit.truncated"
        title="回执窗口已截断"
        description="服务端存在更多历史记录，本页最多返回并展示 100 条。"
        type="warning"
        show-icon
        :closable="false"
        class="section-state"
      />
      <el-skeleton v-if="auditLoading" :rows="5" animated class="section-state" />
      <el-alert
        v-else-if="auditError"
        :title="auditError"
        type="error"
        show-icon
        :closable="false"
        class="section-state"
      />
      <el-empty
        v-else-if="audit.records.length === 0"
        description="暂无可展示的套餐策略回执"
        class="section-state"
      />
      <div v-else class="table-scroll">
        <el-table :data="audit.records" row-key="receiptId" empty-text="暂无策略回执">
          <el-table-column label="套餐 / 版本" min-width="150">
            <template #default="scope">
              <div class="plan-cell">
                <strong>{{ scope.row.planName }}</strong>
                <span>v{{ scope.row.policyVersion }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="动作" width="105" align="center">
            <template #default="scope">
              <el-tag :type="actionTagType(scope.row.action)" effect="plain">
                {{ planPolicyActionLabel(scope.row.action) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="完整策略" min-width="245">
            <template #default="scope">
              <div class="policy-summary">
                <span>每日 {{ scope.row.dailyMeetingLimit }} 场 · 议题 {{ scope.row.agendaLimit }} 个</span>
                <span>席位 {{ seatLabel(scope.row.seatLimit) }} · 秘书{{ scope.row.secretaryEnabled ? '开启' : '关闭' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作者" width="105" align="center">
            <template #default="scope">{{ actorLabel(scope.row.actorType) }}</template>
          </el-table-column>
          <el-table-column label="完成时间" min-width="180">
            <template #default="scope">{{ formatPlanPolicyDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
          <el-table-column v-if="canRevise" label="补偿" width="110" fixed="right" align="center">
            <template #default="scope">
              <el-button
                link
                type="warning"
                :disabled="!canStartMutation || !canRollbackPlanPolicyReceipt(plans, scope.row)"
                @click="openRollback(scope.row)"
                v-hasPermi="['board:plan:revise']"
              >发起回滚</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <PlanPolicyRevisionDialog
      ref="dialogRef"
      v-model="dialogVisible"
      :catalog="plans"
      :submitting="mutating"
      :ambiguous="mutationAmbiguous"
      :conflict="mutationConflict"
      :refreshing="refreshingCatalog"
      :write-available="crossTabLockAvailable"
      :external-error="dialogError"
      @submit="submitMutation"
      @manual-refresh="manualRefreshCatalog"
      @abandon="abandonConflictIntent"
      @closed="handleDialogClosed"
    />
  </el-card>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listIndependentBoardPlanPolicyReceipts,
  listIndependentBoardPlans,
  reviseIndependentBoardPlanPolicy
} from '@/api/business/independentBoard/admin'
import PlanPolicyRevisionDialog from './PlanPolicyRevisionDialog.vue'
import {
  canRollbackPlanPolicyReceipt,
  createPlanPolicyPendingSnapshot,
  formatPlanPolicyDateTime,
  isAmbiguousPlanPolicyFailure,
  isPlanPolicyConflict,
  parsePlanPolicyCatalog,
  parsePlanPolicyPendingSnapshot,
  parsePlanPolicyReceiptEnvelope,
  payloadMatchesPlanPolicyPending,
  planPolicyActionLabel,
  planPolicyPendingStorageKey,
  reconcilePlanPolicyRevisionCatalog,
  verifyPlanPolicyRevisionResult,
  withPlanPolicyMutationLock
} from './planPolicyModel.js'

const props = defineProps({
  plans: { type: Array, required: true },
  plansLoading: { type: Boolean, default: false },
  plansError: { type: String, default: '' },
  canRevise: { type: Boolean, default: false },
  canAudit: { type: Boolean, default: false },
  actorUserId: { type: Number, required: true },
  externalBusy: { type: Boolean, default: false }
})
const emit = defineEmits(['catalog-updated', 'mutating-change'])

const actorIdentityValid = Number.isSafeInteger(props.actorUserId) && props.actorUserId > 0
const pendingStorageKey = actorIdentityValid
  ? planPolicyPendingStorageKey(props.actorUserId)
  : null
const crossTabLockAvailable = Boolean(typeof navigator !== 'undefined'
  && navigator.locks
  && typeof navigator.locks.request === 'function')
const restoredPendingState = restorePendingFromStorage()

const audit = ref({ records: [], limit: 100, truncated: false })
const auditLoading = ref(false)
const auditError = ref('')
const mutating = ref(false)
const refreshingCatalog = ref(false)
const dialogVisible = ref(false)
const dialogRef = ref(null)
const dialogError = ref('')
const pendingMutation = ref(restoredPendingState.snapshot)
const pendingRecoveryBlocked = ref(restoredPendingState.blocked)
const mutationAmbiguous = ref(Boolean(restoredPendingState.snapshot))
const mutationConflict = ref(false)
const manualRefreshRequired = ref(false)
const catalogManuallyRefreshed = ref(false)
const mutationError = ref(restoredPendingState.snapshot
  ? '检测到上次未决套餐策略请求；只能使用原负载与原幂等键恢复。'
  : (restoredPendingState.blocked
    ? '当前操作人的未决套餐策略数据不可恢复，已停止新的写操作。'
    : ''))

const catalogReady = computed(() => !props.plansLoading
  && !props.plansError
  && props.plans.length === 2)
const canStartMutation = computed(() => props.canRevise
  && actorIdentityValid
  && Boolean(crossTabLockAvailable)
  && catalogReady.value
  && !props.externalBusy
  && !mutating.value
  && !pendingMutation.value
  && !pendingRecoveryBlocked.value
  && !manualRefreshRequired.value)

watch(mutating, value => emit('mutating-change', value), {
  immediate: true,
  flush: 'sync'
})

function readPendingFromStorage() {
  if (!pendingStorageKey) throw new Error('当前操作人身份无效')
  const raw = localStorage.getItem(pendingStorageKey)
  return raw === null ? null : parsePlanPolicyPendingSnapshot(raw, props.actorUserId)
}

function restorePendingFromStorage() {
  if (!pendingStorageKey) return { snapshot: null, blocked: true }
  try {
    return { snapshot: readPendingFromStorage(), blocked: false }
  } catch {
    return { snapshot: null, blocked: true }
  }
}

function persistPendingToStorage(snapshot) {
  try {
    localStorage.setItem(pendingStorageKey, JSON.stringify(snapshot))
    const stored = readPendingFromStorage()
    if (!stored || JSON.stringify(stored) !== JSON.stringify(snapshot)) {
      throw new Error('未决槽写入回读不一致')
    }
    return stored
  } catch {
    pendingRecoveryBlocked.value = true
    mutationError.value = '浏览器无法安全保存并回读未决请求，已在发送前停止策略写入。'
    return null
  }
}

function removePendingFromStorage() {
  try {
    localStorage.removeItem(pendingStorageKey)
    const removed = localStorage.getItem(pendingStorageKey) === null
    if (!removed) {
      pendingRecoveryBlocked.value = true
      mutationError.value = '操作已确认，但浏览器无法清理旧未决槽；后续策略写入已停止。'
    }
    return removed
  } catch {
    pendingRecoveryBlocked.value = true
    mutationError.value = '操作已确认，但浏览器无法清理旧未决槽；后续策略写入已停止。'
    return false
  }
}

function synchronizePendingFromStorage() {
  try {
    const stored = readPendingFromStorage()
    if (!stored) {
      if (pendingMutation.value) throw new Error('未决槽被外部清除')
      return true
    }
    if (pendingMutation.value
        && JSON.stringify(pendingMutation.value) !== JSON.stringify(stored)) {
      throw new Error('检测到不同未决请求')
    }
    if (!pendingMutation.value) {
      pendingMutation.value = stored
      mutationAmbiguous.value = true
      mutationError.value = '检测到另一标签页的未决策略请求；只能恢复原负载与原幂等键。'
    }
    return true
  } catch {
    pendingRecoveryBlocked.value = true
    mutationError.value = '跨标签未决请求发生冲突或损坏，已停止新的策略写入。'
    return false
  }
}

function establishPendingMutation(payload) {
  if (!synchronizePendingFromStorage()) return false
  if (pendingMutation.value) {
    return payloadMatchesPlanPolicyPending(pendingMutation.value, payload)
  }
  let snapshot
  try {
    snapshot = createPlanPolicyPendingSnapshot({
      actorUserId: props.actorUserId,
      payload
    })
  } catch {
    pendingRecoveryBlocked.value = true
    mutationError.value = '当前登录身份无法建立安全的未决请求，已停止策略写入。'
    return false
  }
  const stored = persistPendingToStorage(snapshot)
  if (!stored || !payloadMatchesPlanPolicyPending(stored, payload)) return false
  pendingMutation.value = stored
  return true
}

function clearPendingMutation() {
  const removed = removePendingFromStorage()
  pendingMutation.value = null
  mutationAmbiguous.value = false
  return removed
}

function handlePendingStorageEvent(event) {
  if (event.key !== pendingStorageKey || event.storageArea !== localStorage) return
  if (event.newValue === null) {
    if (pendingMutation.value) {
      pendingMutation.value = null
      mutationAmbiguous.value = false
      manualRefreshRequired.value = true
      mutationError.value = '另一标签页已清除未决请求；请手动刷新当前策略后再操作。'
    }
  } else {
    synchronizePendingFromStorage()
    manualRefreshRequired.value = true
  }
  dialogVisible.value = false
  dialogRef.value?.reset()
}

function guardPendingBeforeUnload(event) {
  if (!pendingMutation.value && !pendingRecoveryBlocked.value) return
  event.preventDefault()
  event.returnValue = ''
}

window.addEventListener('beforeunload', guardPendingBeforeUnload)
window.addEventListener('storage', handlePendingStorageEvent)
onBeforeUnmount(() => {
  emit('mutating-change', false)
  window.removeEventListener('beforeunload', guardPendingBeforeUnload)
  window.removeEventListener('storage', handlePendingStorageEvent)
})
onBeforeRouteLeave(() => {
  if (!pendingMutation.value && !pendingRecoveryBlocked.value) return true
  return window.confirm('当前存在未决或无法恢复的套餐策略请求。恢复数据仍会保留，确认离开吗？')
})

onMounted(() => {
  if (props.canAudit) loadAudit()
})

async function loadAudit() {
  if (!props.canAudit || auditLoading.value) return
  auditLoading.value = true
  auditError.value = ''
  try {
    const response = await listIndependentBoardPlanPolicyReceipts()
    audit.value = parsePlanPolicyReceiptEnvelope(response?.data)
  } catch {
    audit.value = { records: [], limit: 100, truncated: false }
    auditError.value = '策略审计回执加载失败或响应发生安全合同漂移，页面已停止展示。'
  } finally {
    auditLoading.value = false
  }
}

async function readCurrentCatalog() {
  const response = await listIndependentBoardPlans()
  return parsePlanPolicyCatalog(response?.data)
}

function openRevision(plan) {
  if (!canStartMutation.value) return
  resetMutationPresentation()
  dialogRef.value?.openRevision(plan)
}

function openRollback(receipt) {
  if (!canStartMutation.value || !canRollbackPlanPolicyReceipt(props.plans, receipt)) return
  resetMutationPresentation()
  dialogRef.value?.openRollback(receipt)
}

function resetMutationPresentation() {
  mutationError.value = ''
  dialogError.value = ''
  mutationAmbiguous.value = false
  mutationConflict.value = false
  catalogManuallyRefreshed.value = false
}

function resumePendingMutation() {
  if (!pendingMutation.value || mutating.value || props.externalBusy) return
  dialogError.value = ''
  if (!mutationConflict.value) {
    mutationAmbiguous.value = true
    mutationError.value = '请求结果仍不明确；本次只能使用已锁定的原负载与原幂等键精确重放。'
  }
  nextTick(() => dialogRef.value?.restore(pendingMutation.value.payload))
}

async function submitMutation(payload) {
  if (!props.canRevise || !actorIdentityValid || !crossTabLockAvailable
      || mutating.value || props.externalBusy || pendingRecoveryBlocked.value) return
  let enteredLock = false
  let localSafetyRejected = false
  let wasRecovering = false
  let committedResponse = false
  mutating.value = true
  mutationError.value = ''
  dialogError.value = ''
  try {
    await withPlanPolicyMutationLock({
      actorUserId: props.actorUserId,
      // 无 Web Lock 时本组件降级为只读；显式关闭仅限当前 JS realm 的
      // mutex，避免它被误当成跨标签互斥后放行写请求。
      fallbackMutex: null,
      run: async () => {
        enteredLock = true
        if (!synchronizePendingFromStorage()) {
          localSafetyRejected = true
          throw new Error('PLAN_POLICY_LOCAL_PENDING_CONFLICT')
        }
        wasRecovering = pendingMutation.value !== null
        if (!establishPendingMutation(payload)) {
          localSafetyRejected = true
          mutationError.value = '未决请求的套餐、版本、完整负载或幂等键发生变化，已拒绝发送。'
          throw new Error('PLAN_POLICY_LOCAL_PENDING_CONFLICT')
        }

        const response = await reviseIndependentBoardPlanPolicy(payload)
        committedResponse = true
        const result = verifyPlanPolicyRevisionResult({
          payload, result: response?.data, actorUserId: props.actorUserId
        })
        const catalog = await readCurrentCatalog()
        const reconciliation = reconcilePlanPolicyRevisionCatalog(catalog, result)

        const pendingCleared = clearPendingMutation()
        manualRefreshRequired.value = false
        emit('catalog-updated', catalog)
        dialogVisible.value = false
        dialogRef.value?.reset()
        resetMutationPresentation()
        if (!pendingCleared) {
          mutationError.value = '策略已提交，但浏览器无法清理旧未决槽；本标签页后续写入已锁定。'
          ElMessage.warning('策略已提交，但本地恢复槽清理失败；请保留现场并重新登录后再操作。')
        } else if (reconciliation === 'SUPERSEDED') {
          ElMessage.warning('策略回执已确认提交；当前套餐 head 已由后续版本继续推进。')
        } else {
          ElMessage.success(payload.rollbackOfReceiptId
            ? '补偿回滚已创建新版本，回执与当前套餐 head 一致。'
            : '套餐策略已修订，回执与当前套餐 head 一致。')
        }
      }
    })
    if (props.canAudit) await loadAudit()
  } catch (error) {
    if (localSafetyRejected || !enteredLock) {
      mutationAmbiguous.value = Boolean(pendingMutation.value)
      if (!mutationError.value) {
        mutationError.value = '浏览器未能建立安全互斥，已在发送前停止策略写入。'
      }
      return
    }
    if (isPlanPolicyConflict(error)) {
      mutationConflict.value = true
      mutationAmbiguous.value = false
      manualRefreshRequired.value = true
      catalogManuallyRefreshed.value = false
      mutationError.value = '409：原意图已保留。必须手动刷新对比；页面不会自动套用新版本。'
      return
    }

    mutationAmbiguous.value = wasRecovering
      || committedResponse
      || isAmbiguousPlanPolicyFailure(error)
    manualRefreshRequired.value = committedResponse
    mutationError.value = mutationAmbiguous.value
      ? '策略写入结果不明确，页面未显示成功；只能使用原负载与原幂等键精确重放。'
      : '策略修订被服务端明确拒绝，页面未改变当前套餐。'
    if (!mutationAmbiguous.value) clearPendingMutation()
  } finally {
    mutating.value = false
  }
}

async function manualRefreshCatalog() {
  if (refreshingCatalog.value || mutating.value) return
  refreshingCatalog.value = true
  dialogError.value = ''
  try {
    const catalog = await readCurrentCatalog()
    emit('catalog-updated', catalog)
    manualRefreshRequired.value = false
    catalogManuallyRefreshed.value = true
    ElMessage.info('当前策略已手动刷新；旧表单和旧预期版本保持不变。')
  } catch {
    dialogError.value = '手动刷新失败或返回了非安全套餐目录；旧意图仍保持锁定。'
  } finally {
    refreshingCatalog.value = false
  }
}

async function abandonConflictIntent() {
  if (!mutationConflict.value || !pendingMutation.value || mutating.value) return
  if (manualRefreshRequired.value) {
    ElMessage.warning('请先完成手动刷新并对比当前策略，再决定是否放弃旧意图。')
    return
  }
  try {
    await ElMessageBox.confirm(
      '这会清除当前浏览器保存的旧表单与原幂等键，不会回滚或修改服务端策略。确认放弃吗？',
      '放弃旧套餐策略意图',
      {
        confirmButtonText: '确认放弃',
        cancelButtonText: '继续保留',
        type: 'warning',
        distinguishCancelAndClose: true,
        closeOnClickModal: false
      }
    )
  } catch {
    return
  }
  if (!clearPendingMutation()) return
  dialogVisible.value = false
  dialogRef.value?.reset()
  resetMutationPresentation()
  ElMessage.info('旧意图已明确放弃，可从当前版本重新发起完整修订。')
}

function handleDialogClosed() {
  if (!pendingMutation.value) dialogRef.value?.reset()
  dialogError.value = ''
}

function seatLabel(value) {
  return value === null ? '不限' : `${value} 席`
}

function actorLabel(actorType) {
  return actorType === 'SYSTEM_MIGRATION' ? '系统迁移' : '管理员'
}

function actionTagType(action) {
  if (action === 'PLAN_POLICY_ROLLED_BACK') return 'warning'
  if (action === 'PLAN_POLICY_REVISED') return 'primary'
  return 'info'
}
</script>

<style lang="scss" scoped>
.policy-card {
  margin-bottom: 16px;
  border: 1px solid #d9e2f1;
  border-radius: 14px;
  background: #fff;
}

.policy-heading,
.section-heading,
.heading-line,
.pending-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.heading-line {
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
}

.policy-heading h3,
.section-heading h4 {
  margin: 0;
  color: #14233b;
}

.policy-heading p,
.section-heading p {
  margin: 7px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

.policy-alert,
.pending-toolbar,
.policy-section {
  margin-top: 16px;
}

.pending-toolbar {
  align-items: center;
  padding: 12px 14px;
  color: #7a4d00;
  background: #fff8e8;
  border: 1px solid #f1c76f;
  border-radius: 10px;
  line-height: 1.5;
}

.policy-section {
  padding-top: 16px;
  border-top: 1px solid #edf0f5;
}

.audit-section {
  margin-top: 20px;
}

.section-state {
  margin-top: 14px;
  padding: 18px;
}

.table-scroll {
  margin-top: 14px;
  overflow-x: auto;
}

.plan-cell,
.policy-summary {
  display: grid;
  gap: 4px;
}

.plan-cell span,
.policy-summary span {
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .policy-card :deep(.el-card__body) {
    padding: 14px;
  }

  .policy-heading,
  .pending-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .policy-heading > .el-button,
  .pending-toolbar .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
