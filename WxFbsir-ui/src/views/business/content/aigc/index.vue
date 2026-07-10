<template>
  <div class="ai-management-platform">
    <!-- 顶部导航区 -->
    <div class="top-nav">
      <div class="nav-container">
        <div class="logo-area">
          <el-icon class="logo-icon" :size="28">
            <ChatDotRound />
          </el-icon>
          <h1 class="platform-title">AI助手</h1>
        </div>
        <div class="nav-buttons">
          <el-button type="primary" @click="createNewChat">
            <el-icon>
              <Plus />
            </el-icon>
            创建新对话
          </el-button>
          <el-button @click="showHistoryDrawer">
            <el-icon>
              <Clock />
            </el-icon>
            历史记录
          </el-button>
          <el-button size="mini" type="primary" @click="handleGenerateOutput" v-hasPermi="['business:output:generate']">
            生成输出物
          </el-button>

          <el-button size="mini" @click="handleExportMarkdown" v-hasPermi="['business:output:exportMarkdown']">
            导出Markdown
          </el-button>

          <el-button size="mini" @click="handlePushWebhook" v-hasPermi="['business:output:pushWebhook']">
            推送Webhook
          </el-button>
        </div>
      </div>
    </div>

    <!-- 🔥 历史记录抽屉（完全参考旧项目cube-ui实现） -->
    <el-drawer title="历史会话记录" v-model="historyDrawerVisible" direction="rtl" size="35%">
      <div class="history-content">
        <div v-if="historyLoading" class="history-loading">
          <el-icon class="is-loading">
            <Loading />
          </el-icon>
          <span>加载中...</span>
        </div>
        <!-- 🔥 按日期和chatId分组显示历史记录 -->
        <div v-else-if="chatHistory.length > 0">
          <div v-for="(group, date) in groupedHistory" :key="date" class="history-group">
            <div class="history-date">{{ date }}</div>
            <div class="history-list">
              <div v-for="(item, index) in group" :key="index" class="history-item">
                <!-- 🔥 会话组父记录 -->
                <div class="history-parent"
                  @click="item.isChatGroup ? toggleHistoryExpansion(item) : loadHistoryItem(item)">
                  <div class="history-header">
                    <!-- 会话组展开/收起箭头 -->
                    <el-icon v-if="item.isChatGroup" :class="{ 'is-expanded': item.isExpanded }" class="expand-arrow">
                      <ArrowRight />
                    </el-icon>
                    <!-- 单轮对话图标 -->
                    <el-icon v-else class="chat-icon">
                      <ChatDotRound />
                    </el-icon>
                    <div class="history-content-wrapper">
                      <div class="history-prompt">{{ item.userPrompt }}</div>
                      <div class="history-meta">
                        <span class="history-time">{{ formatHistoryTime(item.createTime) }}</span>
                        <span class="history-separator">•</span>
                        <span class="history-chatid" :title="'会话ID: ' + item.chatId">
                          会话 {{ item.chatId ? item.chatId.substring(0, 8) : '' }}
                        </span>
                        <span v-if="item.isChatGroup" class="children-count">
                          • {{ item.totalRounds }}轮对话
                        </span>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 🔥 展开显示各轮对话 -->
                <div v-if="item.isChatGroup && item.children && item.children.length > 0 && item.isExpanded"
                  class="history-children">
                  <div v-for="(round, roundIndex) in item.children" :key="roundIndex" class="history-child-item"
                    @click="loadHistoryItem(round)">
                    <div class="history-child-content">
                      <span class="child-index">第{{ roundIndex + 1 }}轮</span>
                      <div class="history-prompt">{{ round.roundPrompt || round.userPrompt }}</div>
                      <div class="history-meta">
                        <span class="history-time">{{ formatHistoryTime(round.createTime) }}</span>
                        <span class="history-separator">•</span>
                        <span class="ai-count">{{ round.aiResponseCount || 1 }}个AI响应</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="history-empty">
          <el-icon>
            <Document />
          </el-icon>
          <p>暂无历史记录</p>
        </div>
      </div>
    </el-drawer>

    <div class="main-content">
      <div class="content-container">
        <el-collapse v-model="activeCollapses" class="custom-collapse">
          <!-- AI选择配置 -->
          <el-collapse-item name="ai-selection">
            <template #title>
              <div class="ai-config-header">
                <el-icon :size="18" style="margin-right: 8px;">
                  <ChatDotRound />
                </el-icon>
                <span>AI选择配置</span>
              </div>
              <el-dialog v-model="outputDialogVisible" title="编辑输出物" width="600px">
                <el-form label-width="80px">
                  <el-form-item label="标题">
                    <el-input v-model="outputTitle" />
                  </el-form-item>
                  <el-form-item label="内容">
                    <el-input v-model="outputContent" type="textarea" :rows="10" />
                  </el-form-item>
                </el-form>

                <template #footer>
                  <el-button @click="outputDialogVisible = false">取消</el-button>
                  <el-button type="primary" @click="handleSaveOutputArtifact" v-hasPermi="['business:output:save']">
                    保存输出物
                  </el-button>
                </template>
              </el-dialog>
            </template>
            <div class="ai-selection-section">
              <div class="ai-cards">
                <!-- 动态渲染AI卡片 -->
                <el-card v-for="ai in aiServices" :key="ai.id" class="ai-card modern-card" :class="{
                  'ai-card-not-logged': !ai.loggedIn,
                  'ai-card-enabled': aiStates[ai.id]?.enabled
                }" shadow="hover">
                  <!-- 未登录遮罩 -->
                  <div v-if="!ai.loggedIn" class="card-login-overlay">
                    <div class="card-login-message">
                      <el-icon :size="24">
                        <Warning />
                      </el-icon>
                      <span>未登录</span>
                      <el-button size="small" type="primary" @click="handleServiceLogin(ai.id)">
                        <el-icon>
                          <Link />
                        </el-icon>
                        前往登录
                      </el-button>
                    </div>
                  </div>

                  <!-- 卡片头部 -->
                  <div class="ai-card-header">
                    <div class="ai-left">
                      <!-- 图标显示 -->
                      <div class="ai-avatar" :class="`${ai.id}-avatar`">
                        <img v-if="ai.icon.type === 'url'" :src="ai.icon.value" class="ai-avatar-img" />
                        <el-icon v-else-if="ai.icon.type === 'element'" :size="20">{{ ai.icon.value }}</el-icon>
                        <el-icon v-else :size="20">
                          <ChatDotRound />
                        </el-icon>
                      </div>
                      <div class="ai-info">
                        <div class="ai-name">{{ ai.displayName }}</div>
                        <div class="ai-description">{{ ai.description }}</div>
                      </div>
                    </div>
                    <div class="ai-status">
                      <el-switch v-if="aiStates[ai.id]" v-model="aiStates[ai.id].enabled" active-color="#409eff"
                        inactive-color="#dcdfe6" :disabled="!ai.loggedIn" @change="handleAiToggle(ai.id)" />
                    </div>
                  </div>

                  <!-- AI选项 -->
                  <div class="ai-options" v-if="aiStates[ai.id]?.enabled && ai.options && ai.options.length > 0">
                    <div class="options-divider"></div>
                    <div class="ai-capabilities">
                      <el-tag v-for="option in ai.options" :key="option.id"
                        :type="aiStates[ai.id].options[option.id] ? 'primary' : 'info'"
                        :effect="aiStates[ai.id].options[option.id] ? 'dark' : 'plain'" class="capability-tag"
                        @click="toggleAiOption(ai.id, option.id)" :class="{
                          'tag-disabled': !aiStates[ai.id].enabled || !ai.loggedIn || option.disabled,
                          'tag-clickable': aiStates[ai.id].enabled && ai.loggedIn && !option.disabled
                        }">
                        <el-icon>
                          <Setting />
                        </el-icon>
                        {{ option.label }}
                      </el-tag>
                    </div>
                  </div>
                </el-card>
              </div>
            </div>
          </el-collapse-item>

          <!-- 提示词输入区 -->
          <el-collapse-item name="prompt-input">
            <template #title>
              <div class="ai-config-header">
                <el-icon :size="18" style="margin-right: 8px;">
                  <Document />
                </el-icon>
                <span>提示词输入</span>
              </div>
            </template>
            <div class="prompt-input-section modern-input">
              <div class="input-wrapper">
                <el-input type="textarea" placeholder="请输入您的问题或需求，支持Markdown格式..." v-model="promptInput" resize="none"
                  class="prompt-input dynamic-textarea" :autosize="{ minRows: 1, maxRows: 3 }" />
              </div>
              <div class="prompt-footer">
                <div class="footer-left">
                  <el-button size="small" class="upload-button" @click="handleFileUpload">
                    <el-icon>
                      <Picture />
                    </el-icon>
                    上传文件
                  </el-button>
                  <span class="word-count">
                    <el-icon>
                      <Document />
                    </el-icon>
                    {{ promptInput.length }} 字
                  </span>
                </div>
                <el-button type="primary" @click="sendPrompt" :disabled="!canSend" :loading="isSending"
                  class="send-button" size="large">
                  <el-icon v-if="!isSending">
                    <ChatDotRound />
                  </el-icon>
                  {{ isSending ? '发送中...' : '发送' }}
                </el-button>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- 🔥 执行状态展示区（支持多AI区分显示，参考旧项目） -->
      <div class="execution-status-section" v-if="taskStarted">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card class="task-flow-card">
              <template #header>
                <div class="card-header">
                  <span>任务流程</span>
                  <el-tag v-if="allTasksCompleted" type="success" size="small">全部完成</el-tag>
                  <el-tag v-else-if="hasRunningTasks" type="warning" size="small">执行中</el-tag>
                </div>
              </template>
              <div class="task-flow">
                <!-- 🔥 支持多AI任务流程展示 -->
                <div v-for="(ai, aiIndex) in enabledAIs" :key="aiIndex" class="task-item">
                  <div class="task-header" @click="toggleAiExpand(ai)">
                    <div class="header-left">
                      <el-icon class="expand-icon" :class="{ 'is-expanded': ai.isExpanded }">
                        <ArrowRight />
                      </el-icon>
                      <span class="ai-name">{{ ai.name || 'DeepSeek' }}</span>
                    </div>
                    <div class="header-right">
                      <span class="status-text">{{ getStatusText(ai.status || taskStatus) }}</span>
                      <el-icon v-if="(ai.status || taskStatus) === 'running'" class="is-loading">
                        <Loading />
                      </el-icon>
                      <el-icon v-else-if="(ai.status || taskStatus) === 'completed'" color="#67c23a">
                        <CircleCheck />
                      </el-icon>
                      <el-icon v-else-if="(ai.status || taskStatus) === 'failed'" color="#f56c6c">
                        <CircleClose />
                      </el-icon>
                      <el-icon v-else color="#909399">
                        <Clock />
                      </el-icon>
                    </div>
                  </div>
                  <!-- 🔥 进度日志（按AI区分） -->
                  <div class="progress-timeline"
                    v-if="ai.isExpanded !== false && (ai.progressLogs || progressLogs).length > 0">
                    <div class="timeline-scroll">
                      <div v-for="(log, logIndex) in (ai.progressLogs || progressLogs)" :key="logIndex"
                        class="progress-item">
                        <div class="progress-dot" :class="getLogDotClass(log)"></div>
                        <div class="progress-content">
                          <div class="progress-time">{{ formatTime(log.timestamp) }}</div>
                          <div class="progress-text">{{ log.content }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card class="screenshots-card">
              <template #header>
                <div class="card-header">
                  <span>执行可视化</span>
                </div>
              </template>
              <div class="screenshots">
                <el-carousel ref="screenshotCarousel" v-if="screenshots.length > 0" :interval="3000" :autoplay="false"
                  indicator-position="outside" height="700px">
                  <el-carousel-item v-for="(screenshot, index) in screenshots" :key="index">
                    <img :src="screenshot" alt="执行截图" class="screenshot-image" @click="showLargeImage(screenshot)" />
                  </el-carousel-item>
                </el-carousel>
                <div v-else class="no-screenshots">
                  <el-icon :size="48">
                    <Picture />
                  </el-icon>
                  <p>等待截图...</p>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 结果展示区 -->
      <div class="results-section" v-if="results.length > 0">
        <div class="section-header">
          <h2 class="section-title">执行结果</h2>
        </div>
        <el-card>
          <div v-for="(result, index) in results" :key="index" class="result-content">
            <div class="result-header">
              <div class="result-title">{{ result.aiName }}的执行结果</div>
              <div class="result-actions">
                <el-button v-if="result.shareUrl" size="small" type="primary" @click="openShareUrl(result.shareUrl)">
                  <el-icon>
                    <Link />
                  </el-icon>
                  查看原链接
                </el-button>
                <el-button v-if="result.content" size="small" @click="copyToClipboard(result)">
                  <el-icon>
                    <Document />
                  </el-icon>
                  复制文本
                </el-button>
              </div>
            </div>

            <!-- 🔥 优先显示截图 -->
            <div v-if="result.hasScreenshot && result.screenshotUrl" class="result-screenshot">
              <img :src="result.screenshotUrl" alt="AI回复截图" class="result-screenshot-image"
                @click="showLargeImage(result.screenshotUrl)" />
              <div class="screenshot-tip">点击图片查看大图</div>
            </div>

            <!-- 🔥 如果没有截图，显示文本内容 -->
            <div v-else-if="result.content" class="markdown-content" v-html="renderMarkdown(result.content)"></div>

            <!-- 🔥 既没有截图也没有文本 -->
            <div v-else class="no-result">
              <el-icon :size="48">
                <Warning />
              </el-icon>
              <p>暂无结果内容</p>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 大图查看对话框 -->
    <el-dialog v-model="showImageDialog" width="90%" center class="image-dialog">
      <img :src="currentLargeImage" alt="大图" class="large-image" />
    </el-dialog>

    <!-- 主机ID验证对话框 -->
    <el-dialog v-model="hostIdDialogVisible" title="需要配置主机ID" width="500px" center :close-on-click-modal="false"
      :close-on-press-escape="false" :show-close="false">
      <div class="host-id-dialog-content">
        <el-icon :size="64" color="#e6a23c" style="margin-bottom: 16px;">
          <Warning />
        </el-icon>
        <p style="font-size: 16px; margin-bottom: 24px; color: #606266;">
          您还未配置Engine主机ID，无法使用AI助手功能。
        </p>
        <p style="font-size: 14px; color: #909399; margin-bottom: 24px;">
          请前往个人中心填写主机ID后再使用本功能。
        </p>
      </div>
      <template #footer>
        <el-button type="primary" @click="goToProfile">前往个人中心</el-button>
      </template>
    </el-dialog>

    <!-- 服务登录对话框 -->
    <el-dialog v-model="loginDialogVisible" :title="`${currentLoginServiceName}扫码登录`" width="600px" center
      :close-on-click-modal="false">
      <div class="login-dialog-content">
        <div v-if="loginLoading" class="login-loading">
          <el-icon class="is-loading" :size="32">
            <Loading />
          </el-icon>
          <p>{{ loginStatusText }}</p>
        </div>
        <div v-if="qrCodeUrl" class="qrcode-container">
          <img :src="qrCodeUrl" alt="登录二维码" class="qrcode-image"
            style="width: 400px; height: 400px; display: block; margin: 0 auto;" />
          <p style="text-align: center; margin-top: 20px; color: #666;">请使用微信扫码登录{{ currentLoginServiceName }}</p>
        </div>
      </div>
    </el-dialog>

    <!-- 文件上传对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传文件" width="500px" center>
      <el-upload ref="uploadRef" :action="uploadAction" :headers="uploadHeaders" :on-success="handleUploadSuccess"
        :on-error="handleUploadError" :before-upload="beforeUpload" :limit="1" :file-list="fileList" drag>
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持图片、文档、压缩包等，最大50MB
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmUpload" :disabled="!uploadedFileUrl">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import Cookies from 'js-cookie'
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Clock, Loading, Document, Warning, CircleCheck, CircleClose, Link, Picture, ChatDotRound, ArrowRight, Setting, UploadFilled } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { restoreLoginStatusFromStorage } from '@/config/engineConfig'
import { getToken } from '@/utils/auth'
import { addDraft, getDraftContent } from '@/api/aigc/drafts'
import { sendAiRequest, getChatHistory } from '@/api/aigc/assistant'
import {
  generateOutputArtifact,
  exportOutputMarkdown,
  exportOutputJson,
  pushOutputWebhook,
  saveOutputArtifact
} from '@/api/business/content/aigc/output'
import { buildWebSocketUrl } from '@/utils/websocket'
import useUserStore from '@/store/modules/user'
import {
  ENGINE_CONFIGS,
  DEFAULT_CONFIG,
  getEngineConfig,
  getAiServices,
  getAiQueryMessageType,
  getServiceScanLoginMessageType,
  getAiScanLoginMessageType,
  initServiceOptionsState,
  handleExclusiveOptionToggle,
  updateServiceLoginStatus
} from '@/config/engineConfig'

