<template>
  <div class="app-container candidate-admin-page" :aria-busy="loading">
    <header class="page-heading">
      <p class="brand-line">{{ brand }}</p>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </header>

    <el-alert
      v-if="!candidateEnabled"
      :title="defaultOffNotice"
      description="W4b.2 只读候选页尚未发布，当前环境不会调用候选接口。"
      type="info"
      show-icon
      :closable="false"
    />
    <el-alert
      v-else-if="!hasAccess"
      title="当前账号缺少候选审计权限"
      description="首期要求若依全局 admin 角色和本页细粒度查询权限；企业成员 ADMIN 不自动获得系统后台权限。"
      type="warning"
      show-icon
      :closable="false"
    />

    <template v-else>
      <el-card shadow="never" class="control-card">
        <el-form :inline="true" label-position="top">
          <el-form-item v-if="requiresTenant" label="当前企业">
            <el-select
              v-model="selectedTenantId"
              filterable
              :loading="enterpriseLoading"
              :disabled="enterpriseLoading || enterprises.length === 0"
              placeholder="从可管理企业中选择"
              @change="loadFirstPage"
            >
              <el-option
                v-for="enterprise in enterprises"
                :key="enterprise.id"
                :label="enterprise.enterpriseName"
                :value="enterprise.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="数据操作">
            <el-button
              :loading="loading"
              :disabled="requiresTenant && !selectedTenantId"
              @click="loadFirstPage"
            >刷新当前读数</el-button>
          </el-form-item>
        </el-form>
        <p class="boundary-note">本页只有 GET current-read；权限、租户隔离和字段投影最终由服务端执行。</p>
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
        title="服务端结果尚有下一页"
        description="继续加载仍会执行字段白名单、记录去重和企业边界校验。"
        type="warning"
        show-icon
        :closable="false"
        class="page-alert"
      />
      <el-skeleton v-if="loading && records.length === 0" :rows="8" animated class="table-card" />
      <el-empty
        v-else-if="readyForData && records.length === 0 && !errorMessage"
        description="当前范围暂无可展示记录"
        class="table-card"
      />

      <el-card v-else-if="records.length" shadow="never" class="table-card">
        <template #header>
          <div class="card-heading">
            <div>
              <h2>{{ currentScopeLabel }}</h2>
              <p>已安全核验 {{ records.length }} 条只读记录。</p>
            </div>
            <el-button :loading="loading" :disabled="!truncated || !nextCursor" @click="loadNextPage">
              加载下一页
            </el-button>
          </div>
        </template>

        <el-table :data="records" empty-text="暂无记录" class="candidate-table">
          <el-table-column
            v-for="column in columns"
            :key="column.prop"
            :prop="column.prop"
            :label="column.label"
            :min-width="column.minWidth || 140"
            :show-overflow-tooltip="column.overflow !== false"
          >
            <template #default="scope">
              <el-tag v-if="column.tag" :type="resolveTagType(scope.row, column)">
                {{ formatCell(scope.row, column) }}
              </el-tag>
              <span v-else>{{ formatCell(scope.row, column) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { listEnterprise } from '@/api/business/fbs/enterprise'
import { checkPermi, checkRole } from '@/utils/permission'
import { parseEnterprisePage } from '../model'
import { formatBoardCandidateReference } from '../../portalCandidateModel'

const props = defineProps({
  brand: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
  defaultOffNotice: { type: String, required: true },
  permission: { type: String, required: true },
  candidateEnabled: { type: Boolean, required: true },
  requiresTenant: { type: Boolean, default: true },
  identityField: { type: String, required: true },
  columns: { type: Array, required: true },
  loader: { type: Function, required: true },
  parser: { type: Function, required: true }
})

const DEFAULT_TAG_TYPES = Object.freeze({
  ACTIVE: 'success',
  ACTION_COMPLETED: 'success',
  PENDING_BINDING: 'warning',
  EXPIRED: 'warning',
  REVOKED: 'danger',
  COMPROMISED: 'danger'
})
const MAX_CANDIDATE_RECORDS = 5000

const hasAdminRole = checkRole(['admin'])
const hasPagePermission = checkPermi([props.permission])
const canListEnterprises = !props.requiresTenant
  || checkPermi(['business:fbs:enterprise:list'])
const hasAccess = hasAdminRole && hasPagePermission && canListEnterprises

const enterprises = ref([])
const selectedTenantId = ref(null)
const records = ref([])
const nextCursor = ref(null)
const truncated = ref(false)
const enterpriseLoading = ref(false)
const dataLoading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

const loading = computed(() => enterpriseLoading.value || dataLoading.value)
const selectedEnterprise = computed(() =>
  enterprises.value.find(item => item.id === selectedTenantId.value) || null)
const currentScopeLabel = computed(() => props.requiresTenant
  ? selectedEnterprise.value?.enterpriseName || '当前企业'
  : '全部受控客户端')
const readyForData = computed(() => !props.requiresTenant || selectedTenantId.value !== null)

onMounted(async () => {
  if (!props.candidateEnabled || !hasAccess) return
  if (props.requiresTenant) await loadEnterprises()
  else await loadFirstPage()
})

async function loadEnterprises() {
  enterpriseLoading.value = true
  errorMessage.value = ''
  try {
    const response = await listEnterprise({ pageNum: 1, pageSize: 1000 })
    const page = parseEnterprisePage(response)
    if (page.truncated) throw new Error('企业列表超出候选页的完整读取上限。')
    enterprises.value = [...page.records]
    selectedTenantId.value = enterprises.value[0]?.id ?? null
    if (selectedTenantId.value) await loadFirstPage()
  } catch {
    enterprises.value = []
    selectedTenantId.value = null
    clearData()
    errorMessage.value = '企业列表加载失败或返回了非安全数据。'
  } finally {
    enterpriseLoading.value = false
  }
}

function loadFirstPage() {
  return loadData(null, false)
}

function loadNextPage() {
  if (!truncated.value || !nextCursor.value) return Promise.resolve()
  return loadData(nextCursor.value, true)
}

async function loadData(cursor, append) {
  const tenantId = props.requiresTenant ? selectedTenantId.value : null
  const sequence = ++requestSequence
  errorMessage.value = ''
  if (props.requiresTenant && (!Number.isSafeInteger(tenantId) || tenantId <= 0)) {
    clearData()
    return
  }
  if (!append) clearData()
  dataLoading.value = true
  try {
    const response = await props.loader(tenantId, cursor)
    const page = props.parser(response?.data, tenantId)
    if (sequence !== requestSequence || (props.requiresTenant && selectedTenantId.value !== tenantId)) return
    const combined = append ? [...records.value, ...page.records] : [...page.records]
    if (combined.length > MAX_CANDIDATE_RECORDS) {
      throw new Error('候选记录超出浏览器安全展示上限。')
    }
    const identities = combined.map(item => item[props.identityField])
    if (identities.some(value => !value) || new Set(identities).size !== identities.length) {
      throw new Error('候选分页包含重复或无身份记录。')
    }
    records.value = combined
    truncated.value = page.truncated
    nextCursor.value = page.nextCursor
  } catch {
    if (sequence !== requestSequence) return
    clearData()
    errorMessage.value = '候选数据加载失败或返回了非安全投影，页面已停止展示。'
  } finally {
    if (sequence === requestSequence) dataLoading.value = false
  }
}

function clearData() {
  records.value = []
  truncated.value = false
  nextCursor.value = null
}

function formatCell(row, column) {
  const value = row[column.prop]
  if (value === null || value === undefined || value === '') {
    return column.nullText || '未记录'
  }
  if (/(?:Ref|DigestRef)$/.test(column.prop)) return formatBoardCandidateReference(value)
  if (Array.isArray(value)) return value.join(' · ')
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (column.date) {
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'medium'
    }).format(new Date(value))
  }
  return String(value)
}

function resolveTagType(row, column) {
  const value = row[column.prop]
  return column.tagTypes?.[value] || DEFAULT_TAG_TYPES[value] || 'info'
}
</script>

<style lang="scss" scoped>
.candidate-admin-page { min-height: calc(100vh - 84px); background: #f6f8fb; }
.page-heading { margin-bottom: 18px; }
.page-heading h1 { margin: 4px 0 8px; color: #14233b; font-size: 28px; }
.page-heading p, .boundary-note, .card-heading p { color: #667085; }
.brand-line { margin: 0; color: #246bfd !important; font-weight: 700; }
.control-card, .table-card, .page-alert { margin-bottom: 16px; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.card-heading h2 { margin: 0 0 4px; color: #14233b; }
.card-heading p { margin: 0; }
.candidate-table { width: 100%; }
@media (max-width: 768px) {
  .card-heading { align-items: flex-start; flex-direction: column; }
  .table-card { overflow-x: auto; }
}
</style>
