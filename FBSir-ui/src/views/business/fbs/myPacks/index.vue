<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="权益状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="有效" :value="1" />
          <el-option label="已过期" :value="2" />
          <el-option label="已撤销" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源类型" prop="sourceType">
        <el-select v-model="queryParams.sourceType" placeholder="全部来源" clearable style="width: 140px">
          <el-option label="平台分发" :value="1" />
          <el-option label="企业分发" :value="2" />
          <el-option label="用户激活" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="场景包ID" prop="packId">
        <el-input
          v-model="queryParams.packId"
          placeholder="请输入场景包ID"
          clearable
          style="width: 160px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="packList">
      <el-table-column label="场景包名称" align="center" prop="packName" min-width="160" show-overflow-tooltip />
      <el-table-column label="场景包编码" align="center" prop="packCode" width="140" show-overflow-tooltip />
      <el-table-column label="版本" align="center" prop="packVersion" width="100" />
      <el-table-column label="激活时间" align="center" prop="activatedAt" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.activatedAt) || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="到期时间" align="center" prop="expiresAt" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expiresAt) || '永久' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="来源" align="center" prop="sourceTypeDesc" width="100">
        <template #default="scope">
          <el-tag size="small" :type="getSourceTagType(scope.row.sourceType)">
            {{ scope.row.sourceTypeDesc || getSourceTypeName(scope.row.sourceType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="statusDesc" width="110">
        <template #default="scope">
          <el-tag size="small" :type="getPackStatusTagType(scope.row)">
            {{ scope.row.statusDesc || getPackStatusName(scope.row.status) }}
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
  </div>
</template>

<script setup name="FbsMyPacks">
import { ref, reactive, onMounted } from 'vue'
import { listMyPacks } from "@/api/business/fbs/mySelfService"
import { parseTime } from '@/utils/FBSir'

const { proxy } = getCurrentInstance()

const packList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    status: undefined,
    sourceType: undefined,
    packId: undefined
  }
})

const { queryParams } = toRefs(data)

/** 获取列表 */
function getList() {
  loading.value = true
  listMyPacks(queryParams.value).then(response => {
    packList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 用户权益状态名称 */
function getPackStatusName(status) {
  const map = { 1: '有效', 2: '已过期', 3: '已撤销' }
  return map[status] || '-'
}

/** 用户权益状态标签颜色（综合考虑场景包状态） */
function getPackStatusTagType(row) {
  // 场景包已下架或已删除 → danger
  if (row.packStatus == null) return 'danger'
  if (row.packStatus !== 1 && row.status === 1) return 'danger'
  const map = { 1: 'success', 2: 'warning', 3: 'danger' }
  return map[row.status] || ''
}

/** 权益来源类型名称 */
function getSourceTypeName(sourceType) {
  const map = { 1: '平台分发', 2: '企业分发', 3: '用户激活' }
  return map[sourceType] || '-'
}

/** 权益来源标签颜色 */
function getSourceTagType(sourceType) {
  const map = { 1: '', 2: 'warning', 3: 'success' }
  return map[sourceType] || ''
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
}
</style>