export default {
  name: 'AiAssistant',
  components: {
    Plus, Clock, Loading, Document, Warning, CircleCheck, CircleClose, Link, Picture, ChatDotRound, ArrowRight, Setting, UploadFilled
  },
  setup() {
    // 获取用户store（用于获取hostId/engineId）
    const userStore = useUserStore()

    // 主机ID验证对话框
    const hostIdDialogVisible = ref(false)

    // 响应式数据
    const activeCollapses = ref(['ai-selection', 'prompt-input'])
    const historyDrawerVisible = ref(false)
    const historyLoading = ref(false)
    const chatHistory = ref([])
    const expandedHistoryItems = ref({})  // 🔥 历史记录展开状态

    // 🔥 获取所有AI服务
    const aiServices = computed(() => getAiServices())

    // 🔥 动态AI状态管理
    const aiStates = ref({})

    // 🔥 初始化AI状态
    const initAiStates = () => {
      ENGINE_CONFIGS.forEach(service => {
        if (service.type === 'ai') {
          aiStates.value[service.id] = {
            enabled: service.enabled,
            options: initServiceOptionsState(service.id)
          }
        }
      })
    }


    // 🔥 切换AI选项
    const toggleAiOption = (aiId, optionId) => {
      const currentState = aiStates.value[aiId].options
      const newValue = !currentState[optionId]

      // gitee 服务：模式选项（openSourceExploration/helpCenter）互斥，但 enableFileUpload 独立
      if (aiId === 'gitee') {
        const newState = { ...currentState }
        const modeOptions = ['openSourceExploration', 'helpCenter']
        if (modeOptions.includes(optionId)) {
          // 模式选项互斥：选中时取消其他模式，不影响 enableFileUpload
          if (newValue) {
            modeOptions.forEach(key => { newState[key] = key === optionId })
          } else {
            newState[optionId] = false
          }
        } else {
          // 非模式选项（如 enableFileUpload）独立切换
          newState[optionId] = newValue
        }
        aiStates.value[aiId].options = newState
      } else {
        // 其他服务使用默认的互斥逻辑
        aiStates.value[aiId].options = handleExclusiveOptionToggle(aiId, optionId, newValue, currentState)
      }
    }

    // 🔥 处理服务登录
    const handleServiceLogin = (serviceId) => {
      const config = getEngineConfig(serviceId)
      if (!config) return

      // 🔥 提示用户前往登录管理器登录
      ElMessage.info({
        message: `请前往"登录管理器"页面登录 ${config.displayName}`,
        duration: 3000
      })

      console.log('📝 [AI助手] 用户需要前往登录管理器登录:', config.displayName)
    }

    // 输入和发送状态
    const promptInput = ref('')
    const isSending = ref(false)
    const taskStarted = ref(false)
    const taskStatus = ref('pending')
    const progressLogs = ref([])
    const screenshots = ref([])
    const results = ref([])
    const currentChatId = ref(null)
    const enabledAIs = ref([])  // 🔥 启用的AI列表（支持多AI）
    const screenshotCarousel = ref(null)  // 🔥 幻灯片组件引用
    const isNewChat = ref(true)  // 🔥 是否是新会话
    // 输出物相关
    const currentArtifactId = ref('')
    const outputTitle = ref('')
    const outputContent = ref('')
    const outputDialogVisible = ref(false)

    // 当前登录的服务ID
    const currentLoginService = ref('')

    // 🔥 AI会话ID管理（完全参考旧项目cube-admin）
    const userInfoReq = ref({
      chatId: '',
      toneChatId: '',
      ybChatId: '',
      dbChatId: '',
      tyChatId: '',
      deepseekChatId: '',
      giteeChatId: '',
      maxChatId: '',
      metasoChatId: '',
      kimiChatId: '',
      baiduChatId: '',
      zhzdChatId: '',
      isNewChat: true
    })

    // 对话框状态
    const showImageDialog = ref(false)
    const currentLargeImage = ref('')
    const loginDialogVisible = ref(false)
    const loginLoading = ref(false)
    const loginStatusText = ref('')
    const qrCodeUrl = ref('')

    // 文件上传相关
    const uploadDialogVisible = ref(false)
    const uploadRef = ref(null)
    const fileList = ref([])
    const uploadedFileUrl = ref('')
    const uploadAction = ref(import.meta.env.VITE_APP_BASE_API + '/common/upload')
    const uploadHeaders = ref({
      Authorization: 'Bearer ' + getToken()
    })

    // WebSocket连接
    let websocket = null
    let sessionId = null  // 会话ID，用于全链路追踪（与系统的requestId区分）

    // 🔥 当前登录服务名称
    const currentLoginServiceName = computed(() => {
      if (!currentLoginService.value) return ''
      const config = getEngineConfig(currentLoginService.value)
      return config ? config.displayName : ''
    })

    // 计算属性
    const canSend = computed(() => {
      // 检查是否有已启用的AI
      const hasEnabledAi = Object.values(aiStates.value).some(state => state.enabled)
      return promptInput.value.trim().length > 0 && hasEnabledAi && !isSending.value
    })

    // 🔥 验证主机ID
    const checkHostId = () => {
      const hostId = userStore.hostId
      if (!hostId || hostId.trim() === '') {
        hostIdDialogVisible.value = true
        return false
      }
      return true
    }

    // 🔥 前往个人中心
    const goToProfile = () => {
      hostIdDialogVisible.value = false
      window.location.href = '/#/user/profile'
    }

    // 🔥 检查是否所有任务完成
    const allTasksCompleted = computed(() => {
      if (!taskStarted.value || enabledAIs.value.length === 0) return false
      return enabledAIs.value.every(ai => ai.status === 'completed' || ai.status === 'failed')
    })

    // 🔥 检查是否有任务运行中
    const hasRunningTasks = computed(() => {
      return enabledAIs.value.some(ai => ai.status === 'running')
    })

    // 🔥 按日期和chatId分组历史记录（完全参考旧项目cube-ui）
    const groupedHistory = computed(() => {
      const groups = {}
      const chatGroups = {}

      // 首先按chatId分组
      chatHistory.value.forEach(item => {
        const cid = item.chatId || 'unknown'
        if (!chatGroups[cid]) {
          chatGroups[cid] = []
        }
        chatGroups[cid].push(item)
      })

      // 按chatId聚合，每个chatId作为一个父记录
      Object.entries(chatGroups).forEach(([chatId, chatGroup]) => {
        // 按时间升序排序
        chatGroup.sort((a, b) => {
          const timeA = new Date(a.createTime).getTime()
          const timeB = new Date(b.createTime).getTime()
          return timeA - timeB
        })

        // 按userPrompt分组（同一个提问的多个AI响应算一轮）
        const roundGroups = {}
        chatGroup.forEach(record => {
          const prompt = record.userPrompt || '未知提问'
          if (!roundGroups[prompt]) {
            roundGroups[prompt] = []
          }
          roundGroups[prompt].push(record)
        })

        // 获取第一条记录用于日期分组
        const firstRecord = chatGroup[0]
        const date = getHistoryDate(firstRecord.createTime)

        if (!groups[date]) {
          groups[date] = []
        }

        // 将每一轮作为子记录
        const rounds = Object.entries(roundGroups).map(([prompt, roundRecords], roundIndex) => {
          const lastRecord = roundRecords[roundRecords.length - 1]
          let aiResponseCount = 0
          try {
            const recordData = JSON.parse(lastRecord.data)
            aiResponseCount = recordData.results ? recordData.results.length : 0
          } catch (e) {
            aiResponseCount = 1
          }

          return {
            ...lastRecord,
            roundIndex: roundIndex,
            roundPrompt: prompt,
            aiResponseCount: aiResponseCount,
            isRound: true
          }
        })

        // chatId作为父记录，各轮作为子记录
        groups[date].push({
          ...firstRecord,
          isParent: true,
          isChatGroup: rounds.length > 1,
          totalRounds: rounds.length,
          chatId: chatId,
          isExpanded: expandedHistoryItems.value[chatId] || false,
          children: rounds
        })
      })

      return groups
    })

    // 方法
    const createNewChat = () => {
      // 🔥 重置所有数据，标记为新会话（实际chatId在发送消息时生成）
      currentChatId.value = null  // 清空chatId，等待发送消息时生成
      isNewChat.value = true
      promptInput.value = ''
      taskStarted.value = false
      taskStatus.value = 'pending'
      progressLogs.value = []
      screenshots.value = []
      results.value = []
      enabledAIs.value = []
      uploadedFileUrl.value = ''
      fileList.value = []

      // 🔥 重置AI会话ID
      userInfoReq.value = {
        chatId: '',
        toneChatId: '',
        ybChatId: '',
        dbChatId: '',
        tyChatId: '',
        deepseekChatId: '',
        maxChatId: '',
        metasoChatId: '',
        kimiChatId: '',
        baiduChatId: '',
        zhzdChatId: '',
        isNewChat: true
      }

      console.log('📝 [新建会话] 已标记为新会话，chatId将在发送消息时生成')
      ElMessage.success('已创建新对话')
    }

    const showHistoryDrawer = () => {
      historyDrawerVisible.value = true
      loadChatHistory()
    }

    const loadChatHistory = async () => {
      historyLoading.value = true
      try {
        const response = await getChatHistory({ pageNum: 1, pageSize: 50 })
        if (response.rows) {
          chatHistory.value = response.rows
        } else {
          chatHistory.value = []
        }
      } catch (error) {
        console.error('加载历史记录失败:', error)
        chatHistory.value = []
      } finally {
        historyLoading.value = false
      }
    }

    // 🔥 加载历史记录项（完全参考旧项目cube-ui，支持上下文复用）
    const loadHistoryItem = (item) => {
      try {
        const historyData = JSON.parse(item.data)
        console.log('📝 [加载历史] 原始item:', item)
        console.log('📝 [加载历史] 解析data:', historyData)

        // 🔥 从data.data中提取嵌套数据（Engine返回的结构是 payload.data.xxx）
        const nestedData = historyData.data || {}

        // 恢复提示词输入（优先从nestedData.query获取）
        promptInput.value = nestedData.query || historyData.promptInput || item.userPrompt || ''

        const storedResults = historyData.results || nestedData.results || []
        const buildEnabledAiFromType = (aiType) => {
          const aiConfig = getEngineConfig(aiType)
          return {
            name: aiConfig ? aiConfig.displayName : aiType,
            status: 'completed',
            isExpanded: false,
            progressLogs: []
          }
        }

        // 🔥 恢复任务流程（多层级解析）
        // 尝试从多个位置获取enabledAIs: historyData.enabledAIs, historyData.extraParams.enabledAIs
        let enabledAIsData = historyData.enabledAIs
        if (!enabledAIsData && historyData.extraParams && historyData.extraParams.enabledAIs) {
          enabledAIsData = historyData.extraParams.enabledAIs
        }

        if (enabledAIsData && enabledAIsData.length > 0) {
          enabledAIs.value = enabledAIsData
        } else if (Array.isArray(storedResults) && storedResults.length > 0) {
          const uniqueAiTypes = [...new Set(storedResults
            .map(result => (result?.aiType || '').toLowerCase())
            .filter(Boolean))]
          enabledAIs.value = uniqueAiTypes.map(buildEnabledAiFromType)
        } else {
          // 🔥 根据AI类型动态构造任务流程
          const aiType = historyData.aiType || nestedData.aiType || 'deepseek'
          enabledAIs.value = [buildEnabledAiFromType(aiType)]
        }

        // 🔥 恢复进度日志（多层级解析）
        let progressLogsData = historyData.progressLogs
        if (!progressLogsData && historyData.extraParams && historyData.extraParams.progressLogs) {
          progressLogsData = historyData.extraParams.progressLogs
        }
        progressLogs.value = progressLogsData || []

        // 🔥 按aiType分配日志给对应的AI（支持多AI任务流程显示）
        if (progressLogs.value.length > 0 && enabledAIs.value.length > 0) {
          progressLogs.value.forEach(log => {
            const logAiType = log.aiType || 'deepseek'
            const targetAi = enabledAIs.value.find(ai =>
              ai.name.toLowerCase().includes(logAiType.toLowerCase())
            )
            if (targetAi) {
              if (!targetAi.progressLogs) {
                targetAi.progressLogs = []
              }
              targetAi.progressLogs.push(log)
            }
          })
          console.log('📝 [加载历史] 已按AI类型分配进度日志')
        }

        console.log('📝 [加载历史] 任务流程:', enabledAIs.value, '进度日志数:', progressLogs.value.length)

        // 恢复主机可视化（从nestedData中获取截图）
        screenshots.value = historyData.screenshots || []
        if (nestedData.conversationScreenshot) {
          screenshots.value.push(nestedData.conversationScreenshot)
        }

        // 恢复执行结果（🔥 添加截图相关字段）
        const mapHistoryResult = (result = {}) => {
          const resultAiType = result.aiType || historyData.aiType || nestedData.aiType || 'deepseek'
          const resultAiConfig = getEngineConfig(resultAiType)
          const resultAiName = resultAiConfig ? resultAiConfig.displayName : resultAiType
          const resultAnswer = result.answer || result.content || ''
          const answerIsUrl = resultAnswer &&
            (resultAnswer.startsWith('http://') || resultAnswer.startsWith('https://'))
          const screenshotUrl = result.conversationScreenshot || result.screenshotUrl || (answerIsUrl ? resultAnswer : null)

          return {
            aiName: result.aiName || resultAiName,
            content: result.textContent || resultAnswer,
            screenshotUrl,
            hasScreenshot: result.hasScreenshot !== false && !!screenshotUrl,
            shareUrl: result.shareUrl,
            chatId: result.chatId,
            sessionId: historyData.sessionId,
            query: result.query,
            mode: result.mode
          }
        }

        const findStoredResultByAiType = (aiType) => {
          if (!Array.isArray(storedResults)) return null
          return storedResults.find(result =>
            (result?.aiType || '').toLowerCase() === aiType.toLowerCase()
          ) || null
        }
        if (Array.isArray(storedResults) && storedResults.length > 0) {
          results.value = storedResults.map(mapHistoryResult)
        } else if (nestedData.answer) {
          // 🔥 判断answer是否为截图URL
          const answerIsUrl = nestedData.answer &&
            (nestedData.answer.startsWith('http://') || nestedData.answer.startsWith('https://'))

          // 🔥 动态获取 AI 显示名称
          const historyAiType = nestedData.aiType || historyData.aiType || 'deepseek'
          const historyAiConfig = getEngineConfig(historyAiType)
          const historyAiName = historyAiConfig ? historyAiConfig.displayName : historyAiType

          results.value = [{
            aiName: historyAiName,  // 🔥 动态 AI 名称
            content: nestedData.answer,  // 可能是截图URL或文本
            screenshotUrl: nestedData.conversationScreenshot || (answerIsUrl ? nestedData.answer : null),
            hasScreenshot: nestedData.hasScreenshot !== false && (nestedData.conversationScreenshot || answerIsUrl),
            shareUrl: nestedData.shareUrl,
            chatId: nestedData.chatId,
            sessionId: historyData.sessionId,  // 🔥 保存sessionId用于复制功能
            query: nestedData.query,
            mode: nestedData.mode
          }]
        } else {
          // 🔥 优先使用historyData.results，如果没有则使用historyData.data.results
          if (historyData.results) {
            results.value = historyData.results
          } else if (nestedData.results) {
            results.value = nestedData.results
          } else {
            results.value = []
          }
        }

        // 🔥 恢复chatId（区分前端分组ID和AI内部会话ID）
        // item.chatId 是数据库中的前端分组ID，用于关联多轮对话
        // nestedData.chatId 是AI返回的内部会话ID，用于上下文复用
        const restoredChatId = item.chatId || item.id  // 前端分组ID
        currentChatId.value = restoredChatId
        isNewChat.value = false

        // 🔥 恢复所有AI会话ID（关键：上下文复用）
        userInfoReq.value.chatId = restoredChatId
        userInfoReq.value.toneChatId = item.toneChatId || ''
        userInfoReq.value.ybChatId = item.ybChatId || ''
        userInfoReq.value.dbChatId = item.dbChatId || ''
        userInfoReq.value.tyChatId = item.tyChatId || ''
        // 🔥 DeepSeek会话ID：优先从nestedData获取（AI上下文复用），其次从数据库字段
        userInfoReq.value.deepseekChatId = item.deepseekChatId || findStoredResultByAiType('deepseek')?.chatId || nestedData.chatId || ''
        userInfoReq.value.giteeChatId = item.giteeChatId || findStoredResultByAiType('gitee')?.chatId || nestedData.chatId || ''
        userInfoReq.value.maxChatId = item.maxChatId || ''
        userInfoReq.value.metasoChatId = item.metasoChatId || ''
        userInfoReq.value.kimiChatId = item.kimiChatId || ''
        userInfoReq.value.baiduChatId = item.baiduChatId || ''
        userInfoReq.value.zhzdChatId = item.zhzdChatId || ''
        userInfoReq.value.isNewChat = false

        console.log('📝 [加载历史] 前端分组ID(currentChatId):', restoredChatId)
        console.log('📝 [加载历史] AI上下文ID(deepseekChatId):', userInfoReq.value.deepseekChatId)

        // 展开相关区域
        activeCollapses.value = ['ai-selection', 'prompt-input']
        taskStarted.value = true
        taskStatus.value = 'completed'

        historyDrawerVisible.value = false
        ElMessage.success('已加载历史对话，可继续对话')
      } catch (error) {
        console.error('解析历史记录失败:', error)
        ElMessage.error('加载历史记录失败')
      }
    }

    // 🔥 切换历史记录展开状态
    const toggleHistoryExpansion = (item) => {
      const key = item.chatId
      expandedHistoryItems.value[key] = !expandedHistoryItems.value[key]
    }

    // 🔥 获取历史记录日期分组
    const getHistoryDate = (timestamp) => {
      const date = new Date(timestamp)
      const today = new Date()
      const yesterday = new Date(today)
      yesterday.setDate(yesterday.getDate() - 1)

      if (date.toDateString() === today.toDateString()) {
        return '今天'
      } else if (date.toDateString() === yesterday.toDateString()) {
        return '昨天'
      } else {
        return date.toLocaleDateString('zh-CN', {
          year: 'numeric',
          month: 'long',
          day: 'numeric'
        })
      }
    }

    // 🔥 格式化历史记录时间
    const formatHistoryTime = (timestamp) => {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      return date.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      })
    }

    // 🔥 切换AI展开状态
    const toggleAiExpand = (ai) => {
      ai.isExpanded = ai.isExpanded !== false ? false : true
    }

    // 🔥 获取日志点样式
    const getLogDotClass = (log) => {
      if (log.type === 'error') return 'dot-error'
      if (log.type === 'success') return 'dot-success'
      return ''
    }

    const handleAiToggle = (aiId) => {
      console.log(`[AI助手] AI切换: ${aiId} -> ${aiStates.value[aiId]?.enabled}`)
    }

    const handleDeepSeekLogin = () => {
      loginDialogVisible.value = true
      loginLoading.value = true
      loginStatusText.value = '正在获取登录二维码...'

      const scanLoginType = getAiScanLoginMessageType('deepseek')
      sendWebSocketMessage(scanLoginType, {})
    }

    const sendPrompt = () => {
      if (!canSend.value) return

      // 🔥 获取所有启用的 AI
      const enabledAiList = Object.entries(aiStates.value)
        .filter(([aiId, state]) => state.enabled)
        .map(([aiId, state]) => ({
          aiId,
          config: getEngineConfig(aiId),
          state
        }))

      if (enabledAiList.length === 0) {
        ElMessage.error('请先选择并启用一个 AI')
        return
      }

      isSending.value = true
      taskStarted.value = true
      taskStatus.value = 'running'
      progressLogs.value = []
      screenshots.value = []
      results.value = []

      // 🔥 初始化启用的AI列表（支持多AI）
      enabledAIs.value = enabledAiList.map(ai => ({
        name: ai.config.displayName,
        status: 'running',
        isExpanded: true,
        progressLogs: [],
        aiId: ai.aiId
      }))

      // 🔥 生成sessionId（用于追踪本次请求）
      sessionId = generateUUID()

      // 🔥 只在点击"创建新对话"按钮时才生成新的chatId
      if (isNewChat.value) {
        currentChatId.value = generateUUID()
        userInfoReq.value.chatId = currentChatId.value
        console.log('📝 [新建会话] 生成新的chatId:', currentChatId.value)
        isNewChat.value = false  // 生成后立即标记为非新会话
      }

      // 🔥 如果没有chatId（首次打开且未加载历史），则生成一个
      if (!currentChatId.value) {
        currentChatId.value = generateUUID()
        userInfoReq.value.chatId = currentChatId.value
        console.log('📝 [首次使用] 生成新的chatId:', currentChatId.value)
      }

      // 🔥 构建所有AI的请求payload列表（先收集，后并发发送）
      const aiPayloads = []
      for (const selectedAi of enabledAiList) {
        const { aiId, config, state } = selectedAi

        if (config.requireLogin && !config.loggedIn) {
          ElMessage.error(`请先在"登录管理器"中登录 ${config.displayName}`)
          console.warn(`❌ [AI助手] ${config.displayName} 未登录，禁止发送请求`)
          continue
        }

        const aiOptions = state.options || {}
        const chatIdField = config.chatIdField || `${aiId}ChatId`
        const aiChatId = userInfoReq.value[chatIdField] || ''

        const payload = {
          query: promptInput.value,
          uploadedFileUrl: uploadedFileUrl.value || '',
          chatId: currentChatId.value,
          sessionId: sessionId,
          aiType: aiId,
          isNewChat: isNewChat.value,
          userPrompt: promptInput.value,
          enabledAIs: enabledAIs.value,
          progressLogs: progressLogs.value
        }
        payload[chatIdField] = aiChatId
        Object.keys(aiOptions).forEach(optionKey => {
          payload[optionKey] = aiOptions[optionKey]
        })

        aiPayloads.push({ aiId, config, payload })
      }

      // 🔥 首次发送后标记为非新会话
      isNewChat.value = false
      userInfoReq.value.isNewChat = false

      // 🔥 确保WebSocket连接成功后，一次性并发发送所有AI请求
      const sendAll = () => {
        for (const { aiId, config, payload } of aiPayloads) {
          doSendMessage(getAiQueryMessageType(aiId), payload)
          addProgressLog(`已发送请求到 ${config.displayName}`, aiId)
          console.log(`📝 [发送请求] AI: ${config.displayName}, chatId: ${currentChatId.value}, sessionId: ${sessionId}`)
        }
      }

      if (!websocket || websocket.readyState !== WebSocket.OPEN) {
        connectWebSocket(sendAll)
      } else {
        sendAll()
      }
    }

    const sendWebSocketMessage = (type, payload) => {
      if (!websocket || websocket.readyState !== WebSocket.OPEN) {
        connectWebSocket(() => {
          doSendMessage(type, payload)
        })
      } else {
        doSendMessage(type, payload)
      }
    }

    const doSendMessage = (type, payload) => {
      // 生成sessionId用于追踪会话（与系统的requestId区分）
      if (!sessionId) {
        sessionId = generateUUID()
      }

      const finalChatId = payload.chatId || currentChatId.value

      // 从用户配置获取engineId，未配置则使用默认值
      const engineId = userStore.hostId || DEFAULT_CONFIG.defaultEngineId

      const message = {
        type: type,
        engineId: engineId,
        chatId: finalChatId,
        payload: {
          ...payload,
          sessionId: sessionId,
          chatId: finalChatId,
          aiType: payload.aiType || 'deepseek'
        }
      }
      console.log('🔥 [WebSocket] 发送消息 - engineId:', engineId, 'chatId:', finalChatId, 'sessionId:', sessionId)
      websocket.send(JSON.stringify(message))
    }

    const connectWebSocket = (callback) => {
      // 从 cookie 获取 token（比你原来的 getToken 更稳）
      const token = Cookies.get('Admin-Token')

      // 自动判断 ws / wss（关键）
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'

      // 直接拼接，不再用 buildWebSocketUrl（避免它写死 ws://）
      const host = window.location.hostname
      const port = ['localhost', '127.0.0.1'].includes(host) ? ':8080' : ''
      const wsUrl = `${protocol}://${host}${port}/ws/client?clientType=web&token=${token}`

      console.log('连接 WebSocket（已隐藏认证参数）')

      websocket = new WebSocket(wsUrl)

      websocket.onopen = () => {
        console.log('WebSocket已连接')
        if (callback) callback()
        ElMessage.success('WebSocket连接成功')
      }

      websocket.onmessage = (event) => {
        handleWebSocketMessage(event.data)
      }

      websocket.onerror = (error) => {
        console.error('WebSocket错误:', error)
        ElMessage.error('WebSocket连接失败')
      }

      websocket.onclose = () => {
        console.log('WebSocket已断开')
      }
    }

    const checkDeepSeekLoginStatus = () => {
      sessionId = generateUUID()
      sendWebSocketMessage('AI_DEEPSEEK_CHECK_LOGIN', { sessionId: sessionId, aiType: 'deepseek' })
    }

    const handleWebSocketMessage = (data) => {
      try {
        const message = JSON.parse(data)
        console.log('收到WebSocket消息:', message)

        // Engine返回的消息格式：messageType/type 表示消息类型
        const messageType = message.messageType || message.type
        const payload = message.payload || {}

        // ==========================================================================
        // 🤖 AIGC消息处理（仅支持AI_TASK_*格式）
        // ==========================================================================
        // ⚠️ 重要：本模块只处理 AI_TASK_* 系列消息，不处理 TASK_* 系列消息
        // - AI_TASK_LOG：AI对话进度日志
        // - AI_TASK_SCREENSHOT：AI对话进度截图
        // - AI_TASK_RESULT：AI对话最终结果
        // - AI_TASK_ERROR：AI对话错误信息
        // ==========================================================================

        // 处理AI任务日志（AI_TASK_LOG）
        if (messageType === 'AI_TASK_LOG') {
          const logMessage = payload.message
          const aiType = payload.aiType || 'deepseek'
          if (logMessage) {
            addProgressLog(logMessage, aiType)
          }
        }

        // 🔥 处理AI任务截图消息（AI_TASK_SCREENSHOT）
        if (messageType === 'AI_TASK_SCREENSHOT') {
          const screenshotUrl = payload.screenshotUrl
          console.log('📸 [AI截图消息] URL:', screenshotUrl)
          if (screenshotUrl) {
            screenshots.value.push(screenshotUrl)
            console.log('📸 [AIGC截图] 收到新截图:', screenshotUrl, '总数:', screenshots.value.length)

            // 🔥 自动跳转到最后一张幻灯片
            setTimeout(() => {
              if (screenshotCarousel.value) {
                const lastIndex = screenshots.value.length - 1
                screenshotCarousel.value.setActiveItem(lastIndex)
                console.log('📸 [幻灯片] 已跳转到最后一张 (索引:', lastIndex, ')')
              }
            }, 100)
          }
        }

        // 处理结果消息（AI_TASK_RESULT）
        if (messageType === 'AI_TASK_RESULT') {
          const resultData = payload.data || payload
          const success = payload.success
          const aiType = payload.aiType || 'deepseek'
          const messageSessionId = payload.sessionId || data.sessionId  // 🔥 从payload或data中获取sessionId

          console.log('🔥 [AIGC] AI_TASK_RESULT - aiType:', aiType, 'success:', success, 'sessionId:', messageSessionId, 'resultData:', resultData)

          if (success) {
            // 🔥 检查是否是登录检查结果
            if (resultData.isLoggedIn !== undefined || (resultData.data && resultData.data.isLoggedIn !== undefined)) {
              const isLoggedIn = resultData.isLoggedIn !== undefined ? resultData.isLoggedIn : resultData.data?.isLoggedIn
              const userName = resultData.userName || resultData.data?.userName || ''
              updateServiceLoginStatus(aiType, isLoggedIn === true)
              if (isLoggedIn) {
                ElMessage.success(`${aiType} 已登录: ${userName}`)
              } else {
                ElMessage.info(`${aiType} 未登录`)
              }
              console.log('✅ [AIGC] 登录检测完成 - aiType:', aiType, '已登录:', isLoggedIn)
              return
            }

            // 检查是否是扫码登录结果
            if (resultData.loginTime !== undefined) {
              updateServiceLoginStatus(aiType, resultData.success === true)
              loginDialogVisible.value = false
              if (resultData.success) {
                ElMessage.success(`${aiType} 登录成功: ${resultData.userName || ''}`)
              }
              return
            }

            // 处理AI咨询结果
            if (resultData.answer) {
              // 🔥 更新对应AI的完成状态
              const targetAi = enabledAIs.value.find(ai => ai.name.toLowerCase().includes(aiType.toLowerCase()))
              if (targetAi) {
                targetAi.status = 'completed'
              }

              // 🔥 所有AI全部完成/失败后，才关闭发送状态
              if (enabledAIs.value.every(ai => ai.status === 'completed' || ai.status === 'failed' || ai.status === 'error')) {
                taskStatus.value = 'completed'
                isSending.value = false
              }

              // 🔥 动态获取 AI 显示名称
              const aiConfig = getEngineConfig(aiType)
              const aiDisplayName = aiConfig ? aiConfig.displayName : aiType

              // 🔥 优先使用截图，文本作为备用
              const resultItem = {
                aiName: aiDisplayName,
                content: resultData.answer,
                screenshotUrl: resultData.conversationScreenshot,
                hasScreenshot: resultData.hasScreenshot !== false && resultData.conversationScreenshot,
                shareUrl: resultData.shareUrl,
                chatId: resultData.chatId,
                sessionId: messageSessionId,
                query: resultData.query,
                mode: resultData.mode
              }
              results.value.push(resultItem)

              // 🔥 保存返回的AI会话ID（仅用于上下文复用）
              if (resultData.chatId) {
                const chatIdField = aiConfig?.chatIdField || `${aiType}ChatId`
                userInfoReq.value[chatIdField] = resultData.chatId
                console.log(`📝 [保存AI会话ID] ${chatIdField}:`, resultData.chatId, '(前端chatId保持不变:', currentChatId.value, ')')
              }

              // 添加对话截图到幻灯片（如果有）
              if (resultData.conversationScreenshot) {
                screenshots.value.push(resultData.conversationScreenshot)
              }

              addProgressLog(`${aiDisplayName}回复完成，耗时${resultData.elapsedTime}秒`, aiType)
              ElMessage.success(payload.message || `${aiDisplayName}回复完成`)
            }
          } else {
            // 处理错误（success=false）
            const targetAi = enabledAIs.value.find(ai => ai.name.toLowerCase().includes(aiType.toLowerCase()))
            if (targetAi) {
              targetAi.status = 'failed'
            }
            // 所有AI完成/失败后再关闭发送状态
            if (enabledAIs.value.every(ai => ai.status === 'completed' || ai.status === 'failed' || ai.status === 'error')) {
              taskStatus.value = 'failed'
              isSending.value = false
            }
            const errorMsg = payload.errorMessage || payload.message || '请求失败'
            addProgressLog('错误: ' + errorMsg, aiType)
            ElMessage.error(errorMsg)
          }
        }

        // 处理错误消息（AI_TASK_ERROR）
        if (messageType === 'AI_TASK_ERROR') {
          const aiType = payload.aiType || 'deepseek'
          const errorTitle = payload.errorTitle || '任务失败'
          const errorMessage = payload.errorMessage || payload.message || 'AI任务执行失败'
          const engineId = payload.engineId || '未知'

          // 更新对应AI的状态
          const targetAi = enabledAIs.value.find(ai =>
            ai.name.toLowerCase().includes(aiType.toLowerCase())
          )
          if (targetAi) {
            targetAi.status = 'error'
          }

          // 🔥 所有AI完成/失败后，才关闭发送状态
          if (enabledAIs.value.every(ai => ai.status === 'completed' || ai.status === 'failed' || ai.status === 'error')) {
            taskStatus.value = 'failed'
            isSending.value = false
          }

          // 添加错误日志到进度日志
          addProgressLog(`❌ ${errorTitle}`, aiType)
          addProgressLog(errorMessage, aiType)

          ElMessage({
            message: `${errorTitle}: ${errorMessage}`,
            type: 'error',
            duration: 8000,
            showClose: true
          })

          console.error('🚨 [AI任务错误]', {
            errorTitle,
            errorMessage,
            engineId,
            aiType,
            errorCode: payload.errorCode
          })
        }

        // ==========================================================================
        // 🤖 AIGC消息处理结束
        // ==========================================================================

        // 处理CONNECTED消息
        if (messageType === 'CONNECTED') {
          console.log('WebSocket连接确认:', message)
        }
      } catch (error) {
        console.error('处理WebSocket消息失败:', error)
      }
    }

    const addProgressLog = (content, aiType = 'deepseek') => {
      const logEntry = {
        content: content,
        timestamp: new Date(),
        aiType: aiType
      }

      // 添加到全局日志
      progressLogs.value.push(logEntry)

      // 🔥 同时添加到对应AI的日志列表（支持按AI区分显示）
      const targetAi = enabledAIs.value.find(ai =>
        ai.name.toLowerCase().includes(aiType.toLowerCase())
      )
      if (targetAi) {
        if (!targetAi.progressLogs) {
          targetAi.progressLogs = []
        }
        targetAi.progressLogs.push(logEntry)
      }

      console.log('📋 [进度日志]', aiType, ':', content)
    }

    // 🔥 数据存储已完全由后端Admin自动处理
    // Admin在以下时机自动存储到数据库：
    // 1. 收到前端请求时 - 存储初始请求信息
    // 2. Engine返回进度/截图时 - 实时更新数据
    // 3. Engine返回最终结果时 - 存储完整结果
    // 前端无需手动调用数据库API

    const formatTime = (timestamp) => {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      return date.toLocaleTimeString()
    }

    const getStatusText = (status) => {
      const statusMap = {
        pending: '等待中',
        running: '执行中',
        completed: '已完成',
        failed: '失败'
      }
      return statusMap[status] || status
    }

    const renderMarkdown = (content) => {
      if (!content) return ''
      return marked(content)
    }

    const showLargeImage = (url) => {
      currentLargeImage.value = url
      showImageDialog.value = true
    }

    const openShareUrl = (url) => {
      window.open(url, '_blank')
    }

    // 🔥 复制内容（从数据库获取，不使用DOM）
    const copyToClipboard = async (result) => {
      if (!result) {
        ElMessage.warning('无法获取复制内容')
        return
      }

      try {
        // 🔥 从数据库获取真实的draft_content文本
        // taskId应该是sessionId，而不是chatId
        const taskId = result.sessionId || currentChatId.value
        const aiName = result.aiName || 'deepseek'

        console.log('🔥 [复制] taskId:', taskId, 'aiName:', aiName)
        const response = await getDraftContent(taskId, aiName)

        if (response && response.data && response.data.content) {
          await navigator.clipboard.writeText(response.data.content)
          ElMessage.success('已复制到剪贴板')
        } else {
          ElMessage.warning('没有可复制的文本内容')
        }
      } catch (error) {
        console.error('复制失败:', error)
        ElMessage.error('复制失败，请重试')
      }
    }

    // 文件上传处理函数
    const handleFileUpload = () => {
      // 检查是否有任何已启用的AI开启了文件上传选项
      const hasFileUploadEnabled = Object.entries(aiStates.value).some(([aiId, state]) => {
        return state.enabled && state.options?.enableFileUpload
      })
      if (!hasFileUploadEnabled) {
        ElMessage.warning('请先为至少一个AI启用"上传图片"选项')
        return
      }
      uploadDialogVisible.value = true
    }

    const beforeUpload = (file) => {
      const isLt50M = file.size / 1024 / 1024 < 50
      if (!isLt50M) {
        ElMessage.error('文件大小不能超过 50MB!')
        return false
      }
      return true
    }

    const handleUploadSuccess = (response, file) => {
      console.log('📤 文件上传响应:', response)
      console.log('📤 响应类型检查 - code:', response.code, 'type:', typeof response.code)

      // 检查响应状态（支持code为数字或字符串）
      const code = parseInt(response.code)
      console.log('📤 转换后的code:', code)

      if (code === 200) {
        // 直接从响应对象中提取URL（不需要data层级）
        // 后端可能直接返回 url 或 fileName 字段
        const fileUrl = response.url || response.fileName

        console.log('📤 提取的URL:', fileUrl)

        if (fileUrl) {
          uploadedFileUrl.value = fileUrl
          ElMessage.success('文件上传成功')
          console.log('✅ 上传的文件URL已保存:', uploadedFileUrl.value)
        } else {
          ElMessage.error('文件上传失败: 返回的URL为空')
          console.error('❌ 响应中没有URL字段:', response)
        }
      } else {
        ElMessage.error('文件上传失败: ' + (response.msg || '未知错误'))
        console.error('❌ 上传失败，code不是200，响应:', response)
      }
    }

    const handleUploadError = (error) => {
      console.error('文件上传失败:', error)
      ElMessage.error('文件上传失败，请重试')
    }

    const confirmUpload = () => {
      if (uploadedFileUrl.value) {
        uploadDialogVisible.value = false
        ElMessage.success('文件已添加到消息中')
        console.log('确认上传，文件URL:', uploadedFileUrl.value)
      }
    }

    const saveToDraft = async () => {
      if (results.value.length === 0) {
        ElMessage.warning('没有可保存的内容')
        return
      }

      try {
        const result = results.value[0]
        // 🔥 后端已自动保存，前端只需要提示用户
        // 保存逻辑在后端EngineMessageRouter.saveToExtensionTable中自动完成
        // 优先保存截图URL，同时保存文本内容和hasScreenshot标记
        await addDraft({
          aiName: result.aiName,
          content: result.content,
          shareUrl: result.shareUrl
        })
        ElMessage.success('已保存到草稿库')
      } catch (error) {
        console.error('保存草稿失败:', error)
        ElMessage.error('保存草稿失败')
      }
    }

    const generateUUID = () => {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        const r = Math.random() * 16 | 0
        const v = c === 'x' ? r : (r & 0x3 | 0x8)
        return v.toString(16)
      })
    }

    // 🔥 自动加载最后一次会话历史
    const loadLastChat = async () => {
      try {
        historyLoading.value = true
        const res = await getChatHistory({ isAll: 0 })  // isAll=0 只获取最新一条

        console.log('📝 [自动加载] API响应:', res)

        // 🔥 处理分页结构：res.rows 或 res.data
        const dataList = res.rows || res.data || []

        if (res.code === 200 && dataList.length > 0) {
          const lastChat = dataList[0]
          console.log('📝 [自动加载] 最后一次会话:', lastChat)

          // 加载最后一次会话
          await loadHistoryItem(lastChat)
          console.log('✅ [自动加载] 已恢复最后一次会话')
        } else {
          // 没有历史记录，初始化为空状态
          console.log('📝 [自动加载] 无历史记录，等待用户创建新对话')
          currentChatId.value = null
          isNewChat.value = true
        }
      } catch (error) {
        console.error('自动加载最后一次会话失败:', error)
        // 失败时初始化为空状态
        currentChatId.value = null
        isNewChat.value = true
      } finally {
        historyLoading.value = false
      }
    }
    // 获取当前会话 sessionId
    const getCurrentSessionId = () => {
      if (sessionId) {
        return sessionId
      }

      if (results.value && results.value.length > 0) {
        const lastResult = results.value[results.value.length - 1]
        return lastResult.sessionId || ''
      }

      return ''
    }

    // ==================== 输出物生成 ====================
    /**
     * 生成当前会话的输出物
     * 设计说明：
     * 1. 依赖当前 sessionId，未选择会话时禁止生成
     * 2. 调用后端生成接口，并将结果填充到编辑弹窗中
     * 3. 生成成功后自动打开编辑对话框，支持用户二次修改
     */
    const handleGenerateOutput = async () => {
      const currentSessionId = getCurrentSessionId()

      // 未选中会话时直接拦截
      if (!currentSessionId) {
        ElMessage.error('当前没有可用的会话ID')
        return
      }

      try {
        const res = await generateOutputArtifact({
          sessionId: currentSessionId
        })

        if (res.code === 200) {
          const artifact = res.data.data

          // 将后端返回的输出物数据绑定到前端表单
          currentArtifactId.value = artifact.id || ''
          outputTitle.value = artifact.title || ''
          outputContent.value = artifact.content || ''
          outputDialogVisible.value = true

          ElMessage.success('输出物生成成功')
        } else {
          // 透传后端业务错误信息
          ElMessage.error(res.msg || res.data?.message || '生成输出物失败')
        }
      } catch (error) {
        ElMessage.error('生成输出物失败')
      }
    }


    // ==================== 输出物保存 ====================
    /**
     * 保存用户编辑后的输出物
     * 设计说明：
     * 1. 必须存在 sessionId 与 artifactId 才允许保存
     * 2. 保存成功后关闭弹窗，数据以服务端为准
     */
    const handleSaveOutputArtifact = async () => {
      const currentSessionId = getCurrentSessionId()

      // 会话校验
      if (!currentSessionId) {
        ElMessage.error('当前没有可用的会话ID')
        return
      }

      // 输出物存在性校验
      if (!currentArtifactId.value) {
        ElMessage.error('当前没有可保存的输出物')
        return
      }

      try {
        const res = await saveOutputArtifact({
          sessionId: currentSessionId,
          artifactId: currentArtifactId.value,
          title: outputTitle.value,
          content: outputContent.value
        })

        if (res.code === 200) {
          ElMessage.success('保存成功')

          // 保存成功后关闭编辑弹窗
          outputDialogVisible.value = false
        } else {
          ElMessage.error(res.msg || '保存失败')
        }
      } catch (error) {
        ElMessage.error('保存失败')
      }
    }


    // ==================== Markdown 导出 ====================
    /**
     * 导出当前会话输出物为 Markdown 文件
     * 设计说明：
     * 1. 后端成功返回为二进制流（Markdown文件）
     * 2. 后端失败返回为 JSON（需手动解析 Blob）
     * 3. 通过 Content-Type 区分成功/失败，避免误下载错误内容
     */
    const handleExportMarkdown = async () => {
      const currentSessionId = getCurrentSessionId()

      // 会话校验
      if (!currentSessionId) {
        ElMessage.error('当前没有可用的会话ID')
        return
      }

      try {
        const response = await exportOutputMarkdown(currentSessionId)

        // 根据响应类型判断结果（关键逻辑）
        const contentType = response.headers['content-type']

        // ===== 失败场景：返回 JSON =====
        if (contentType && contentType.includes('application/json')) {
          const text = await new Blob([response.data]).text()
          const json = JSON.parse(text)

          ElMessage.error(json.msg || json.message || '导出失败')
          return
        }

        // ===== 成功场景：返回 Markdown 文件 =====
        const blob = new Blob([response.data], { type: 'text/markdown;charset=utf-8' })
        const url = window.URL.createObjectURL(blob)

        const a = document.createElement('a')
        a.href = url
        a.download = `fubangshou-output-${currentSessionId}.md`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)

        window.URL.revokeObjectURL(url)

        ElMessage.success('Markdown 导出成功')
      } catch (error) {
        ElMessage.error('导出失败')
      }
    }


    // ==================== Webhook 推送 ====================
    /**
     * 推送当前会话输出物到指定 Webhook 地址
     * 设计说明：
     * 1. 必须依赖当前 sessionId，未选择会话时禁止推送
     * 2. 用户手动输入 webhookUrl，空值时直接终止操作
     * 3. 推送内容统一由后端生成（支持 Markdown / JSON）
     * 4. 成功仅提示一次，失败时透传后端或网络异常信息
     */
    const handlePushWebhook = async () => {
      const currentSessionId = getCurrentSessionId()

      // 会话校验
      if (!currentSessionId) {
        ElMessage.error('当前没有可用的会话ID')
        return
      }

      // 获取用户输入的 Webhook 地址
      const webhookUrl = window.prompt('请输入Webhook地址')
      if (!webhookUrl) {
        return
      }

      try {
        const res = await pushOutputWebhook({
          sessionId: currentSessionId,
          format: 'md',
          webhookUrl
        })

        // 成功场景
        if (res.code === 200 && res.data && res.data.success) {
          ElMessage.success('已推送')
        }
      } catch (error) {
        // 错误提示交给全局 request.js 统一处理，这里不再重复弹窗
      }
    }
    // 生命周期
    onMounted(() => {
      // 页面加载时恢复登录状态
      restoreLoginStatusFromStorage()

      // 初始化AI状态
      initAiStates()

      // 验证主机ID
      if (!checkHostId()) {
        return  // 如果没有主机ID，不继续初始化
      }

      connectWebSocket(() => {
        // WebSocket连接成功后，加载最后一次会话
        loadLastChat()
      })
    })

    onUnmounted(() => {
      if (websocket) {
        websocket.close()
      }
    })

    return {
      // 数据
      activeCollapses,
      historyDrawerVisible,
      historyLoading,
      chatHistory,
      hostIdDialogVisible,
      promptInput,
      isSending,
      taskStarted,
      taskStatus,
      progressLogs,
      screenshots,
      results,
      showImageDialog,
      currentLargeImage,
      loginDialogVisible,
      loginLoading,
      loginStatusText,
      qrCodeUrl,
      canSend,
      screenshotCarousel,
      // 🔥 动态AI配置
      aiServices,
      aiStates,
      currentLoginServiceName,
      // 🔥 新增：上下文复用相关
      enabledAIs,
      groupedHistory,
      allTasksCompleted,
      hasRunningTasks,
      // 方法
      createNewChat,
      showHistoryDrawer,
      loadHistoryItem,
      toggleAiOption,
      handleServiceLogin,
      goToProfile,
      handleDeepSeekLogin,
      sendPrompt,
      formatTime,
      getStatusText,
      renderMarkdown,
      showLargeImage,
      openShareUrl,
      copyToClipboard,
      handleAiToggle,
      handleFileUpload,
      saveToDraft,
      // 🔥 文件上传相关
      uploadDialogVisible,
      uploadRef,
      fileList,
      uploadedFileUrl,
      uploadAction,
      uploadHeaders,
      beforeUpload,
      handleUploadSuccess,
      handleUploadError,
      confirmUpload,
      // 🔥 新增：历史记录和AI管理方法
      toggleHistoryExpansion,
      formatHistoryTime,
      toggleAiExpand,
      getLogDotClass,
      // 输出物相关
      currentArtifactId,
      outputTitle,
      outputContent,
      outputDialogVisible,
      handleGenerateOutput,
      handleSaveOutputArtifact,
      handleExportMarkdown,
      handlePushWebhook
    }
  }
}
</script>

