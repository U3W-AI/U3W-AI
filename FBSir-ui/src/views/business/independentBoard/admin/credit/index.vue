<template>
  <div class="app-container credit-admin-page">
    <header class="page-heading">
      <div>
        <p class="brand-line">福帮手 FBSir · 独董会</p>
        <h2>积分账本</h2>
        <p>按用户 ID 查询 USER_GLOBAL / FBS_POINTS 不可变账本，并执行受控发放或原操作冲正。</p>
      </div>
      <el-tag type="warning" effect="plain">候选能力 · 默认关闭</el-tag>
    </header>

    <el-alert
      v-if="!candidateEnabled"
      title="积分候选能力未启用"
      description="当前前端构建未显式打开 VITE_FBSIR_BOARD_CREDIT_CANDIDATE；路由应已在进入本页前被移除。"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    />
    <el-alert
      v-else-if="!canOperatePage"
      title="当前账号无权使用积分账本"
      description="该页面同时要求全局管理员角色与 board:credit:query 权限；不会提供绕过身份边界的查询入口。"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <template v-else>
      <el-card shadow="never" class="query-card">
        <el-form label-position="top" class="query-form" @submit.prevent="loadAccount">
          <el-form-item label="目标用户 ID">
            <el-input-number
              v-model="queryUserId"
              :min="1"
              :max="Number.MAX_SAFE_INTEGER"
              :precision="0"
              :controls="false"
              :disabled="loading || mutating"
              aria-label="目标用户 ID"
              placeholder="请输入正整数用户 ID"
              class="user-id-input"
              @keyup.enter="loadAccount"
            />
          </el-form-item>
          <el-form-item label="账本操作">
            <div class="query-actions">
              <el-button
                type="primary"
                icon="Search"
                :loading="loading"
                :disabled="!validQueryUserId || mutating"
                @click="loadAccount"
              >查询账本</el-button>
              <el-button
                v-hasPermi="['board:credit:grant']"
                icon="Plus"
                :disabled="!canGrantCurrentUser || loading || mutating"
                @click="openGrantDialog"
              >发放积分</el-button>
            </div>
          </el-form-item>
        </el-form>
        <p class="query-note">仅使用稳定的数字用户 ID 定位账户；页面不会按姓名、手机号或租户推断身份。</p>
      </el-card>

      <el-alert
        v-if="pageError"
        :title="pageError"
        type="error"
        show-icon
        :closable="false"
        aria-live="assertive"
        class="page-alert"
      />
      <el-alert
        v-if="mutationError"
        :title="mutationError"
        :description="mutationAmbiguous
          ? '本次不会自动重试。请保留当前对话框中的幂等键，或先刷新账本核对服务器是否已提交。'
          : undefined"
        :type="mutationAmbiguous ? 'error' : 'warning'"
        show-icon
        :closable="false"
        aria-live="assertive"
        class="page-alert"
      />
      <div v-if="pendingMutation" class="pending-toolbar" aria-live="polite">
        <span>用户 #{{ pendingMutation.userId }} 有一笔结果未明确的{{ pendingMutation.kind === 'grant' ? '发放' : '冲正' }}请求。</span>
        <el-button
          type="warning"
          plain
          :disabled="selectedUserId !== pendingMutation.userId || !identityContextCurrent || mutating"
          @click="resumePendingMutation"
        >使用原幂等键继续</el-button>
      </div>

      <el-skeleton v-if="loading" :rows="6" animated class="content-card" />
      <el-empty
        v-else-if="!queryAttempted"
        description="输入用户 ID 并主动查询后显示积分账本"
        class="content-card"
      />
      <el-empty
        v-else-if="!identityContextCurrent"
        description="目标用户 ID 已变更，请重新查询后再执行账本操作"
        class="content-card"
      />
      <el-card v-else-if="accountNotFound" shadow="never" class="content-card first-grant-card">
        <el-empty description="该用户尚未建立独董会积分账本">
          <el-button
            v-hasPermi="['board:credit:grant']"
            type="primary"
            :disabled="!canGrantCurrentUser || mutating"
            @click="openGrantDialog"
          >首次发放并建立账本</el-button>
        </el-empty>
        <p>首次发放仍由服务端校验用户身份、读取既有积分投影并原子建立影子账本。</p>
      </el-card>

      <template v-else-if="account && !pageError">
        <el-alert
          v-if="account.truncated"
          title="仅展示最近 100 条操作"
          description="审计窗口已截断；当前余额与链头已验证，但更早记录须通过后端审计能力查询。"
          type="warning"
          show-icon
          :closable="false"
          class="page-alert"
        />

        <section class="summary-grid" aria-label="积分账户摘要">
          <el-card shadow="never" class="summary-card balance-card">
            <span>当前余额</span>
            <strong>{{ account.balance }}</strong>
            <small>FBS_POINTS</small>
          </el-card>
          <el-card shadow="never" class="summary-card">
            <span>用户 ID</span>
            <strong>#{{ account.userId }}</strong>
            <small>USER_GLOBAL</small>
          </el-card>
          <el-card shadow="never" class="summary-card">
            <span>账本版本</span>
            <strong>{{ account.version }}</strong>
            <small>{{ account.records.length }} 条可见记录</small>
          </el-card>
          <el-card shadow="never" class="summary-card">
            <span>开户余额</span>
            <strong>{{ account.openingBalance }}</strong>
            <small>最近更新 {{ formatCreditDateTime(account.updatedAt) }}</small>
          </el-card>
        </section>

        <el-card shadow="never" class="content-card ledger-card">
          <div class="table-toolbar">
            <div>
              <h3>不可变操作记录</h3>
              <p>操作按序号倒序展示；自由文本审计备注、幂等键、摘要和内部账户编号不会下发到页面。</p>
            </div>
            <el-button
              icon="Refresh"
              :loading="loading"
              :disabled="mutating"
              @click="loadAccount"
            >刷新账本</el-button>
          </div>
          <div class="table-scroll">
            <CreditOperationTable
              :records="account.records"
              :can-reverse="canReverseCurrentUser"
              @reverse="openReversalDialog"
            />
          </div>
        </el-card>
      </template>
    </template>

    <CreditGrantDialog
      ref="grantDialogRef"
      v-model="grantDialogVisible"
      :user-id="pendingMutation?.userId || selectedUserId || 1"
      :account-version="pendingMutation?.payload.expectedAccountVersion ?? account?.version ?? 0"
      :submitting="mutating"
      :ambiguous="mutationAmbiguous"
      :external-error="mutationError"
      @submit="submitGrant"
    />
    <CreditReversalDialog
      ref="reversalDialogRef"
      v-model="reversalDialogVisible"
      :user-id="pendingMutation?.userId || selectedUserId || 1"
      :operation="pendingMutation?.kind === 'reverse' ? pendingMutation.original : reversalOperation"
      :account-version="pendingMutation?.payload.expectedAccountVersion ?? account?.version ?? 0"
      :submitting="mutating"
      :ambiguous="mutationAmbiguous"
      :external-error="mutationError"
      @submit="submitReversal"
    />
  </div>
