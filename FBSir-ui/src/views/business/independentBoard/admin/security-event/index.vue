<template>
  <CandidateReadTable
    :brand="brand"
    title="OAuth 安全事件"
    description="以不可变动作回执为主轴，只读核验授权、授权族、连接绑定与重放处置结果。"
    :default-off-notice="defaultOffNotice"
    :permission="permission"
    :candidate-enabled="candidateEnabled"
    identity-field="eventRef"
    :columns="columns"
    :loader="loadRecords"
    :parser="parseRecords"
  />
</template>

<script setup name="IndependentBoardAdminSecurityEventCandidate">
import CandidateReadTable from '../components/CandidateReadTable.vue'
import { listBoardSecurityEventsCandidate } from '@/api/business/independentBoard/portalCandidate'
import {
  isBoardPortalCandidateEnabled,
  parseBoardCandidateEnvelope,
  parseBoardSecurityEvent
} from '../../portalCandidateModel'

const brand = '福帮手 FBSir · 独董会'
const defaultOffNotice = '候选功能默认关闭'
const permission = 'board:oauth:security:audit'
const candidateEnabled = isBoardPortalCandidateEnabled(import.meta.env)
const columns = Object.freeze([
  { prop: 'eventRef', label: '事件', minWidth: 150 },
  { prop: 'occurredAt', label: '时间', minWidth: 170, date: true },
  { prop: 'action', label: '动作', minWidth: 230 },
  { prop: 'actorType', label: '触发方', minWidth: 100, tag: true },
  { prop: 'tenantLabel', label: '企业', minWidth: 170 },
  { prop: 'memberLabel', label: '成员', minWidth: 140 },
  { prop: 'clientRef', label: '客户端', minWidth: 150 },
  { prop: 'familyRef', label: '授权族', minWidth: 150 },
  { prop: 'bindingRef', label: '连接绑定', minWidth: 150 },
  { prop: 'correlationRef', label: '关联编号', minWidth: 160 },
  { prop: 'evidenceLevel', label: '证据级别', minWidth: 160, tag: true },
  {
    prop: 'reasonCode',
    label: '固定原因码',
    minWidth: 200,
    nullText: '该回执模型未提供'
  }
])

function loadRecords(tenantId, cursor) {
  return listBoardSecurityEventsCandidate({ tenantId, cursor })
}

function parseRecords(value, tenantId) {
  return parseBoardCandidateEnvelope(value, parseBoardSecurityEvent, tenantId)
}
</script>