<style lang="scss" scoped>
.ai-management-platform {
  padding: 0;
}

.top-nav {
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  position: sticky;
  top: 0;
  z-index: 100;

  .nav-container {
    max-width: 1400px;
    margin: 0 auto;
    padding: 16px 32px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .logo-area {
    display: flex;
    align-items: center;
    gap: 12px;

    .logo-icon {
      color: #409eff;
    }

    .platform-title {
      font-size: 20px;
      font-weight: 600;
      color: #303133;
      margin: 0;
    }
  }

  .nav-buttons {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.main-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 32px;

  .content-container {
    background: transparent;
  }

  .custom-collapse {
    border: none;
    background: transparent;

    :deep(.el-collapse-item) {
      margin-bottom: 20px;
      background: #ffffff;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
      border: 1px solid #e4e7ed;
      transition: all 0.3s;

      &:hover {
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
      }
    }

    :deep(.el-collapse-item__header) {
      height: 56px;
      line-height: 56px;
      padding: 0 24px;
      background: #fafbfc;
      border-bottom: 1px solid #e4e7ed;
      font-size: 15px;
      font-weight: 500;
      color: #303133;

      &:hover {
        background: #f5f7fa;
      }
    }

    :deep(.el-collapse-item__wrap) {
      border: none;
    }

    :deep(.el-collapse-item__content) {
      padding: 0;
    }
  }
}

.ai-config-header {
  display: flex;
  align-items: center;
  width: 100%;
  font-weight: 500;
  color: #303133;
}

.ai-selection-section {
  padding: 20px;
  background: #ffffff;
}

.ai-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;

  @media (min-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
  }

  @media (min-width: 1024px) {
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
  }

  @media (min-width: 1400px) {
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
  }

  @media (min-width: 1600px) {
    grid-template-columns: repeat(5, 1fr);
  }
}

.ai-card {
  position: relative;
  border-radius: 16px;
  transition: all 0.3s;
  border: 2px solid #e4e7ed;
  overflow: hidden;

  &.modern-card {
    background: #ffffff;
  }

  &.ai-card-enabled {
    border-color: #409eff;
    box-shadow: 0 4px 20px rgba(64, 158, 255, 0.15);

    :deep(.el-card__body) {
      background: linear-gradient(135deg, #f0f9ff 0%, #ffffff 100%);
    }
  }

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.12);
  }

  &.ai-card-not-logged {
    opacity: 0.75;
    filter: grayscale(0.3);
  }

  :deep(.el-card__body) {
    padding: 16px;
  }

  .card-login-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(255, 255, 255, 0.95);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10;
    border-radius: 16px;

    .card-login-message {
      text-align: center;

      .el-icon {
        font-size: 28px;
        color: #e6a23c;
        margin-bottom: 8px;
      }

      span {
        display: block;
        color: #909399;
        margin-bottom: 8px;
        font-size: 13px;
      }
    }
  }

  .ai-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .ai-left {
      display: flex;
      align-items: center;
      gap: 10px;
      flex: 1;
      min-width: 0;

      .ai-avatar {
        width: 40px;
        height: 40px;
        border-radius: 10px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
        box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
        flex-shrink: 0;
        overflow: hidden;

        .ai-avatar-img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .el-icon {
          font-size: 20px;
        }

        &.deepseek-avatar {
          background: linear-gradient(135deg, #409eff 0%, #3a8ee6 100%);
        }
      }

      .ai-info {
        flex: 1;
        min-width: 0;

        .ai-name {
          font-weight: 600;
          font-size: 14px;
          color: #303133;
          margin-bottom: 2px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .ai-description {
          font-size: 11px;
          color: #909399;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }
    }

    .ai-status {
      flex-shrink: 0;
      margin-left: 8px;
    }
  }

  .ai-options {
    margin-top: 12px;

    .options-divider {
      height: 1px;
      background: linear-gradient(90deg, transparent 0%, #e4e7ed 50%, transparent 100%);
      margin-bottom: 10px;
    }

    .ai-capabilities {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;

      .capability-tag {
        cursor: pointer;
        transition: all 0.3s;
        padding: 6px 12px;
        font-size: 12px;
        border-radius: 6px;

        &.tag-clickable:hover {
          transform: translateY(-1px);
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
        }

        &.tag-disabled {
          cursor: not-allowed;
          opacity: 0.5;
        }

        .el-icon {
          margin-right: 3px;
          font-size: 12px;
        }
      }
    }
  }
}

.prompt-input-section {
  padding: 28px;
  background: #ffffff;

  &.modern-input {
    background: #ffffff;
  }

  .input-wrapper {
    margin-bottom: 20px;

    .prompt-input {
      &.dynamic-textarea {
        :deep(.el-textarea__inner) {
          border-radius: 12px;
          border: 2px solid #dcdfe6;
          transition: all 0.3s;
          font-size: 15px;
          line-height: 1.6;
          padding: 12px 16px;
          background: #fafbfc;
          max-height: calc(1.6em * 3 + 24px); // 3行高度 + padding
          overflow-y: auto;
          resize: none;

          &:hover {
            border-color: #c0c4cc;
            background: #ffffff;
          }

          &:focus {
            border-color: #409eff;
            background: #ffffff;
            box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.08);
          }

          &::placeholder {
            color: #a8abb2;
          }

          // 自定义滚动条
          &::-webkit-scrollbar {
            width: 6px;
          }

          &::-webkit-scrollbar-thumb {
            background-color: #dcdfe6;
            border-radius: 3px;

            &:hover {
              background-color: #c0c4cc;
            }
          }

          &::-webkit-scrollbar-track {
            background-color: transparent;
          }
        }
      }
    }
  }

  .prompt-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .footer-left {
      display: flex;
      align-items: center;
      gap: 16px;

      .upload-button {
        border-radius: 6px;

        .el-icon {
          margin-right: 4px;
        }
      }

      .word-count {
        display: flex;
        align-items: center;
        gap: 4px;
        color: #909399;
        font-size: 13px;

        .el-icon {
          font-size: 14px;
        }
      }
    }

    .send-button {
      border-radius: 10px;
      padding: 13px 36px;
      font-weight: 500;
      font-size: 15px;
      box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
      transition: all 0.3s;

      &:hover:not(:disabled) {
        transform: translateY(-2px);
        box-shadow: 0 6px 20px rgba(64, 158, 255, 0.35);
      }

      &:active:not(:disabled) {
        transform: translateY(0);
      }

      .el-icon {
        margin-right: 6px;
      }
    }
  }
}

.execution-status-section {
  max-width: 1400px;
  margin: 24px auto 0;
  padding: 0 32px;

  .task-flow-card,
  .screenshots-card {
    height: 800px;
    border-radius: 12px;
    border: 1px solid #e4e7ed;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);

    :deep(.el-card__header) {
      background: #fafbfc;
      border-bottom: 1px solid #e4e7ed;
      padding: 16px 20px;
    }

    :deep(.el-card__body) {
      padding: 20px;
    }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .task-flow {
    .task-item {
      background: #fff;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      padding: 12px;
      margin-bottom: 15px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
      transition: box-shadow 0.3s;

      &:hover {
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      }

      .task-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 8px 10px;
        background: #f5f7fa;
        border-radius: 6px;
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
          background: #ebeef5;
        }

        .header-left {
          display: flex;
          align-items: center;
          gap: 10px;

          .expand-icon {
            transition: transform 0.3s;
            color: #909399;

            &.is-expanded {
              transform: rotate(90deg);
            }
          }

          .ai-name {
            font-weight: 500;
            font-size: 15px;
            color: #303133;
          }
        }

        .header-right {
          display: flex;
          align-items: center;
          gap: 8px;

          .status-text {
            font-size: 14px;
            color: #606266;
          }
        }
      }

      .progress-timeline {
        margin-top: 15px;
        padding: 10px 15px;
        background: #fafafa;
        border-radius: 6px;
        max-height: 350px;
        overflow-y: auto;

        .progress-item {
          display: flex;
          gap: 15px;
          margin-bottom: 15px;
          position: relative;

          .progress-dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #409eff;
            flex-shrink: 0;
            margin-top: 5px;
          }

          .progress-content {
            .progress-time {
              font-size: 12px;
              color: #909399;
            }

            .progress-text {
              font-size: 14px;
              color: #303133;
            }
          }
        }
      }
    }
  }

  .screenshots {
    height: 720px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f5f7fa;
    border-radius: 8px;
    padding: 10px;

    :deep(.el-carousel) {
      width: 100%;
      height: 100%;
    }

    :deep(.el-carousel__container) {
      height: 680px !important;
    }

    :deep(.el-carousel__item) {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #fff;
      border-radius: 8px;
    }

    .screenshot-image {
      max-width: 100%;
      max-height: 660px;
      width: auto;
      height: auto;
      object-fit: contain;
      cursor: pointer;
      transition: transform 0.3s;
      border-radius: 4px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    }

    .screenshot-image:hover {
      transform: scale(1.02);
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
    }

    .no-screenshots {
      text-align: center;
      color: #909399;

      .el-icon {
        margin-bottom: 10px;
      }
    }
  }
}

