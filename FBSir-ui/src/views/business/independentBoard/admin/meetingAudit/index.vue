<template>
  <div class="app-container board-admin-page">
    <div class="page-heading">
      <div>
        <p class="brand-line">福帮手 FBSir · 独董会</p>
        <h2>会议额度审计</h2>
        <p>只读查看独董会会议额度操作；额度预留不代表会议已经召开或完成。</p>
      </div>
    </div>

    <el-alert
      v-if="!canListEnterprises || !canListMembers || !canAuditOperations"
      title="当前账号缺少会议额度审计所需权限"
      description="需要企业列表、企业成员列表和独董会操作审计权限；页面不会提供手工企业、成员或用户编号旁路。"
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
            :disabled="enterpriseLoading"
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
            :disabled="!selectedTenantId"
            @click="loadSelectedTenant"
          >刷新当前企业</el-button>
        </el-form-item>
      </el-form>
      <p class="context-note">本页只查询当前企业；权限和租户隔离最终仍由服务端校验。</p>
    </el-card>

    <el-alert
      v-if="enterpriseTruncated"
      title="企业选择列表未完整返回"
      description="当前仅能选择接口返回的前 1000 个企业。"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    />
    <el-alert
      v-if="memberTruncated && selectedTenantId"
      title="成员列表未完整返回"
      description="审计记录仍按服务端安全响应展示，但部分成员名称可能只能显示为编号。"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    />
    <el-alert
      v-if="enterpriseError || dataError"
      :title="enterpriseError || dataError"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-skeleton v-if="dataLoading" :rows="8" animated class="loading-card" />

    <el-empty
      v-else-if="canOperatePage && !selectedTenantId"
      description="请选择企业后查看会议额度审计"
      class="empty-card"
    />

    <template v-else-if="canOperatePage && selectedTenantId && !dataError">
      <el-alert
        v-if="auditEnvelope.truncated"
        :title="`审计记录已截断：服务端本次最多返回 ${auditEnvelope.limit} 条`"
        description="请使用筛选定位目标记录；需要完整历史时，应先补充服务端分页或导出能力。"
        type="warning"
        show-icon
        :closable="false"
        class="page-alert"
      />
      <el-alert
        v-else
        :title="`当前接口单次最多返回 ${auditEnvelope.limit} 条安全审计记录`"
        type="info"
        show-icon
        :closable="false"
        class="page-alert"
      />

      <el-card shadow="never" class="audit-card">
        <div class="table-heading">
          <div>
            <h3>{{ selectedEnterprise?.enterpriseName || '当前企业' }}</h3>
            <p>共返回 {{ auditEnvelope.records.length }} 条，筛选后 {{ filteredRecords.length }} 条。</p>
          </div>
        </div>

        <el-form :model="filters" :inline="true" label-position="top" class="filter-form">
          <el-form-item label="操作编号">
            <el-input
              v-model.trim="filters.operationId"
              clearable
              placeholder="按操作编号筛选"
              class="operation-filter"
              @input="resetPage"
            />
          </el-form-item>
          <el-form-item label="企业成员">
            <el-select
              v-model="filters.memberId"
              filterable
              clearable
              placeholder="选择成员"
              class="filter-select"
              @change="resetPage"
            >
              <el-option
                v-for="member in memberFilterOptions"
                :key="member.id"
                :label="memberLabel(member)"
                :value="member.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="额度日期">
            <el-date-picker
              v-model="filters.bucketDate"
              type="date"
              value-format="YYYY-MM-DD"
              clearable
              placeholder="选择日期"
              class="filter-select"
              @change="resetPage"
            />
          </el-form-item>
          <el-form-item label="额度状态">
            <el-select
              v-model="filters.status"
              clearable
              placeholder="全部状态"
              class="filter-select"
              @change="resetPage"
            >
              <el-option
                v-for="status in statusOptions"
                :key="status"
                :label="operationStatusMeta(status).label"
                :value="status"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="有效套餐">
            <el-select
              v-model="filters.planCode"
              clearable
              placeholder="全部套餐"
              class="filter-select"
              @change="resetPage"
            >
              <el-option label="免费版" value="BOARD_FREE" />
              <el-option label="VIP版" value="BOARD_VIP" />
            </el-select>
          </el-form-item>
          <el-form-item label="筛选操作">
            <el-button icon="RefreshLeft" @click="clearFilters">清空筛选</el-button>
          </el-form-item>
        </el-form>

        <div class="table-scroll">
          <el-table :data="pagedRecords" empty-text="当前筛选条件下没有额度审计记录">
            <el-table-column label="操作编号" prop="operationId" min-width="230" show-overflow-tooltip />
            <el-table-column label="成员" min-width="205">
              <template #default="scope">
                <div class="member-cell">
                  <strong>{{ memberLabel(memberFor(scope.row)) }}</strong>
                  <span>用户 #{{ scope.row.userId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="额度日期" prop="bucketDate" width="115" align="center" />
            <el-table-column label="额度状态" width="145" align="center">
              <template #default="scope">
                <el-tag :type="operationStatusMeta(scope.row.status).type">
                  {{ operationStatusMeta(scope.row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="历史策略" min-width="220" show-overflow-tooltip>
              <template #default="scope">
                <div class="policy-cell">
                  <strong>{{ scope.row.policyPlanName }}</strong>
                  <span>{{ scope.row.effectivePlanCode }} / v{{ scope.row.policyVersion }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="议题数" prop="agendaCount" width="80" align="center" />
            <el-table-column label="席位数" prop="seatCount" width="80" align="center" />
            <el-table-column label="预留后余额" prop="remainingCount" width="105" align="center" />
            <el-table-column label="创建时间" min-width="175">
              <template #default="scope">{{ formatBoardDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="175">
              <template #default="scope">{{ formatBoardDateTime(scope.row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="额度处理时间" min-width="175">
              <template #default="scope">
                {{ scope.row.completedAt ? formatBoardDateTime(scope.row.completedAt) : '--' }}
              </template>
            </el-table-column>
          </el-table>
        </div>

        <pagination
          v-show="filteredRecords.length > 0"
          :total="filteredRecords.length"
          v-model:page="pageNum"
          v-model:limit="pageSize"
          @pagination="handlePagination"
        />
      </el-card>
    </template>
  </div>
</template>

<script setup name="IndependentBoardAdminMeetingAudit">
import { computed, onMounted, reactive, ref } from 'vue'
import { listEnterprise } from '@/api/business/fbs/enterprise'
import { listEnterpriseMember } from '@/api/business/fbs/enterpriseMember'
import { listIndependentBoardOperations } from '@/api/business/independentBoard/admin'
import { checkPermi } from '@/utils/permission'
import {
  createLatestRequestGuard,
  formatBoardDateTime,
  memberLabel,
  operationStatusMeta,
  parseEnterprisePage,
  parseMemberPage,
  parseOperationEnvelope
} from '../model.js'

const canListEnterprises = checkPermi(['business:fbs:enterprise:list'])
const canListMembers = checkPermi(['business:fbs:enterpriseMember:list'])
const canAuditOperations = checkPermi(['board:operation:audit'])
const canOperatePage = canListEnterprises && canListMembers && canAuditOperations

const statusOptions = Object.freeze([
  'PENDING', 'RESERVED', 'COMPLETED', 'REJECTED', 'RELEASED', 'FAILED', 'UNKNOWN'
])
const EMPTY_AUDIT_ENVELOPE = Object.freeze({ records: Object.freeze([]), limit: 500, truncated: false })

const enterpriseOptions = ref([])
const enterpriseLoading = ref(false)
const enterpriseError = ref('')
const enterpriseTruncated = ref(false)
const selectedTenantId = ref(null)
const members = ref([])
const memberTruncated = ref(false)
const auditEnvelope = ref(EMPTY_AUDIT_ENVELOPE)
const dataLoading = ref(false)
const dataError = ref('')
const tenantRequests = createLatestRequestGuard()
const pageNum = ref(1)
const pageSize = ref(10)
const filters = reactive({
  operationId: '',
  memberId: null,
  bucketDate: null,
  status: null,
  planCode: null
})

const selectedEnterprise = computed(() =>
  enterpriseOptions.value.find(item => item.id === selectedTenantId.value) || null)
const memberMap = computed(() => new Map(members.value.map(item => [item.id, item])))
const memberFilterOptions = computed(() => {
  const result = [...members.value]
  const known = new Set(result.map(item => item.id))
  for (const record of auditEnvelope.value.records) {
    if (!known.has(record.memberId)) {
      result.push(Object.freeze({
        id: record.memberId,
        enterpriseId: record.tenantId,
        userId: record.userId,
        userName: '',
        role: 'UNKNOWN',
        status: 2
      }))
      known.add(record.memberId)
    }
  }
  return result
})
const filteredRecords = computed(() => {
  const operationNeedle = filters.operationId.trim().toLowerCase()
  return auditEnvelope.value.records.filter(record =>
    (!operationNeedle || record.operationId.toLowerCase().includes(operationNeedle))
    && (!filters.memberId || record.memberId === filters.memberId)
    && (!filters.bucketDate || record.bucketDate === filters.bucketDate)
    && (!filters.status || record.status === filters.status)
    && (!filters.planCode || record.effectivePlanCode === filters.planCode))
})
const pagedRecords = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})

function enterpriseOptionLabel(enterprise) {
  return enterprise.status === 1
    ? enterprise.enterpriseName
    : `${enterprise.enterpriseName}（已禁用）`
}

function memberFor(record) {
  const member = memberMap.value.get(record.memberId)
  if (!member || member.userId !== record.userId) return {
    id: record.memberId,
    userId: record.userId,
    userName: ''
  }
  return member
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
  memberTruncated.value = false
  auditEnvelope.value = EMPTY_AUDIT_ENVELOPE
  dataError.value = ''
  clearFilters()
}

function handleTenantChange() {
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
    const [memberResponse, operationResponse] = await Promise.all([
      listEnterpriseMember({ enterpriseId: requestedTenantId, pageNum: 1, pageSize: 1000 }),
      listIndependentBoardOperations(requestedTenantId)
    ])
    if (!tenantRequests.isCurrent(requestToken)
        || selectedTenantId.value !== requestedTenantId) return
    const memberPage = parseMemberPage(memberResponse, requestedTenantId)
    const safeEnvelope = parseOperationEnvelope(operationResponse.data, requestedTenantId)
    members.value = [...memberPage.records]
    memberTruncated.value = memberPage.truncated
    auditEnvelope.value = safeEnvelope
  } catch {
    if (!tenantRequests.isCurrent(requestToken)
        || selectedTenantId.value !== requestedTenantId) return
    clearTenantData()
    dataError.value = '会议额度审计加载失败或返回了非安全数据，页面已停止展示。'
  } finally {
    if (tenantRequests.isCurrent(requestToken)) dataLoading.value = false
  }
}

function resetPage() {
  pageNum.value = 1
}

function clearFilters() {
  filters.operationId = ''
  filters.memberId = null
  filters.bucketDate = null
  filters.status = null
  filters.planCode = null
  resetPage()
}

function handlePagination() {
  const maxPage = Math.max(1, Math.ceil(filteredRecords.value.length / pageSize.value))
  if (pageNum.value > maxPage) pageNum.value = maxPage
}

onMounted(async () => {
  await loadEnterpriseOptions()
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
.audit-card,
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
.table-heading p {
  margin: 10px 0 0;
  color: var(--board-muted);
  font-size: 12px;
}

.page-alert,
.loading-card,
.empty-card,
.audit-card {
  margin-top: 16px;
}

.loading-card,
.empty-card {
  padding: 24px;
  background: #fff;
}

.table-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 15px;

  h3 {
    margin: 0;
    color: var(--board-navy);
  }
}

.filter-form {
  padding: 14px 14px 0;
  border-radius: 11px;
  background: #f7f9fc;

  :deep(.el-form-item) {
    margin-right: 12px;
  }
}

.operation-filter {
  width: 240px;
}

.filter-select {
  width: 190px;
}

.table-scroll {
  overflow-x: auto;
}

.member-cell {
  display: grid;
  gap: 4px;

  span {
    color: var(--board-muted);
    font-size: 12px;
  }
}

.policy-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.policy-cell span {
  color: #7a8494;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .filter-form {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));

    :deep(.el-form-item),
    .operation-filter,
    .filter-select {
      width: 100%;
    }
  }
}

@media (max-width: 768px) {
  .board-admin-page {
    padding: 14px;
  }

  .context-form {
    align-items: stretch;
    flex-direction: column;
  }

  .context-form :deep(.el-form-item),
  .tenant-select {
    width: 100%;
  }

  .filter-form {
    grid-template-columns: 1fr;
  }

  .audit-card :deep(.el-card__body) {
    padding: 14px;
  }
}
</style>