</template>

<script setup name="IndependentBoardCreditGovernance">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getIndependentBoardCreditAccount,
  grantIndependentBoardCredit,
  reverseIndependentBoardCredit
} from '@/api/business/independentBoard/admin'
import { checkPermi, checkRole } from '@/utils/permission'
import { isBoardCreditCandidateEnabled } from '@/utils/independentBoardPortalCandidate'
import useUserStore from '@/store/modules/user'
import CreditGrantDialog from './CreditGrantDialog.vue'
import CreditOperationTable from './CreditOperationTable.vue'
import CreditReversalDialog from './CreditReversalDialog.vue'
import {
  creditPendingStorageKey,
  formatCreditDateTime,
  createCreditPendingSnapshot,
  isAmbiguousCreditFailure,
  isCreditAccountNotFound,
  isCreditAccountVersionConflict,
  isCreditOperationReversed,
  parseCreditAccount,
  parseCreditCommandResult,
  parseCreditPendingSnapshot,
  withCreditAccountMutationLock,
  verifyCreditGrantResult,
  verifyCreditReversalResult
} from './creditModel'

const candidateEnabled = isBoardCreditCandidateEnabled(import.meta.env)
const isGlobalAdmin = checkRole(['admin'])
const canQueryCredits = checkPermi(['board:credit:query'])
const canGrantCredits = checkPermi(['board:credit:grant'])
const canReverseCredits = checkPermi(['board:credit:reverse'])
const canOperatePage = candidateEnabled && isGlobalAdmin && canQueryCredits
const actorUserId = Number(useUserStore().id)
const PENDING_STORAGE_KEY = creditPendingStorageKey(actorUserId)
const restoredPendingState = restorePendingFromStorage()

