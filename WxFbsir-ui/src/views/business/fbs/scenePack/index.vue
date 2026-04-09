<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="场景包名称" prop="packName">
        <el-input
          v-model="queryParams.packName"
          placeholder="请输入场景包名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
          <el-option label="已下架" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" prop="packType">
        <el-select v-model="queryParams.packType" placeholder="全部类型" clearable style="width: 150px">
          <el-option label="平台包" :value="1" />
          <el-option label="企业包" :value="2" />
          <el-option label="自定义包" :value="3" />
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
          @click="handleAdd"
          v-hasPermi="['business:fbs:scenePack:add']"
        >新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="packList">
      <el-table-column label="编码" align="center" prop="packCode" width="140" />
      <el-table-column label="名称" align="center" prop="packName" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" align="center" prop="packType" width="100">
        <template #default="scope">
          <el-tag size="small">{{ getPackTypeName(scope.row.packType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag size="small" :type="getStatusTagType(scope.row.status)">
            {{ getStatusName(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="积分规则" align="center" prop="pointsRuleCode" width="150" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.pointsRuleCode || "免费" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="版本" align="center" prop="currentVersion" width="100" />
      <el-table-column label="创建人" align="center" prop="createdBy" width="90" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="260" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="View"
            @click="handleView(scope.row)"
            v-hasPermi="['business:fbs:scenePack:query']"
          >详情</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            :disabled="scope.row.status === 2"
            v-hasPermi="['business:fbs:scenePack:edit']"
          >编辑</el-button>
          <el-button
            link
            type="success"
            icon="Top"
            @click="handlePublish(scope.row)"
            v-if="scope.row.status === 0"
            v-hasPermi="['business:fbs:scenePack:publish']"
          >发布</el-button>
          <el-button
            link
            type="warning"
            icon="Bottom"
            @click="handleUnpublish(scope.row)"
            v-if="scope.row.status === 1"
            v-hasPermi="['business:fbs:scenePack:unpublish']"
          >下架</el-button>
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

    <!-- 新增/编辑场景包对话框 -->
    <el-dialog :title="title" v-model="open" width="650px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="场景包编码" prop="packCode">
              <el-input v-model="form.packCode" placeholder="请输入编码(唯一标识)" :disabled="form.id != null" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="场景包名称" prop="packName">
              <el-input v-model="form.packName" placeholder="请输入名称" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="场景包类型" prop="packType">
              <el-select v-model="form.packType" placeholder="请选择" style="width: 100%">
                <el-option label="平台包" :value="1" />
                <el-option label="企业包" :value="2" />
                <el-option label="自定义包" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可见范围" prop="visibleScope">
              <el-select v-model="form.visibleScope" placeholder="请选择" style="width: 100%">
                <el-option label="公开" value="ALL" />
                <el-option label="私有" value="PRIVATE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关联积分规则" prop="pointsRuleCode">
          <el-input v-model="form.pointsRuleCode" placeholder="留空表示免费包" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog title="场景包详情" v-model="detailOpen" width="650px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="编码">{{ detailData.packCode }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ detailData.packName }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag size="small">{{ getPackTypeName(detailData.packType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="getStatusTagType(detailData.status)">
            {{ getStatusName(detailData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="积分规则">{{ detailData.pointsRuleCode || "免费" }}</el-descriptions-item>
        <el-descriptions-item label="积分规则名">{{ detailData.pointsRuleName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="当前版本">{{ detailData.currentVersion || "-" }}</el-descriptions-item>
        <el-descriptions-item label="可见范围">{{ detailData.visibleScope || "-" }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailData.createdBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailData.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ detailData.description || "-" }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup name="FbsScenePack">
import { ref, reactive, onMounted } from 'vue'
import { listScenePack, getScenePack, addScenePack, updateScenePack, publishScenePack, unpublishScenePack } from "@/api/business/fbs/scenePack"
import { parseTime } from '@/utils/WxFbsir'

const { proxy } = getCurrentInstance()

const packList = ref([])
const open = ref(false)
const detailOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref("")
const detailData = ref({})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    packName: undefined,
    status: undefined,
    packType: undefined
  },
  rules: {
    packCode: [{ required: true, message: "场景包编码不能为空", trigger: "blur" }],
    packName: [{ required: true, message: "场景包名称不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 获取列表 */
function getList() {
  loading.value = true
  listScenePack(queryParams.value).then(response => {
    packList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    id: undefined,
    packCode: undefined,
    packName: undefined,
    packType: 1,
    ownerType: 1,
    description: undefined,
    visibleScope: 'ALL',
    pointsRuleCode: undefined,
    contentSnapshot: undefined
  }
  proxy.resetForm("formRef")
}

/** 搜索按钮 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 新增按钮 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增场景包"
}

/** 修改按钮 */
function handleUpdate(row) {
  reset()
  const id = row.id
  getScenePack(id).then(response => {
    form.value = response.data
    open.value = true
    title.value = "编辑场景包"
  })
}

/** 详情按钮 */
function handleView(row) {
  getScenePack(row.id).then(response => {
    detailData.value = response.data
    detailOpen.value = true
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["formRef"].validate(valid => {
    if (valid) {
      if (form.value.id != undefined) {
        updateScenePack(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addScenePack(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 发布 */
function handlePublish(row) {
  proxy.$confirm('确认发布场景包"' + row.packName + '"吗?', "发布提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning"
  }).then(function() {
    return publishScenePack({ id: row.id })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("发布成功")
  }).catch(() => {})
}

/** 下架 */
function handleUnpublish(row) {
  proxy.$confirm('确认下架场景包"' + row.packName + '"吗?下架后不可再次发布或编辑!', "下架警告", {
    confirmButtonText: "确认下架",
    cancelButtonText: "取消",
    type: "warning"
  }).then(function() {
    return unpublishScenePack({ id: row.id })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("下架成功")
  }).catch(() => {})
}

/** 场景包类型名称 */
function getPackTypeName(packType) {
  const map = { 1: '平台包', 2: '企业包', 3: '自定义包' }
  return map[packType] || '-'
}

/** 状态名称 */
function getStatusName(status) {
  const map = { 0: '草稿', 1: '已发布', 2: '已下架' }
  return map[status] || '-'
}

/** 状态标签颜色 */
function getStatusTagType(status) {
  const map = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[status] || ''
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
