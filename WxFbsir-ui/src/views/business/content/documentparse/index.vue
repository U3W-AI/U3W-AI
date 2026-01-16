<template>
  <div class="document-parse-container">
    <el-row :gutter="20">
      <!-- 左侧：上传文档和解析列表 -->
      <el-col :span="12" :xs="24">
        <el-card class="box-card left-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Document /></el-icon>
                文档解析助手
              </span>
              <div style="display: flex; gap: 10px;">
                <el-button
                  :type="agentConfigured ? 'success' : 'primary'"
                  size="small"
                  @click="showConfigDialog"
                >
                  <el-icon>
                    <component :is="agentConfigured ? 'CircleCheck' : 'Setting'" />
                  </el-icon>
                  {{ agentConfigured ? '已配置' : '配置智能体' }}
                </el-button>
              </div>
            </div>
          </template>

          <!-- 上传文档 -->
          <div class="upload-section">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :limit="1"
              :accept="acceptFileTypes"
              drag
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                将文档拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持 PDF、Word、TXT 等格式文件
                </div>
              </template>
            </el-upload>

            <el-input
              v-model="prompt"
              type="textarea"
              :rows="3"
              placeholder="请输入解析提示词，例如：请提取文档中的关键信息、请总结文档内容等"
              style="margin-top: 15px;"
              :disabled="uploading"
            />

            <el-input
              v-model="documentName"
              placeholder="文档名称（可选，默认使用文件名）"
              style="margin-top: 10px;"
              :disabled="uploading"
              clearable
            />

            <el-button
              type="primary"
              :loading="uploading"
              @click="handleUploadAndParse"
              :disabled="!selectedFile || !prompt.trim()"
              style="width: 100%; margin-top: 15px;"
              v-hasPermi="['business:document:add']"
            >
              <el-icon><Upload /></el-icon>
              {{ uploading ? '上传解析中...' : '上传并解析' }}
            </el-button>

            <div class="tip-text" style="margin-top: 10px;">
              <el-icon><InfoFilled /></el-icon>
              文档将自动编码为base64并发送到腾讯元器进行解析
            </div>
          </div>

          <!-- 解析记录列表 -->
          <div class="parse-list">
            <div class="list-header">
              <span>我的解析记录 <el-tag v-if="totalParses > 0" size="small" type="info">{{ totalParses }}</el-tag></span>
              <el-button
                type="text"
                size="small"
                @click="refreshParses"
                :loading="loading"
              >
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>

            <el-scrollbar height="500px" ref="scrollbarRef" @scroll="handleScroll">
              <div
                v-for="parse in parseList"
                :key="parse.id"
                class="parse-item"
                :class="{ 
                  active: selectedParse && selectedParse.id === parse.id,
                  'parse-failed': parse.processStatus === 2
                }"
                @click="selectParse(parse)"
              >
                <div class="parse-header">
                  <span class="parse-name">{{ parse.documentName || parse.documentId }}</span>
                  <el-tag
                    :type="getStatusType(parse.processStatus)"
                    size="small"
                  >
                    {{ getStatusText(parse.processStatus) }}
                  </el-tag>
                </div>
                <!-- 失败时显示错误信息 -->
                <div v-if="parse.processStatus === 2" class="error-msg">
                  <el-icon><WarningFilled /></el-icon>
                  {{ parse.errorMessage || '解析失败' }}
                </div>
                <div class="parse-meta">
                  <span class="time">{{ formatTime(parse.createTime) }}</span>
                  <div class="meta-actions">
                    <!-- 失败时显示重试按钮 -->
                    <el-button
                      v-if="parse.processStatus === 2"
                      type="primary"
                      size="small"
                      text
                      @click.stop="handleRetry(parse)"
                    >
                      <el-icon><RefreshRight /></el-icon>
                      重试
                    </el-button>
                    <el-button
                      type="danger"
                      size="small"
                      text
                      @click.stop="handleDelete(parse)"
                      v-hasPermi="['business:document:remove']"
                    >
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>

              <!-- 加载更多提示 -->
              <div v-if="hasMore && !loading" class="load-more-tip">
                <el-text type="info" size="small">下拉加载更多...</el-text>
              </div>
              
              <!-- 加载中 -->
              <div v-if="loadingMore" class="loading-more">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>加载中...</span>
              </div>
              
              <!-- 没有更多 -->
              <div v-if="!hasMore && parseList.length > 0" class="no-more-tip">
                <el-text type="info" size="small">没有更多了</el-text>
              </div>

              <el-empty
                v-if="!loading && parseList.length === 0"
                description="暂无解析记录，快来上传第一个文档吧！"
                :image-size="120"
              />
            </el-scrollbar>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：解析内容展示 -->
      <el-col :span="12" :xs="24">
        <el-card class="box-card content-card right-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Reading /></el-icon>
                解析内容
              </span>
            </div>
          </template>

          <div v-if="selectedParse" class="content-section">
            <div class="content-wrapper">
              <div class="content-header">
                <h3>{{ selectedParse.documentName || selectedParse.documentId }}</h3>
                <div class="action-buttons">
                  <el-button
                    v-if="selectedParse.parsedContent"
                    type="primary"
                    size="small"
                    @click="copyContent(selectedParse.parsedContent)"
                  >
                    <el-icon><CopyDocument /></el-icon>
                    复制内容
                  </el-button>
                </div>
              </div>

              <!-- 提示词显示 -->
              <div v-if="selectedParse.prompt" class="prompt-section">
                <el-tag type="info" size="small">
                  <el-icon><ChatLineRound /></el-icon>
                  提示词：{{ selectedParse.prompt }}
                </el-tag>
              </div>

              <el-scrollbar height="600px">
                <div v-if="selectedParse.parsedContent" class="content-text">
                  {{ selectedParse.parsedContent }}
                </div>
                <div v-else-if="selectedParse.processStatus === 0" class="parsing-status">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <p>正在解析中...</p>
                  <p class="tip">请稍候，AI正在处理您的文档</p>
                </div>
                <el-empty
                  v-else
                  description="等待智能体解析..."
                  :image-size="100"
                />
              </el-scrollbar>
            </div>

            <!-- 错误信息 -->
            <el-alert
              v-if="selectedParse.processStatus === 2 && selectedParse.errorMessage"
              type="error"
              :title="selectedParse.errorMessage"
              :closable="false"
              style="margin-top: 10px"
            />
          </div>

          <el-empty
            v-else
            description="请选择一条解析记录查看内容"
            :image-size="150"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- 配置智能体对话框 -->
    <el-dialog
      v-model="configDialogVisible"
      title="配置腾讯元器智能体"
      width="600px"
      @close="handleConfigDialogClose"
    >
      <el-form
        ref="configFormRef"
        :model="configForm"
        :rules="configRules"
        label-width="120px"
      >
        <el-form-item label="智能体ID" prop="agentId">
          <el-input
            v-model="configForm.agentId"
            :placeholder="isAgentIdEncrypted ? '已加密存储，输入新值可覆盖' : '请输入appid'"
            @focus="handleAgentIdFocus"
          >
            <template #suffix>
              <el-tag v-if="isAgentIdEncrypted" type="success" size="small">已添加</el-tag>
            </template>
          </el-input>
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            从 智能体配置→应用发布→体验链接 中获取
          </div>
        </el-form-item>
        <el-form-item label="智能体名称" prop="agentName">
          <el-input
            v-model="configForm.agentName"
            placeholder="自定义名称，如：文档解析助手"
          />
        </el-form-item>
        <el-form-item label="API密钥" prop="apiKey">
          <el-input
            v-model="configForm.apiKey"
            type="password"
            :placeholder="isApiKeyEncrypted ? '已加密存储，输入新值可覆盖' : '请输入appkey'"
            show-password
            @focus="handleApiKeyFocus"
          >
            <template #suffix>
              <el-tag v-if="isApiKeyEncrypted" type="success" size="small" style="margin-right: 30px;">已添加</el-tag>
            </template>
          </el-input>
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            从 应用发布→API管理 中获取
          </div>
        </el-form-item>
        <el-form-item label="API端点" prop="apiEndpoint">
          <el-input
            v-model="configForm.apiEndpoint"
            placeholder="请输入API端点URL"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            默认：https://yuanqi.tencent.com/openapi/v1/agent/chat/completions
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import useUserStore from '@/store/modules/user'
import {
  Document,
  Upload,
  UploadFilled,
  InfoFilled,
  Refresh,
  Delete,
  Reading,
  CopyDocument,
  Setting,
  WarningFilled,
  RefreshRight,
  Loading,
  CircleCheck,
  ChatLineRound
} from '@element-plus/icons-vue'
import {
  getMyDocumentParses,
  uploadAndParse,
  delDocumentParse,
  getDocumentParseStatus
} from '@/api/business/content/documentparse/documentParse'
import {
  getMyConfig,
  addYuanqiConfig,
  updateYuanqiConfig
} from '@/api/business/content/dailyassistant/yuanqiConfig'

