<template>
  <div class="app-container">
    <el-card class="system-prompt-card" :loading="loading">
      <template #header>
        <div class="card-header">
          <span class="card-title">系统提示词配置</span>
        </div>
      </template>

      <el-form :model="form" label-width="120px" class="system-prompt-form">
        <el-form-item label="提示词名称">
          <el-input
            v-model="form.name"
            placeholder="例如：默认工作流提示词 / code_review_prompt"
            clearable
          />
        </el-form-item>

        <el-form-item label="提示词描述">
          <el-input
            v-model="form.description"
            placeholder="例如：用于 XXX 场景下的系统提示词说明"
            clearable
          />
        </el-form-item>

        <el-form-item label="提示词内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="在这里输入要保存的系统提示词……"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="true"
            :inactive-value="false"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>

        <el-form-item label="版本号">
          <el-input
            v-model="form.version"
            placeholder="例如：1.0"
            clearable
            style="max-width: 240px"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="saving"
            @click="handleSubmit"
          >
            保存提示词
          </el-button>
          <el-button @click="handleReset" :disabled="loading || saving">
            还原为服务端配置
          </el-button>
        </el-form-item>

        <el-alert
          v-if="statusMessage"
          :type="statusType"
          :closable="false"
          show-icon
          style="max-width: 600px"
        >
          <p>{{ statusMessage }}</p>
        </el-alert>
      </el-form>
    </el-card>
  </div>
</template>

<script setup name="SystemPrompt">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSystemPrompt, updateSystemPrompt } from '@/api/business/systemPrompt/systemPrompt.js'

const DEFAULT_PROMPT_ID = 1

const loading = ref(false)
const saving = ref(false)
const statusMessage = ref('')
const statusType = ref('success')

const form = reactive({
  id: DEFAULT_PROMPT_ID,
  name: '',
  description: '',
  content: '',
  status: 1,
  version: '1.0'
})

const fillForm = (data = {}) => {
  form.id = data.id ?? DEFAULT_PROMPT_ID
  form.name = data.name ?? ''
  form.description = data.description ?? ''
  form.content = data.content ?? ''
  form.status = data.status ?? 1
  form.version = data.version ?? '1.0'
}

const fetchPrompt = async () => {
  loading.value = true
  statusMessage.value = ''
  try {
    const res = await getSystemPrompt(DEFAULT_PROMPT_ID)
    if (res.code === 200 && res.data) {
      fillForm(res.data)
      statusType.value = 'success'
      statusMessage.value = `已加载 ID=${form.id} 的系统提示词`
    } else {
      statusType.value = 'error'
      statusMessage.value = res.msg || '获取系统提示词失败'
    }
  } catch (e) {
    statusType.value = 'error'
    statusMessage.value = e?.message || '获取系统提示词异常'
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!form.name || !form.name.trim()) {
    ElMessage.warning('请输入提示词名称')
    return
  }
  if (!form.content || !form.content.trim()) {
    ElMessage.warning('请输入提示词内容')
    return
  }

  saving.value = true
  statusMessage.value = ''

  try {
    const payload = {
      id: form.id ?? DEFAULT_PROMPT_ID,
      name: form.name,
      description: form.description || null,
      content: form.content,
      status: form.status,
      version: form.version || '1.0'
    }
    const res = await updateSystemPrompt(payload)
    if (res.code === 200) {
      statusType.value = 'success'
      statusMessage.value = res.msg || '保存成功'
      ElMessage.success(statusMessage.value)
      await fetchPrompt()
    } else {
      statusType.value = 'error'
      statusMessage.value = res.msg || '保存失败'
      ElMessage.error(statusMessage.value)
    }
  } catch (e) {
    statusType.value = 'error'
    statusMessage.value = e?.message || '保存失败'
    ElMessage.error(statusMessage.value)
  } finally {
    saving.value = false
  }
}

const handleReset = () => {
  fetchPrompt()
}

onMounted(() => {
  fetchPrompt()
})
</script>

<style scoped lang="scss">
.system-prompt-card {
  max-width: 900px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.system-prompt-form {
  max-width: 720px;
}
</style>
