<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="授权码" prop="authCode">
        <el-input
          v-model="queryParams.authCode"
          placeholder="请输入授权码"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="启用状态" prop="available">
        <el-select v-model="queryParams.available" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="使用状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
          <el-option label="未激活" :value="0" />
          <el-option label="已激活" :value="1" />
          <el-option label="已用尽" :value="2" />
          <el-option label="已过期" :value="3" />
          <el-option label="已撤销" :value="4" />
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
          icon="Ticket"
          @click="handleGenerate"
          v-hasPermi="['business:fbs:authCode:generate']"
        >生成授权码</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="codeList">
      <el-table-column label="ID" align="center" prop="id" width="60" />
      <el-table-column label="授权码" align="center" prop="authCode" min-width="200">
        <template #default="scope">
          <code class="auth-code-text">{{ scope.row.authCode }}</code>
          <el-button link size="small" icon="CopyDocument" @click="copyAuthCode(scope.row.authCode)">复制</el-button>
        </template>
      </el-table-column>
      <el-table-column label="关联目标" align="center" prop="targetId" width="90" />
      <el-table-column label="启用状态" align="center" prop="available" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.available === 1 ? 'success' : 'danger'" size="small">
            {{ scope.row.available === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="使用状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag size="small" :type="getAuthStatusTagType(scope.row.status)">
            {{ getAuthStatusName(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="激活/上限" align="center" width="100">
        <template #default="scope">
          {{ scope.row.activatedCount || 0 }} / {{ scope.row.maxActivations || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="截止时间" align="center" prop="deadline" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.deadline) || '不限' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="发放者类型" align="center" prop="issuerType" width="100">
        <template #default="scope">
          {{ getIssuerTypeName(scope.row.issuerType) }}
        </template>
      </el-table-column>
      <el-table-column label="说明" align="center" prop="description" width="140" show-overflow-tooltip />
      <el-table-column label="创建时间" align="center" prop="createTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="240" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="warning"
            icon="Unlock"
            @click="handleDisable(scope.row)"
            v-if="scope.row.available === 1"
            v-hasPermi="['business:fbs:authCode:disable']"
          >禁用</el-button>
          <el-button
            link
            type="success"
            icon="Lock"
            @click="handleEnable(scope.row)"
            v-if="scope.row.available === 0 && scope.row.status !== 2 && scope.row.status !== 3 && scope.row.status !== 4"
            v-hasPermi="['business:fbs:authCode:enable']"
          >启用</el-button>
          <el-button
            link
            type="danger"
            icon="CircleClose"
            @click="handleRevoke(scope.row)"
            v-if="scope.row.status !== 4"
            v-hasPermi="['business:fbs:authCode:revoke']"
          >撤销</el-button>
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

    <!-- 生成授权码对话框 -->
    <el-dialog title="批量生成授权码" v-model="generateOpen" width="550px" append-to-body>
      <el-form ref="genFormRef" :model="genForm" :rules="genRules" label-width="120px">
        <el-form-item label="关联目标ID" prop="targetId">
          <el-input-number v-model="genForm.targetId" :min="1" controls-position="right" style="width: 100%" placeholder="场景包ID" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="最大激活次数" prop="maxActivations">
              <el-input-number v-model="genForm.maxActivations" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发放者类型" prop="issuerType">
              <el-select v-model="genForm.issuerType" style="width: 100%">
                <el-option label="平台" :value="1" />
                <el-option label="企业" :value="2" />
                <el-option label="用户" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="生成数量" prop="count">
              <el-input-number v-model="genForm.count" :min="1" :max="1000" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="截止时间">
              <el-date-picker
                v-model="genForm.deadline"
                type="datetime"
                placeholder="留空=不限"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="说明/备注">
          <el-input v-model="genForm.description" type="textarea" :rows="2" placeholder="如: 2026年4月推广礼包" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitGenerate">确 定</el-button>
          <el-button @click="generateOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 生成结果对话框 -->
    <el-dialog title="授权码生成结果" v-model="resultOpen" width="700px" append-to-body>
      <el-alert
        :title="'共生成 ' + generatedCodes.length + ' 个授权码, 请及时保存!'"
        type="success"
        show-icon
        :closable="false"
        style="margin-bottom: 16px;"
      />
      <div class="generated-codes-area">
        <div v-for="(item, index) in generatedCodes" :key="index" class="code-item">
          <span class="code-index">#{{ index + 1 }}</span>
          <code>{{ item.code }}</code>
          <el-button link size="small" icon="CopyDocument" @click="copyAuthCode(item.code)">复制</el-button>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="copyAllCodes">复制全部</el-button>
          <el-button @click="resultOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="FbsAuthCode">
import { ref, reactive, onMounted } from 'vue'
import { listAuthCode, generateAuthCode, disableAuthCode, enableAuthCode, revokeAuthCode } from "@/api/business/fbs/authCode"
import { parseTime } from '@/utils/WxFbsir'

const { proxy } = getCurrentInstance()

const codeList = ref([])
const generateOpen = ref(false)
const resultOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const generatedCodes = ref([])

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    authCode: undefined,
    available: undefined,
    status: undefined
  },
  genForm: {
    targetType: 'SCENE_PACK',
    targetId: undefined,
    issuerType: 1,
    issuerId: undefined,
    maxActivations: 1,
    deadline: undefined,
    description: undefined,
    count: 1
  },
  genRules: {
    targetId: [{ required: true, message: "关联目标ID不能为空", trigger: "blur" }],
    count: [{ required: true, message: "生成数量不能为空", trigger: "blur" }]
  }
})

const { queryParams, genForm, genRules } = toRefs(data)

/** 获取列表 */
function getList() {
  loading.value = true
  listAuthCode(queryParams.value).then(response => {
    codeList.value = response.rows
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

/** 打开生成对话框 */
function handleGenerate() {
  genForm.value = {
    targetType: 'SCENE_PACK',
    targetId: undefined,
    issuerType: 1,
    maxActivations: 1,
    deadline: undefined,
    description: undefined,
    count: 1
  }
  proxy.resetForm("genFormRef")
  generateOpen.value = true
}

/** 提交生成 */
function submitGenerate() {
  proxy.$refs["genFormRef"].validate(valid => {
    if (valid) {
      generateAuthCode(genForm.value).then(response => {
        const resData = response.data || {}
        const codes = Object.keys(resData).map(key => ({
          id: key,
          code: resData[key]
        }))
        generatedCodes.value = codes
        generateOpen.value = false
        resultOpen.value = true
        getList()
      })
    }
  })
}

/** 禁用 */
function handleDisable(row) {
  proxy.$confirm('确认禁用该授权码吗?禁用后任何人无法使用。', "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning"
  }).then(function() {
    return disableAuthCode({ id: row.id })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("禁用成功")
  }).catch(() => {})
}

/** 启用 */
function handleEnable(row) {
  proxy.$confirm('确认启用该授权码吗?', "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "info"
  }).then(function() {
    return enableAuthCode({ id: row.id })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("启用成功")
  }).catch(() => {})
}

/** 撤销 */
function handleRevoke(row) {
  proxy.$confirm('确认撤销该授权码吗?撤销操作不可逆!', "严重警告", {
    confirmButtonText: "确认撤销",
    cancelButtonText: "取消",
    type: "error"
  }).then(function() {
    return revokeAuthCode({ id: row.id })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("撤销成功")
  }).catch(() => {})
}

/** 复制授权码 */
function copyAuthCode(text) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      proxy.$modal.msgSuccess("已复制到剪贴板")
    })
  } else {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    proxy.$modal.msgSuccess("已复制到剪贴板")
  }
}

