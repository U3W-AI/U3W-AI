<template>
  <CandidateReadTable
    :brand="brand"
    title="OAuth 客户端"
    description="只读核验未验证本地公共客户端的固定 profile、回调地址、Scope 与生命周期。"
    :default-off-notice="defaultOffNotice"
    :permission="permission"
    :candidate-enabled="candidateEnabled"
    :requires-tenant="false"
    identity-field="clientRef"
    :columns="columns"
    :loader="loadRecords"
    :parser="parseRecords"
  />
</template>

<script setup name="IndependentBoardAdminOAuthClientCandidate">
import CandidateReadTable from '../components/CandidateReadTable.vue'
import { listBoardOAuthClientsCandidate } from '@/api/business/independentBoard/portalCandidate'
import {
  isBoardPortalCandidateEnabled,
  parseBoardCandidateEnvelope,
  parseBoardOAuthClient
} from '../../portalCandidateModel'

const brand = '福帮手 FBSir · 独董会'
const defaultOffNotice = '候选功能默认关闭'
const permission = 'board:oauth:client:query'
const candidateEnabled = isBoardPortalCandidateEnabled(import.meta.env)
const columns = Object.freeze([
  { prop: 'clientRef', label: '客户端', minWidth: 150 },
  { prop: 'displayName', label: '中性名称', minWidth: 210 },
  { prop: 'status', label: '状态', minWidth: 100, tag: true },
  { prop: 'redirectUri', label: '完整回调地址', minWidth: 280 },
  { prop: 'grantTypes', label: '授权类型', minWidth: 240 },
  { prop: 'responseTypes', label: '响应类型', minWidth: 120 },
  { prop: 'scopes', label: '固定 Scope', minWidth: 360 },
  { prop: 'registeredAt', label: '注册时间', minWidth: 170, date: true },
  { prop: 'expiresAt', label: '到期时间', minWidth: 170, date: true },
  { prop: 'terminatedAt', label: '终止时间', minWidth: 170, date: true },
  { prop: 'version', label: '版本', minWidth: 80 },
  { prop: 'metadataDigestRef', label: 'Metadata 摘要', minWidth: 180 },
  { prop: 'registrationSourceDigestRef', label: '注册来源摘要', minWidth: 180 }
])

function loadRecords(_tenantId, cursor) {
  return listBoardOAuthClientsCandidate({ cursor })
}

function parseRecords(value) {
  return parseBoardCandidateEnvelope(value, parseBoardOAuthClient)
}
</script>
