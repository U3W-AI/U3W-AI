<template>
  <CandidateReadTable
    :brand="brand"
    title="Token Family"
    description="只读核验授权族状态、刷新代次、连接绑定和最终 current-read；并发结果不使用乐观提示。"
    :default-off-notice="defaultOffNotice"
    :permission="permission"
    :candidate-enabled="candidateEnabled"
    identity-field="familyRef"
    :columns="columns"
    :loader="loadRecords"
    :parser="parseRecords"
  />
</template>

<script setup name="IndependentBoardAdminOAuthFamilyCandidate">
import CandidateReadTable from '../components/CandidateReadTable.vue'
import { listBoardOAuthFamiliesCandidate } from '@/api/business/independentBoard/portalCandidate'
import {
  isBoardPortalCandidateEnabled,
  parseBoardCandidateEnvelope,
  parseBoardOAuthFamily
} from '../../portalCandidateModel'

const brand = '福帮手 FBSir · 独董会'
const defaultOffNotice = '候选功能默认关闭'
const permission = 'board:oauth:family:query'
const candidateEnabled = isBoardPortalCandidateEnabled(import.meta.env)
const columns = Object.freeze([
  { prop: 'familyRef', label: '授权族', minWidth: 150 },
  { prop: 'tenantLabel', label: '企业', minWidth: 170 },
  { prop: 'memberLabel', label: '成员', minWidth: 140 },
  { prop: 'userLabel', label: '用户', minWidth: 140 },
  { prop: 'clientRef', label: '客户端', minWidth: 150 },
  { prop: 'consentIntent', label: '授权意图', minWidth: 190 },
  { prop: 'status', label: '状态', minWidth: 150, tag: true },
  { prop: 'currentRefreshGeneration', label: '刷新代次', minWidth: 110 },
  { prop: 'bindingRef', label: '连接绑定', minWidth: 150 },
  { prop: 'issuedAt', label: '签发时间', minWidth: 170, date: true },
  { prop: 'activatedAt', label: '激活时间', minWidth: 170, date: true },
  { prop: 'expiresAt', label: '到期时间', minWidth: 170, date: true },
  { prop: 'terminatedAt', label: '终止时间', minWidth: 170, date: true },
  { prop: 'version', label: '版本', minWidth: 80 }
])

function loadRecords(tenantId, cursor) {
  return listBoardOAuthFamiliesCandidate({ tenantId, cursor })
}

function parseRecords(value, tenantId) {
  return parseBoardCandidateEnvelope(value, parseBoardOAuthFamily, tenantId)
}
</script>
