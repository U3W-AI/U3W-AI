<template>
  <div class="app-container">
    <el-form :inline="true" class="mb8">
      <el-form-item label="所属企业">
        <el-select v-model="enterpriseId" placeholder="请选择企业" filterable style="width: 260px" @change="getList">
          <el-option v-for="item in enterpriseOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Plus" :disabled="!enterpriseId" @click="handleAdd"
                   v-hasPermi="['business:wecom:add']">新增</el-button>
        <el-button icon="Refresh" :disabled="!enterpriseId" @click="getList">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="enterpriseOptionsLoaded && enterpriseOptions.length === 0"
      title="当前账号尚未加入可用企业"
      description="请先在“FBS运营管理 → 企业管理”创建企业，再到“企业成员”把当前用户加入该企业；完成后返回本页刷新。"
      type="warning" show-icon :closable="false" class="mb8" />

    <el-alert
      title="Webhook 密钥已加密保存，列表和详情只显示掩码；每次投递都生成可审计回执。"
      type="info" show-icon :closable="false" class="mb8" />

    <el-table v-loading="loading" :data="webhookList">
      <el-table-column label="名称" prop="name" min-width="140" />
      <el-table-column label="Webhook 地址" prop="webhookUrl" min-width="320" show-overflow-tooltip />
      <el-table-column label="描述" prop="description" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status ? 'success' : 'danger'">{{ scope.row.status ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="scope">{{ parseTime(scope.row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="scope">
          <el-button link type="success" icon="Promotion" @click="handleSend(scope.row)"
                     v-hasPermi="['business:wecom:send']">测试投递</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
                     v-hasPermi="['business:wecom:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)"
                     v-hasPermi="['business:wecom:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editOpen" :title="editTitle" width="600px" append-to-body>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="editForm.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="Webhook 地址" :prop="editForm.id ? undefined : 'webhookUrl'">
          <el-input v-model="editForm.webhookUrl" type="textarea" :rows="3"
                    :placeholder="editForm.id ? '留空表示不更换密钥' : '请输入企业微信消息推送 Webhook 地址'" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="editForm.status" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sendOpen" title="测试投递" width="600px" append-to-body>
      <el-form :model="sendForm" label-width="90px">
        <el-form-item label="标题"><el-input v-model="sendForm.title" maxlength="100" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="sendForm.messageContent" type="textarea" :rows="5" maxlength="4096" show-word-limit /></el-form-item>
        <el-form-item label="行动链接"><el-input v-model="sendForm.actionUrl" placeholder="可选：U3W 签名跳转链接" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendOpen = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="submitSend">发送并生成回执</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WecomWebhook">
import { parseTime } from '@/utils/FBSir'
import {
  listWecomWebhook, listWebhookEnterprises, addWecomWebhook, updateWecomWebhook, delWecomWebhook, sendWecomWebhook
} from '@/api/business/airobotmessage/wecomWebhook'

const { proxy } = getCurrentInstance()
const enterpriseId = ref()
const enterpriseOptions = ref([])
const enterpriseOptionsLoaded = ref(false)
const webhookList = ref([])
const loading = ref(false)
const editOpen = ref(false)
const editTitle = ref('')
const editFormRef = ref()
const editForm = reactive({ id: null, name: '', webhookUrl: '', description: '', status: true, version: 1 })
const editRules = { name: [{ required: true, message: '名称不能为空', trigger: 'blur' }] }
const sendOpen = ref(false)
const sending = ref(false)
const sendForm = reactive({ webhookId: null, idempotencyKey: '', title: 'U3W归因联测', messageContent: '', actionUrl: '' })

function newIdempotencyKey() {
  return `ui:${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`}`
}

function responseRows(response) {
  if (Array.isArray(response?.rows)) return response.rows
  if (Array.isArray(response?.data?.rows)) return response.data.rows
  if (Array.isArray(response?.data)) return response.data
  return []
}

async function loadEnterprises() {
  enterpriseOptionsLoaded.value = false
  try {
    const response = await listWebhookEnterprises()
    enterpriseOptions.value = responseRows(response)
    if (!enterpriseId.value && enterpriseOptions.value.length) enterpriseId.value = enterpriseOptions.value[0].id
    await getList()
  } finally {
    enterpriseOptionsLoaded.value = true
  }
}

async function getList() {
  if (!enterpriseId.value) { webhookList.value = []; return }
  loading.value = true
  try {
    const response = await listWecomWebhook(enterpriseId.value)
    webhookList.value = responseRows(response)
  } finally {
    loading.value = false
  }
}

function resetEdit() {
  Object.assign(editForm, { id: null, name: '', webhookUrl: '', description: '', status: true, version: 1 })
}

function handleAdd() {
  resetEdit()
  editTitle.value = '新增企业微信消息推送'
  editOpen.value = true
}

function handleUpdate(row) {
  Object.assign(editForm, { id: row.id, name: row.name, webhookUrl: '', description: row.description || '', status: !!row.status, version: row.version })
  editTitle.value = '修改企业微信消息推送'
  editOpen.value = true
}

async function submitEdit() {
  await editFormRef.value.validate()
  const data = { ...editForm, enterpriseId: enterpriseId.value }
  if (data.id) await updateWecomWebhook(data)
  else await addWecomWebhook(data)
  proxy.$modal.msgSuccess(data.id ? '修改成功' : '新增成功')
  editOpen.value = false
  await getList()
}

function handleDelete(row) {
  proxy.$modal.confirm(`确认删除 Webhook“${row.name}”吗？`).then(async () => {
    await delWecomWebhook(row.id, enterpriseId.value, row.version)
    proxy.$modal.msgSuccess('删除成功')
    await getList()
  }).catch(() => {})
}

function handleSend(row) {
  sendForm.webhookId = row.id
  // Keep the key stable while this dialog remains open so a timeout retry cannot double-send.
  sendForm.idempotencyKey = newIdempotencyKey()
  sendForm.title = 'U3W归因联测'
  sendForm.messageContent = `测试时间：${new Date().toLocaleString()}\n本消息只用于验证 U3W 投递与回执链路。`
  sendForm.actionUrl = ''
  sendOpen.value = true
}

async function submitSend() {
  if (!sendForm.messageContent.trim()) return proxy.$modal.msgError('内容不能为空')
  sending.value = true
  try {
    const response = await sendWecomWebhook({
      enterpriseId: enterpriseId.value,
      webhookId: sendForm.webhookId,
      idempotencyKey: sendForm.idempotencyKey,
      title: sendForm.title,
      messageContent: sendForm.messageContent,
      actionUrl: sendForm.actionUrl || null
    })
    const receipt = response?.data || {}
    const text = `投递状态：${receipt.status || 'UNKNOWN'}，traceId：${receipt.traceId || '-'}`
    if (receipt.status === 'PROVIDER_ACCEPTED') {
      proxy.$modal.msgSuccess(text)
      sendOpen.value = false
    } else if (receipt.status === 'UNKNOWN' || receipt.status === 'PENDING') {
      proxy.$modal.msgWarning(text)
    } else {
      proxy.$modal.msgError(text)
    }
  } finally {
    sending.value = false
  }
}

loadEnterprises()
</script>
