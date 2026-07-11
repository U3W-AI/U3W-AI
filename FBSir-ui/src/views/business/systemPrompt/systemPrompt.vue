<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Refresh" @click="getList">刷新</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="promptList">
      <el-table-column label="ID" align="center" width="80">
        <template #default="scope">{{ scope.row.id ?? '' }}</template>
      </el-table-column>
      <el-table-column label="名称" align="center" min-width="120" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.name ?? '' }}</template>
      </el-table-column>
      <el-table-column label="描述" align="center" min-width="150" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.description ?? '' }}</template>
      </el-table-column>
      <el-table-column label="版本" align="center" width="90">
        <template #default="scope">{{ scope.row.version ?? '' }}</template>
      </el-table-column>
      <el-table-column label="分类" align="center" width="100">
        <template #default="scope">{{ scope.row.category ?? '' }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="(scope.row.status !== false && scope.row.status !== 0) ? 'success' : 'danger'">
            {{ (scope.row.status !== false && scope.row.status !== 0) ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime ?? scope.row.create_time) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" align="center" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.updateTime ?? scope.row.update_time) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.$index)">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.$index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加或修改系统提示词对话框 -->
    <el-dialog :title="title" v-model="open" width="800px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入提示词名称" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" placeholder="请输入描述信息" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="提示词内容" prop="content">
          <el-input v-model="form.content" type="textarea" placeholder="请输入提示词内容" :rows="10" />
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="form.version" placeholder="请输入版本号" maxlength="50" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="form.category" placeholder="请输入分类" maxlength="100" />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model="form.tags" type="textarea" placeholder="请输入标签（逗号分隔）" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="true">启用</el-radio>
            <el-radio :label="false">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SystemPrompt">
import { listSystemPrompt, getSystemPrompt, deleteSystemPrompt, insertSystemPrompt, updateSystemPrompt } from '@/api/business/systemPrompt/systemPrompt'
import { parseTime } from '@/utils/FBSir'

const { proxy } = getCurrentInstance()

const promptList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const title = ref('')

const data = reactive({
  form: {},
  rules: {
    name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
    content: [{ required: true, message: '提示词内容不能为空', trigger: 'blur' }]
  }
})

const { form, rules } = toRefs(data)

/** 将后端返回的数据统一转为 camelCase（兼容 snake_case） */
function normalizeRow(row) {
  if (!row || typeof row !== 'object') return row
  return {
    id: row.id ?? row.ID ?? row.Id,
    name: row.name,
    content: row.content,
    description: row.description,
    version: row.version,
    category: row.category,
    tags: row.tags,
    status: row.status ?? true,
    createTime: row.createTime ?? row.create_time,
    updateTime: row.updateTime ?? row.update_time
  }
}

/** 查询列表 */
function getList() {
  loading.value = true
  listSystemPrompt().then(response => {
    // 兼容多种返回格式：{ data: [] }、{ rows: [] }、或直接为数组
    let list = []
    if (Array.isArray(response)) {
      list = response
    } else if (Array.isArray(response?.data)) {
      list = response.data
    } else if (Array.isArray(response?.rows)) {
      list = response.rows
    } else if (response?.data?.rows && Array.isArray(response.data.rows)) {
      list = response.data.rows
    } else if (response?.data?.data && Array.isArray(response.data.data)) {
      list = response.data.data
    }
    promptList.value = list.map(normalizeRow)
    loading.value = false
  }).catch(() => {
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
    id: null,
    name: null,
    content: null,
    description: null,
    version: '1.0',
    category: null,
    tags: null,
    status: true
  }
  proxy.resetForm('formRef')
}

/** 新增 */
function handleAdd() {
  reset()
  open.value = true
  title.value = '添加系统提示词'
}

/** 修改 */
function handleUpdate(index) {
  reset()
  const row = promptList.value[index]
  if (!row) return
  const r = normalizeRow(row)
  const id = r.id ?? row.id
  if (id == null || id === undefined) {
    proxy.$modal.msgError('无法获取记录ID，请刷新后重试')
    return
  }
  form.value = { 
    id, 
    name: r.name, 
    content: r.content, 
    description: r.description, 
    version: r.version ?? '1.0',
    category: r.category,
    tags: r.tags,
    status: r.status ?? true 
  }
  open.value = true
  title.value = '修改系统提示词'
}

/** 提交 */
function submitForm() {
  proxy.$refs['formRef'].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateSystemPrompt(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        insertSystemPrompt(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除 */
function handleDelete(index) {
  const row = promptList.value[index]
  if (!row) return
  const r = normalizeRow(row)
  const id = r.id ?? row.id
  if (id == null || id === undefined) {
    proxy.$modal.msgError('无法获取记录ID，请刷新后重试')
    return
  }
  proxy.$modal.confirm('是否确认删除系统提示词 "' + (r.name || '') + '"？').then(() => {
    return deleteSystemPrompt(id)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