// 获取 Pinia store
const userStore = useUserStore()

// 数据
const selectedFile = ref(null)
const prompt = ref('')
const documentName = ref('')
const parseList = ref([])
const selectedParse = ref(null)
const loading = ref(false)
const loadingMore = ref(false)
const uploading = ref(false)
const scrollbarRef = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)
const totalParses = ref(0)
const hasMore = ref(true)
const uploadRef = ref(null)

// 文件类型限制
const acceptFileTypes = '.pdf,.doc,.docx,.txt,.md'

// 智能体配置相关
const agentConfigured = ref(false)

// 配置对话框
const configDialogVisible = ref(false)
const configFormRef = ref(null)
const configForm = ref({
  id: null,
  agentId: '',
  agentName: '',
  apiKey: '',
  apiEndpoint: 'https://yuanqi.tencent.com/openapi/v1/agent/chat/completions',
  isActive: 1
})

// 加密标记
const MASKED_VALUE = '***已加密***'
const isAgentIdEncrypted = ref(false)
const isApiKeyEncrypted = ref(false)

const configRules = {
  agentId: [{ required: true, message: '请输入智能体ID', trigger: 'blur' }],
  agentName: [{ required: true, message: '请输入智能体名称', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入API密钥', trigger: 'blur' }],
  apiEndpoint: [{ required: true, message: '请输入API端点URL', trigger: 'blur' }]
}

// 处理文件选择
const handleFileChange = (file) => {
  selectedFile.value = file.raw
  if (!documentName.value && file.name) {
    documentName.value = file.name
  }
}

// 处理文件移除
const handleFileRemove = () => {
  selectedFile.value = null
}

// 上传并解析
const handleUploadAndParse = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文档')
    return
  }

  if (!prompt.value.trim()) {
    ElMessage.warning('请输入解析提示词')
    return
  }

  uploading.value = true

  try {
    // 生成documentId（前端生成，后端也会生成，但前端生成可以用于追踪）
    const documentId = 'DOC-' + Date.now().toString(36).toUpperCase() + '-' + Math.random().toString(36).substring(2, 8).toUpperCase()

    const response = await uploadAndParse(
      selectedFile.value,
      prompt.value.trim(),
      documentId,
      documentName.value || selectedFile.value.name
    )

    if (response.code === 200 && response.data) {
      const newParseId = response.data.id
      const newDocumentId = response.data.documentId

      // 清空表单
      selectedFile.value = null
      prompt.value = ''
      documentName.value = ''
      if (uploadRef.value) {
        uploadRef.value.clearFiles()
      }

      // 显示成功提示
      ElMessage({
        message: `文档"${response.data.documentName || newDocumentId}"上传成功，AI正在解析中...`,
        type: 'success',
        duration: 3000
      })

      // 立即刷新列表
      await loadParses()

      // 自动选中新创建的记录
      if (newParseId) {
        const newParse = parseList.value.find(p => p.id === newParseId)
        if (newParse) {
          selectParse(newParse)
        }

        // 启动轮询，监控解析进度
        startPollingParse(newParseId, newDocumentId)
      }
    } else {
      ElMessage.error(response.msg || '上传解析失败，请重试')
    }
  } catch (error) {
    console.error('上传解析失败:', error)

    if (error.response && error.response.status === 400) {
      ElMessage.error('上传解析失败，请检查智能体配置')
      ElMessageBox.confirm(
        '未找到智能体配置，是否现在配置？',
        '提示',
        {
          confirmButtonText: '去配置',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(() => {
        showConfigDialog()
      }).catch(() => {
        // 用户取消
      })
    } else {
      ElMessage.error('上传解析失败：' + (error.message || '未知错误'))
    }
  } finally {
    uploading.value = false
  }
}

// 轮询单条解析记录的生成状态
const startPollingParse = (parseId, documentId) => {
  let pollCount = 0
  const maxPolls = 60  // 5分钟（60 * 5秒 = 300秒）

  const pollInterval = setInterval(async () => {
    pollCount++

    try {
      // 刷新列表，获取最新的解析内容
      await loadParses()
      const parse = parseList.value.find(p => p.id === parseId)

      if (parse) {
        // 如果当前选中的就是这条记录，自动更新显示最新内容
        if (selectedParse.value && selectedParse.value.id === parseId) {
          selectedParse.value = parse
        }

        // 检查是否完成或失败
        if (parse.processStatus === 1) {
          // 解析完成
          clearInterval(pollInterval)

          ElNotification({
            title: '解析完成',
            message: `文档"${parse.documentName || documentId}"已解析完成！`,
            type: 'success',
            duration: 5000
          })
        } else if (parse.processStatus === 2) {
          // 解析失败
          clearInterval(pollInterval)

          ElNotification({
            title: '解析失败',
            message: `文档"${parse.documentName || documentId}"解析失败：${parse.errorMessage || '未知错误'}`,
            type: 'error',
            duration: 8000
          })
        }
        // status=0 继续轮询

      } else if (pollCount >= maxPolls) {
        // 超时处理（5分钟）
        clearInterval(pollInterval)

        ElNotification({
          title: '解析超时',
          message: `文档"${documentId}"解析超时（超过5分钟），请刷新页面检查解析状态`,
          type: 'warning',
          duration: 0  // 不自动关闭
        })
      }
    } catch (error) {
      console.error('轮询失败:', error)
      // 轮询失败不影响整体流程，继续轮询
    }
  }, 5000)  // 每5秒轮询一次
}

// 加载解析记录列表（分页）
const loadParses = async (append = false) => {
  if (append) {
    loadingMore.value = true
  } else {
    loading.value = true
    currentPage.value = 1
  }

  try {
    const response = await getMyDocumentParses({
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })

    const newParses = response.rows || response.data || []
    totalParses.value = response.total || newParses.length

    // 追加或替换列表
    if (append) {
      parseList.value = [...parseList.value, ...newParses]
    } else {
      parseList.value = newParses
    }

    // 判断是否还有更多数据
    hasMore.value = parseList.value.length < totalParses.value

    // 如果有选中的记录，更新它的状态
    if (selectedParse.value) {
      const updated = parseList.value.find(p => p.id === selectedParse.value.id)
      if (updated) {
        selectedParse.value = updated
      }
    }
  } catch (error) {
    console.error('加载解析记录失败:', error)
    ElMessage.error('加载解析记录失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

// 刷新列表
const refreshParses = async () => {
  currentPage.value = 1
  await loadParses(false)
}

// 加载更多
const loadMoreParses = async () => {
  if (!hasMore.value || loadingMore.value) return
  currentPage.value++
  await loadParses(true)
}

// 处理滚动事件
const handleScroll = ({ scrollTop, scrollLeft }) => {
  const scrollbarEl = scrollbarRef.value
  if (!scrollbarEl) return

  const wrap = scrollbarEl.wrapRef
  if (!wrap) return

  const scrollHeight = wrap.scrollHeight
  const clientHeight = wrap.clientHeight
  const distanceToBottom = scrollHeight - scrollTop - clientHeight

  // 距离底部小于100px时加载更多
  if (distanceToBottom < 100 && hasMore.value && !loadingMore.value) {
    loadMoreParses()
  }
}

// 选择解析记录
const selectParse = (parse) => {
  selectedParse.value = parse
}

// 删除解析记录
const handleDelete = async (parse) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这条解析记录吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await delDocumentParse(parse.id)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      if (selectedParse.value && selectedParse.value.id === parse.id) {
        selectedParse.value = null
      }
      await loadParses()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 重试解析
const handleRetry = async (parse) => {
  try {
    await ElMessageBox.confirm(
      `确定要重新解析文档"${parse.documentName || parse.documentId}"吗？`,
      '重新解析确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    ElMessage.info('重试功能需要重新上传文档，请使用上传功能')
  } catch (error) {
    // 用户取消
  }
}

// 复制内容
const copyContent = (content) => {
  navigator.clipboard.writeText(content).then(() => {
    ElMessage.success('复制成功')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

// 加载配置状态
const loadMyConfig = async () => {
  try {
    const response = await getMyConfig('document_parse')
    if (response.code === 200 && response.data) {
      agentConfigured.value = !!(response.data.agentId && response.data.apiKey)
    } else {
      agentConfigured.value = false
    }
  } catch (error) {
    console.error('加载配置状态失败', error)
    agentConfigured.value = false
  }
}

// 显示配置对话框
const showConfigDialog = async () => {
  try {
    const response = await getMyConfig('document_parse')
    if (response.code === 200 && response.data) {
      const config = { ...response.data }

      if (!config.apiEndpoint) {
        config.apiEndpoint = 'https://yuanqi.tencent.com/openapi/v1/agent/chat/completions'
      }

      isAgentIdEncrypted.value = config.agentId === MASKED_VALUE
      isApiKeyEncrypted.value = config.apiKey === MASKED_VALUE

      if (isAgentIdEncrypted.value) {
        config.agentId = ''
      }
      if (isApiKeyEncrypted.value) {
        config.apiKey = ''
      }

      configForm.value = config
    } else {
      configForm.value.apiEndpoint = 'https://yuanqi.tencent.com/openapi/v1/agent/chat/completions'
      isAgentIdEncrypted.value = false
      isApiKeyEncrypted.value = false
    }
  } catch (error) {
    console.error('加载配置失败', error)
  }
  configDialogVisible.value = true
}

// 保存配置
const handleSaveConfig = async () => {
  try {
    await configFormRef.value.validate()

    ElMessage.info('正在保存配置，请稍候...')

    const configData = {
      ...configForm.value,
      businessType: 'document_parse'  // 指定业务类型为文档解析
    }

    if (!configData.agentId && isAgentIdEncrypted.value) {
      configData.agentId = MASKED_VALUE
    }
    if (!configData.apiKey && isApiKeyEncrypted.value) {
      configData.apiKey = MASKED_VALUE
    }

    const apiFunc = configForm.value.id ? updateYuanqiConfig : addYuanqiConfig
    const response = await apiFunc(configData)

    if (response.code === 200) {
      ElMessage.success('配置保存成功')
      configDialogVisible.value = false
      await loadMyConfig()
    } else {
      ElMessage.error(response.msg || '保存失败')
    }
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存失败: ' + (error.response?.data?.msg || error.message))
  }
}

// 关闭配置对话框
const handleConfigDialogClose = () => {
  configFormRef.value?.resetFields()
  isAgentIdEncrypted.value = false
  isApiKeyEncrypted.value = false
}

// 处理智能体ID输入框获得焦点
const handleAgentIdFocus = () => {
  // 用户可以输入新值
}

// 处理API密钥输入框获得焦点
const handleApiKeyFocus = () => {
  // 用户可以输入新值
}

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    0: 'warning',
    1: 'success',
    2: 'danger'
  }
  return types[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    0: '处理中',
    1: '已完成',
    2: '失败'
  }
  return texts[status] || '未知'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'

  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 页面加载
onMounted(async () => {
  loadParses()

  // 加载配置状态
  await loadMyConfig()

  // 检查是否有配置
  try {
    const response = await getMyConfig('document_parse')
    if (!response.data) {
      setTimeout(() => {
        ElMessageBox.confirm(
          '您还未配置腾讯元器智能体，需要先配置才能使用文档解析助手功能。',
          '提示',
          {
            confirmButtonText: '去配置',
            cancelButtonText: '稍后配置',
            type: 'info',
            closeOnClickModal: false
          }
        ).then(() => {
          showConfigDialog()
        }).catch(() => {
          // 用户选择稍后配置
        })
      }, 500)
    }
  } catch (error) {
    console.warn('检查配置失败，可能是后端服务未启动或Redis连接异常', error)
  }

  // 定期刷新列表（每30秒）
  setInterval(() => {
    loadParses()
  }, 30000)
})

</script>

<style scoped lang="scss">
.document-parse-container {
  padding: 20px;

  .box-card {
    height: calc(100vh - 140px);
    display: flex;
    flex-direction: column;
    margin-bottom: 20px;

    :deep(.el-card__body) {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    &.left-card {
      :deep(.el-card__body) {
        overflow-y: auto;
      }
    }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .card-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      font-weight: bold;
    }

    .el-button {
      margin-left: auto;
    }
  }

  .upload-section {
    margin-bottom: 20px;

    .tip-text {
      display: flex;
      align-items: center;
      gap: 5px;
      font-size: 12px;
      color: #909399;
    }
  }

  .parse-list {
    .list-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
      font-weight: bold;
    }

    .parse-item {
      padding: 12px;
      margin-bottom: 8px;
      border: 1px solid #e4e7ed;
      border-radius: 4px;
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        background-color: #f5f7fa;
        border-color: #409eff;
      }

      &.active {
        background-color: #ecf5ff;
        border-color: #409eff;
      }

      &.parse-failed {
        border-left: 4px solid #f56c6c;
        background-color: #fef0f0;

        &:hover {
          background-color: #fde2e2;
          border-color: #f56c6c;
        }

        &.active {
          background-color: #fde2e2;
          border-color: #f56c6c;
        }
      }

      .parse-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;

        .parse-name {
          font-weight: 500;
          color: #303133;
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .error-msg {
        display: flex;
        align-items: center;
        gap: 5px;
        font-size: 12px;
        color: #f56c6c;
        margin-bottom: 8px;
        padding: 4px 8px;
        background-color: #fff;
        border-radius: 4px;
      }

      .parse-meta {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .time {
          font-size: 12px;
          color: #909399;
        }

        .meta-actions {
          display: flex;
          gap: 5px;
          align-items: center;
        }
      }
    }

    .load-more-tip,
    .loading-more,
    .no-more-tip {
      text-align: center;
      padding: 15px;
      color: #909399;
      font-size: 13px;
    }

    .loading-more {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;

      .el-icon {
        font-size: 16px;
      }
    }

    .load-more-tip {
      cursor: pointer;
      transition: color 0.3s;

      &:hover {
        color: #409eff;
      }
    }
  }

  .content-card {
    height: calc(100vh - 140px);
    display: flex;
    flex-direction: column;

    :deep(.el-card__body) {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    &.right-card {
      :deep(.el-card__body) {
        overflow-y: auto;
      }
    }
  }

  .content-section {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .content-wrapper {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      .content-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 15px;
        padding-bottom: 10px;
        border-bottom: 1px solid #e4e7ed;

        h3 {
          margin: 0;
          font-size: 18px;
          color: #303133;
        }

        .action-buttons {
          display: flex;
          gap: 10px;
        }
      }

      .prompt-section {
        flex-shrink: 0;
        margin-bottom: 15px;
        padding: 10px;
        background-color: #f5f7fa;
        border-radius: 4px;

        .el-tag {
          display: flex;
          align-items: center;
          gap: 5px;
        }
      }

      :deep(.el-scrollbar) {
        flex: 1;
        overflow: hidden;
      }

      .content-text {
        padding: 15px;
        background-color: #f5f7fa;
        border-radius: 4px;
        line-height: 1.8;
        white-space: pre-wrap;
        word-wrap: break-word;
        color: #606266;
      }

      .parsing-status {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 60px 20px;
        color: #909399;

        .el-icon {
          font-size: 48px;
          margin-bottom: 20px;
          color: #409eff;
        }

        p {
          margin: 8px 0;
          font-size: 16px;

          &.tip {
            font-size: 14px;
            color: #c0c4cc;
          }
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .document-parse-container {
    padding: 10px;

    .content-card {
      height: auto;
      margin-top: 20px;
    }
  }
}
</style>

