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
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="enterpriseOptionsLoaded && enterpriseOptions.length === 0"
      title="尚未创建可管理的企业"
      description="请先在“FBS运营管理 → 企业管理”创建企业，再返回本页添加成员。"
      type="warning"
      show-icon
      :closable="false"
      class="mb8"
    />

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          :disabled="!queryParams.enterpriseId"
          v-hasPermi="['business:fbs:enterpriseMember:add']"
        >添加成员</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="memberList">
      <el-table-column label="成员ID" align="center" prop="id" width="70" />
      <el-table-column label="用户ID" align="center" prop="userId" width="90" />
      <el-table-column label="企业成员角色" align="center" prop="role" width="110">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.role === 'ADMIN' ? 'warning' : ''">
            {{ scope.row.role === 'ADMIN' ? '管理员' : '普通成员' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '正常' : '已移除' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已授权场景包" align="center" prop="packCount" width="110" />
      <el-table-column label="加入时间" align="center" prop="joinTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.joinTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="160" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="View"
            @click="handleView(scope.row)"
            v-hasPermi="['business:fbs:enterpriseMember:query']"
          >详情</el-button>
          <el-button
            link
            type="danger"
            icon="Close"
            @click="handleRemove(scope.row)"
            v-if="scope.row.status === 1"
            v-hasPermi="['business:fbs:enterpriseMember:remove']"
          >移除</el-button>
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

    <!-- 添加成员对话框 -->
    <el-dialog title="添加企业成员" v-model="addOpen" width="500px" append-to-body>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="120px">
        <el-form-item label="目标企业">
          <el-input :value="currentEnterpriseName" disabled />
        </el-form-item>
        <el-form-item label="用户ID" prop="userId">
          <el-input-number v-model="addForm.userId" :min="1" controls-position="right" style="width: 100%" placeholder="输入用户ID（sys_user.user_id）" />
        </el-form-item>
        <el-form-item label="成员角色" prop="role">
          <el-select v-model="addForm.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="普通成员" value="MEMBER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-alert
          title="添加后，成员将自动继承企业当前所有已授权场景包"
          type="info"
          :closable="false"
          style="margin-top: 8px"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitAdd">确 定</el-button>
          <el-button @click="addOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog title="成员详情" v-model="detailOpen" width="550px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="成员ID">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detailData.userId }}</el-descriptions-item>
        <el-descriptions-item label="企业名称">{{ detailData.enterpriseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成员角色">
          <el-tag size="small" :type="detailData.role === 'ADMIN' ? 'warning' : ''">
            {{ detailData.role === 'ADMIN' ? '管理员' : '普通成员' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="detailData.status === 1 ? 'success' : 'danger'">
            {{ detailData.status === 1 ? '正常' : '已移除' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="已授权场景包">{{ detailData.packCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="加入时间">{{ parseTime(detailData.joinTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup name="FbsEnterpriseMember">
import { ref, reactive, computed, onMounted } from 'vue'
import { listEnterprise } from "@/api/business/fbs/enterprise"
import { listEnterpriseMember, getEnterpriseMember, addEnterpriseMember, removeEnterpriseMember } from "@/api/business/fbs/enterpriseMember"
import { parseTime } from '@/utils/FBSir'

const { proxy } = getCurrentInstance()

const memberList = ref([])
const enterpriseOptions = ref([])
const enterpriseOptionsLoaded = ref(false)
const addOpen = ref(false)
const detailOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const detailData = ref({})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    enterpriseId: undefined
  },
  addForm: {
    enterpriseId: undefined,
    userId: undefined,
    role: 'MEMBER'
  },
  addRules: {
    userId: [{ required: true, message: "用户ID不能为空", trigger: "blur" }]
  }
})

const { queryParams, addForm, addRules } = toRefs(data)

const currentEnterpriseName = computed(() => {
  const ent = enterpriseOptions.value.find(e => e.id === queryParams.value.enterpriseId)
  return ent ? ent.enterpriseName : ''
})

/** 加载企业列表（下拉框） */
async function loadEnterpriseOptions() {
  enterpriseOptionsLoaded.value = false
  try {
    const response = await listEnterprise({ pageNum: 1, pageSize: 1000 })
    enterpriseOptions.value = response.rows || []
    if (!queryParams.value.enterpriseId && enterpriseOptions.value.length > 0) {
      queryParams.value.enterpriseId = enterpriseOptions.value[0].id
    }
  } finally {
    enterpriseOptionsLoaded.value = true
  }
}

/** 获取列表 */
function getList() {
  if (!queryParams.value.enterpriseId) {
    memberList.value = []
    total.value = 0
    loading.value = false
    return
  }
  loading.value = true
  listEnterpriseMember(queryParams.value).then(response => {
    memberList.value = response.rows || []
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

/** 打开添加对话框 */
function handleAdd() {
  addForm.value = {
    enterpriseId: queryParams.value.enterpriseId,
    userId: undefined,
    role: 'MEMBER'
  }
  proxy.resetForm("addFormRef")
  addOpen.value = true
}

/** 提交添加 */
function submitAdd() {
  proxy.$refs["addFormRef"].validate(valid => {
    if (valid) {
      addEnterpriseMember(addForm.value).then(response => {
        proxy.$modal.msgSuccess("添加成功")
        addOpen.value = false
        getList()
      })
    }
  })
}

/** 详情 */
function handleView(row) {
  getEnterpriseMember(row.id).then(response => {
    detailData.value = response.data || {}
    detailOpen.value = true
  })
}

/** 移除 */
function handleRemove(row) {
  proxy.$confirm('确认移除成员"' + (row.userId) + '"吗?移除后成员将失去企业场景包权益。', "移除警告", {
    confirmButtonText: "确认移除",
    cancelButtonText: "取消",
    type: "warning"
  }).then(function() {
    return removeEnterpriseMember(row.id)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("移除成功")
  }).catch(() => {})
}

onMounted(async () => {
  await loadEnterpriseOptions()
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