/** 复制全部授权码 */
function copyAllCodes() {
  const texts = generatedCodes.value.map(c => c.code).join('\n')
  copyAuthCode(texts)
}

/** 授权码使用状态名称 */
function getAuthStatusName(status) {
  const map = { 0: '未激活', 1: '已激活', 2: '已用尽', 3: '已过期', 4: '已撤销' }
  return map[status] || '-'
}

/** 授权码使用状态标签颜色 */
function getAuthStatusTagType(status) {
  const map = { 0: 'info', 1: '', 2: 'warning', 3: 'danger', 4: 'danger' }
  return map[status] || ''
}

/** 发放者类型名称 */
function getIssuerTypeName(type) {
  const map = { 1: '平台', 2: '企业', 3: '用户' }
  return map[type] || '-'
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

  .auth-code-text {
    font-family: 'Consolas', 'Monaco', monospace;
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 13px;
    color: #303133;
  }
}

.generated-codes-area {
  max-height: 400px;
  overflow-y: auto;

  .code-item {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    border-bottom: 1px solid #ebeef5;

    .code-index {
      font-weight: bold;
      color: #909399;
      width: 50px;
      flex-shrink: 0;
    }

    code {
      flex: 1;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 14px;
      padding: 4px 8px;
      background: #f5f7fa;
      border-radius: 4px;
      color: #303133;
    }
  }
}
</style>
