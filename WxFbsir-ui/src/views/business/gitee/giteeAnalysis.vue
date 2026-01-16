<template>
  <div class="gitee-analysis-container">
    <el-row :gutter="20">
      <el-col :span="10" :xs="24">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><DataAnalysis /></el-icon>
                gitee分析
              </span>
              <div class="header-actions">
                <el-button type="primary" size="small" :loading="analyzing" @click="handleReevaluate">
                  <el-icon><RefreshRight /></el-icon>
                  重新评测
                </el-button>
                <el-button type="success" size="small" :loading="aiEvaluating" @click="handleAiEvaluate">
                  <el-icon><MagicStick /></el-icon>
                  AI测评
                </el-button>
                <el-button size="small" @click="handleViewReport">
                  <el-icon><Document /></el-icon>
                  查看报告
                </el-button>
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

          <div class="intro-section">
            <div class="section-title">
              <el-icon><InfoFilled /></el-icon>
              评测说明
            </div>
            <div class="section-content">
              <p>从“资料完整度”、“社区贡献度”、“技术能力”等方面对用户进行综合评估，得出分项分级评价和综合评分。</p>
              <p>例如，社区贡献度可以从“最近一年内社区互动的数量和时间持续性”、“所创建的项目的社区口碑（fork数量、星、关注者数量、是否GVP或推荐）”、“社区动作的类型（fork、PR、讨论等）分权重统计”等维度进行统计。</p>
              <p>支持能力评测（重新评测）实时呈现和存储、查看报告。</p>
            </div>
          </div>

          <div class="dimension-section">
            <div class="section-title">
              <el-icon><Document /></el-icon>
              分项维度
            </div>
            <div class="dimension-list">
              <div class="dimension-item">
                <div class="dimension-header">
                  <span class="dimension-title">资料完整度</span>
                  <el-tag size="small" :type="scoreTagType(analysis.profileScore)">
                    {{ renderScoreLabel(analysis.profileScore, analysis.profileLevel) }}
                  </el-tag>
                </div>
                <div class="dimension-desc">覆盖头像、简介、组织/项目完善度、活跃时间与更新频次。</div>
              </div>
              <div class="dimension-item">
                <div class="dimension-header">
                  <span class="dimension-title">社区贡献度</span>
                  <el-tag size="small" :type="scoreTagType(analysis.communityScore)">
                    {{ renderScoreLabel(analysis.communityScore, analysis.communityLevel) }}
                  </el-tag>
                </div>
                <div class="dimension-desc">统计最近一年互动数量与持续性、项目口碑（fork、star、关注者、GVP/推荐）、社区动作类型权重。</div>
              </div>
              <div class="dimension-item">
                <div class="dimension-header">
                  <span class="dimension-title">技术能力</span>
                  <el-tag size="small" :type="scoreTagType(analysis.techScore)">
                    {{ renderScoreLabel(analysis.techScore, analysis.techLevel) }}
                  </el-tag>
                </div>
                <div class="dimension-desc">结合项目复杂度、Issue/PR质量、技术栈广度与稳定产出表现。</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="14" :xs="24">
        <el-card class="box-card content-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Reading /></el-icon>
                评测结果
              </span>
            </div>
          </template>
          <el-tabs v-model="activeTab" type="card">
            <el-tab-pane label="综合评分" name="score">
              <div class="score-panel">
                <div class="score-value">{{ analysis.totalScore ?? '--' }}</div>
                <div class="score-desc">
                  {{ analysis.totalScore === null ? '综合评分将在评测完成后展示' : `综合评级：${analysis.totalLevel}` }}
                </div>
              </div>
              <el-descriptions :column="2" border class="score-detail">
                <el-descriptions-item label="资料完整度">
                  {{ renderScoreLabel(analysis.profileScore, analysis.profileLevel) }}
                </el-descriptions-item>
                <el-descriptions-item label="社区贡献度">
                  {{ renderScoreLabel(analysis.communityScore, analysis.communityLevel) }}
                </el-descriptions-item>
                <el-descriptions-item label="技术能力">
                  {{ renderScoreLabel(analysis.techScore, analysis.techLevel) }}
                </el-descriptions-item>
                <el-descriptions-item label="综合评级">
                  {{ renderScoreLabel(analysis.totalScore, analysis.totalLevel) }}
                </el-descriptions-item>
              </el-descriptions>
              <div class="analysis-comments">
                <div class="comment-block">
                  <div class="comment-title">评语</div>
                  <el-tag v-if="!analysis.comments.length" size="small" type="info">暂无</el-tag>
                  <ul v-else class="comment-list">
                    <li v-for="(item, index) in analysis.comments" :key="`comment-${index}`">
                      {{ item }}
                    </li>
                  </ul>
                </div>
                <div class="comment-block">
                  <div class="comment-title">建议</div>
                  <el-tag v-if="!analysis.suggestions.length" size="small" type="info">暂无</el-tag>
                  <ul v-else class="comment-list">
                    <li v-for="(item, index) in analysis.suggestions" :key="`suggestion-${index}`">
                      {{ item }}
                    </li>
                  </ul>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="评测报告" name="report">
              <el-table
                v-if="reportList.length"
                :data="reportList"
                size="small"
                border
                class="report-table"
              >
                <el-table-column prop="time" label="评测时间" min-width="160" />
                <el-table-column prop="totalScore" label="综合评分" width="100" align="center" />
                <el-table-column prop="totalLevel" label="综合评级" width="100" align="center" />
                <el-table-column prop="profileScore" label="资料完整度" width="110" align="center" />
                <el-table-column prop="communityScore" label="社区贡献度" width="110" align="center" />
                <el-table-column prop="techScore" label="技术能力" width="100" align="center" />
              </el-table>
              <el-empty v-else description="暂无评测记录，请点击“重新评测”生成报告" />
            </el-tab-pane>
          </el-tabs>
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
            placeholder="自定义名称，如：Gitee分析助手"
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