const queryUserId = ref(restoredPendingState.snapshot?.userId ?? null)
const selectedUserId = ref(null)
const account = ref(null)
const loading = ref(false)
const queryAttempted = ref(false)
const accountNotFound = ref(false)
const pageError = ref('')
let requestSequence = 0

const mutating = ref(false)
const mutationError = ref(restoredPendingState.snapshot
  ? '检测到上次未决积分请求；查询对应用户后只能使用原参数与原幂等键恢复。'
  : (restoredPendingState.blocked
    ? '当前操作人的未决积分恢复数据无效，已阻止新的积分写操作。'
    : ''))
const mutationAmbiguous = ref(Boolean(restoredPendingState.snapshot))
const pendingMutation = ref(restoredPendingState.snapshot)
const pendingRecoveryBlocked = ref(restoredPendingState.blocked)
const grantDialogVisible = ref(false)
const grantDialogRef = ref(null)
const reversalDialogVisible = ref(false)
const reversalDialogRef = ref(null)
const reversalOperation = ref(null)

const validQueryUserId = computed(() => Number.isSafeInteger(queryUserId.value)
  && queryUserId.value > 0)
const identityContextCurrent = computed(() => queryUserId.value === selectedUserId.value
  && (!account.value || account.value.userId === selectedUserId.value))
const canGrantCurrentUser = computed(() => canOperatePage
  && canGrantCredits
  && !pendingRecoveryBlocked.value
  && Number.isSafeInteger(selectedUserId.value)
  && selectedUserId.value > 0
  && queryAttempted.value
  && identityContextCurrent.value
  && (!pendingMutation.value
    || (pendingMutation.value.kind === 'grant'
      && pendingMutation.value.userId === selectedUserId.value))
  && !pageError.value)
const canReverseCurrentUser = computed(() => canOperatePage
  && canReverseCredits
  && !pendingRecoveryBlocked.value
  && identityContextCurrent.value
  && account.value?.userId === selectedUserId.value
  && (!pendingMutation.value
    || (pendingMutation.value.kind === 'reverse'
      && pendingMutation.value.userId === selectedUserId.value)))

function readPendingFromStorage() {
  const raw = localStorage.getItem(PENDING_STORAGE_KEY)
  return raw === null ? null : parseCreditPendingSnapshot(raw, actorUserId)
}

function restorePendingFromStorage() {
  try {
    return { snapshot: readPendingFromStorage(), blocked: false }
  } catch {
    return { snapshot: null, blocked: true }
  }
}

function persistPendingToStorage(snapshot) {
  try {
    localStorage.setItem(PENDING_STORAGE_KEY, JSON.stringify(snapshot))
    return true
  } catch {
    pendingRecoveryBlocked.value = true
    mutationError.value = '浏览器无法安全保存未决请求，已在发送前停止积分写操作。'
    return false
  }
}

function removePendingFromStorage() {
  try {
    localStorage.removeItem(PENDING_STORAGE_KEY)
  } catch {
    // A proven successful readback keeps idempotent replay safe even if stale
    // session data cannot be removed in this browser process.
  }
}

