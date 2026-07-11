<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" :inline="true" label-width="80px">
      <el-form-item label="文章ID" prop="articleId">
        <el-input
          v-model="queryParams.articleId"
          placeholder="请输入文章ID"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="内容类型" prop="contentType">
        <el-select
          v-model="queryParams.contentType"
          placeholder="请选择内容类型"
          clearable
          style="width: 200px"
        >
          <el-option label="优化后" value="optimized" />
          <el-option label="模型1" value="model1" />
          <el-option label="模型2" value="model2" />
          <el-option label="模型3" value="model3" />
          <el-option label="排版后" value="layout" />
        </el-select>
      </el-form-item>
      <el-form-item label="发布状态" prop="publishStatus">
        <el-select
          v-model="queryParams.publishStatus"
          placeholder="请选择发布状态"
          clearable
          style="width: 200px"
        >
          <el-option label="发布中" :value="0" />
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" style="width: 308px">
        <el-date-picker
          v-model="dateRange"
          value-format="YYYY-MM-DD"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
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
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['business:publish:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['business:publish:list']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <!-- 表格数据 -->
    <el-table 
      v-loading="loading" 
      :data="recordList" 
      @selection-change="handleSelectionChange"
      style="width: 100%"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="记录ID" prop="id" width="80" />
      <el-table-column label="内容类型" prop="contentType" width="100" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.contentType === 'optimized'" size="small">优化后</el-tag>
          <el-tag v-else-if="scope.row.contentType === 'model1'" type="success" size="small">模型1</el-tag>
          <el-tag v-else-if="scope.row.contentType === 'model2'" type="warning" size="small">模型2</el-tag>
          <el-tag v-else-if="scope.row.contentType === 'model3'" type="danger" size="small">模型3</el-tag>
          <el-tag v-else-if="scope.row.contentType === 'layout'" type="info" size="small">排版后</el-tag>
          <span v-else>{{ scope.row.contentType }}</span>
        </template>
      </el-table-column>
      <el-table-column label="发布状态" prop="publishStatus" width="100" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.publishStatus === 0" type="info" size="small">发布中</el-tag>
          <el-tag v-else-if="scope.row.publishStatus === 1" type="success" size="small">成功</el-tag>
          <el-tag v-else-if="scope.row.publishStatus === 2" type="danger" size="small">失败</el-tag>
          <span v-else>未知</span>
        </template>
      </el-table-column>
      <el-table-column label="素材ID" prop="mediaId" min-width="200" align="center">
        <template #default="scope">
          <div v-if="scope.row.mediaId" style="display: flex; align-items: center; justify-content: center; gap: 8px;">
            <span style="font-family: monospace; font-size: 12px;">{{ scope.row.mediaId }}</span>
            <el-button 
              link 
              type="primary" 
              icon="CopyDocument" 
              size="small"
              @click="copyMediaId(scope.row.mediaId)"
              title="复制素材ID"
            />
          </div>
          <span v-else style="color: #909399;">-</span>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" width="100" align="center">
        <template #default="scope">
          <el-button 
            v-if="scope.row.publishStatus === 2 && scope.row.errorMessage"
            link 
            type="danger" 
            size="small"
            @click="showErrorDetail(scope.row.errorMessage)"
          >查看原因</el-button>
          <span v-else style="color: #909399;">-</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" align="center">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="120" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="查看详情" placement="top">
            <el-button 
              link 
              type="primary" 
              icon="View" 
              @click="handleDetail(scope.row)"
              v-hasPermi="['business:publish:query']"
            />
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button 
              link 
              type="danger" 
              icon="Delete" 
              @click="handleDelete(scope.row)"
              v-hasPermi="['business:publish:remove']"
            />
          </el-tooltip>
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

    <!-- 详情对话框 -->
    <el-dialog title="发布记录详情" v-model="detailOpen" width="900px" append-to-body>
      <el-descriptions :column="2" border v-if="recordDetail">
        <el-descriptions-item label="记录ID">{{ recordDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="文章ID">{{ recordDetail.articleId }}</el-descriptions-item>
        <el-descriptions-item label="内容类型">
          <el-tag v-if="recordDetail.contentType === 'optimized'">优化后</el-tag>
          <el-tag v-else-if="recordDetail.contentType === 'model1'" type="success">模型1</el-tag>
          <el-tag v-else-if="recordDetail.contentType === 'model2'" type="warning">模型2</el-tag>
          <el-tag v-else-if="recordDetail.contentType === 'model3'" type="danger">模型3</el-tag>
          <el-tag v-else-if="recordDetail.contentType === 'layout'" type="info">排版后</el-tag>
          <span v-else>{{ recordDetail.contentType }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="发布状态">
          <el-tag v-if="recordDetail.publishStatus === 0" type="info">发布中</el-tag>
          <el-tag v-else-if="recordDetail.publishStatus === 1" type="success">成功</el-tag>
          <el-tag v-else-if="recordDetail.publishStatus === 2" type="danger">失败</el-tag>
          <span v-else>未知</span>
        </el-descriptions-item>
        <el-descriptions-item label="素材ID" :span="2">{{ recordDetail.mediaId || '无' }}</el-descriptions-item>
        <el-descriptions-item label="失败原因" :span="2">{{ recordDetail.errorMessage || '无' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ parseTime(recordDetail.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 关联的文章内容 -->
      <el-divider content-position="left">关联文章内容</el-divider>
      <el-descriptions :column="1" border v-if="articleDetail">
        <el-descriptions-item label="文章标题">{{ articleDetail.articleTitle }}</el-descriptions-item>
        <el-descriptions-item label="优化内容">
          <div v-html="articleDetail.optimizedContent" class="article-content" />
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="无关联文章" />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PublishRecord">
import { listPublishRecord, getPublishRecord, delPublishRecord, exportPublishRecord } from '@/api/business/content/publishrecord/index'

const { proxy } = getCurrentInstance()

// 数据
const recordList = ref([])
const detailOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const dateRange = ref([])
const recordDetail = ref(null)
const articleDetail = ref(null)

// 查询参数
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  articleId: undefined,
  contentType: undefined,
  publishStatus: undefined
})

/** 查询发布记录列表 */
function getList() {
  loading.value = true
  listPublishRecord(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    recordList.value = response.rows
    total.value = response.total
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
  dateRange.value = []
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 查看详情按钮操作 */
function handleDetail(row) {
  const recordId = row.id
  getPublishRecord(recordId).then(response => {
    recordDetail.value = response.data
    articleDetail.value = response.article || null
    detailOpen.value = true
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const recordIds = row.id ? [row.id] : ids.value
  proxy.$modal.confirm('是否确认删除选中的发布记录？').then(() => {
    return delPublishRecord(recordIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('business/publish-record/export', {
    ...queryParams.value
  }, `publish_record_${new Date().getTime()}.xlsx`)
}

/** 复制素材ID */
function copyMediaId(mediaId) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(mediaId).then(() => {
      proxy.$modal.msgSuccess('素材ID已复制到剪贴板')
    }).catch(() => {
      fallbackCopy(mediaId)
    })
  } else {
    fallbackCopy(mediaId)
  }
}

/** 降级复制方法 */
function fallbackCopy(text) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  try {
    document.execCommand('copy')
    proxy.$modal.msgSuccess('素材ID已复制到剪贴板')
  } catch (err) {
    proxy.$modal.msgError('复制失败，请手动复制')
  }
  document.body.removeChild(textarea)
}

/** 显示失败原因详情 */
function showErrorDetail(errorMessage) {
  proxy.$alert(errorMessage, '失败原因', {
    confirmButtonText: '确定',
    type: 'error',
    customClass: 'error-detail-dialog'
  })
}

// 初始化
getList()
</script>

<style scoped>
.article-content {
  max-height: 400px;
  overflow-y: auto;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

:deep(.error-detail-dialog) {
  max-width: 600px;
  word-break: break-word;
}
</style>
