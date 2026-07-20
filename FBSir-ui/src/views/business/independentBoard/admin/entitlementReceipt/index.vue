<template>
  <div class="app-container board-admin-page">
    <div class="page-heading">
      <div>
        <p class="brand-line">福帮手 FBSir · 独董会</p>
        <h2>权益回执审计</h2>
        <p>只读核验独董会权益授予、调整与撤销回执；回执不提供修改或删除入口。</p>
      </div>
    </div>

    <el-alert
      v-if="!canListEnterprises || !canAuditReceipts"
      title="当前账号缺少权益回执审计所需权限"
      description="需要企业列表和独董会权益审计权限；页面不会提供手工输入企业编号的旁路。"
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
            @change="loadSelectedTenant"
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
      <p class="context-note">本页只读取当前选中企业的安全回执视图；租户隔离最终仍由服务端校验。</p>
    </el-card>

    <el-alert
      v-if="canOperatePage"
      title="回执证据边界"
      description="当前回读来自独董会专用权益回执表，并仅按租户筛选；该表不含 productCode，本页不能证明服务端执行了结构化产品过滤。"
      type="info"
      show-icon
      :closable="false"
      class="page-alert"
    />

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
      description="回执仍按服务端安全响应展示，但部分历史成员只能显示为成员编号。"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    />
    <el-alert
      v-if="selectedTenantId && (!canListMembers || memberEnrichmentError)"
      :title="canListMembers ? '成员名称增强加载失败' : '当前账号无成员名称增强权限'"
      description="不影响权益回执的安全读取；目标身份将以“历史成员 #编号”显示。"
      type="info"
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
      description="请选择企业后查看权益回执"
      class="empty-card"
    />

    <template v-else-if="canOperatePage && selectedTenantId && !dataError">
      <el-alert
        v-if="receiptEnvelope.truncated"
        :title="`权益回执已截断：服务端本次最多返回 ${receiptEnvelope.limit} 条`"
        description="当前不会把截断结果误作完整历史；需要完整历史时应补充分页或导出能力。"
        type="warning"
        show-icon
        :closable="false"
        class="page-alert"
      />
      <el-alert
        v-else
        :title="`当前接口单次最多返回 ${receiptEnvelope.limit} 条安全回执`"
        type="info"
        show-icon
        :closable="false"
        class="page-alert"
      />

      <el-card shadow="never" class="audit-card">
        <div class="table-heading">
          <div>
            <h3>{{ selectedEnterprise?.enterpriseName || '当前企业' }}</h3>
            <p>共返回 {{ receiptEnvelope.records.length }} 条，筛选后 {{ filteredRecords.length }} 条。</p>
          </div>
          <el-tag type="info" effect="plain">只读审计</el-tag>
        </div>

        <el-form :model="filters" :inline="true" label-position="top" class="filter-form">
          <el-form-item label="回执编号">
            <el-input
              v-model.trim="filters.receiptId"
              clearable
              placeholder="按回执编号筛选"
              class="receipt-filter"
              @input="resetPage"
            />
          </el-form-item>
          <el-form-item label="目标成员">
            <el-select
              v-model="filters.targetMemberId"
              filterable
              clearable
              placeholder="选择成员"
              class="filter-select"
              @change="resetPage"
            >
              <el-option
                v-for="memberId in targetMemberOptions"
                :key="memberId"
                :label="targetMemberLabel(memberId)"
                :value="memberId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="回执动作">
            <el-select
              v-model="filters.action"
              clearable
              placeholder="全部动作"
              class="filter-select"
              @change="resetPage"
            >
              <el-option
                v-for="action in actionOptions"
                :key="action"
                :label="entitlementReceiptActionMeta(action).label"
                :value="action"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="筛选操作">
            <el-button icon="RefreshLeft" @click="clearFilters">清空筛选</el-button>
          </el-form-item>
        </el-form>

        <div class="table-scroll">
          <el-table :data="pagedRecords" empty-text="当前筛选条件下没有权益回执">
            <el-table-column label="回执编号" prop="receiptId" min-width="260" show-overflow-tooltip />
            <el-table-column label="回执动作" width="135" align="center">
              <template #default="scope">
                <el-tag :type="entitlementReceiptActionMeta(scope.row.action).type">
                  {{ entitlementReceiptActionMeta(scope.row.action).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="目标成员" min-width="205">
              <template #default="scope">
                <div class="member-cell">
                  <strong>{{ targetMemberLabel(scope.row.targetMemberId) }}</strong>
                  <span>成员 #{{ scope.row.targetMemberId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作人" width="125" align="center">
              <template #default="scope">用户 #{{ scope.row.actorUserId }}</template>
            </el-table-column>
            <el-table-column label="证据级别" width="135" align="center">
              <template #default>操作已完成</template>
            </el-table-column>
            <el-table-column label="回执时间" min-width="175">
              <template #default="scope">{{ formatBoardDateTime(scope.row.createdAt) }}</template>
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

<script setup name="IndependentBoardAdminEntitlementReceipt">
import { computed, onMounted, reactive, ref } from 'vue'
import { listEnterprise } from '@/api/business/fbs/enterprise'
import { listEnterpriseMember } from '@/api/business/fbs/enterpriseMember'
import { listIndependentBoardEntitlementReceipts } from '@/api/business/independentBoard/admin'
import { checkPermi } from '@/utils/permission'
import {
  createLatestRequestGuard,
  entitlementReceiptActionMeta,
  formatBoardDateTime,
  memberLabel,
  parseEnterprisePage,
  parseEntitlementReceiptEnvelope,
  parseMemberPage
} from '../model.js'

const canListEnterprises = checkPermi(['business:fbs:enterprise:list'])
const canListMembers = checkPermi(['business:fbs:enterpriseMember:list'])
const canAuditReceipts = checkPermi(['board:entitlement:audit'])
const canOperatePage = canListEnterprises && canAuditReceipts
const actionOptions = Object.freeze([
  'ENTITLEMENT_GRANTED', 'ENTITLEMENT_UPDATED', 'ENTITLEMENT_REVOKED'
])
const EMPTY_RECEIPT_ENVELOPE = Object.freeze({
  records: Object.freeze([]), limit: 500, truncated: false
})

const enterpriseOptions = ref([])
const enterpriseLoading = ref(false)
const enterpriseError = ref('')
const enterpriseTruncated = ref(false)
const selectedTenantId = ref(null)
const members = ref([])
const memberTruncated = ref(false)
const memberEnrichmentError = ref(false)
const receiptEnvelope = ref(EMPTY_RECEIPT_ENVELOPE)
const dataLoading = ref(false)
const dataError = ref('')
const tenantRequests = createLatestRequestGuard()
const pageNum = ref(1)
const pageSize = ref(10)
const filters = reactive({ receiptId: '', targetMemberId: null, action: null })

const selectedEnterprise = computed(() =>
  enterpriseOptions.value.find(item => item.id === selectedTenantId.value) || null)
const memberMap = computed(() => new Map(members.value.map(item => [item.id, item])))
const targetMemberOptions = computed(() => {
  const result = new Set()
  for (const receipt of receiptEnvelope.value.records) result.add(receipt.targetMemberId)
  return [...result].sort((left, right) => left - right)
})
const filteredRecords = computed(() => {
  const receiptNeedle = filters.receiptId.trim().toLowerCase()
  return receiptEnvelope.value.records.filter(record =>
    (!receiptNeedle || record.receiptId.toLowerCase().includes(receiptNeedle))
    && (!filters.targetMemberId || record.targetMemberId === filters.targetMemberId)
    && (!filters.action || record.action === filters.action))
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

function targetMemberLabel(memberId) {
  const member = memberMap.value.get(memberId)
  return member ? memberLabel(member) : `历史成员 #${memberId}`
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
  memberEnrichmentError.value = false
  receiptEnvelope.value = EMPTY_RECEIPT_ENVELOPE
  dataError.value = ''
  clearFilters()
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
  if (canListMembers) {
    void applyMemberEnrichment(requestToken, requestedTenantId)
  }
  try {
    const receiptResponse = await listIndependentBoardEntitlementReceipts(requestedTenantId)
    if (!tenantRequests.isCurrent(requestToken)
        || selectedTenantId.value !== requestedTenantId) return
    const safeEnvelope = parseEntitlementReceiptEnvelope(receiptResponse.data, requestedTenantId)
    receiptEnvelope.value = safeEnvelope
  } catch {
    if (!tenantRequests.isCurrent(requestToken)
        || selectedTenantId.value !== requestedTenantId) return
    clearTenantData()
    dataError.value = '权益回执加载失败或返回了非安全数据，页面已停止展示。'
  } finally {
    if (tenantRequests.isCurrent(requestToken)) dataLoading.value = false
  }
}

async function applyMemberEnrichment(requestToken, tenantId) {
  const enrichment = await loadMemberEnrichment(tenantId)
  if (!tenantRequests.isCurrent(requestToken)
      || selectedTenantId.value !== tenantId) return
  members.value = [...enrichment.records]
  memberTruncated.value = enrichment.truncated
  memberEnrichmentError.value = enrichment.failed
}

async function loadMemberEnrichment(tenantId) {
  try {
    const response = await listEnterpriseMember({
      enterpriseId: tenantId, pageNum: 1, pageSize: 1000
    })
    const page = parseMemberPage(response, tenantId)
    return { records: page.records, truncated: page.truncated, failed: false }
  } catch {
    return { records: Object.freeze([]), truncated: false, failed: true }
  }
}

function resetPage() {
  pageNum.value = 1
}

function clearFilters() {
  filters.receiptId = ''
  filters.targetMemberId = null
  filters.action = null
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
  align-items: flex-start;
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

.receipt-filter {
  width: 260px;
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

@media (max-width: 1100px) {
  .filter-form {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));

    :deep(.el-form-item),
    .receipt-filter,
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