<script setup name="GiteeAnalysis">
import { ref, onMounted } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import {
  DataAnalysis,
  RefreshRight,
  Document,
  InfoFilled,
  Reading,
  Setting,
  CircleCheck,
  MagicStick
} from '@element-plus/icons-vue'
import {
  getGiteeStatus,
  fetchGiteeProfile,
  fetchGiteeRepos,
  fetchGiteeIssues,
  fetchGiteeNotifications,
  reevaluateGiteeAnalysis,
  saveGiteeAnalysisReport
} from '@/api/business/gitee/profile'
import {
  getMyConfig,
  addYuanqiConfig,
  updateYuanqiConfig
} from '@/api/business/content/dailyassistant/yuanqiConfig'
import useUserStore from '@/store/modules/user'
import { parseTime } from '@/utils/WxFbsir'

const activeTab = ref('score')
const analyzing = ref(false)
const aiEvaluating = ref(false)
const analysis = ref({
  profileScore: null,
  profileLevel: '待评测',
  communityScore: null,
  communityLevel: '待评测',
  techScore: null,
  techLevel: '待评测',
  totalScore: null,
  totalLevel: '待评测',
  comments: [],
  suggestions: []
})
const reportList = ref([])
const storageKeyBase = 'giteeAnalysisReports'
const latestAnalysisKeyBase = 'giteeAnalysisLatest'
const MASKED_VALUE = '***已加密***'
const userStore = useUserStore()