.results-section {
  max-width: 1400px;
  margin: 24px auto 0;
  padding: 0 32px;

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .section-title {
      font-size: 20px;
      font-weight: 600;
      margin: 0;
      color: #303133;
    }
  }

  >.el-card {
    border-radius: 12px;
    border: 1px solid #e4e7ed;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);

    :deep(.el-card__body) {
      padding: 28px;
    }
  }

  .result-content {
    &+.result-content {
      margin-top: 32px;
      padding-top: 32px;
      border-top: 1px solid #e4e7ed;
    }

    .result-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      padding-bottom: 16px;
      border-bottom: 2px solid #f0f2f5;

      .result-title {
        font-weight: 600;
        font-size: 18px;
        color: #303133;
        display: flex;
        align-items: center;
        gap: 8px;

        &::before {
          content: '';
          display: inline-block;
          width: 4px;
          height: 20px;
          background: linear-gradient(135deg, #409eff 0%, #3a8ee6 100%);
          border-radius: 2px;
        }
      }

      .result-actions {
        display: flex;
        gap: 12px;
      }
    }

    .result-screenshot {
      text-align: center;
      background: #fafbfc;
      padding: 24px;
      border-radius: 12px;
      max-height: 600px;
      overflow-y: auto;
      padding-right: 10px;

      /* 自定义滚动条样式 */
      &::-webkit-scrollbar {
        width: 8px;
      }

      &::-webkit-scrollbar-track {
        background: #f1f1f1;
        border-radius: 4px;
      }

      &::-webkit-scrollbar-thumb {
        background: #c1c1c1;
        border-radius: 4px;

        &:hover {
          background: #a1a1a1;
        }
      }

      .result-screenshot-image {
        max-width: 100%;
        height: auto;
        border-radius: 12px;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
        cursor: pointer;
        transition: all 0.3s;
        border: 1px solid #e4e7ed;

        &:hover {
          transform: scale(1.01);
          box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
        }
      }

      .screenshot-tip {
        margin-top: 16px;
        color: #909399;
        font-size: 13px;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;

        &::before {
          content: "💡";
        }
      }
    }

    .no-result {
      text-align: center;
      padding: 60px 20px;
      color: #909399;

      .el-icon {
        margin-bottom: 16px;
        color: #dcdfe6;
      }

      p {
        font-size: 14px;
        margin: 0;
      }
    }

    .markdown-content {
      line-height: 1.8;
      max-height: 600px;
      overflow-y: auto;
      padding-right: 10px;

      /* 自定义滚动条样式 */
      &::-webkit-scrollbar {
        width: 8px;
      }

      &::-webkit-scrollbar-track {
        background: #f1f1f1;
        border-radius: 4px;
      }

      &::-webkit-scrollbar-thumb {
        background: #c1c1c1;
        border-radius: 4px;

        &:hover {
          background: #a8a8a8;
        }
      }

      :deep(h1),
      :deep(h2),
      :deep(h3) {
        margin-top: 20px;
        margin-bottom: 10px;
      }

      :deep(p) {
        margin-bottom: 10px;
      }

      :deep(code) {
        background: #f5f7fa;
        padding: 2px 6px;
        border-radius: 4px;
      }

      :deep(pre) {
        background: #f5f7fa;
        padding: 15px;
        border-radius: 8px;
        overflow-x: auto;
      }
    }
  }
}

