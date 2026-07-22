<template>
  <div class="app-container board-admin-page">
    <div class="page-heading">
      <div>
        <p class="brand-line">福帮手 FBSir · 独董会</p>
        <h2>产品与权益</h2>
        <p>按企业查看和调整独董会授予记录。VIP 授予与连接器认证是两个独立状态。</p>
      </div>
    </div>

    <PlanPolicyGovernancePanel
      v-if="showPlanPolicyCandidate"
      :plans="planCatalog"
      :plans-loading="planCatalogLoading"
      :plans-error="planCatalogError"
      :can-revise="canRevisePlanPolicy"
      :can-audit="canAuditPlanPolicy"
      :actor-user-id="actorUserId"
      :external-busy="entitlementMutating"
      @catalog-updated="handlePlanPolicyCatalogUpdated"
      @mutating-change="planPolicyMutating = $event"
    />

    <el-alert
      v-if="!canListEnterprises || !canListMembers || !canQueryEntitlements"
      title="当前账号缺少运营台所需权限"
      description="需要企业列表、企业成员列表和独董会权益查询权限；页面不会提供手工输入企业或成员编号的旁路。"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-card v-else shadow="never" class="context-card">
      <el-form :inline="true" label-position="top" class="context-form">
        <el-form-item label="当前企业">
          <el-select
            v-model="selectedTenantId"
            filterable
            clearable
            :loading="enterpriseLoading"
            :disabled="enterpriseLoading || isMutating"
            placeholder="从可管理企业中选择"
            class="tenant-select"
            @change="handleTenantChange"
          >
            <el-option
              v-for="enterprise in enterpriseOptions"
              :key="enterprise.id"
              :label="enterpriseOptionLabel(enterprise)"
              :value="enterprise.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据操作">
          <el-button
            icon="Refresh"
            :loading="dataLoading"
            :disabled="!selectedTenantId || isMutating"
            @click="refreshCurrentTenant"
          >刷新当前企业</el-button>
        </el-form-item>
      </el-form>
      <p class="context-note">本页所有统计均只针对当前选中的企业，不代表系统全局数据。</p>
    </el-card>

    <el-alert
      v-if="enterpriseTruncated"
      title="企业选择列表未完整返回"
      description="当前仅能选择接口返回的前 1000 个企业；请先通过企业管理缩小范围后重试。"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    />
    <el-alert
      v-if="memberTruncated && selectedTenantId"
      title="成员选择列表未完整返回"
      description="为避免把权益授予错误身份，本页已停止新增或调整，请先完善成员列表分页接口。"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    />
    <el-alert
      v-if="enterpriseError || planCatalogError || dataError"
      :title="enterpriseError || planCatalogError || dataError"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-skeleton v-if="dataLoading" :rows="7" animated class="loading-card" />

    <el-empty
      v-else-if="canOperatePage && !selectedTenantId"
      description="请选择企业后查看独董会权益"
      class="empty-card"
    />

    <template v-else-if="canOperatePage && selectedTenantId && !dataError">
      <section class="summary-grid" aria-label="当前企业权益摘要">
        <el-card shadow="never" class="summary-card">
          <span>授予记录</span>
          <strong>{{ entitlements.length }}</strong>
          <small>当前企业</small>
        </el-card>
        <el-card shadow="never" class="summary-card">
          <span>当前授予 VIP</span>
          <strong>{{ grantedVipCount }}</strong>
          <small>不等于已激活</small>
        </el-card>
        <el-card shadow="never" class="summary-card pending-card">
          <span>等待连接</span>
          <strong>{{ pendingConnectorCount }}</strong>
          <small>需完成连接器认证</small>
        </el-card>
        <el-card shadow="never" class="summary-card">
          <span>VIP 已生效</span>
          <strong>{{ activeVipCount }}</strong>
          <small>以服务端激活状态为准</small>
        </el-card>
      </section>

      <el-card shadow="never" class="table-card">
        <div class="table-toolbar">
          <div>
            <h3>{{ selectedEnterprise?.enterpriseName || '当前企业' }}</h3>
            <p>授予状态和激活状态分开展示；未设置截止时间不代表永久承诺。</p>
          </div>
          <el-button
            type="primary"
            icon="Plus"
            :disabled="!canOpenGrant || isMutating"
            @click="openGrantDialog"
            v-hasPermi="['board:entitlement:grant']"
          >授予权益</el-button>
        </div>

        <div class="table-scroll">
          <el-table :data="entitlements" empty-text="当前企业暂无独董会权益记录">
            <el-table-column label="成员" min-width="210">
              <template #default="scope">
                <div class="member-cell">
                  <strong>{{ memberLabel(memberFor(scope.row)) }}</strong>
                  <span>用户 #{{ scope.row.userId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="授予套餐" width="110" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.planCode === 'BOARD_VIP' ? 'primary' : 'info'">
                  {{ currentPlanLabel(scope.row.planCode) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="授予状态" width="120" align="center">
              <template #default="scope">
                <el-tag :type="entitlementStatusMeta(scope.row.entitlementStatus).type">
                  {{ entitlementStatusMeta(scope.row.entitlementStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前生效状态" width="150" align="center">
              <template #default="scope">
                <el-tag
                  :type="activationMeta(scope.row.activationState).type"
                  :effect="scope.row.activationState === 'PENDING_CONNECTOR' ? 'dark' : 'light'"
                >
                  {{ activationMeta(scope.row.activationState).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="有效期" min-width="190">
              <template #default="scope">
                <div class="date-cell">
                  <span>自 {{ formatBoardDateTime(scope.row.validFrom) }}</span>
                  <span>至 {{ formatBoardDateTime(scope.row.validUntil) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="版本" prop="version" width="80" align="center" />
            <el-table-column label="更新时间" min-width="175">
              <template #default="scope">{{ formatBoardDateTime(scope.row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right" align="center">
              <template #default="scope">
                <el-button
                  link
                  type="primary"
                  :disabled="!canAdjust(scope.row) || isMutating"
                  @click="openAdjustDialog(scope.row)"
                  v-hasPermi="['board:entitlement:grant']"
                >调整</el-button>
                <el-button
                  link
                  type="danger"
                  :loading="revokingMemberId === scope.row.memberId"
                  :disabled="!canRevoke(scope.row) || isMutating"
                  @click="revokeEntitlement(scope.row)"
                  v-hasPermi="['board:entitlement:revoke']"
                >撤销</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
    </template>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '授予独董会权益' : '调整独董会权益'"
      width="min(640px, calc(100vw - 32px))"
      append-to-body
      :close-on-click-modal="!submitting"
      :close-on-press-escape="!submitting"
      @closed="resetDialog"
    >
      <el-alert
        v-if="grantForm.planCode === 'BOARD_VIP'"
        title="授予 VIP 不等于激活 VIP"
        description="保存后若连接器尚未完成权威认证，状态将保持“待连接器认证”。本页不能代替用户完成连接授权或强制激活。"
        type="warning"
        show-icon
        :closable="false"
        class="dialog-alert"
      />
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="dialog-alert"
      />
      <el-form ref="grantFormRef" :model="grantForm" :rules="grantRules" label-width="110px">
        <el-form-item label="目标企业">
          <el-input :model-value="selectedEnterprise?.enterpriseName || ''" disabled />
        </el-form-item>
        <el-form-item label="目标成员" prop="memberId">
          <el-select
            v-model="grantForm.memberId"
            filterable
            :disabled="dialogMode === 'adjust' || submitting"
            placeholder="从当前企业有效成员中选择"
            style="width: 100%"
          >
            <el-option
              v-for="member in grantableMembers"
              :key="member.id"
              :label="memberLabel(member)"
              :value="member.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="授予套餐" prop="planCode">
          <el-radio-group v-model="grantForm.planCode" :disabled="submitting || planCatalogLoading">
            <el-radio-button
              v-for="plan in planCatalog"
              :key="plan.planCode"
              :value="plan.planCode"
            >{{ plan.planName }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="截止时间" prop="validUntil">
          <el-date-picker
            v-model="grantForm.validUntil"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="不设置截止时间"
            :disabled="submitting"
            style="width: 100%"
          />
          <span class="field-note">空值仅表示当前记录未设置截止时间。</span>
        </el-form-item>
        <el-form-item v-if="dialogMode === 'adjust'" label="并发版本">
          <el-input :model-value="editingEntitlement?.version" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitGrant">保存权益</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="IndependentBoardAdminEntitlement">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listEnterprise } from '@/api/business/fbs/enterprise'
import { listEnterpriseMember } from '@/api/business/fbs/enterpriseMember'
import {
  grantIndependentBoardEntitlement,
  listIndependentBoardEntitlements,
  listIndependentBoardPlans,
  revokeIndependentBoardEntitlement
} from '@/api/business/independentBoard/admin'
import { checkPermi, checkRole } from '@/utils/permission'
import useUserStore from '@/store/modules/user'
import PlanPolicyGovernancePanel from './PlanPolicyGovernancePanel.vue'
import {
  activationMeta,
  buildEntitlementGrantPayload,
  buildEntitlementRevokeConfirmation,
  buildEntitlementRevokePayload,
  createLatestRequestGuard,
  entitlementStatusMeta,
  expectedSavedEntitlementVersion,
  formatBoardDateTime,
  isCasConflict,
  memberLabel,
  parseEnterprisePage,
  parseEntitlement,
  parseEntitlementList,
  parseMemberPage,
  parseProductPlanCatalog,
  toBoardDateTimeInput,
  verifyEntitlementRevokeReadback
} from '../model.js'

const canListEnterprises = checkPermi(['business:fbs:enterprise:list'])
const canListMembers = checkPermi(['business:fbs:enterpriseMember:list'])
const canQueryEntitlements = checkPermi(['board:entitlement:query'])
const canGrantEntitlements = checkPermi(['board:entitlement:grant'])
const canRevokeEntitlements = checkPermi(['board:entitlement:revoke'])
const planPolicyCandidateEnabled =
  import.meta.env.VITE_FBSIR_BOARD_PLAN_POLICY_CANDIDATE === 'true'
const isGlobalAdmin = checkRole(['admin'])
const canRevisePlanPolicy = checkPermi(['board:plan:revise'])
const canAuditPlanPolicy = checkPermi(['board:plan:audit'])
const showPlanPolicyCandidate = planPolicyCandidateEnabled
  && isGlobalAdmin
  && (canRevisePlanPolicy || canAuditPlanPolicy)
const actorUserId = Number(useUserStore().id)
const canOperatePage = canListEnterprises && canListMembers && canQueryEntitlements

const enterpriseOptions = ref([])
const enterpriseLoading = ref(false)
const enterpriseError = ref('')
const enterpriseTruncated = ref(false)
const selectedTenantId = ref(null)
const members = ref([])
const memberTruncated = ref(false)
const entitlements = ref([])
const planCatalog = ref([])
const planCatalogLoading = ref(false)
const planCatalogError = ref('')
const dataLoading = ref(false)
const dataError = ref('')
const tenantRequests = createLatestRequestGuard()

const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingEntitlement = ref(null)
const submitting = ref(false)
const revokeGuardMemberId = ref(null)
const revokingMemberId = ref(null)
const planPolicyMutating = ref(false)
const formError = ref('')
const grantFormRef = ref(null)
const grantForm = reactive({
  memberId: null,
  planCode: null,
  validUntil: null
})

const grantRules = {
  memberId: [{ required: true, message: '请选择当前企业的有效成员', trigger: 'change' }],
  planCode: [{ required: true, message: '请选择独董会套餐', trigger: 'change' }],
  validUntil: [{ validator: validateValidUntil, trigger: 'change' }]
}

const selectedEnterprise = computed(() =>
  enterpriseOptions.value.find(item => item.id === selectedTenantId.value) || null)
const entitlementMutating = computed(() => submitting.value
  || revokeGuardMemberId.value !== null
  || revokingMemberId.value !== null)
const isMutating = computed(() => entitlementMutating.value || planPolicyMutating.value)
const memberMap = computed(() => new Map(members.value.map(item => [item.id, item])))
const grantedVipCount = computed(() =>
  entitlements.value.filter(item =>
    item.planCode === 'BOARD_VIP' && item.entitlementStatus === 'ACTIVE').length)
const pendingConnectorCount = computed(() =>
  entitlements.value.filter(item => item.activationState === 'PENDING_CONNECTOR').length)
const activeVipCount = computed(() =>
  entitlements.value.filter(item => item.activationState === 'ACTIVE').length)
const unassignedActiveMembers = computed(() => {
  const assigned = new Set(entitlements.value.map(item => item.memberId))
  return members.value.filter(item => item.status === 1 && !assigned.has(item.id))
})
const grantableMembers = computed(() => {
  if (dialogMode.value === 'adjust' && editingEntitlement.value) {
    const member = memberMap.value.get(editingEntitlement.value.memberId)
    return member ? [member] : []
  }
  return unassignedActiveMembers.value
})
const canOpenGrant = computed(() =>
  canGrantEntitlements
  && planCatalog.value.length === 2
  && selectedEnterprise.value?.status === 1
  && !memberTruncated.value
  && unassignedActiveMembers.value.length > 0)

function enterpriseOptionLabel(enterprise) {
  return enterprise.status === 1
    ? enterprise.enterpriseName
    : `${enterprise.enterpriseName}（已禁用）`
}

function memberFor(entitlement) {
  const member = memberMap.value.get(entitlement.memberId)
  if (!member || member.userId !== entitlement.userId) return null
  return member
}

function canAdjust(entitlement) {
  const member = memberFor(entitlement)
  return canGrantEntitlements
    && entitlement.entitlementStatus === 'ACTIVE'
    && selectedEnterprise.value?.status === 1
    && planCatalog.value.some(plan => plan.planCode === entitlement.planCode)
    && !memberTruncated.value
    && member?.status === 1
}

function currentPlanLabel(planCode) {
  return planCatalog.value.find(plan => plan.planCode === planCode)?.planName || planCode
}

async function loadPlanCatalog() {
  if (!canQueryEntitlements) return
  planCatalogLoading.value = true
  planCatalogError.value = ''
  try {
    const response = await listIndependentBoardPlans()
    planCatalog.value = [...parseProductPlanCatalog(response.data)]
  } catch {
    planCatalog.value = []
    planCatalogError.value = '套餐与配额策略加载失败或发生契约漂移，已停止授予和调整操作。'
  } finally {
    planCatalogLoading.value = false
  }
}

function handlePlanPolicyCatalogUpdated(catalog) {
  try {
    planCatalog.value = [...parseProductPlanCatalog(catalog)]
    planCatalogError.value = ''
  } catch {
    planCatalog.value = []
    planCatalogError.value = '套餐策略写入后的目录回读不符合安全合同，已停止后续操作。'
  }
}

function canRevoke(entitlement) {
  return canRevokeEntitlements
    && entitlement?.tenantId === selectedTenantId.value
    && entitlement?.entitlementStatus === 'ACTIVE'
}

async function loadEnterpriseOptions() {
  if (!canListEnterprises) return
  enterpriseLoading.value = true
  enterpriseError.value = ''
  try {
    const response = await listEnterprise({ pageNum: 1, pageSize: 1000 })
    const page = parseEnterprisePage(response)
    enterpriseOptions.value = [...page.records]
    enterpriseTruncated.value = page.truncated
  } catch {
    enterpriseOptions.value = []
    enterpriseError.value = '企业列表加载失败或响应格式不符合安全要求，请刷新后重试。'
  } finally {
    enterpriseLoading.value = false
  }
}

function clearTenantData() {
  members.value = []
  entitlements.value = []
  memberTruncated.value = false
  dataError.value = ''
}

function handleTenantChange() {
  dialogVisible.value = false
  loadSelectedTenant()
}

function refreshCurrentTenant() {
  dialogVisible.value = false
  loadSelectedTenant()
}

async function loadSelectedTenant() {
  const requestToken = tenantRequests.next()
  const requestedTenantId = selectedTenantId.value
  clearTenantData()
  if (!requestedTenantId || !canOperatePage) {
    dataLoading.value = false
    return
  }
  dataLoading.value = true
  try {
    const [memberResponse, entitlementResponse] = await Promise.all([
      listEnterpriseMember({ enterpriseId: requestedTenantId, pageNum: 1, pageSize: 1000 }),
      listIndependentBoardEntitlements(requestedTenantId)
    ])
    if (!tenantRequests.isCurrent(requestToken)
        || selectedTenantId.value !== requestedTenantId) return
    const memberPage = parseMemberPage(memberResponse, requestedTenantId)
    const safeEntitlements = parseEntitlementList(entitlementResponse.data, requestedTenantId)
    members.value = [...memberPage.records]
    memberTruncated.value = memberPage.truncated
    entitlements.value = [...safeEntitlements]
  } catch {
    if (!tenantRequests.isCurrent(requestToken)
        || selectedTenantId.value !== requestedTenantId) return
    clearTenantData()
    dataError.value = '当前企业权益加载失败或返回了非安全数据，页面已停止展示。'
  } finally {
    if (tenantRequests.isCurrent(requestToken)) dataLoading.value = false
  }
}

function resetDialog() {
  dialogMode.value = 'create'
  editingEntitlement.value = null
  formError.value = ''
  grantForm.memberId = null
  grantForm.planCode = planCatalog.value.find(plan => plan.planCode === 'BOARD_FREE')?.planCode || null
  grantForm.validUntil = null
  nextTick(() => grantFormRef.value?.clearValidate())
}

function openGrantDialog() {
  if (!canOpenGrant.value) {
    ElMessage.warning('当前企业没有可安全授予权益的有效成员。')
    return
  }
  resetDialog()
  dialogVisible.value = true
}

function openAdjustDialog(entitlement) {
  if (!canAdjust(entitlement)) {
    ElMessage.warning('该记录未绑定当前企业有效成员，不能直接调整。')
    return
  }
  dialogMode.value = 'adjust'
  editingEntitlement.value = entitlement
  formError.value = ''
  grantForm.memberId = entitlement.memberId
  grantForm.planCode = entitlement.planCode
  grantForm.validUntil = toBoardDateTimeInput(entitlement.validUntil)
  dialogVisible.value = true
  nextTick(() => grantFormRef.value?.clearValidate())
}

function validateValidUntil(rule, value, callback) {
  if (!value) {
    callback()
    return
  }
  const instant = Date.parse(`${value}+08:00`)
  if (Number.isNaN(instant) || instant <= Date.now()) {
    callback(new Error('截止时间必须晚于当前时间'))
    return
  }
  callback()
}

async function submitGrant() {
  if (submitting.value || !canGrantEntitlements) return
  const valid = await grantFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const tenantId = selectedTenantId.value
  const member = memberMap.value.get(grantForm.memberId)
  const original = editingEntitlement.value
  let payload
  try {
    payload = buildEntitlementGrantPayload({
      tenantId,
      member,
      planCode: grantForm.planCode,
      validUntil: grantForm.validUntil,
      existingEntitlement: original
    })
  } catch (error) {
    formError.value = error.message
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    const response = await grantIndependentBoardEntitlement(payload)
    const saved = parseEntitlement(response.data, tenantId)
    const expectedSavedVersion = expectedSavedEntitlementVersion(original?.version ?? null)
    if (saved.memberId !== payload.memberId
        || saved.userId !== payload.userId
        || saved.planCode !== payload.planCode
        || saved.version !== expectedSavedVersion) {
      throw new Error('保存回读与提交内容不一致')
    }
    dialogVisible.value = false
    ElMessage.success(saved.activationState === 'PENDING_CONNECTOR'
      ? 'VIP 已授予，正在等待连接器认证。'
      : '独董会权益已保存。')
    await loadSelectedTenant()
  } catch (error) {
    if (isCasConflict(error)) {
      const memberId = payload.memberId
      dialogVisible.value = false
      ElMessage.warning('权益已被其他操作更新，页面已刷新到最新版本，请重新确认。')
      await loadSelectedTenant()
      const current = entitlements.value.find(item => item.memberId === memberId)
      if (current && canAdjust(current)) openAdjustDialog(current)
    } else {
      formError.value = '权益保存失败或回读不一致，未将本次操作显示为成功。'
    }
  } finally {
    submitting.value = false
  }
}

async function revokeEntitlement(entitlement) {
  if (isMutating.value || !canRevoke(entitlement)) return
  revokeGuardMemberId.value = entitlement.memberId
  const tenantId = selectedTenantId.value
  let payload
  try {
    payload = buildEntitlementRevokePayload({ tenantId, entitlement })
  } catch (error) {
    revokeGuardMemberId.value = null
    ElMessage.error(error.message)
    return
  }

  let confirmation
  try {
    confirmation = buildEntitlementRevokeConfirmation({
      enterprise: selectedEnterprise.value,
      member: memberFor(entitlement),
      entitlement,
      planCatalog: planCatalog.value
    })
  } catch (error) {
    revokeGuardMemberId.value = null
    ElMessage.error(error.message)
    return
  }
  try {
    await ElMessageBox.confirm(
      confirmation.message,
      confirmation.title,
      {
        confirmButtonText: confirmation.confirmButtonText,
        cancelButtonText: '取消',
        type: 'warning',
        distinguishCancelAndClose: true,
        closeOnClickModal: false
      }
    )
  } catch {
    revokeGuardMemberId.value = null
    return
  }

  const current = entitlements.value.find(item => item.memberId === payload.memberId)
  if (selectedTenantId.value !== tenantId
      || !current
      || current.userId !== payload.userId
      || current.version !== payload.expectedVersion
      || !canRevoke(current)) {
    revokeGuardMemberId.value = null
    ElMessage.warning('权益数据已变化，请刷新后重新确认；本次未发出撤销请求。')
    return
  }

  revokingMemberId.value = payload.memberId
  try {
    const response = await revokeIndependentBoardEntitlement(payload)
    verifyEntitlementRevokeReadback({ tenantId, original: current, saved: response.data })
    ElMessage.success('独董会权益已撤销，该成员已回退为免费版；审计回执已生成。')
    await loadSelectedTenant()
  } catch (error) {
    if (isCasConflict(error)) {
      ElMessage.warning('权益数据已被其他操作更新，页面已刷新；本次不会自动重试撤销。')
      await loadSelectedTenant()
    } else {
      ElMessage.error('权益撤销失败或回读不一致，未将本次操作显示为成功。')
    }
  } finally {
    revokingMemberId.value = null
    revokeGuardMemberId.value = null
  }
}

onMounted(async () => {
  await Promise.all([loadEnterpriseOptions(), loadPlanCatalog()])
})
</script>

<style lang="scss" scoped>
.board-admin-page {
  --board-navy: #14233b;
  --board-muted: #667085;
  min-height: calc(100vh - 84px);
  background: #f6f8fb;
}

.page-heading {
  margin-bottom: 18px;

  h2 {
    margin: 2px 0 7px;
    color: var(--board-navy);
    font-size: 25px;
  }

  p:last-child {
    margin: 0;
    color: var(--board-muted);
    line-height: 1.6;
  }
}

.brand-line {
  margin: 0;
  color: #246bfd;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.context-card,
.table-card,
.summary-card,
.loading-card,
.empty-card {
  border: 1px solid #e5eaf1;
  border-radius: 14px;
}

.context-form {
  display: flex;
  align-items: flex-end;
  gap: 12px;

  :deep(.el-form-item) {
    margin-right: 0;
    margin-bottom: 0;
  }
}

.tenant-select {
  width: 360px;
}

.context-note,
.table-toolbar p {
  margin: 10px 0 0;
  color: var(--board-muted);
  font-size: 12px;
}

.page-alert,
.loading-card,
.empty-card {
  margin-top: 16px;
}

.loading-card,
.empty-card {
  padding: 24px;
  background: #fff;
}

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
  small {
    color: var(--board-muted);
  }

  strong {
    color: var(--board-navy);
    font-size: 28px;
  }
}

.pending-card {
  border-color: #f1c76f;
  background: #fffbf0;
}

.table-card {
  margin-top: 16px;
}

.table-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;

  h3 {
    margin: 0;
    color: var(--board-navy);
  }
}

.table-scroll {
  overflow-x: auto;
}

.member-cell,
.date-cell {
  display: grid;
  gap: 4px;

  span {
    color: var(--board-muted);
    font-size: 12px;
  }
}

.dialog-alert {
  margin-bottom: 18px;
}

.field-note {
  display: block;
  margin-top: 7px;
  color: var(--board-muted);
  font-size: 12px;
  line-height: 1.5;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .board-admin-page {
    padding: 14px;
  }

  .context-form,
  .table-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .context-form :deep(.el-form-item),
  .tenant-select,
  .table-toolbar .el-button {
    width: 100%;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .table-card :deep(.el-card__body) {
    padding: 14px;
  }

  .dialog-footer {
    display: grid;
    grid-template-columns: 1fr 1fr;

    .el-button {
      width: 100%;
      margin-left: 0;
    }
  }
}
</style>
