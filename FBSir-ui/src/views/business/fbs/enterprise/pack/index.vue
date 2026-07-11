<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="所属企业" prop="enterpriseId">
        <el-select
          v-model="queryParams.enterpriseId"
          placeholder="请先选择企业"
          clearable
          filterable
          style="width: 220px"
          @change="handleQuery"
        >
          <el-option
            v-for="ent in enterpriseOptions"
            :key="ent.id"
            :label="ent.enterpriseName"
            :value="ent.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="已授权" :value="1" />
          <el-option label="已用尽" :value="2" />
          <el-option label="已撤销" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleGrant"
          :disabled="!queryParams.enterpriseId"
          v-hasPermi="['business:fbs:enterprisePack:grant']"
        >分发企业包</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="packList">
      <el-table-column label="场景包编码" align="center" prop="packCode" width="160" show-overflow-tooltip />
      <el-table-column label="场景包名称" align="center" prop="packName" min-width="140" show-overflow-tooltip />
      <el-table-column label="总配额" align="center" prop="packQuota" width="80" />
      <el-table-column label="已用" align="center" prop="usedQuota" width="80">
        <template #default="scope">
          <span :style="{ color: (scope.row.packQuota - scope.row.usedQuota) <= 0 ? '#f56c6c' : '' }">
            {{ scope.row.usedQuota || 0 }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="剩余" align="center" width="80">
        <template #default="scope">
          <span :style="{ color: (scope.row.packQuota - scope.row.usedQuota) <= 0 ? '#f56c6c' : '#67c23a' }">
            {{ Math.max((scope.row.packQuota || 0) - (scope.row.usedQuota || 0), 0) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag size="small" :type="getStatusTagType(scope.row.status)">
            {{ getStatusName(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="过期时间" align="center" prop="expiryTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expiryTime) || '永不过期' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="分发时间" align="center" prop="grantTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.grantTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="View"
            @click="handleView(scope.row)"
            v-hasPermi="['business:fbs:enterprisePack:query']"
          >详情</el-button>
          <el-button
            link
            type="danger"
            icon="Close"
            @click="handleRevoke(scope.row)"
            v-if="scope.row.status === 1"
            v-hasPermi="['business:fbs:enterprisePack:revoke']"
          >回收</el-button>
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

    <!-- 分发企业包对话框 -->
    <el-dialog title="分发企业场景包" v-model="grantOpen" width="520px" append-to-body>
      <el-form ref="grantFormRef" :model="grantForm" :rules="grantRules" label-width="120px">
        <el-form-item label="目标企业">
          <el-input :value="currentEnterpriseName" disabled />
        </el-form-item>
        <el-form-item label="场景包编码" prop="packCode">
          <el-input v-model="grantForm.packCode" placeholder="请输入场景包编码（必须为已发布状态）" maxlength="30" />
        </el-form-item>
        <el-form-item label="配额数量" prop="packQuota">
          <el-input-number v-model="grantForm.packQuota" :min="1" :max="99999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="grantForm.expiryTime"
            type="datetime"
            placeholder="留空=永不过期"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitGrant">确 定</el-button>
          <el-button @click="grantOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog title="企业包详情" v-model="detailOpen" width="600px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="企业包ID">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="场景包编码">{{ detailData.packCode }}</el-descriptions-item>
        <el-descriptions-item label="场景包名称">{{ detailData.packName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="getStatusTagType(detailData.status)">
            {{ getStatusName(detailData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总配额">{{ detailData.packQuota }}</el-descriptions-item>
        <el-descriptions-item label="已用配额">{{ detailData.usedQuota || 0 }}</el-descriptions-item>
        <el-descriptions-item label="剩余配额">{{ detailData.remainQuota || 0 }}</el-descriptions-item>
        <el-descriptions-item label="分发时间">{{ parseTime(detailData.grantTime) }}</el-descriptions-item>
        <el-descriptions-item label="过期时间" :span="2">{{ parseTime(detailData.expiryTime) || '永不过期' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup name="FbsEnterprisePack">
import { ref, reactive, computed, onMounted } from 'vue'
import { listEnterprise } from "@/api/business/fbs/enterprise"
import { listEnterprisePack, getEnterprisePack, grantEnterprisePack, revokeEnterprisePack } from "@/api/business/fbs/enterprisePack"
import { parseTime } from '@/utils/FBSir'

const { proxy } = getCurrentInstance()

const packList = ref([])
const enterpriseOptions = ref([])
const grantOpen = ref(false)
const detailOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const detailData = ref({})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    enterpriseId: undefined,
    status: undefined
  },
  grantForm: {
    enterpriseId: undefined,
    packCode: undefined,
    packQuota: undefined,
    expiryTime: undefined
  },
  grantRules: {
    packCode: [{ required: true, message: "场景包编码不能为空", trigger: "blur" }],
    packQuota: [{ required: true, message: "配额数量不能为空", trigger: "blur" }]
  }
})

const { queryParams, grantForm, grantRules } = toRefs(data)

const currentEnterpriseName = computed(() => {
  const ent = enterpriseOptions.value.find(e => e.id === queryParams.value.enterpriseId)
  return ent ? ent.enterpriseName : ''
})

/** 加载企业列表（下拉框） */
function loadEnterpriseOptions() {
  listEnterprise({ pageNum: 1, pageSize: 1000 }).then(response => {
    enterpriseOptions.value = response.rows || []
  })
}

/** 获取列表 */
function getList() {
  if (!queryParams.value.enterpriseId) {
    packList.value = []
    total.value = 0
    loading.value = false
    return
  }
  loading.value = true
  listEnterprisePack(queryParams.value).then(response => {
    packList.value = response.rows || []
    total.value = response.total || 0
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

/** 打开分发对话框 */
function handleGrant() {
  grantForm.value = {
    enterpriseId: queryParams.value.enterpriseId,
    packCode: undefined,
    packQuota: undefined,
    expiryTime: undefined
  }
  proxy.resetForm("grantFormRef")
  grantOpen.value = true
}

/** 提交分发 */
function submitGrant() {
  proxy.$refs["grantFormRef"].validate(valid => {
    if (valid) {
      grantEnterprisePack(grantForm.value).then(response => {
        proxy.$modal.msgSuccess("分发成功")
        grantOpen.value = false
        getList()
      })
    }
  })
}

/** 详情 */
function handleView(row) {
  getEnterprisePack(row.id).then(response => {
    detailData.value = response.data || {}
    detailOpen.value = true
  })
}

/** 回收 */
function handleRevoke(row) {
  proxy.$confirm('确认回收场景包"' + (row.packName || row.packCode) + '"吗?回收后成员权益同步失效。', "回收警告", {
    confirmButtonText: "确认回收",
    cancelButtonText: "取消",
    type: "warning"
  }).then(function() {
    return revokeEnterprisePack({ id: row.id })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("回收成功")
  }).catch(() => {})
}

/** 状态名称 */
function getStatusName(status) {
  const map = { 1: '已授权', 2: '已用尽', 3: '已撤销' }
  return map[status] || '-'
}

/** 状态标签颜色 */
function getStatusTagType(status) {
  const map = { 1: 'success', 2: 'warning', 3: 'danger' }
  return map[status] || ''
}

onMounted(() => {
  loadEnterpriseOptions()
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