.history-content {
  padding: 15px;

  .history-loading {
    text-align: center;
    padding: 40px;
    color: #909399;
  }

  // 🔥 历史记录分组样式（参考旧项目cube-ui）
  .history-group {
    margin-bottom: 20px;

    .history-date {
      font-size: 14px;
      font-weight: 600;
      color: #606266;
      padding: 10px 0;
      border-bottom: 1px solid #ebeef5;
      margin-bottom: 10px;
    }

    .history-list {
      .history-item {
        margin-bottom: 8px;

        .history-parent {
          padding: 12px;
          border-radius: 8px;
          cursor: pointer;
          transition: background 0.3s;

          &:hover {
            background: #f5f7fa;
          }

          .history-header {
            display: flex;
            align-items: flex-start;
            gap: 10px;

            .expand-arrow {
              flex-shrink: 0;
              color: #909399;
              transition: transform 0.3s;
              margin-top: 3px;

              &.is-expanded {
                transform: rotate(90deg);
              }
            }

            .chat-icon {
              flex-shrink: 0;
              color: #909399;
              margin-top: 3px;
            }

            .history-content-wrapper {
              flex: 1;
              min-width: 0;

              .history-prompt {
                font-size: 14px;
                color: #303133;
                margin-bottom: 6px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
              }

              .history-meta {
                font-size: 12px;
                color: #909399;
                display: flex;
                align-items: center;
                gap: 4px;
                flex-wrap: wrap;

                .history-separator {
                  color: #c0c4cc;
                }

                .history-chatid {
                  color: #409eff;
                }

                .children-count {
                  color: #67c23a;
                }
              }
            }
          }
        }

        // 🔥 子记录（各轮对话）
        .history-children {
          margin-left: 30px;
          border-left: 2px solid #ebeef5;
          padding-left: 15px;
          margin-top: 8px;

          .history-child-item {
            padding: 10px;
            border-radius: 6px;
            cursor: pointer;
            transition: background 0.3s;
            margin-bottom: 6px;

            &:hover {
              background: #f5f7fa;
            }

            .history-child-content {
              .child-index {
                display: inline-block;
                font-size: 12px;
                color: #409eff;
                background: #ecf5ff;
                padding: 2px 8px;
                border-radius: 4px;
                margin-bottom: 6px;
              }

              .history-prompt {
                font-size: 13px;
                color: #606266;
                margin-bottom: 4px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
              }

              .history-meta {
                font-size: 12px;
                color: #909399;

                .ai-count {
                  color: #67c23a;
                }
              }
            }
          }
        }
      }
    }
  }

  .history-empty {
    text-align: center;
    padding: 40px;
    color: #909399;

    .el-icon {
      font-size: 48px;
      margin-bottom: 10px;
    }
  }
}

.image-dialog {
  .large-image {
    max-width: 100%;
    max-height: 80vh;
    object-fit: contain;
  }
}

.login-dialog-content {
  text-align: center;
  padding: 20px;

  .login-loading {
    margin-bottom: 20px;
  }

  .qrcode-container {
    .qrcode-image {
      max-width: 300px;
      border: 1px solid #ebeef5;
      border-radius: 8px;
    }

    p {
      margin-top: 15px;
      color: #606266;
    }
  }
}
</style>