function synchronizePendingFromStorage() {
  try {
    const stored = readPendingFromStorage()
    if (!stored) return true
    if (pendingMutation.value
        && JSON.stringify(pendingMutation.value) !== JSON.stringify(stored)) {
      pendingRecoveryBlocked.value = true
      mutationError.value = '检测到另一标签页中的不同未决积分请求，已停止新的写操作。'
      return false
    }
    if (!pendingMutation.value) {
      pendingMutation.value = stored
      mutationAmbiguous.value = true
      mutationError.value = '检测到另一标签页的未决积分请求；只能恢复原参数与原幂等键。'
      if (!queryUserId.value) queryUserId.value = stored.userId
    }
    return true
  } catch {
    pendingRecoveryBlocked.value = true
    mutationError.value = '跨标签未决积分恢复数据无效，已停止新的写操作。'
    return false
  }
}

function handlePendingStorageEvent(event) {
  if (event.key !== PENDING_STORAGE_KEY || event.storageArea !== localStorage) return
  if (event.newValue === null) {
    if (pendingMutation.value) {
      pendingMutation.value = null
      mutationAmbiguous.value = false
      mutationError.value = '另一标签页已确认并清除未决请求；请刷新账本后再操作。'
    }
    grantDialogVisible.value = false
    reversalDialogVisible.value = false
    reversalOperation.value = null
    return
  }
  synchronizePendingFromStorage()
  grantDialogVisible.value = false
  reversalDialogVisible.value = false
  reversalOperation.value = null
}

function guardPendingBeforeUnload(event) {
  if (!pendingMutation.value && !pendingRecoveryBlocked.value) return
  event.preventDefault()
  event.returnValue = ''
}

window.addEventListener('beforeunload', guardPendingBeforeUnload)
window.addEventListener('storage', handlePendingStorageEvent)
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', guardPendingBeforeUnload)
  window.removeEventListener('storage', handlePendingStorageEvent)
})
onBeforeRouteLeave(() => {
  if (!pendingMutation.value && !pendingRecoveryBlocked.value) return true
  return window.confirm('当前存在未决或无法恢复的积分请求。离开后仍会保留恢复数据，确认离开吗？')
})

watch(queryUserId, value => {
  if (value === selectedUserId.value || mutating.value) return
  grantDialogVisible.value = false
  reversalDialogVisible.value = false
  reversalOperation.value = null
  if (!pendingMutation.value && !pendingRecoveryBlocked.value) {
    mutationError.value = ''
    mutationAmbiguous.value = false
  }
})

function operationMatchesReadback(audit, result) {
  const observed = audit.records.find(item => item.operationId === result.operationId)
  return observed
    && observed.operationType === result.operationType
    && observed.delta === result.delta
    && observed.balanceAfter === result.balanceAfter
    && observed.reversalOfOperationId === result.reversalOfOperationId
}

function payloadMatchesPending(kind, userId, payload, original = null) {
  const pending = pendingMutation.value
  if (!pending) return true
  return pending.kind === kind
    && pending.userId === userId
    && pending.original?.operationId === original?.operationId
    && JSON.stringify(pending.payload) === JSON.stringify(payload)
}

function establishPendingMutation(kind, userId, payload, original = null) {
  if (!synchronizePendingFromStorage()) return false
  if (!payloadMatchesPending(kind, userId, payload, original)) return false
  if (!pendingMutation.value) {
    let snapshot
    try {
      snapshot = createCreditPendingSnapshot({
        actorUserId,
        kind,
        userId,
        payload,
        original
      })
    } catch {
      pendingRecoveryBlocked.value = true
      mutationError.value = '当前登录身份无法建立安全的未决恢复数据，已停止积分写操作。'
      return false
    }
    if (!persistPendingToStorage(snapshot)) return false
    pendingMutation.value = snapshot
  }
  return true
}

function clearPendingMutation() {
  removePendingFromStorage()
  pendingMutation.value = null
  mutationAmbiguous.value = false
}

