<template>
  <el-table
    :data="records"
    row-key="operationId"
    empty-text="当前账户暂无积分操作"
    class="credit-operation-table"
  >
    <el-table-column label="序号" prop="sequenceNo" width="88" align="right" />
    <el-table-column label="类型" width="104">
      <template #default="scope">
        <el-tag :type="scope.row.operationType === 'GRANT' ? 'success' : 'warning'">
          {{ scope.row.operationType === 'GRANT' ? '发放' : '冲正' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="变动" width="120" align="right">
      <template #default="scope">
        <span :class="scope.row.delta > 0 ? 'positive-delta' : 'negative-delta'">
          {{ scope.row.delta > 0 ? '+' : '' }}{{ scope.row.delta }}
        </span>
      </template>
    </el-table-column>
    <el-table-column label="余额" min-width="150">
      <template #default="scope">
        {{ scope.row.balanceBefore }} → {{ scope.row.balanceAfter }}
      </template>
    </el-table-column>
    <el-table-column label="原因" min-width="150">
      <template #default="scope">{{ creditReasonLabel(scope.row.reasonCode) }}</template>
    </el-table-column>
    <el-table-column label="操作编号" prop="operationId" min-width="300" show-overflow-tooltip>
      <template #default="scope">
        <code>{{ scope.row.operationId }}</code>
      </template>
    </el-table-column>
    <el-table-column label="操作人 ID" prop="actorUserId" min-width="120" align="right" />
    <el-table-column label="创建时间" min-width="190">
      <template #default="scope">{{ formatCreditDateTime(scope.row.createdAt) }}</template>
    </el-table-column>
    <el-table-column v-if="canReverse" label="操作" width="116" fixed="right">
      <template #default="scope">
        <el-tag
          v-if="scope.row.operationType === 'GRANT'
            && isCreditOperationReversed(scope.row, records)"
          type="info"
        >已冲正</el-tag>
        <el-button
          v-else-if="scope.row.operationType === 'GRANT'"
          v-hasPermi="['board:credit:reverse']"
          link
          type="danger"
          :aria-label="`冲正积分发放 ${scope.row.operationId}`"
          @click="$emit('reverse', scope.row)"
        >冲正</el-button>
        <span v-else aria-label="冲正操作不可再次冲正">—</span>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import {
  creditReasonLabel,
  formatCreditDateTime,
  isCreditOperationReversed
} from './creditModel'

defineProps({
  records: { type: Array, required: true },
  canReverse: { type: Boolean, required: true }
})

defineEmits(['reverse'])
</script>

<style lang="scss" scoped>
.credit-operation-table { width: 100%; }
.credit-operation-table code { color: #344054; font-size: 12px; }
.positive-delta { color: var(--el-color-success-dark-2); font-weight: 700; }
.negative-delta { color: var(--el-color-danger); font-weight: 700; }
</style>
