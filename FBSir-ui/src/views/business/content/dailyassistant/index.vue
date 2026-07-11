<template>
  <div class="daily-assistant-container">
    <el-row :gutter="20">
      <!-- 左侧：创建和文章列表 -->
      <el-col :span="10" :xs="24">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Document /></el-icon>
                日更助手
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
                
                <!-- 公众号配置检查按钮 -->
                <el-button
                  v-if="!officeAccountConfigured"
                  type="warning"
                  size="small"
                  @click="goToOfficeAccountConfig"
                >
                  <el-icon><WarningFilled /></el-icon>
                  未配置公众号
                </el-button>
                <el-button
                  v-else
                  :type="officeAccountValid ? 'success' : 'danger'"
                  size="small"
                  @click="verifyOfficeAccount"
                  :loading="verifyingOfficeAccount"
                >
                  <el-icon v-if="!verifyingOfficeAccount">
                    <component :is="officeAccountValid ? 'CircleCheck' : 'CircleClose'" />
                  </el-icon>
                  {{ officeAccountValid ? '公众号可用' : '验证公众号' }}
                </el-button>
              </div>
            </div>
          </template>

          <!-- 创建文章 -->
          <div class="create-section">
            <el-input
              v-model="articleTitle"
              placeholder="请输入公众号文章标题"
              clearable
              :disabled="creating"
              @keyup.enter="handleCreate"
            >
              <template #append>
                <el-button
                  :loading="creating"
                  @click="handleCreate"
                  :disabled="!articleTitle.trim() || selectedModels.length === 0"
                  v-hasPermi="['business:daily:add']"
                  style="background-color: #409EFF; color: white; border-color: #409EFF;"
                >
                  <el-icon><Plus /></el-icon>
                  创建
                </el-button>
              </template>
            </el-input>
            
            <!-- 模型选择 -->
            <div class="model-selection">
              <span class="selection-label">选择参与的模型：</span>
              <el-checkbox-group v-model="selectedModels">
                <el-checkbox :value="1" label="混元大模型" />
                <el-checkbox :value="2" label="DeepSeek" />
                <el-checkbox :value="3" label="精调模型" />
              </el-checkbox-group>
            </div>
            
            <div class="tip-text">
              <el-icon><InfoFilled /></el-icon>
              选择的模型将生成文章初稿，再由混元优化模型整合优化
            </div>
          </div>

          <!-- 文章列表 -->
          <div class="article-list">
            <div class="list-header">
              <span>我的文章 <el-tag v-if="totalArticles > 0" size="small" type="info">{{ totalArticles }}</el-tag></span>
              <el-button
                link
                size="small"
                @click="refreshArticles"
                :loading="loading"
              >
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>

            <el-scrollbar height="500px" ref="scrollbarRef" @scroll="handleScroll">
              <div
                v-for="article in articles"
                :key="article.id"
                class="article-item"
                :class="{ 
                  active: selectedArticle && selectedArticle.id === article.id,
                  'article-failed': article.processStatus === 2
                }"
                @click="selectArticle(article)"
              >
                <div class="article-header">
                  <span class="article-title">{{ article.articleTitle }}</span>
                  <el-tag
                    :type="getStatusType(article.processStatus)"
                    size="small"
                  >
                    {{ getStatusText(article.processStatus) }}
                  </el-tag>
                </div>
                <!-- 失败时显示错误信息 -->
                <div v-if="article.processStatus === 2" class="error-msg">
                  <el-icon><WarningFilled /></el-icon>
                  {{ article.errorMessage || '生成失败' }}
                </div>
                <div class="article-meta">
                  <span class="time">{{ formatTime(article.createTime) }}</span>
                  <div class="meta-actions">
                    <!-- 失败时显示重试按钮 -->
                    <el-button
                      v-if="article.processStatus === 2"
                      type="primary"
                      size="small"
                      text
                      @click.stop="handleRetry(article)"
                    >
                      <el-icon><RefreshRight /></el-icon>
                      重试
                    </el-button>
                    <el-button
                      type="danger"
                      size="small"
                      text
                      @click.stop="handleDelete(article)"
                      v-hasPermi="['business:daily:remove']"
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
              <div v-if="!hasMore && articles.length > 0" class="no-more-tip">
                <el-text type="info" size="small">没有更多了</el-text>
              </div>

              <el-empty
                v-if="!loading && articles.length === 0"
                description="暂无文章，快来创建第一篇文章吧！"
                :image-size="120"
              />
            </el-scrollbar>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：文章内容展示 -->
      <el-col :span="14" :xs="24">
        <el-card class="box-card content-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Reading /></el-icon>
                文章内容
              </span>
            </div>
          </template>

          <div v-if="selectedArticle" class="content-section">
            <el-tabs v-model="activeTab" type="card">
              <!-- 优化后的内容 -->
              <el-tab-pane label="优化后内容" name="optimized">
                <div class="content-wrapper">
                  <div class="content-header">
                    <h3>优化后的文章</h3>
                    <div class="action-buttons">
                      <el-button
                        v-if="selectedArticle.optimizedContent"
                        type="primary"
                        size="small"
                        @click="copyContent(selectedArticle.optimizedContent)"
                      >
                        <el-icon><CopyDocument /></el-icon>
                        复制内容
                      </el-button>
                      <el-button
                        v-if="selectedArticle.optimizedContent"
                        type="success"
                        size="small"
                        @click="handleSmartLayout(selectedArticle.optimizedContent)"
                        v-hasPermi="['business:daily:layout']"
                      >
                        <el-icon><Reading /></el-icon>
                        智能排版
                      </el-button>
                      <el-button
                        v-if="selectedArticle.optimizedContent"
                        type="warning"
                        size="small"
                        :loading="publishing"
                        :disabled="!officeAccountConfigured || !officeAccountValid"
                        @click="handlePublishToWechat('optimized')"
                        v-hasPermi="['business:daily:publish']"
                      >
                        <el-icon><Upload /></el-icon>
                        投递到公众号
                      </el-button>
                    </div>
                  </div>
                  <el-scrollbar height="600px">
                    <div v-if="selectedArticle.optimizedContent" class="content-text">
                      {{ selectedArticle.optimizedContent }}
                    </div>
                    <div v-else-if="selectedArticle.processStatus === 0" class="generating-status">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <p>正在整合优化中...</p>
                      <p class="tip">已生成的模型内容可在其他标签页查看</p>
                    </div>
                    <el-empty
                      v-else
                      description="等待智能体优化..."
                      :image-size="100"
                    />
                  </el-scrollbar>
                </div>
              </el-tab-pane>

              <!-- 混元大模型内容 -->
              <el-tab-pane
                v-if="isModelSelected(selectedArticle, 1)"
                name="model1"
              >
                <template #label>
                  <span>
                    {{ selectedArticle.model1Name || '混元大模型' }}
                    <el-tag v-if="selectedArticle.model1Content" type="success" size="small" style="margin-left: 5px;">已生成</el-tag>
                    <el-tag v-else type="info" size="small" style="margin-left: 5px;">生成中</el-tag>
                  </span>
                </template>
                <div class="content-wrapper">
                  <div class="content-header">
                    <h3>{{ selectedArticle.model1Name || '混元大模型' }}</h3>
                    <div class="action-buttons">
                      <el-button
                        v-if="selectedArticle.model1Content"
                        type="primary"
                        size="small"
                        @click="copyContent(selectedArticle.model1Content)"
                      >
                        <el-icon><CopyDocument /></el-icon>
                        复制内容
                      </el-button>
                      <el-button
                        v-if="selectedArticle.model1Content"
                        type="success"
                        size="small"
                        @click="handleSmartLayout(selectedArticle.model1Content)"
                        v-hasPermi="['business:daily:layout']"
                      >
                        <el-icon><Reading /></el-icon>
                        智能排版
                      </el-button>
                      <el-button
                        v-if="selectedArticle.model1Content"
                        type="warning"
                        size="small"
                        :loading="publishing"
                        :disabled="!officeAccountConfigured || !officeAccountValid"
                        @click="handlePublishToWechat('model1')"
                        v-hasPermi="['business:daily:publish']"
                      >
                        <el-icon><Upload /></el-icon>
                        投递到公众号
                      </el-button>
                    </div>
                  </div>
                  <el-scrollbar height="600px">
                    <div v-if="selectedArticle.model1Content" class="content-text">
                      {{ selectedArticle.model1Content }}
                    </div>
                    <div v-else class="generating-status">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <p>正在生成中...</p>
                    </div>
                  </el-scrollbar>
                </div>
              </el-tab-pane>

              <!-- DeepSeek内容 -->
              <el-tab-pane
                v-if="isModelSelected(selectedArticle, 2)"
                name="model2"
              >
                <template #label>
                  <span>
                    {{ selectedArticle.model2Name || 'DeepSeek' }}
                    <el-tag v-if="selectedArticle.model2Content" type="success" size="small" style="margin-left: 5px;">已生成</el-tag>
                    <el-tag v-else type="info" size="small" style="margin-left: 5px;">生成中</el-tag>
                  </span>
                </template>
                <div class="content-wrapper">
                  <div class="content-header">
                    <h3>{{ selectedArticle.model2Name || 'DeepSeek' }}</h3>
                    <div class="action-buttons">
                      <el-button
                        v-if="selectedArticle.model2Content"
                        type="primary"
                        size="small"
                        @click="copyContent(selectedArticle.model2Content)"
                      >
                        <el-icon><CopyDocument /></el-icon>
                        复制内容
                      </el-button>
                      <el-button
                        v-if="selectedArticle.model2Content"
                        type="success"
                        size="small"
                        @click="handleSmartLayout(selectedArticle.model2Content)"
                        v-hasPermi="['business:daily:layout']"
                      >
                        <el-icon><Reading /></el-icon>
                        智能排版
                      </el-button>
                      <el-button
                        v-if="selectedArticle.model2Content"
                        type="warning"
                        size="small"
                        :loading="publishing"
                        :disabled="!officeAccountConfigured || !officeAccountValid"
                        @click="handlePublishToWechat('model2')"
                        v-hasPermi="['business:daily:publish']"
                      >
                        <el-icon><Upload /></el-icon>
                        投递到公众号
                      </el-button>
                    </div>
                  </div>
                  <el-scrollbar height="600px">
                    <div v-if="selectedArticle.model2Content" class="content-text">
                      {{ selectedArticle.model2Content }}
                    </div>
                    <div v-else class="generating-status">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <p>正在生成中...</p>
                    </div>
                  </el-scrollbar>
                </div>
              </el-tab-pane>

              <!-- 大模型3内容 -->
              <el-tab-pane
                v-if="isModelSelected(selectedArticle, 3)"
                name="model3"
              >
                <template #label>
                  <span>
                    {{ selectedArticle.model3Name || '精调模型' }}
                    <el-tag v-if="selectedArticle.model3Content" type="success" size="small" style="margin-left: 5px;">已生成</el-tag>
                    <el-tag v-else type="info" size="small" style="margin-left: 5px;">生成中</el-tag>
                  </span>
                </template>
                <div class="content-wrapper">
                  <div class="content-header">
                    <h3>{{ selectedArticle.model3Name || '精调模型' }}</h3>
                    <div class="action-buttons">
                      <el-button
                        v-if="selectedArticle.model3Content"
                        type="primary"
                        size="small"
                        @click="copyContent(selectedArticle.model3Content)"
                      >
                        <el-icon><CopyDocument /></el-icon>
                        复制内容
                      </el-button>
                      <el-button
                        v-if="selectedArticle.model3Content"
                        type="success"
                        size="small"
                        @click="handleSmartLayout(selectedArticle.model3Content)"
                        v-hasPermi="['business:daily:layout']"
                      >
                        <el-icon><Reading /></el-icon>
                        智能排版
                      </el-button>
                      <el-button
                        v-if="selectedArticle.model3Content"
                        type="warning"
                        size="small"
                        :loading="publishing"
                        :disabled="!officeAccountConfigured || !officeAccountValid"
                        @click="handlePublishToWechat('model3')"
                        v-hasPermi="['business:daily:publish']"
                      >
                        <el-icon><Upload /></el-icon>
                        投递到公众号
                      </el-button>
                    </div>
                  </div>
                  <el-scrollbar height="600px">
                    <div v-if="selectedArticle.model3Content" class="content-text">
                      {{ selectedArticle.model3Content }}
                    </div>
                    <div v-else class="generating-status">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <p>正在生成中...</p>
                    </div>
                  </el-scrollbar>
                </div>
              </el-tab-pane>
            </el-tabs>

            <!-- 错误信息 -->
            <el-alert
              v-if="selectedArticle.processStatus === 2 && selectedArticle.errorMessage"
              type="error"
              :title="selectedArticle.errorMessage"
              :closable="false"
              style="margin-top: 10px"
            />
          </div>

          <el-empty
            v-else
            description="请选择一篇文章查看内容"
            :image-size="150"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- 智能排版对话框 -->
    <el-dialog
      v-model="layoutDialogVisible"
      title="智能排版（腾讯元器）"
      width="80%"
      :close-on-click-modal="false"
    >
      <el-tabs v-model="layoutActiveTab">
        <el-tab-pane label="原文内容" name="original">
          <el-scrollbar height="500px">
            <div class="layout-content">
              {{ layoutContent }}
            </div>
          </el-scrollbar>
        </el-tab-pane>

        <el-tab-pane label="排版后内容" name="layouted" :disabled="!layoutedContent">
          <div class="layout-actions" style="margin-bottom: 15px;">
            <el-button
              v-if="layoutedContent"
              type="primary"
              size="small"
              @click="copyLayoutedContent"
            >
              <el-icon><CopyDocument /></el-icon>
              复制排版内容
            </el-button>
            <el-button
              v-if="layoutedContent && selectedArticle"
              type="success"
              size="small"
              :loading="publishing"
              style="margin-left: 10px;"
              :disabled="!officeAccountConfigured || !officeAccountValid"
              @click="handlePublishToWechat('layout')"
            >
              <el-icon><Upload /></el-icon>
              投递到公众号
            </el-button>
          </div>
          <el-scrollbar height="500px">
            <div class="layout-content" v-html="layoutedContent"></div>
          </el-scrollbar>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="layoutDialogVisible = false">关闭</el-button>
        <el-button 
          type="primary" 
          @click="startLayout" 
          :loading="layouting"
          :disabled="layouting || !layoutContent"
        >
          {{ layouting ? '排版中...' : '开始排版' }}
        </el-button>
      </template>
    </el-dialog>

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
            placeholder="自定义名称，如：文章优化助手"
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
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import useUserStore from '@/store/modules/user'
import {
  Document,
  Plus,
  InfoFilled,
  Refresh,
  Delete,
  Reading,
  CopyDocument,
  Setting,
  Upload,
  WarningFilled,
  RefreshRight,
  Loading,
  CircleCheck,
  CircleClose
} from '@element-plus/icons-vue'
import {
  getMyArticles,
  createAndOptimize,
  delDailyArticle,
  layoutArticleNew as layoutArticle
} from '@/api/business/content/dailyassistant/dailyArticle'
import {
  getMyConfig,
  addYuanqiConfig,
  updateYuanqiConfig
} from '@/api/business/content/dailyassistant/yuanqiConfig'
import {
  publishToWechat,
  getMyOfficeAccount,
  verifyConfig
} from '@/api/business/content/dailyassistant/officeAccount'
import { useRouter } from 'vue-router'