function restorePendingDialog() {
  const pending = pendingMutation.value
  if (!pending) return
  nextTick(() => {
    if (pending.kind === 'grant') {
      grantDialogRef.value?.restore(pending.payload)
    } else {
      reversalDialogRef.value?.restore(pending.payload)
    }
  })
}

async function loadAccount() {
  if (!canOperatePage || !validQueryUserId.value || loading.value || mutating.value) return
  const requestedUserId = queryUserId.value
  selectedUserId.value = requestedUserId
  grantDialogVisible.value = false
  reversalDialogVisible.value = false
  reversalOperation.value = null
  if (!pendingMutation.value && !pendingRecoveryBlocked.value) {
    mutationError.value = ''
    mutationAmbiguous.value = false
  }
  pageError.value = ''
  accountNotFound.value = false
  queryAttempted.value = true
  account.value = null
  loading.value = true
  const currentSequence = ++requestSequence
  try {
    const response = await getIndependentBoardCreditAccount(requestedUserId)
    const audit = parseCreditAccount(response?.data, requestedUserId)
    if (currentSequence !== requestSequence || selectedUserId.value !== requestedUserId) return
    if (audit.truncated && audit.records.length !== 100) {
      throw new Error('积分审计窗口截断声明不一致')
    }
    account.value = audit
  } catch (error) {
    if (currentSequence !== requestSequence || selectedUserId.value !== requestedUserId) return
    if (isCreditAccountNotFound(error)) {
      accountNotFound.value = true
    } else {
      pageError.value = '积分账本加载失败或响应不符合安全合同，页面已停止展示。'
    }
  } finally {
    if (currentSequence === requestSequence) loading.value = false
  }
}

async function readBackAccount(requestedUserId) {
  const response = await getIndependentBoardCreditAccount(requestedUserId)
  return parseCreditAccount(response?.data, requestedUserId)
}

function openGrantDialog() {
  if (!canGrantCurrentUser.value || mutating.value) return
  if (pendingMutation.value) {
    mutationAmbiguous.value = true
    mutationError.value = '积分发放结果仍不明确；仅可使用已锁定的原请求与原幂等键重试。'
  } else {
    mutationError.value = ''
    mutationAmbiguous.value = false
  }
  grantDialogVisible.value = true
  if (pendingMutation.value) restorePendingDialog()
}

function openReversalDialog(operation) {
  if (!canReverseCurrentUser.value || mutating.value || !account.value) return
  if (pendingMutation.value
      && pendingMutation.value.original?.operationId !== operation?.operationId) {
    ElMessage.warning('当前有另一笔未决冲正，只能恢复原操作与原幂等键。')
    return
  }
  if (operation?.operationType !== 'GRANT'
      || isCreditOperationReversed(operation, account.value.records)) return
  if (pendingMutation.value) {
    mutationAmbiguous.value = true
    mutationError.value = '积分冲正结果仍不明确；仅可使用已锁定的原请求与原幂等键重试。'
  } else {
    mutationError.value = ''
    mutationAmbiguous.value = false
  }
  reversalOperation.value = operation
  reversalDialogVisible.value = true
  if (pendingMutation.value) restorePendingDialog()
}

function resumePendingMutation() {
  const pending = pendingMutation.value
  if (!pending || pending.userId !== selectedUserId.value
      || !identityContextCurrent.value || pageError.value || mutating.value) return
  mutationAmbiguous.value = true
  mutationError.value = `积分${pending.kind === 'grant' ? '发放' : '冲正'}结果仍不明确；本次只会重试同一请求。`
  if (pending.kind === 'grant') {
    grantDialogVisible.value = true
  } else {
    reversalOperation.value = pending.original
    reversalDialogVisible.value = true
  }
  restorePendingDialog()
}

function currentExpectedAccountVersion() {
  if (account.value?.userId === selectedUserId.value) return account.value.version
  return accountNotFound.value ? 0 : null
}

