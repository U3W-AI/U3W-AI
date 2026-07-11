<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="用户ID" prop="userId">
        <el-input
          v-model="queryParams.userId"
          placeholder="请输入用户ID"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="权益状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="有效" :value="1" />
          <el-option label="已过期" :value="2" />
          <el-option label="已撤销" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="info" icon="DataAnalysis" @click="showStatsDialog">统计概览</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-cards mb8" v-if="statsData.totalCount !== undefined && statsData.totalCount !== null">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-number primary">{{ statsData.totalCount || 0 }}</div>
          <div class="stat-label">总权益数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card success">
          <div class="stat-number">{{ statsData.activeCount || 0 }}</div>
          <div class="stat-label">有效</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card warning">
          <div class="stat-number">{{ statsData.expiredCount || 0 }}</div>
          <div class="stat-label">已过期</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card danger">
          <div class="stat-number">{{ statsData.revokedCount || 0 }}</div>
          <div class="stat-label">已撤销</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="packList">
      <el-table-column label="ID" align="center" prop="id" width="60" />
      <el-table-column label="用户ID" align="center" prop="userId" width="90" />
      <el-table-column label="场景包名称" align="center" prop="packName" min-width="160" show-overflow-tooltip />
      <el-table-column label="版本" align="center" prop="packVersion" width="100" />
      <el-table-column label="授权码ID" align="center" prop="authCodeId" width="90" />
      <el-table-column label="激活时间" align="center" prop="activatedAt" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.activatedAt) || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="到期时间" align="center" prop="expiresAt" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expiresAt) || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="权益来源" align="center" prop="sourceType" width="100">
        <template #default="scope">
          {{ getSourceTypeName(scope.row.sourceType) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag size="small" :type="getPackStatusTagType(scope.row.status)">
            {{ getPackStatusName(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 统计详情弹窗 -->
    <el-dialog title="用户权益统计" v-model="statsOpen" width="500px" append-to-body>
      <el-form label-width="120px">
        <el-form-item label="用户ID">
          <el-input v-model="statsUserId" placeholder="请输入用户ID" clearable style="width: 250px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="fetchStats">查 询</el-button>
        </el-form-item>
      </el-form>
      <div v-if="statsFetched" class="stats-detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户ID">{{ statsData.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="总权益数"><b>{{ statsData.totalCount || 0 }}</b></el-descriptions-item>
          <el-descriptions-item label="有效"><b class="text-success">{{ statsData.activeCount || 0 }}</b></el-descriptions-item>
          <el-descriptions-item label="已过期"><b class="text-warning">{{ statsData.expiredCount || 0 }}</b></el-descriptions-item>
          <el-descriptions-item label="已撤销"><b class="text-danger">{{ statsData.revokedCount || 0 }}</b></el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="statsOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="FbsUserPack">
import { ref, reactive, onMounted } from 'vue'
import { listUserPack, getUserPackStats } from "@/api/business/fbs/userPack"
import { parseTime } from '@/utils/FBSir'

const { proxy } = getCurrentInstance()

const packList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const statsOpen = ref(false)
const statsUserId = ref(undefined)
const statsFetched = ref(false)
const statsData = ref({})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    userId: undefined,
    status: undefined
  }
})

const { queryParams } = toRefs(data)

/** 获取列表 */
function getList() {
  loading.value = true
  listUserPack(queryParams.value).then(response => {
    packList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
  if (queryParams.value.userId) {
    fetchStatsByUserId(queryParams.value.userId)
  }
}

/** 重置 */
function resetQuery() {
  proxy.resetForm("queryRef")
  statsData.value = {}
  handleQuery()
}

/** 打开统计弹窗 */
function showStatsDialog() {
  statsOpen.value = true
  statsUserId.value = queryParams.value.userId
  statsFetched.value = !!queryParams.value.userId
}

/** 查询统计 */
function fetchStats() {
  if (!statsUserId.value) {
    proxy.$modal.msgWarning("请先输入用户ID")
    return
  }
  fetchStatsByUserId(statsUserId.value)
}

/** 按用户ID查询统计 */
function fetchStatsByUserId(userId) {
  getUserPackStats({ userId }).then(response => {
    statsData.value = response.data || {}
    statsFetched.value = true
  })
}

/** 用户权益状态名称 */
function getPackStatusName(status) {
  const map = { 1: '有效', 2: '已过期', 3: '已撤销' }
  return map[status] || '-'
}

/** 用户权益状态标签颜色 */
function getPackStatusTagType(status) {
  const map = { 1: 'success', 2: 'warning', 3: 'danger' }
  return map[status] || ''
}

/** 权益来源类型名称 */
function getSourceTypeName(sourceType) {
  const map = { 1: '平台分发', 2: '企业分发', 3: '用户激活' }
  return map[sourceType] || '-'
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.app-container {
  .mb8 {
    margin-bottom: 8px;
  }

  .stats-cards {
    margin-bottom: 12px;

    .stat-card {
      text-align: center;
      padding: 10px;

      .stat-number {
        font-size: 28px;
        font-weight: bold;
        line-height: 1.4;
      }

      .stat-label {
        font-size: 13px;
        color: #909399;
        margin-top: 4px;
      }

      &.primary .stat-number { color: #409eff; }
      &.success .stat-number { color: #67c23a; }
      &.warning .stat-number { color: #e6a23c; }
      &.danger .stat-number { color: #f56c6c; }

      :deep(.el-card__body) {
        padding: 14px 10px;
      }
    }
  }
}

.stats-detail {
  margin-top: 20px;

  .text-success { color: #67c23a; font-size: 18px; }
  .text-warning { color: #e6a23c; font-size: 18px; }
  .text-danger { color: #f56c6c; font-size: 18px; }
}
</style>