const agentConfigured = ref(false)
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
const isAgentIdEncrypted = ref(false)
const isApiKeyEncrypted = ref(false)
const configRules = {
  agentId: [{ required: true, message: '请输入智能体ID', trigger: 'blur' }],
  agentName: [{ required: true, message: '请输入智能体名称', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入API密钥', trigger: 'blur' }]
}

function handleViewReport() {
  activeTab.value = 'report'
}

function renderScoreLabel(score, level) {
  if (score === null || score === undefined) {
    return '待评测'
  }
  return `${level}（${score}）`
}

function scoreTagType(score) {
  if (score === null || score === undefined) {
    return 'info'
  }
  if (score >= 85) {
    return 'success'
  }
  if (score >= 70) {
    return ''
  }
  if (score >= 60) {
    return 'warning'
  }
  return 'danger'
}

function normalizeNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function clampScore(value) {
  return Math.max(0, Math.min(100, Math.round(value)))
}

function scoreLevel(score) {
  if (score >= 85) return 'A'
  if (score >= 70) return 'B'
  if (score >= 60) return 'C'
  return 'D'
}

function isRecent(dateStr, days) {
  if (!dateStr) return false
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return false
  const diff = Date.now() - date.getTime()
  return diff <= days * 24 * 60 * 60 * 1000
}

function calculateProfileScore(profile) {
  if (!profile) return { score: 0, level: 'D' }
  let score = 0
  if (profile.avatar_url) score += 15
  if (profile.name || profile.login) score += 10
  if (profile.bio) score += 15
  if (profile.blog) score += 10
  if (profile.weibo) score += 10
  if (profile.email) score += 10
  if (normalizeNumber(profile.public_repos) > 0) score += 10
  if (isRecent(profile.updated_at, 365)) score += 20
  const finalScore = clampScore(score)
  return { score: finalScore, level: scoreLevel(finalScore) }
}

function calculateCommunityScore(repos, issues, notifications) {
  const repoList = Array.isArray(repos) ? repos : []
  const issueList = Array.isArray(issues) ? issues : []
  const noticeList = Array.isArray(notifications) ? notifications : []
  const repoCount = repoList.length
  const interactionCount = issueList.length + noticeList.length
  let popularity = 0
  repoList.forEach(repo => {
    popularity += normalizeNumber(repo.forks_count || repo.forks)
    popularity += normalizeNumber(repo.stargazers_count || repo.stars)
    popularity += normalizeNumber(repo.watchers_count || repo.watchers)
  })
  const recentRepoCount = repoList.filter(repo => isRecent(repo.updated_at || repo.pushed_at, 365)).length
  const recentIssueCount = issueList.filter(issue => isRecent(issue.created_at, 365)).length
  const recentTotal = recentRepoCount + recentIssueCount
  const baseTotal = repoCount + issueList.length
  const activityScore = Math.min(30, interactionCount * 2)
  const repoScore = Math.min(25, repoCount * 5)
  const popularityScore = Math.min(25, popularity * 0.5)
  const continuityScore = baseTotal ? Math.min(20, (recentTotal / baseTotal) * 20) : 0
  const finalScore = clampScore(activityScore + repoScore + popularityScore + continuityScore)
  return { score: finalScore, level: scoreLevel(finalScore) }
}

function calculateTechScore(repos, issues) {
  const repoList = Array.isArray(repos) ? repos : []
  const issueList = Array.isArray(issues) ? issues : []
  const languages = new Set()
  repoList.forEach(repo => {
    if (repo.language) {
      languages.add(repo.language)
    }
  })
  const repoCount = repoList.length
  const recentRepoCount = repoList.filter(repo => isRecent(repo.updated_at || repo.pushed_at, 90)).length
  let commentsTotal = 0
  issueList.forEach(issue => {
    commentsTotal += normalizeNumber(issue.comments)
  })
  const avgComments = issueList.length ? commentsTotal / issueList.length : 0
  const languageScore = Math.min(24, languages.size * 8)
  const repoScore = Math.min(30, repoCount * 6)
  const activeScore = Math.min(26, recentRepoCount * 5)
  const issueScore = Math.min(20, avgComments * 5)
  const finalScore = clampScore(languageScore + repoScore + activeScore + issueScore)
  return { score: finalScore, level: scoreLevel(finalScore) }
}

function buildReport(profileScore, communityScore, techScore) {
  const totalScore = clampScore(
    profileScore.score * 0.3 + communityScore.score * 0.4 + techScore.score * 0.3
  )
  const totalLevel = scoreLevel(totalScore)
  return {
    time: parseTime(new Date()),
    totalScore,
    totalLevel,
    profileScore: profileScore.score,
    communityScore: communityScore.score,
    techScore: techScore.score,
    profileLevel: profileScore.level,
    communityLevel: communityScore.level,
    techLevel: techScore.level
  }
}

function resolveStorageKey(baseKey) {
  if (!userStore.id) {
    return null
  }
  return `${baseKey}:${String(userStore.id)}`
}

function loadReports() {
  try {
    const storageKey = resolveStorageKey(storageKeyBase)
    if (!storageKey) {
      reportList.value = []
      return
    }
    const raw = localStorage.getItem(storageKey)
    const list = raw ? JSON.parse(raw) : []
    reportList.value = Array.isArray(list) ? list : []
  } catch (error) {
    reportList.value = []
  }
}

function saveReport(report) {
  const nextList = [report, ...reportList.value].slice(0, 20)
  reportList.value = nextList
  const storageKey = resolveStorageKey(storageKeyBase)
  if (storageKey) {
    localStorage.setItem(storageKey, JSON.stringify(nextList))
  }
}

function saveLatestAnalysis(payload) {
  const storageKey = resolveStorageKey(latestAnalysisKeyBase)
  if (storageKey) {
    localStorage.setItem(storageKey, JSON.stringify(payload))
  }
}

function applyAnalysisPayload(payload) {
  analysis.value = {
    profileScore: payload.profileScore ?? null,
    profileLevel: payload.profileLevel || '待评测',
    communityScore: payload.communityScore ?? null,
    communityLevel: payload.communityLevel || '待评测',
    techScore: payload.techScore ?? null,
    techLevel: payload.techLevel || '待评测',
    totalScore: payload.totalScore ?? null,
    totalLevel: payload.totalLevel || '待评测',
    comments: Array.isArray(payload.comments) ? payload.comments : [],
    suggestions: Array.isArray(payload.suggestions) ? payload.suggestions : []
  }
}

function loadLatestAnalysis() {
  try {
    const storageKey = resolveStorageKey(latestAnalysisKeyBase)
    if (!storageKey) {
      return
    }
    const raw = localStorage.getItem(storageKey)
    if (raw) {
      const payload = JSON.parse(raw)
      if (payload && typeof payload === 'object') {
        applyAnalysisPayload(payload)
        return
      }
    }
  } catch (error) {
    // ignore invalid cache
  }

  if (reportList.value.length) {
    const latestReport = reportList.value[0]
    applyAnalysisPayload({
      ...latestReport,
      comments: [],
      suggestions: []
    })
  }
}

function applyAnalysisResult(result) {
  applyAnalysisPayload({
    ...result,
    comments: Array.isArray(result.comments)
      ? result.comments
      : (Array.isArray(result.highlights) ? result.highlights : []),
    suggestions: Array.isArray(result.suggestions) ? result.suggestions : []
  })
  const report = {
    time: parseTime(new Date()),
    totalScore: analysis.value.totalScore ?? 0,
    totalLevel: analysis.value.totalLevel,
    profileScore: analysis.value.profileScore ?? 0,
    communityScore: analysis.value.communityScore ?? 0,
    techScore: analysis.value.techScore ?? 0,
    profileLevel: analysis.value.profileLevel,
    communityLevel: analysis.value.communityLevel,
    techLevel: analysis.value.techLevel
  }
  saveReport(report)
  saveLatestAnalysis(analysis.value)
}

async function handleReevaluate() {
  if (analyzing.value) return
  analyzing.value = true
  try {
    const status = await getGiteeStatus()
    if (!status.data?.authorized) {
      ElMessage.warning('请先完成 Gitee 授权后再评测')
      return
    }
    const [profileRes, reposRes, issuesRes, noticesRes] = await Promise.all([
      fetchGiteeProfile(),
      fetchGiteeRepos({ per_page: 100 }),
      fetchGiteeIssues({ per_page: 100 }),
      fetchGiteeNotifications({ per_page: 50 })
    ])
    const profile = profileRes.data || null
    const repos = Array.isArray(reposRes.data) ? reposRes.data : []
    const issues = Array.isArray(issuesRes.data) ? issuesRes.data : []
    const noticesData = noticesRes.data
    const notifications = Array.isArray(noticesData?.list) ? noticesData.list : (Array.isArray(noticesData) ? noticesData : [])
    const profileScore = calculateProfileScore(profile)
    const communityScore = calculateCommunityScore(repos, issues, notifications)
    const techScore = calculateTechScore(repos, issues)
    const report = buildReport(profileScore, communityScore, techScore)
    const analysisPayload = {
      profileScore: profileScore.score,
      profileLevel: profileScore.level,
      communityScore: communityScore.score,
      communityLevel: communityScore.level,
      techScore: techScore.score,
      techLevel: techScore.level,
      totalScore: report.totalScore,
      totalLevel: report.totalLevel
    }
    await saveGiteeAnalysisReport(analysisPayload)
    applyAnalysisPayload({
      ...analysisPayload,
      comments: [],
      suggestions: []
    })
    saveReport(report)
    saveLatestAnalysis(analysis.value)
    ElNotification({
      title: '评测完成',
      message: '已生成最新评测结果与报告。',
      type: 'success',
      duration: 4000
    })
  } catch (error) {
    ElMessage.error(error.response?.data?.msg || error.message || '评测失败，请稍后重试')
  } finally {
    analyzing.value = false
  }
}

async function handleAiEvaluate() {
  if (aiEvaluating.value) return
  aiEvaluating.value = true
  try {
    const res = await reevaluateGiteeAnalysis()
    const result = res.data?.analysis || res.data || {}
    if (!result) {
      throw new Error('AI测评返回为空')
    }
    applyAnalysisResult(result)
    ElNotification({
      title: 'AI测评完成',
      message: '评测结果已更新，可在左侧查看。',
      type: 'success',
      duration: 4000
    })
  } catch (error) {
    ElMessage.error(error.response?.data?.msg || error.message || 'AI测评失败')
  } finally {
    aiEvaluating.value = false
  }
}

const loadMyConfig = async () => {
  try {
    const response = await getMyConfig('gitee_analysis')
    if (response.code === 200 && response.data) {
      agentConfigured.value = !!(response.data.agentId && response.data.apiKey)
    } else {
      agentConfigured.value = false
    }
  } catch (error) {
    agentConfigured.value = false
  }
}

const showConfigDialog = async () => {
  try {
    const response = await getMyConfig('gitee_analysis')
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
    ElMessage.error('加载配置失败')
  }
  configDialogVisible.value = true
}

const handleSaveConfig = async () => {
  try {
    await configFormRef.value.validate()
    ElMessage.info('正在保存配置，请稍候...')
    const configData = {
      ...configForm.value,
      businessType: 'gitee_analysis'  // 指定业务类型为Gitee分析
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
    ElMessage.error('保存失败: ' + (error.response?.data?.msg || error.message))
  }
}

const handleConfigDialogClose = () => {
  configFormRef.value?.resetFields()
  isAgentIdEncrypted.value = false
  isApiKeyEncrypted.value = false
}

const handleAgentIdFocus = () => {
  if (isAgentIdEncrypted.value && !configForm.value.agentId) {
    // 用户可输入新值
  }
}

const handleApiKeyFocus = () => {
  if (isApiKeyEncrypted.value && !configForm.value.apiKey) {
    // 用户可输入新值
  }
}

onMounted(() => {
  loadReports()
  loadLatestAnalysis()
  loadMyConfig()
})
</script>

<style scoped lang="scss">
.gitee-analysis-container {
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
  }

  .header-actions {
    display: flex;
    gap: 10px;
  }

  .intro-section,
  .dimension-section {
    margin-bottom: 20px;
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 10px;
  }

  .section-content {
    background-color: #f5f7fa;
    border-radius: 4px;
    padding: 12px;
    color: #606266;
    font-size: 13px;
    line-height: 1.7;

    p {
      margin: 0 0 8px;
    }

    p:last-child {
      margin-bottom: 0;
    }
  }

  .dimension-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .dimension-item {
    padding: 12px;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    transition: all 0.3s;

    &:hover {
      background-color: #f5f7fa;
      border-color: #409eff;
    }
  }

  .dimension-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
  }

  .dimension-title {
    font-weight: 500;
    color: #303133;
  }

  .dimension-desc {
    font-size: 12px;
    color: #909399;
    line-height: 1.6;
  }

  .score-panel {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 160px;
    gap: 8px;
  }

  .score-value {
    font-size: 48px;
    font-weight: 600;
    color: #409EFF;
  }

  .score-desc {
    font-size: 13px;
    color: #909399;
  }

  .score-detail {
    margin-top: 16px;
  }

  .analysis-comments {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 16px;
    margin-top: 16px;
  }

  .comment-block {
    padding: 12px;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    background-color: #f5f7fa;
  }

  .comment-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;
  }

  .comment-list {
    margin: 0;
    padding-left: 18px;
    color: #606266;
    font-size: 13px;
    line-height: 1.6;
  }

  .report-table {
    margin-top: 6px;
  }
}
</style>