async function submitGrant(payload) {
  if (mutating.value || !canGrantCurrentUser.value || payload.userId !== selectedUserId.value) return
  const requestedUserId = selectedUserId.value
  let wasRecovering = false
  let committedResponse = false
  let enteredLock = false
  let localSafetyRejected = false
  mutating.value = true
  mutationError.value = ''
  try {
    await withCreditAccountMutationLock({
      actorUserId,
      run: async () => {
        enteredLock = true
        if (!synchronizePendingFromStorage()) {
          localSafetyRejected = true
          throw new Error('CREDIT_LOCAL_PENDING_CONFLICT')
        }
        wasRecovering = pendingMutation.value !== null
        const expectedVersion = pendingMutation.value?.payload.expectedAccountVersion
          ?? currentExpectedAccountVersion()
        if (payload.expectedAccountVersion !== expectedVersion
            || !establishPendingMutation('grant', requestedUserId, payload)) {
          localSafetyRejected = true
          mutationError.value = '未决发放请求的用户、账户版本、参数或幂等键发生变化，已拒绝发送。'
          throw new Error('CREDIT_LOCAL_PENDING_CONFLICT')
        }
        const response = await grantIndependentBoardCredit(payload)
        committedResponse = true
        const result = verifyCreditGrantResult({
          payload,
          result: parseCreditCommandResult(response?.data)
        })
        const audit = await readBackAccount(requestedUserId)
        if (selectedUserId.value !== requestedUserId || !operationMatchesReadback(audit, result)) {
          throw new Error('积分发放回读未包含已提交操作')
        }
        account.value = audit
        accountNotFound.value = false
        clearPendingMutation()
        grantDialogVisible.value = false
        grantDialogRef.value?.reset()
        ElMessage.success('积分已发放，服务端回执与账本回读一致。')
      }
    })
  } catch (error) {
    if (localSafetyRejected || !enteredLock) {
      mutationAmbiguous.value = Boolean(pendingMutation.value)
      if (!mutationError.value) {
        mutationError.value = '当前浏览器无法建立安全的跨标签积分互斥，已在发送前停止操作。'
      }
      return
    }
    mutationAmbiguous.value = wasRecovering
      || committedResponse
      || isAmbiguousCreditFailure(error)
    mutationError.value = isCreditAccountVersionConflict(error)
      ? '积分账户已被另一请求更新；本次未写入，请刷新账本后重新确认。'
      : (mutationAmbiguous.value
        ? '积分发放结果不明确，页面未将本次操作显示为成功。'
        : '积分发放被服务端拒绝，页面未改变账本状态。')
    if (!mutationAmbiguous.value) {
      clearPendingMutation()
      grantDialogRef.value?.reset()
    }
  } finally {
    mutating.value = false
  }
}

