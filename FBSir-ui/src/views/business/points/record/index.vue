<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="变动类型" prop="type">
        <el-select v-model="queryParams.type" placeholder="筛选类型" clearable style="width: 150px">
          <el-option label="全部" value=""></el-option>
          <el-option label="增加" value="1"></el-option>
          <el-option label="消耗" value="2"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" stripe border>
      <el-table-column type="index" width="60" label="#" />
      <el-table-column prop="createTime" label="变动时间" min-width="160" align="center">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ruleName" label="规则名称" min-width="180" align="center">
        <template #default="scope">
          <span>{{ scope.row.ruleName || scope.row.ruleCode || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="变动积分" min-width="120" align="center">
        <template #default="scope">
          <span :class="{ positive: scope.row.changeAmount >= 0, negative: scope.row.changeAmount < 0 }">
            {{ scope.row.changeAmount > 0 ? '+' : '' }}{{ scope.row.changeAmount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="balanceBefore" label="变动前余额" min-width="120" align="center" />
      <el-table-column prop="balanceAfter" label="变动后余额" min-width="120" align="center" />
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script setup name="PointsRecord">
import { ref, reactive, onMounted } from 'vue'
import { getPointsRecord } from '@/api/business/points'
import { parseTime } from '@/utils/FBSir'

const { proxy } = getCurrentInstance()

const dataList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    type: ''
  }
})

const { queryParams } = toRefs(data)

/** 查询积分明细列表 */
function getList() {
  loading.value = true
  const requestData = {
    ...queryParams.value,
    changeAmount: queryParams.value.type === '' ? null : (queryParams.value.type === '1' ? 1 : -1)
  }
  getPointsRecord(requestData).then(response => {
    dataList.value = response.rows || []
    total.value = response.total || 0
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.app-container {
  .positive {
    color: #67c23a;
    font-weight: 600;
  }
  
  .negative {
    color: #f56c6c;
    font-weight: 600;
  }
}
</style>