// 获取 Pinia store
const userStore = useUserStore()
const router = useRouter()

// 数据
const articleTitle = ref('')
const articles = ref([])
const selectedArticle = ref(null)
const loading = ref(false)
const loadingMore = ref(false)
const creating = ref(false)
const activeTab = ref('optimized')
const selectedModels = ref([1, 2, 3])
const scrollbarRef = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)
const totalArticles = ref(0)
const hasMore = ref(true)
const publishing = ref(false)

// 智能体配置相关
const agentConfigured = ref(false) // 是否已配置智能体

// 公众号配置相关
const officeAccountConfigured = ref(false) // 是否已配置公众号
const officeAccountValid = ref(false) // 公众号配置是否有效
const verifyingOfficeAccount = ref(false) // 正在验证公众号

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

// 排版相关
const layoutDialogVisible = ref(false)
const layoutContent = ref('')
const layoutedContent = ref('')
const layouting = ref(false)
const layoutActiveTab = ref('original')

const configRules = {
  agentId: [{ required: true, message: '请输入智能体ID', trigger: 'blur' }],
  agentName: [{ required: true, message: '请输入智能体名称', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入API密钥', trigger: 'blur' }],
  apiEndpoint: [{ required: true, message: '请输入API端点URL', trigger: 'blur' }]
}

// 加载文章列表（分页）
const loadArticles = async (append = false) => {
  if (append) {
    loadingMore.value = true
  } else {
    loading.value = true
    currentPage.value = 1
  }
  
  try {
    const response = await getMyArticles({
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    
    const newArticles = response.rows || response.data || []
    totalArticles.value = response.total || newArticles.length
    
    // 追加或替换文章列表
    if (append) {
      articles.value = [...articles.value, ...newArticles]
    } else {
      articles.value = newArticles
    }
    
    // 判断是否还有更多数据
    hasMore.value = articles.value.length < totalArticles.value
    
    // 如果有选中的文章，更新它的状态
    if (selectedArticle.value) {
      const updated = articles.value.find(a => a.id === selectedArticle.value.id)
      if (updated) {
        selectedArticle.value = updated
      }
    }
  } catch (error) {
    console.error('加载文章失败:', error)
    ElMessage.error('加载文章失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

// 刷新文章列表
const refreshArticles = async () => {
  currentPage.value = 1
  await loadArticles(false)
}

// 加载更多文章
const loadMoreArticles = async () => {
  if (!hasMore.value || loadingMore.value) return
  currentPage.value++
  await loadArticles(true)
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
    loadMoreArticles()
  }
}

// 创建文章并优化
const handleCreate = async () => {
  if (!articleTitle.value.trim()) {
    ElMessage.warning('请输入文章标题')
    return
  }

  const title = articleTitle.value.trim()
  creating.value = true
  
  try {
    const response = await createAndOptimize(title, selectedModels.value)
    
    if (response.code === 200 && response.data) {
      const newArticleId = response.data.id
      const newArticleTitle = response.data.articleTitle
      
      // 清空输入框
      articleTitle.value = ''
      
      // 显示成功提示
      ElMessage({
        message: `文章"${newArticleTitle}"创建成功，AI正在生成内容...`,
        type: 'success',
        duration: 3000
      })
      
      // 立即刷新文章列表（新文章会出现在列表中，status=0处理中）
      await loadArticles()
      
      // 自动选中新创建的文章并显示详情
      if (newArticleId) {
        const newArticle = articles.value.find(a => a.id === newArticleId)
        if (newArticle) {
          // 使用selectArticle方法，自动切换到"优化内容"tab
          selectArticle(newArticle)
          
          // 提示用户可以查看实时生成的内容
          setTimeout(() => {
            ElMessage({
              message: '内容正在AI生成中，每个模型的内容会实时显示在下方tab中',
              type: 'info',
              duration: 8000,
              showClose: true
            })
          }, 1500)
        }
        
        // 启动轮询，监控生成进度并实时更新内容
        startPollingArticle(newArticleId, newArticleTitle)
      }
    } else {
      ElMessage.error(response.msg || '创建失败，请重试')
    }
  } catch (error) {
    console.error('创建文章失败:', error)
    
    // 判断是否是超时错误（修复后应该不会出现了）
    if (error.code === 'ECONNABORTED' && error.message.includes('timeout')) {
      ElMessage({
        message: '请求超时，但文章可能已创建成功，正在刷新列表...',
        type: 'warning',
        duration: 5000
      })
      // 超时后也刷新列表，可能文章已创建
      await loadArticles()
      
      // 尝试找到最新创建的文章（按时间排序，第一个应该是最新的）
      if (articles.value.length > 0) {
        selectArticle(articles.value[0])
      }
    } else if (error.response && error.response.status === 400) {
      ElMessage.error('创建失败，请检查智能体配置')
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
      ElMessage.error('创建失败：' + (error.message || '未知错误'))
    }
  } finally {
    creating.value = false
  }
}

// 轮询单篇文章的生成状态（实时更新内容）
const startPollingArticle = (articleId, articleTitle) => {
  let pollCount = 0
  const maxPolls = 60  // 5分钟（60 * 5秒 = 300秒）
  
  const pollInterval = setInterval(async () => {
    pollCount++
    
    try {
      // 刷新列表，获取最新的文章内容（包括已生成的模型内容）
      await loadArticles()
      const article = articles.value.find(a => a.id === articleId)
      
      if (article) {
        // 如果当前选中的就是这篇文章，自动更新显示最新内容
        if (selectedArticle.value && selectedArticle.value.id === articleId) {
          selectedArticle.value = article
        }
        
        // 检查是否完成或失败
        if (article.processStatus === 1) {
          // 生成完成
          clearInterval(pollInterval)
          
          ElNotification({
            title: '生成完成',
            message: `文章"${articleTitle}"已生成完成！可以查看各模型的内容了。`,
            type: 'success',
            duration: 5000
          })
        } else if (article.processStatus === 2) {
          // 生成失败
          clearInterval(pollInterval)
          
          ElNotification({
            title: '生成失败',
            message: `文章"${articleTitle}"生成失败：${article.errorMessage || '未知错误'}`,
            type: 'error',
            duration: 8000
          })
        }
        // status=0 继续轮询，用户可以实时看到逐步生成的模型内容
        
      } else if (pollCount >= maxPolls) {
        // 超时处理（5分钟）
        clearInterval(pollInterval)
        
        ElNotification({
          title: '生成超时',
          message: `文章"${articleTitle}"生成超时（超过5分钟），请刷新页面检查文章状态`,
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

// 选择文章
const selectArticle = (article) => {
  selectedArticle.value = article
  activeTab.value = 'optimized'  // 默认显示"优化内容"tab
}

// 删除文章
const handleDelete = async (article) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这篇文章吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await delDailyArticle(article.id)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      if (selectedArticle.value && selectedArticle.value.id === article.id) {
        selectedArticle.value = null
      }
      await loadArticles()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 新增：重试生成失败的文章
const handleRetry = async (article) => {
  try {
    await ElMessageBox.confirm(
      `确定要重新生成文章"${article.articleTitle}"吗？`,
      '重新生成确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 先删除失败的记录
    await delDailyArticle(article.id)
    
    // 重新创建
    const models = article.selectedModels ? article.selectedModels.split(',').map(Number) : [1, 2, 3]
    const response = await createAndOptimize(article.articleTitle, models)
    
    if (response.code === 200) {
      const newArticleId = response.data?.id
      
      ElMessage({
        message: `文章"${article.articleTitle}"正在重新生成中`,
        type: 'info',
        duration: 10000
      })
      
      // 从列表中移除失败的文章
      await loadArticles()
      
      // 启动轮询
      if (newArticleId) {
        startPollingArticle(newArticleId, article.articleTitle)
      }
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('重试失败:', error)
      ElMessage.error('重试失败')
    }
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

// 投递文章到公众号
const handlePublishToWechat = async (contentType) => {
  if (!selectedArticle.value) {
    ElMessage.warning('请先选择文章')
    return
  }
  
  // 内容类型名称映射
  const contentTypeNames = {
    'optimized': '优化后的文章',
    'model1': selectedArticle.value.model1Name || '模型1文章',
    'model2': selectedArticle.value.model2Name || '模型2文章',
    'model3': selectedArticle.value.model3Name || '模型3文章',
    'layout': '排版后的文章'
  }
  
  try {
    await ElMessageBox.confirm(
      `确定要将 "${contentTypeNames[contentType]}" 投递到公众号草稿箱吗？`,
      '投递确认',
      {
        confirmButtonText: '确定投递',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    publishing.value = true
    
    const response = await publishToWechat(
      selectedArticle.value.id,
      contentType,
      contentType === 'layout' ? layoutedContent.value : undefined
    )
    
    if (response.code === 200) {
      ElMessage.success({
        message: '投递成功！已保存到公众号草稿箱',
        duration: 3000
      })
      
      // 刷新文章列表（更新投递次数）
      await loadArticles()
    } else {
      ElMessage.error(response.msg || '投递失败')
    }
    
  } catch (error) {
    if (error !== 'cancel') {
      console.error('投递失败：', error)
      ElMessage.error('投递失败：' + (error.message || error))
    }
  } finally {
    publishing.value = false
  }
}

// 加载配置状态
const loadMyConfig = async () => {
  try {
    const response = await getMyConfig('daily_assistant')
    if (response.code === 200 && response.data) {
      // 检查是否已配置
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
    const response = await getMyConfig('daily_assistant')
    if (response.code === 200 && response.data) {
      const config = { ...response.data }
      
      // 如果没有API端点，使用默认值
      if (!config.apiEndpoint) {
        config.apiEndpoint = 'https://yuanqi.tencent.com/openapi/v1/agent/chat/completions'
      }
      
      // 检查是否已加密
      isAgentIdEncrypted.value = config.agentId === MASKED_VALUE
      isApiKeyEncrypted.value = config.apiKey === MASKED_VALUE
      
      // 如果是加密的，显示为空，但保留加密标记用于提交
      if (isAgentIdEncrypted.value) {
        config.agentId = ''
      }
      if (isApiKeyEncrypted.value) {
        config.apiKey = ''
      }
      
      configForm.value = config
    } else {
      // 新配置，使用默认值
      configForm.value.apiEndpoint = 'https://yuanqi.tencent.com/openapi/v1/agent/chat/completions'
      isAgentIdEncrypted.value = false
      isApiKeyEncrypted.value = false
    }
  } catch (error) {
    console.error('加载配置失败', error)
  }
  configDialogVisible.value = true
}

// 保存配置（验证在后端进行，一次请求完成）
const handleSaveConfig = async () => {
  try {
    await configFormRef.value.validate()
    
    ElMessage.info('正在保存配置，请稍候...')
    
    // 准备保存的数据
    const configData = {
      ...configForm.value,
      businessType: 'daily_assistant'  // 指定业务类型为日更助手
    }
    
    // 如果用户没有修改加密字段（输入框为空且原来已加密），传递加密标记
    if (!configData.agentId && isAgentIdEncrypted.value) {
      configData.agentId = MASKED_VALUE
    }
    if (!configData.apiKey && isApiKeyEncrypted.value) {
      configData.apiKey = MASKED_VALUE
    }
    
    // 直接调用保存接口，验证逻辑在后端进行
    const apiFunc = configForm.value.id ? updateYuanqiConfig : addYuanqiConfig
    const response = await apiFunc(configData)
    
    if (response.code === 200) {
      ElMessage.success('配置保存成功')
      configDialogVisible.value = false
      // 刷新配置状态
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
  // 如果用户开始输入，标记为未加密（将会重新加密）
  if (isAgentIdEncrypted.value && !configForm.value.agentId) {
    // 用户可以输入新值
  }
}

// 处理API密钥输入框获得焦点
const handleApiKeyFocus = () => {
  // 如果用户开始输入，标记为未加密（将会重新加密）
  if (isApiKeyEncrypted.value && !configForm.value.apiKey) {
    // 用户可以输入新值
  }
}

// 打开智能排版对话框
const handleSmartLayout = (content) => {
  if (!content) {
    ElMessage.warning('内容为空，无法排版')
    return
  }

  layoutContent.value = content
  layoutedContent.value = ''
  layoutActiveTab.value = 'original'
  layoutDialogVisible.value = true
}

// 开始排版（调用后端API）
const startLayout = async () => {
  if (!layoutContent.value) {
    ElMessage.warning('排版内容为空')
    return
  }

  layouting.value = true

  try {
    // 使用统一的request封装，自动带上token并走代理
    const result = await layoutArticle(layoutContent.value)

    // axios拦截器已保证code===200才会走到这里
    layoutedContent.value = result.data?.layoutedContent || ''
    layoutActiveTab.value = 'layouted'
    ElMessage.success('排版完成！')
  } catch (error) {
    console.error('排版失败:', error)
    ElMessage.error('排版失败：' + (error.message || error))
  } finally {
    layouting.value = false
  }
}

// 复制排版后的内容
const copyLayoutedContent = () => {
  if (!layoutedContent.value) {
    ElMessage.warning('排版内容为空')
    return
  }

  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = layoutedContent.value
  const textContent = tempDiv.textContent || tempDiv.innerText

  navigator.clipboard.writeText(textContent).then(() => {
    ElMessage.success('复制成功（纯文本）')
  }).catch(() => {
    navigator.clipboard.writeText(layoutedContent.value).then(() => {
      ElMessage.success('复制成功（HTML）')
    }).catch(() => {
      ElMessage.error('复制失败')
    })
  })
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

// 检查模型是否被选中
const isModelSelected = (article, modelIndex) => {
  if (!article || !article.selectedModels) {
    return true // 如果没有selectedModels字段，默认全部显示（兼容旧数据）
  }
  return article.selectedModels.includes(String(modelIndex))
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

// ========== 公众号配置相关 ==========
// 检查公众号配置
const checkOfficeAccountConfig = async () => {
  try {
    const response = await getMyOfficeAccount()
    if (response.data && response.data.id) {
      officeAccountConfigured.value = true
      // 配置存在时，自动验证一次
      await verifyOfficeAccount(false) // false表示静默验证，不显示成功提示
    } else {
      officeAccountConfigured.value = false
      officeAccountValid.value = false
    }
  } catch (error) {
    console.warn('检查公众号配置失败', error)
    officeAccountConfigured.value = false
    officeAccountValid.value = false
  }
}

// 验证公众号配置是否可用
const verifyOfficeAccount = async (showMessage = true) => {
  if (!officeAccountConfigured.value) {
    ElMessage.warning('请先配置公众号信息')
    return
  }
  
  verifyingOfficeAccount.value = true
  try {
    const response = await verifyConfig()
    if (response.code === 200) {
      officeAccountValid.value = true
      if (showMessage) {
        ElMessage.success('公众号配置验证成功，可以正常投递')
      }
    } else {
      officeAccountValid.value = false
      ElMessageBox.alert(
        response.msg || '公众号配置验证失败，请检查配置信息',
        '验证失败',
        {
          type: 'error',
          dangerouslyUseHTMLString: true,
          confirmButtonText: '去配置',
          callback: () => {
            goToOfficeAccountConfig()
          }
        }
      )
    }
  } catch (error) {
    officeAccountValid.value = false
    ElMessageBox.alert(
      '公众号配置验证失败，请检查：<br/>1. 开发者ID和密钥是否正确<br/>2. 服务器IP是否已添加到白名单<br/>3. 网络连接是否正常',
      '验证失败',
      {
        type: 'error',
        dangerouslyUseHTMLString: true,
        confirmButtonText: '去配置',
        callback: () => {
          goToOfficeAccountConfig()
        }
      }
    )
  } finally {
    verifyingOfficeAccount.value = false
  }
}

// 跳转到公众号配置页面
const goToOfficeAccountConfig = () => {
  router.push('/user/profile')
  ElMessage.info('请在个人中心配置公众号信息')
}

// 页面加载
onMounted(async () => {
  loadArticles()
  
  // 加载配置状态
  await loadMyConfig()
  
  // 检查公众号配置
  checkOfficeAccountConfig()
  
  // 检查是否有配置（非阻塞，失败不影响功能使用）
  try {
    const response = await getMyConfig('daily_assistant')
    if (!response.data) {
      // 如果没有配置，显示提示
      setTimeout(() => {
        ElMessageBox.confirm(
          '您还未配置腾讯元器智能体，需要先配置才能使用日更助手功能。',
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
      }, 500) // 延迟500ms显示，避免页面加载时立即弹窗
    }
  } catch (error) {
    console.warn('检查配置失败，可能是后端服务未启动或Redis连接异常', error)
    // 失败时不弹窗，仅在控制台警告，不影响功能使用
  }
  
  // 定期刷新列表（每30秒），保持数据新鲜
  setInterval(() => {
    loadArticles()
  }, 30000)
})

</script>

<style scoped lang="scss">
.daily-assistant-container {
  padding: 20px;

  .box-card {
    margin-bottom: 20px;
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

  .create-section {
    margin-bottom: 20px;

    .model-selection {
      margin-top: 15px;
      padding: 12px;
      background-color: #f5f7fa;
      border-radius: 4px;

      .selection-label {
        font-size: 14px;
        font-weight: 500;
        color: #606266;
        margin-right: 15px;
      }

      :deep(.el-checkbox-group) {
        display: inline-flex;
        gap: 20px;
      }

      :deep(.el-checkbox) {
        margin-right: 0;
      }
    }

    .tip-text {
      display: flex;
      align-items: center;
      gap: 5px;
      margin-top: 10px;
      font-size: 12px;
      color: #909399;
    }
  }

  .article-list {
    .list-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
      font-weight: bold;
    }

    .article-item {
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

      // 失败文章样式
      &.article-failed {
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

      .article-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;

        .article-title {
          font-weight: 500;
          color: #303133;
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      // 错误信息样式
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

      .article-meta {
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
    
    // 加载更多提示
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
  }

  .content-section {
    .content-wrapper {
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

      .content-text {
        padding: 15px;
        background-color: #f5f7fa;
        border-radius: 4px;
        line-height: 1.8;
        white-space: pre-wrap;
        word-wrap: break-word;
        color: #606266;
      }

      .generating-status {
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

  // 排版对话框样式
  .layout-content {
    padding: 20px;
    background-color: #f9f9f9;
    border-radius: 4px;
    line-height: 1.8;
    white-space: pre-wrap;
    word-wrap: break-word;
    color: #333;
    font-size: 14px;

    :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
      margin-top: 20px;
      margin-bottom: 10px;
      font-weight: bold;
    }

    :deep(p) {
      margin-bottom: 10px;
    }

    :deep(img) {
      max-width: 100%;
      height: auto;
    }
  }

  .layout-actions {
    display: flex;
    gap: 10px;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .daily-assistant-container {
    padding: 10px;

    .content-card {
      height: auto;
      margin-top: 20px;
    }
  }
}
</style>
