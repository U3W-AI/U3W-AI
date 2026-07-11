<template>
  <div class="points-overview-page">
    <el-row :gutter="20">
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card class="points-overview-card" v-loading="summaryLoading">
          <template #header>
            <div class="card-header">
              <div class="title">我的积分总览</div>
              <div class="actions">
                <el-button link size="small" @click="refreshSummary">
                  <el-icon><Refresh /></el-icon>刷新
                </el-button>
                <el-button type="primary" size="small" plain @click="showTaskList = true">
                  获取更多积分
                </el-button>
              </div>
            </div>
          </template>
          <div class="points-overview-body">
            <div class="points-main-stat">
              <div class="label">当前积分</div>
              <div class="value">{{ formatPointValue(pointsSummary.balance) }}</div>
            </div>
            <div class="points-sub-stats">
              <div class="points-stat">
                <div class="label">今日获得</div>
                <div class="value positive">+{{ pointsSummary.todayGain || 0 }}</div>
              </div>
              <div class="points-stat">
                <div class="label">本周获得</div>
                <div class="value positive">+{{ pointsSummary.weekGain || 0 }}</div>
              </div>
            </div>
            <div class="points-last-change" v-if="pointsSummary.lastChange">
              <div class="label">最近一次变动</div>
              <div class="desc">
                <span class="change-type">{{ pointsSummary.lastChange.ruleName || pointsSummary.lastChange.ruleCode || '—' }}</span>
                <span
                  class="change-amount"
                  :class="{
                    positive: (pointsSummary.lastChange.changeAmount || 0) >= 0,
                    negative: (pointsSummary.lastChange.changeAmount || 0) < 0
                  }"
                >
                  {{ formatChangeAmount(pointsSummary.lastChange.changeAmount) }}
                </span>
                <span class="change-time">{{ parseTime(pointsSummary.lastChange.createTime) || '—' }}</span>
              </div>
            </div>
            <div class="no-record" v-else>
              暂无积分变动记录
            </div>
            <el-alert
              v-if="summaryError"
              type="error"
              :closable="false"
              :description="summaryError"
              show-icon
            />
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card class="points-quick-panel">
          <template #header>
            <div class="card-header">
              <div class="title">快捷入口</div>
            </div>
          </template>
          <div class="quick-actions">
            <el-button type="primary" icon="Document" plain @click="openPointsRecordDialog">
              查看积分明细
            </el-button>
            <el-button type="success" icon="Trophy" plain @click="showTaskList = true">
              积分任务清单
            </el-button>
          </div>
          <div class="tip-text">
            清晰掌握积分来源与去向，保障积分收支透明。
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="points-table-card">
      <template #header>
        <div class="card-header">
          <div class="title">积分流水</div>
          <div class="actions">
            <el-select
              v-model="queryPointForm.type"
              placeholder="筛选类型"
              clearable
              size="small"
              style="width: 150px"
              @change="handleFilterChange"
            >
              <el-option label="全部" value=""></el-option>
              <el-option label="增加" value="1"></el-option>
              <el-option label="消耗" value="2"></el-option>
            </el-select>
            <el-button type="primary" size="small" @click="refreshRecords">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </div>
      </template>
      <el-table
        v-loading="recordLoading"
        :data="pointsRecordList"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column type="index" width="60" label="#"></el-table-column>
        <el-table-column prop="createTime" label="变动时间" min-width="160">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ruleName" label="规则名称" min-width="160">
          <template #default="scope">
            <span>{{ scope.row.ruleName || scope.row.ruleCode || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动积分" min-width="120">
          <template #default="scope">
            <span :class="{ positive: scope.row.changeAmount >= 0, negative: scope.row.changeAmount < 0 }">
              {{ scope.row.changeAmount > 0 ? '+' : '' }}{{ scope.row.changeAmount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="变动后余额" min-width="120"></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip></el-table-column>
      </el-table>
      <pagination
        class="points-pagination"
        v-show="pointtotal > 0"
        :total="pointtotal"
        v-model:page="queryPointForm.pageNum"
        v-model:limit="queryPointForm.pageSize"
        @pagination="getUserPointsRecord"
      />
    </el-card>

    <PointTaskList v-model="showTaskList" />

    <el-dialog title="积分明细" v-model="openPointsRecord" width="800px" append-to-body>
      <el-table
        :data="pointsRecordList"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="createTime" label="时间" min-width="160">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ruleName" label="规则名称" min-width="160">
          <template #default="scope">
            <span>{{ scope.row.ruleName || scope.row.ruleCode || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动" min-width="120">
          <template #default="scope">
            <span :class="{ positive: scope.row.changeAmount >= 0, negative: scope.row.changeAmount < 0 }">
              {{ scope.row.changeAmount > 0 ? '+' : '' }}{{ scope.row.changeAmount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      </el-table>
      <pagination
        v-show="pointtotal > 0"
        :total="pointtotal"
        v-model:page="queryPointForm.pageNum"
        v-model:limit="queryPointForm.pageSize"
        @pagination="getUserPointsRecord"
      />
    </el-dialog>
  </div>
</template>

<script setup name="PointsOverview">
import { ref, onMounted } from 'vue'
import { Refresh, Document, Trophy } from '@element-plus/icons-vue'
import { getPointsSummary, getPointsRecord } from '@/api/business/points'
import { parseTime } from '@/utils/FBSir'
import PointTaskList from '../components/TaskList.vue'

const summaryLoading = ref(false)
const summaryError = ref(null)
const pointsSummary = ref({
  balance: 0,
  todayGain: 0,
  weekGain: 0,
  lastChange: null
})

const recordLoading = ref(false)
const pointsRecordList = ref([])
const pointtotal = ref(0)
const queryPointForm = ref({
  pageNum: 1,
  pageSize: 10,
  type: ''
})
const openPointsRecord = ref(false)
const showTaskList = ref(false)

onMounted(() => {
  loadSummary()
  getUserPointsRecord()
})

function loadSummary() {
  summaryLoading.value = true
  summaryError.value = null
  getPointsSummary().then(response => {
    if (response.code === 200) {
      const data = response.data || {}
      pointsSummary.value = {
        balance: data.balance ?? 0,
        todayGain: data.todayGain ?? 0,
        weekGain: data.weekGain ?? 0,
        lastChange: data.lastChange || null
      }
    } else {
      summaryError.value = response.msg || '获取积分概览失败'
    }
  }).catch(error => {
    summaryError.value = error?.msg || '获取积分概览失败'
  }).finally(() => {
    summaryLoading.value = false
  })
}

function refreshSummary() {
  loadSummary()
}

function formatPointValue(value) {
  const num = Number(value || 0)
  return num.toLocaleString()
}

function formatChangeAmount(val) {
  if (val === null || val === undefined) return '--'
  const num = Number(val)
  return (num > 0 ? '+' : '') + num
}

function handleFilterChange() {
  queryPointForm.value.pageNum = 1
  getUserPointsRecord()
}

function openPointsRecordDialog() {
  openPointsRecord.value = true
  getUserPointsRecord()
}

function refreshRecords() {
  getUserPointsRecord()
}

function getUserPointsRecord(params) {
  if (params) {
    queryPointForm.value.pageNum = params.pageNum
    queryPointForm.value.pageSize = params.pageSize
  }
  recordLoading.value = true
  const requestData = {
    pageNum: queryPointForm.value.pageNum,
    pageSize: queryPointForm.value.pageSize,
    changeAmount: queryPointForm.value.type === '' ? null : (queryPointForm.value.type === '1' ? 1 : -1)
  }
  getPointsRecord(requestData).then(response => {
    pointsRecordList.value = response.rows || []
    pointtotal.value = response.total || 0
    recordLoading.value = false
  }).catch(() => {
    recordLoading.value = false
  })
}
</script>

<style lang="scss" scoped>
.points-overview-page {
  padding: 24px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;

    .title {
      font-size: 16px;
      font-weight: 600;
    }
  }

  .points-overview-card {
    .points-overview-body {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .points-main-stat {
      background: linear-gradient(135deg, #0fb2fc, #4facfe);
      color: #fff;
      padding: 20px;
      border-radius: 12px;

      .label {
        font-size: 14px;
        opacity: 0.85;
        margin-bottom: 6px;
      }

      .value {
        font-size: 32px;
        font-weight: 700;
      }
    }

    .points-sub-stats {
      display: flex;
      gap: 12px;

      .points-stat {
        flex: 1;
        background: #f5f7fa;
        border-radius: 10px;
        padding: 14px;

        .label {
          font-size: 13px;
          color: #909399;
          margin-bottom: 4px;
        }

        .value {
          font-size: 20px;
          font-weight: 600;

          &.positive {
            color: #67c23a;
          }
        }
      }
    }

    .points-last-change {
      background: #fdf6ec;
      border: 1px solid #f5dab1;
      border-radius: 10px;
      padding: 12px 16px;

      .label {
        font-size: 13px;
        color: #e6a23c;
        margin-bottom: 6px;
      }

      .desc {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        font-size: 14px;
        color: #606266;

        .change-type {
          font-weight: 600;
        }

        .change-amount {
          font-weight: 600;

          &.positive {
            color: #67c23a;
          }

          &.negative {
            color: #f56c6c;
          }
        }

        .change-time {
          color: #909399;
        }
      }
    }

    .no-record {
      font-size: 14px;
      color: #909399;
      text-align: center;
    }
  }

  .points-quick-panel {
    height: 100%;

    .quick-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 20px;
    }

    .tip-text {
      margin-top: 24px;
      font-size: 14px;
      color: #909399;
    }
  }

  .points-table-card {
    margin-top: 20px;

    .card-header {
      margin-bottom: 16px;
    }

    .points-pagination {
      margin-top: 12px;
      text-align: right;
    }

    .positive {
      color: #67c23a;
    }

    .negative {
      color: #f56c6c;
    }
  }
}
</style>