async function submitReversal(payload) {
  const original = pendingMutation.value?.kind === 'reverse'
    ? pendingMutation.value.original
    : reversalOperation.value
  const requestedUserId = selectedUserId.value
  if (mutating.value || !canReverseCurrentUser.value
      || !original || !Number.isSafeInteger(requestedUserId)) return
  let wasRecovering = false
  let committedResponse = false
  let enteredLock = false
  let localSafetyRejected = false
  mutating.value = true
  mutationError.value = ''
  try {
    await withCreditAccountMutationLock({
      actorUserId,
      run: async () => {
        enteredLock = true
        if (!synchronizePendingFromStorage()) {
          localSafetyRejected = true
          throw new Error('CREDIT_LOCAL_PENDING_CONFLICT')
        }
        wasRecovering = pendingMutation.value !== null
        const expectedVersion = pendingMutation.value?.payload.expectedAccountVersion
          ?? currentExpectedAccountVersion()
        if (payload.expectedAccountVersion !== expectedVersion
            || !establishPendingMutation('reverse', requestedUserId, payload, original)) {
          localSafetyRejected = true
          mutationError.value = '未决冲正请求的用户、账户版本、原操作、参数或幂等键发生变化，已拒绝发送。'
          throw new Error('CREDIT_LOCAL_PENDING_CONFLICT')
        }
        const response = await reverseIndependentBoardCredit(payload)
        committedResponse = true
        const result = verifyCreditReversalResult({
          userId: requestedUserId,
          original,
          payload,
          result: parseCreditCommandResult(response?.data)
        })
        const audit = await readBackAccount(requestedUserId)
        if (selectedUserId.value !== requestedUserId || !operationMatchesReadback(audit, result)) {
          throw new Error('积分冲正回读未包含已提交操作')
        }
        account.value = audit
        clearPendingMutation()
        reversalDialogVisible.value = false
        reversalOperation.value = null
        reversalDialogRef.value?.reset()
        ElMessage.success('积分发放已冲正，服务端回执与账本回读一致。')
      }
    })
  } catch (error) {
    if (localSafetyRejected || !enteredLock) {
      mutationAmbiguous.value = Boolean(pendingMutation.value)
      if (!mutationError.value) {
        mutationError.value = '当前浏览器无法建立安全的跨标签积分互斥，已在发送前停止操作。'
      }
      return
    }
    mutationAmbiguous.value = wasRecovering
      || committedResponse
      || isAmbiguousCreditFailure(error)
    mutationError.value = isCreditAccountVersionConflict(error)
      ? '积分账户已被另一请求更新；本次未写入，请刷新账本后重新确认。'
      : (mutationAmbiguous.value
        ? '积分冲正结果不明确，页面未将本次操作显示为成功。'
        : '积分冲正被服务端拒绝，页面未改变账本状态。')
    if (!mutationAmbiguous.value) {
      clearPendingMutation()
      reversalDialogRef.value?.reset()
    }
  } finally {
    mutating.value = false
  }
}
</script>

<style lang="scss" scoped>
.credit-admin-page {
  --board-navy: #14233b;
  --board-muted: #667085;
  min-height: calc(100vh - 84px);
  background: #f6f8fb;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;

  h2 { margin: 2px 0 7px; color: var(--board-navy); font-size: 25px; }
  p:last-child { margin: 0; color: var(--board-muted); line-height: 1.6; }
}

.brand-line {
  margin: 0;
  color: #246bfd;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.query-card,
.content-card,
.summary-card {
  border: 1px solid #e5eaf1;
  border-radius: 14px;
}

.query-form {
  display: flex;
  align-items: flex-end;
  gap: 12px;

  :deep(.el-form-item) { margin: 0; }
}

.user-id-input { width: 320px; }
.query-actions { display: flex; gap: 10px; }
.query-actions .el-button + .el-button { margin-left: 0; }
.query-note,
.first-grant-card p,
.table-toolbar p {
  margin: 10px 0 0;
  color: var(--board-muted);
  font-size: 12px;
  line-height: 1.6;
}

.page-alert,
.content-card { margin-top: 16px; }
.pending-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 12px;
  padding: 12px 14px;
  color: #7a4d00;
  background: #fff8e8;
  border: 1px solid #f1c76f;
  border-radius: 10px;
}
.content-card { padding: 18px; background: #fff; }
.first-grant-card { text-align: center; }

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.summary-card :deep(.el-card__body) {
  display: grid;
  gap: 7px;
  padding: 18px 20px;

  span,
  small { color: var(--board-muted); }
  strong { color: var(--board-navy); font-size: 24px; }
}

.balance-card { border-color: #91b4ff; background: #f4f7ff; }
.ledger-card { padding: 0; }
.table-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;

  h3 { margin: 0; color: var(--board-navy); }
}
.table-scroll { overflow-x: auto; }

@media (max-width: 1100px) {
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 768px) {
  .credit-admin-page { padding: 14px; }
  .page-heading,
  .query-form,
  .table-toolbar,
  .pending-toolbar { align-items: stretch; flex-direction: column; }
  .query-form :deep(.el-form-item),
  .user-id-input,
  .query-actions,
  .query-actions .el-button,
  .table-toolbar .el-button { width: 100%; }
  .query-actions { flex-direction: column; }
  .summary-grid { grid-template-columns: 1fr; }
  .content-card { padding: 12px; }
}
</style>
