<template>
  <CandidateReadTable
    :brand="brand"
    title="Connector Binding"
    description="只读核验权益、授权族、精确 Scope 与连接绑定的完整拓扑，不允许从终态行直接恢复。"
    :default-off-notice="defaultOffNotice"
    :permission="permission"
    :candidate-enabled="candidateEnabled"
    identity-field="bindingRef"
    :columns="columns"
    :loader="loadRecords"
    :parser="parseRecords"
  />
</template>

<script setup name="IndependentBoardAdminConnectorBindingCandidate">
import CandidateReadTable from '../components/CandidateReadTable.vue'
import { listBoardConnectorBindingsCandidate } from '@/api/business/independentBoard/portalCandidate'
import {
  isBoardPortalCandidateEnabled,
  parseBoardCandidateEnvelope,
  parseBoardConnectorBinding
} from '../../portalCandidateModel'

const brand = '福帮手 FBSir · 独董会'
const defaultOffNotice = '候选功能默认关闭'
const permission = 'board:connector:query'
const candidateEnabled = isBoardPortalCandidateEnabled(import.meta.env)
const columns = Object.freeze([
  { prop: 'bindingRef', label: '连接绑定', minWidth: 150 },
  { prop: 'tenantLabel', label: '企业', minWidth: 170 },
  { prop: 'memberLabel', label: '成员', minWidth: 140 },
  { prop: 'userLabel', label: '用户', minWidth: 140 },
  { prop: 'productCode', label: '产品', minWidth: 170 },
  { prop: 'sourceCode', label: '来源', minWidth: 120 },
  { prop: 'connectorCode', label: '连接器', minWidth: 120 },
  { prop: 'status', label: '状态', minWidth: 120, tag: true },
  { prop: 'scopes', label: '固定 Scope', minWidth: 360 },
  { prop: 'verificationMethod', label: '核验方式', minWidth: 250 },
  { prop: 'verifiedAt', label: '核验时间', minWidth: 170, date: true },
  { prop: 'lastSeenAt', label: '最近访问', minWidth: 170, date: true },
  { prop: 'validUntil', label: '有效至', minWidth: 170, date: true },
  { prop: 'revokedAt', label: '终止时间', minWidth: 170, date: true },
  { prop: 'clientRef', label: '客户端', minWidth: 150 },
  { prop: 'subjectDigestRef', label: '主体摘要', minWidth: 180 },
  { prop: 'entitlementActive', label: '权益有效', minWidth: 100 },
  { prop: 'familyActive', label: '授权族有效', minWidth: 110 },
  { prop: 'vipEffective', label: 'VIP 生效', minWidth: 100 },
  { prop: 'version', label: '版本', minWidth: 80 }
])

function loadRecords(tenantId, cursor) {
  return listBoardConnectorBindingsCandidate({ tenantId, cursor })
}

function parseRecords(value, tenantId) {
  return parseBoardCandidateEnvelope(value, parseBoardConnectorBinding, tenantId)
}
</script>
