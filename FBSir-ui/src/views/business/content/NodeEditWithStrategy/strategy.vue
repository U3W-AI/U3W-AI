<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" size="small" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Refresh" size="small" @click="getList">刷新</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="strategyName" label="策略名称" />
      <el-table-column prop="modelName" label="模型" />
      <el-table-column prop="temperature" label="temperature" width="110" />
      <el-table-column prop="topP" label="top_p" width="90" />
      <el-table-column prop="maxTokens" label="max_tokens" width="110" />
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button size="small" type="primary" text @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" text @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" v-model="open" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="策略名称">
          <el-input v-model="form.strategyName" placeholder="唯一策略名" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="form.modelName" placeholder="模型名称" />
        </el-form-item>
        <el-form-item label="temperature">
          <el-input-number
            v-model="form.temperature"
            :step="0.1"
            :min="0"
            :max="2"
          />
        </el-form-item>
        <el-form-item label="top_p">
          <el-input-number
            v-model="form.topP"
            :step="0.1"
            :min="0.1"
            :max="1"
          />
        </el-form-item>
        <el-form-item label="max_tokens">
          <el-input-number
            v-model="form.maxTokens"
            :min="1"
            :max="256000"
            :step="1"
          />
        </el-form-item>
        <el-form-item label="提示词模板">
          <el-input
            v-model="form.prompt"
            type="textarea"
            :rows="6"
            placeholder="提示词模板"
          />
          <div style="margin-top:4px;font-size:12px;color:#999;">
            变量占位符请使用 <strong>{{ '{' }}{{ '{' }}名称2{{ '}' }}{{ '}' }}</strong> 形式，
            其中名称为变量含义，末尾数字为变量序号，例如：<strong>{{ '{' }}{{ '{' }}名称1{{ '}' }}{{ '}' }}</strong>。
            对于 <strong>日更助手-高优先级</strong> 工作流，变量序号最大为 <strong>1</strong>；
            对于 <strong>分析助手-高优先级-多模型</strong> 工作流，变量序号最大为 <strong>3</strong>。
            未写数字时默认按 <strong>1</strong> 处理。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open=false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listStrategy, addStrategy, updateStrategy, delStrategy } from '@/api/business/host/strategy'

// 列表加载状态
const loading = ref(false)
// 数据列表
const list = ref([])
// 总条数
const total = ref(0)
// 弹窗开关
const open = ref(false)
// 弹窗标题
const title = ref('')

// 查询参数（支持后续扩展按策略名称查询）
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  strategyName: undefined
})

const form = ref({
  id: undefined,
  strategyName: '',
  modelName: '',
  temperature: 0.5,
  topP: 0.9,
  maxTokens: 4000,
  prompt: ''
})

const resetForm = () => {
  form.value = {
    id: undefined,
    strategyName: '',
    modelName: '',
    temperature: 0.5,
    topP: 0.9,
    maxTokens: 4000,
    prompt: ''
  }
}

const getList = () => {
  loading.value = true
  listStrategy(queryParams.value).then(res => {
    list.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}

const handleAdd = () => {
  resetForm()
  title.value = '新增策略'
  open.value = true
}

const handleEdit = (row) => {
  resetForm()
  Object.assign(form.value, row)
  title.value = '编辑策略'
  open.value = true
}

const submitForm = () => {
  const api = form.value.id ? updateStrategy : addStrategy
  api(form.value).then(() => {
    open.value = false
    getList()
  })
}

const handleDelete = (row) => {
  delStrategy(row.id).then(() => {
    getList()
  })
}

onMounted(() => getList())
</script>

