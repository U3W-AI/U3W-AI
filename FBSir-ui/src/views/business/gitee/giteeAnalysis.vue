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

          <div class="dimension-section">
            <div class="section-title">
              <el-icon><Document /></el-icon>
              个人简历
            </div>
            <div class="dimension-list">
              <div class="dimension-item">
                <div class="dimension-header">
                  <span class="dimension-title">访问码控制</span>
                </div>
                <div class="dimension-desc">可自定义访问码，保护个人数据安全</div>
              </div>
              <div class="dimension-item">
                <div class="dimension-header">
                  <span class="dimension-title">数据提取/AI分析</span>
                </div>
                <div class="dimension-desc">AI智能分析个人简历，并提取简历关键信息，让你的简历智能化</div>
              </div>
              <div class="dimension-item">
                <div class="dimension-header">
                  <span class="dimension-title">链接分享/数据监控</span>
                </div>
                <div class="dimension-desc">分享简历链接，让更多人看到你的简历，同时监控简历访问数据</div>
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
            <el-tab-pane label="访问码" name="accessCode">
              <div class="access-code-section">
                <div class="section-header">
                  <div class="section-title">
                    <el-icon><Key /></el-icon>
                    访问码管理
                    <el-switch
                        v-model="accessCodeEnabled"
                        :loading="accessCodeSwitchLoading"
                        @change="handleAccessCodeSwitchChange"
                        style="margin-left: 12px"
                    />
                  </div>
                  <div class="header-actions">
                    <el-button type="primary" size="small" @click="showAddAccessCodeDialog">
                      <el-icon><Plus /></el-icon>
                      新增
                    </el-button>
                    <el-button type="success" size="small" @click="showBatchAddAccessCodeDialog">
                      <el-icon><Plus /></el-icon>
                      批量生成
                    </el-button>
                    <el-button type="primary" size="small" @click="loadAccessCodeList">
                      <el-icon><RefreshRight /></el-icon>
                      刷新
                    </el-button>
                  </div>
                </div>

                <el-table
                    v-loading="accessCodeLoading"
                    :data="accessCodeList"
                    size="small"
                    border
                    class="access-code-table"
                >
                  <el-table-column prop="accessCode" label="访问码" min-width="150">
                    <template #default="{ row }">
                      <el-tag type="primary" size="small">{{ row.accessCode }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="accessibleCount" label="可访问次数" width="110" align="center">
                    <template #default="{ row }">
                      <el-tag :type="row.accessibleCount > 0 ? 'success' : 'info'" size="small">
                        {{ row.accessibleCount === -1 ? '无限' : row.accessibleCount }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="deadline" label="截止时间" width="160" align="center">
                    <template #default="{ row }">
                      <span :class="{ 'text-danger': isDeadlineExpired(row.deadline) }">
                        {{ row.deadline || '永久有效' }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="available" label="状态" width="80" align="center">
                    <template #default="{ row }">
                      <el-tag :type="row.available === 1 ? 'success' : 'danger'" size="small">
                        {{ row.available === 1 ? '启用' : '禁用' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="createTime" label="创建时间" width="160" align="center" />
                  <el-table-column prop="updateTime" label="更新时间" width="160" align="center" />
                </el-table>

                <el-pagination
                    v-if="accessCodePagination.total > 0"
                    v-model:current-page="accessCodePagination.pageNum"
                    v-model:page-size="accessCodePagination.pageSize"
                    :page-sizes="[10, 20, 50, 100]"
                    :total="accessCodePagination.total"
                    layout="total, sizes, prev, pager, next, jumper"
                    @current-change="handleAccessCodePageChange"
                    @size-change="handleAccessCodeSizeChange"
                    class="pagination"
                />

                <el-empty v-if="!accessCodeLoading && accessCodeList.length === 0" description="暂无访问码数据" :image-size="100" />
              </div>
            </el-tab-pane>
            <el-tab-pane label="智能分析" name="ai">
              <div class="card-header">
                <span class="card-title">
                  <el-icon><Document /></el-icon>
                  个人简历
                </span>
                <div class="header-actions">
                  <el-button type="primary" size="small" @click="handleRefreshResume">
                    <el-icon><RefreshRight /></el-icon>
                    刷新
                  </el-button>
                  <el-button type="success" size="small" :loading="resumeGenerating" @click="handleGenerateResume">
                    <el-icon><MagicStick /></el-icon>
                    一键生成
                  </el-button>
                  <el-button
                      :type="cvAgentConfigured ? 'success' : 'primary'"
                      size="small"
                      @click="showCvConfigDialog"
                  >
                    <el-icon>
                      <component :is="cvAgentConfigured ? 'CircleCheck' : 'Setting'" />
                    </el-icon>
                    {{ cvAgentConfigured ? '已配置' : '配置智能体' }}
                  </el-button>
                </div>
              </div>

              <div class="resume-data-section">
                <el-descriptions :column="2" border class="resume-info">
                  <el-descriptions-item label="简历名称">{{ resumeData.cvName || '--' }}</el-descriptions-item>
                  <el-descriptions-item label="个人姓名">{{ resumeData.name || '--' }}</el-descriptions-item>
                  <el-descriptions-item label="电话">{{ resumeData.phone || '--' }}</el-descriptions-item>
                  <el-descriptions-item label="邮箱">{{ resumeData.mail || '--' }}</el-descriptions-item>
                  <el-descriptions-item label="访问链接">
                    <div class="access-link-item">
                      <el-link v-if="resumeData.shortlink" type="primary" :href="resumeData.shortlink" target="_blank">{{ resumeData.shortlink }}</el-link>
                      <span v-else>--</span>
                      <el-button v-if="resumeData.shortlink" type="success" size="small" @click="showShareDialog">
                        <el-icon><Share /></el-icon>
                        分享
                      </el-button>
                    </div>
                  </el-descriptions-item>
                  <el-descriptions-item label="截止时间">
                    <div class="deadline-item">
                      <span>{{ resumeData.deadline || '无' }}</span>
                      <el-button type="primary" size="small" @click="showSetDeadlineDialog">
                        <el-icon><Setting /></el-icon>
                        设置
                      </el-button>
                    </div>
                  </el-descriptions-item>
                  <el-descriptions-item label="更新时间">{{ resumeData.updateTime || '--' }}</el-descriptions-item>
                </el-descriptions>

                <div class="ai-analysis-section">
                  <div class="section-title">
                    <el-icon><MagicStick /></el-icon>
                    AI智能分析
                  </div>
                  <div class="section-content">
                    <div v-if="resumeGenerating" class="parsing-status">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <p>正在解析中...</p>
                      <p class="tip">请稍候，AI正在处理您的简历</p>
                    </div>
                    <div v-else-if="resumeData.parseContent" class="analysis-content">
                      <p>根据您的简历内容，AI分析如下：</p>
                      <div class="parse-content-text">{{ resumeData.parseContent }}</div>
                    </div>
                    <el-empty v-else description='暂无解析内容，请点击"一键生成"开始分析' :image-size="100" />
                  </div>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="数据监控" name="monitoring">
              <div class="monitoring-section">
                <el-tabs v-model="monitoringSubTab" type="border-card">
                  <el-tab-pane label="访问数据监控" name="accessData">
                    <div class="monitoring-header">
                      <div class="date-range-picker">
                        <el-date-picker
                            v-model="accessDateRange"
                            type="daterange"
                            range-separator="至"
                            start-placeholder="开始日期"
                            end-placeholder="结束日期"
                            format="YYYY-MM-DD"
                            value-format="YYYY-MM-DD"
                            @change="loadAccessData"
                        />
                      </div>
                      <el-button type="primary" size="small" @click="loadAccessData">
                        <el-icon><RefreshRight /></el-icon>
                        刷新
                      </el-button>
                    </div>

                    <div v-loading="accessDataLoading" class="chart-container">
                      <div ref="accessDataChartRef" class="access-data-chart"></div>
                      <el-empty v-if="!accessDataLoading && accessDataList.length === 0" description="暂无访问数据" :image-size="100" />
                    </div>
                  </el-tab-pane>

                  <el-tab-pane label="访问日志监控" name="accessLog">
                    <div class="monitoring-header">
                      <el-button type="primary" size="small" @click="loadAccessLog">
                        <el-icon><RefreshRight /></el-icon>
                        刷新
                      </el-button>
                    </div>

                    <el-table
                        v-loading="accessLogLoading"
                        :data="accessLogList"
                        size="small"
                        border
                        class="access-log-table"
                    >
                      <el-table-column prop="ip" label="IP地址" min-width="140" />
                      <el-table-column prop="browser" label="浏览器" min-width="120">
                        <template #default="{ row }">
                          <el-tag size="small" type="info">{{ row.browser || '--' }}</el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column prop="os" label="操作系统" min-width="120">
                        <template #default="{ row }">
                          <el-tag size="small" type="success">{{ row.os || '--' }}</el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column prop="device" label="访问设备" min-width="100">
                        <template #default="{ row }">
                          <el-tag size="small" type="warning">{{ row.device || '--' }}</el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column prop="createTime" label="访问时间" width="160" align="center" />
                    </el-table>

                    <el-pagination
                        v-if="accessLogPagination.total > 0"
                        v-model:current-page="accessLogPagination.pageNum"
                        v-model:page-size="accessLogPagination.pageSize"
                        :page-sizes="[10, 20, 50, 100]"
                        :total="accessLogPagination.total"
                        layout="total, sizes, prev, pager, next, jumper"
                        @current-change="handleAccessLogPageChange"
                        @size-change="handleAccessLogSizeChange"
                        class="pagination"
                    />

                    <el-empty v-if="!accessLogLoading && accessLogList.length === 0" description="暂无访问日志" :image-size="100" />
                  </el-tab-pane>
                </el-tabs>
              </div>
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
    <!-- CV解析智能体配置对话框 -->
    <el-dialog
        v-model="cvConfigDialogVisible"
        title="配置CV解析智能体"
        width="600px"
        @close="handleCvConfigDialogClose"
    >
      <el-form
          ref="cvConfigFormRef"
          :model="cvConfigForm"
          :rules="configRules"
          label-width="120px"
      >
        <el-form-item label="智能体ID" prop="agentId">
          <el-input
              v-model="cvConfigForm.agentId"
              :placeholder="cvIsAgentIdEncrypted ? '已加密存储，输入新值可覆盖' : '请输入appid'"
              @focus="handleCvAgentIdFocus"
          >
            <template #suffix>
              <el-tag v-if="cvIsAgentIdEncrypted" type="success" size="small">已添加</el-tag>
            </template>
          </el-input>
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            从 智能体配置→应用发布→体验链接 中获取
          </div>
        </el-form-item>
        <el-form-item label="智能体名称" prop="agentName">
          <el-input
              v-model="cvConfigForm.agentName"
              placeholder="自定义名称，如：CV解析助手"
          />
        </el-form-item>
        <el-form-item label="API密钥" prop="apiKey">
          <el-input
              v-model="cvConfigForm.apiKey"
              type="password"
              :placeholder="cvIsApiKeyEncrypted ? '已加密存储，输入新值可覆盖' : '请输入appkey'"
              show-password
              @focus="handleCvApiKeyFocus"
          >
            <template #suffix>
              <el-tag v-if="cvIsApiKeyEncrypted" type="success" size="small" style="margin-right: 30px;">已添加</el-tag>
            </template>
          </el-input>
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            从 应用发布→API管理 中获取
          </div>
        </el-form-item>
        <el-form-item label="API端点" prop="apiEndpoint">
          <el-input
              v-model="cvConfigForm.apiEndpoint"
              placeholder="请输入API端点URL"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            默认：https://yuanqi.tencent.com/openapi/v1/agent/chat/completions
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cvConfigDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveCvConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增访问码对话框 -->
    <el-dialog
        v-model="addAccessCodeDialogVisible"
        title="新增访问码"
        width="500px"
        @close="handleAddAccessCodeDialogClose"
    >
      <el-form
          ref="addAccessCodeFormRef"
          :model="addAccessCodeForm"
          :rules="addAccessCodeRules"
          label-width="100px"
      >
        <el-form-item label="可访问次数" prop="accessibleCount">
          <el-input-number
              v-model="addAccessCodeForm.accessibleCount"
              :min="-1"
              :max="999999"
              controls-position="right"
              style="width: 100%"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            设置为 -1 表示无限次访问
          </div>
        </el-form-item>
        <el-form-item label="截止时间" prop="deadline">
          <el-date-picker
              v-model="addAccessCodeForm.deadline"
              type="datetime"
              placeholder="请选择截止时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            访问码过期后将无法使用
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addAccessCodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddAccessCode">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量生成访问码对话框 -->
    <el-dialog
        v-model="batchAddAccessCodeDialogVisible"
        title="批量生成一次访问码"
        width="500px"
        @close="handleBatchAddAccessCodeDialogClose"
    >
      <el-form
          ref="batchAddAccessCodeFormRef"
          :model="batchAddAccessCodeForm"
          :rules="batchAddAccessCodeRules"
          label-width="100px"
      >
        <el-form-item label="截止时间" prop="deadline">
          <el-date-picker
              v-model="batchAddAccessCodeForm.deadline"
              type="datetime"
              placeholder="请选择截止时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD"
              style="width: 100%"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            访问码过期后将无法使用
          </div>
        </el-form-item>
        <el-form-item label="生成数量" prop="count">
          <el-input-number
              v-model="batchAddAccessCodeForm.count"
              :min="1"
              :max="10"
              controls-position="right"
              style="width: 100%"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            生成数量不能超过10个
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchAddAccessCodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleBatchAddAccessCode">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分享链接对话框 -->
    <el-dialog
        v-model="shareDialogVisible"
        title="分享链接"
        width="500px"
    >
      <div class="share-dialog-content">
        <div class="share-description">分享你的链接，让更多人看到</div>
        <div class="share-link-container">
          <el-input
              v-model="resumeData.shortlink"
              readonly
              style="margin-bottom: 12px"
          />
          <el-button type="primary" @click="handleCopyLink">
            复制链接
          </el-button>
        </div>
        <div class="share-tip">
          <el-tag size="small" type="info">
            如果设置了访问码，记得带上访问码
          </el-tag>
        </div>
      </div>
    </el-dialog>

    <!-- 设置截止时间对话框 -->
    <el-dialog
        v-model="setDeadlineDialogVisible"
        title="设置截止时间"
        width="500px"
        @close="handleSetDeadlineDialogClose"
    >
      <el-form
          ref="setDeadlineFormRef"
          :model="setDeadlineForm"
          :rules="setDeadlineRules"
          label-width="100px"
      >
        <el-form-item label="无限期">
          <el-checkbox v-model="setDeadlineForm.isInfinite">设置为无限期</el-checkbox>
        </el-form-item>
        <el-form-item label="截止时间" prop="deadline" v-if="!setDeadlineForm.isInfinite">
          <el-date-picker
              v-model="setDeadlineForm.deadline"
              type="datetime"
              placeholder="请选择截止时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            访问链接过期后将无法使用
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="setDeadlineDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSetDeadline">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="GiteeAnalysis">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import * as echarts from 'echarts'
import {
  DataAnalysis,
  RefreshRight,
  Document,
  InfoFilled,
  Reading,
  Setting,
  CircleCheck,
  MagicStick,
  Loading,
  Key,
  Plus,
  Share
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
  parseResume,
  getResumeStatus,
  getAccessCodeList,
  addAccessCode,
  batchAddAccessCode,
  getAccessData,
  getAccessLog,
  updateDeadLine,
  updateAccessCodeAvailable
} from '@/api/business/gitee/report'
import {
  getMyConfig,
  addYuanqiConfig,
  updateYuanqiConfig
} from '@/api/business/content/dailyassistant/yuanqiConfig'
import useUserStore from '@/store/modules/user'
import { parseTime } from '@/utils/FBSir'

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

// CV解析智能体相关状态
const cvAgentConfigured = ref(false)
const cvConfigDialogVisible = ref(false)
const cvConfigFormRef = ref(null)
const cvConfigForm = ref({
  id: null,
  agentId: '',
  agentName: '',
  apiKey: '',
  apiEndpoint: 'https://yuanqi.tencent.com/openapi/v1/agent/chat/completions',
  isActive: 1
})
const cvIsAgentIdEncrypted = ref(false)
const cvIsApiKeyEncrypted = ref(false)

const resumeGenerating = ref(false)
const resumeData = ref({
  cvName: '',
  name: '',
  phone: '',
  mail: '',
  shortlink: '',
  updateTime: '',
  parseContent: '',
  processStatus: null,
  deadline: ''
})

const accessCodeList = ref([])
const accessCodeLoading = ref(false)
const accessCodePagination = ref({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const addAccessCodeDialogVisible = ref(false)
const addAccessCodeFormRef = ref(null)
const addAccessCodeForm = ref({
  accessibleCount: 1,
  deadline: ''
})
const addAccessCodeRules = {
  accessibleCount: [
    { required: true, message: '请输入可访问次数', trigger: 'blur' },
    { type: 'number', min: -1, message: '可访问次数必须大于等于-1', trigger: 'blur' }
  ],
  deadline: [
    { required: true, message: '请选择截止时间', trigger: 'change' }
  ]
}

const batchAddAccessCodeDialogVisible = ref(false)
const batchAddAccessCodeFormRef = ref(null)
const batchAddAccessCodeForm = ref({
  deadline: '',
  count: 1
})
const batchAddAccessCodeRules = {
  deadline: [
    { required: true, message: '请选择截止时间', trigger: 'change' }
  ],
  count: [
    { required: true, message: '请输入生成数量', trigger: 'blur' },
    { type: 'number', min: 1, max: 10, message: '生成数量必须在1-10之间', trigger: 'blur' }
  ]
}

const setDeadlineDialogVisible = ref(false)
const setDeadlineFormRef = ref(null)
const setDeadlineForm = ref({
  deadline: '',
  isInfinite: false
})
const setDeadlineRules = {
  deadline: []
}

const shareDialogVisible = ref(false)

const accessCodeEnabled = ref(true)
const accessCodeSwitchLoading = ref(false)

const monitoringSubTab = ref('accessData')
const accessDataLoading = ref(false)
const accessDataList = ref([])
const accessDataChartRef = ref(null)
const accessDateRange = ref([])
const accessDataTotal = ref(0)

const accessLogLoading = ref(false)
const accessLogList = ref([])
const accessLogPagination = ref({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const getDefaultDateRange = () => {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 6)
  const formatDate = (date) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }
  return [formatDate(start), formatDate(end)]
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

const loadCvAgentConfig = async () => {
  try {
    const response = await getMyConfig('cv_parse')
    if (response.code === 200 && response.data) {
      cvAgentConfigured.value = !!(response.data.agentId && response.data.apiKey)
    } else {
      cvAgentConfigured.value = false
    }
  } catch (error) {
    cvAgentConfigured.value = false
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

const showCvConfigDialog = async () => {
  try {
    const response = await getMyConfig('cv_parse')
    if (response.code === 200 && response.data) {
      const config = { ...response.data }
      if (!config.apiEndpoint) {
        config.apiEndpoint = 'https://yuanqi.tencent.com/openapi/v1/agent/chat/completions'
      }
      cvIsAgentIdEncrypted.value = config.agentId === MASKED_VALUE
      cvIsApiKeyEncrypted.value = config.apiKey === MASKED_VALUE
      if (cvIsAgentIdEncrypted.value) {
        config.agentId = ''
      }
      if (cvIsApiKeyEncrypted.value) {
        config.apiKey = ''
      }
      cvConfigForm.value = config
    } else {
      cvConfigForm.value.apiEndpoint = 'https://yuanqi.tencent.com/openapi/v1/agent/chat/completions'
      cvIsAgentIdEncrypted.value = false
      cvIsApiKeyEncrypted.value = false
    }
  } catch (error) {
    ElMessage.error('加载配置失败')
  }
  cvConfigDialogVisible.value = true
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

const handleSaveCvConfig = async () => {
  try {
    await cvConfigFormRef.value.validate()
    ElMessage.info('正在保存配置，请稍候...')
    const configData = {
      ...cvConfigForm.value,
      businessType: 'cv_parse'  // 指定业务类型为CV解析
    }
    if (!configData.agentId && cvIsAgentIdEncrypted.value) {
      configData.agentId = MASKED_VALUE
    }
    if (!configData.apiKey && cvIsApiKeyEncrypted.value) {
      configData.apiKey = MASKED_VALUE
    }
    const apiFunc = cvConfigForm.value.id ? updateYuanqiConfig : addYuanqiConfig
    const response = await apiFunc(configData)
    if (response.code === 200) {
      ElMessage.success('配置保存成功')
      cvConfigDialogVisible.value = false
      await loadCvAgentConfig()
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

const handleCvConfigDialogClose = () => {
  cvConfigFormRef.value?.resetFields()
  cvIsAgentIdEncrypted.value = false
  cvIsApiKeyEncrypted.value = false
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

const handleCvAgentIdFocus = () => {
  if (cvIsAgentIdEncrypted.value && !cvConfigForm.value.agentId) {
    // 用户可输入新值
  }
}

const handleCvApiKeyFocus = () => {
  if (cvIsApiKeyEncrypted.value && !cvConfigForm.value.apiKey) {
    // 用户可输入新值
  }
}

const handleGenerateResume = async () => {
  if (resumeGenerating.value) return
  resumeGenerating.value = true

  try {
    const response = await parseResume()
    if (response.code === 200) {
      ElMessage.success('简历解析已启动，正在处理中...')

      let pollCount = 0
      const maxPolls = 150

      const pollInterval = setInterval(async () => {
        pollCount++

        try {
          const statusRes = await getResumeStatus()
          console.log('轮询查询简历状态:', statusRes)
          if (statusRes.code === 200 && statusRes.data) {
            resumeData.value = statusRes.data

            if (statusRes.data.processStatus == 1) {
              clearInterval(pollInterval)
              resumeGenerating.value = false
              ElNotification({
                title: '简历解析完成',
                message: '简历已成功解析并生成',
                type: 'success',
                duration: 4000
              })
            } else if (pollCount >= maxPolls) {
              clearInterval(pollInterval)
              resumeGenerating.value = false
              ElNotification({
                title: '解析超时',
                message: '简历解析超时（超过5分钟），请稍后重试',
                type: 'warning',
                duration: 0
              })
            }
          }
        } catch (error) {
          console.error('查询简历状态失败:', error)
          clearInterval(pollInterval)
          resumeGenerating.value = false
          ElNotification({
            title: '查询失败',
            message: '查询简历状态失败，请稍后重试',
            type: 'error',
            duration: 0
          })
        }
      }, 2000)
    } else {
      resumeGenerating.value = false
      ElMessage.error(response.msg || '启动简历解析失败')
    }
  } catch (error) {
    resumeGenerating.value = false
    ElMessage.error('启动简历解析失败：' + (error.message || '未知错误'))
  }
}

const handleRefreshResume = async () => {
  try {
    const response = await getResumeStatus()
    if (response.code === 200 && response.data) {
      resumeData.value = response.data
      ElMessage.success('简历数据已刷新')
    }
  } catch (error) {
    ElMessage.error('刷新失败，请稍后重试')
  }
}

const loadAccessCodeList = async () => {
  accessCodeLoading.value = true
  try {
    const response = await getAccessCodeList({
      PageNum: accessCodePagination.value.pageNum,
      PageSize: accessCodePagination.value.pageSize
    })
    if (response.code === 200) {
      accessCodeList.value = response.rows || []
      accessCodePagination.value.total = response.total || 0
    }
  } catch (error) {
    ElMessage.error('加载访问码列表失败')
  } finally {
    accessCodeLoading.value = false
  }
}

const handleAccessCodePageChange = (page) => {
  accessCodePagination.value.pageNum = page
  loadAccessCodeList()
}

const handleAccessCodeSizeChange = (size) => {
  accessCodePagination.value.pageSize = size
  accessCodePagination.value.pageNum = 1
  loadAccessCodeList()
}

const isDeadlineExpired = (deadline) => {
  if (!deadline) return false
  const deadlineTime = new Date(deadline).getTime()
  return deadlineTime < Date.now()
}

const showAddAccessCodeDialog = () => {
  addAccessCodeForm.value = {
    accessibleCount: 1,
    deadline: ''
  }
  addAccessCodeDialogVisible.value = true
}

const showBatchAddAccessCodeDialog = () => {
  batchAddAccessCodeForm.value = {
    deadline: '',
    count: 1
  }
  batchAddAccessCodeDialogVisible.value = true
}

const handleAddAccessCode = async () => {
  if (!addAccessCodeFormRef.value) return

  await addAccessCodeFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const response = await addAccessCode(addAccessCodeForm.value)
        if (response.code === 200) {
          ElMessage.success('访问码添加成功')
          addAccessCodeDialogVisible.value = false
          loadAccessCodeList()
        }
      } catch (error) {
        ElMessage.error('添加访问码失败')
      }
    }
  })
}

const handleBatchAddAccessCode = async () => {
  if (!batchAddAccessCodeFormRef.value) return

  await batchAddAccessCodeFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const response = await batchAddAccessCode({
          Time: batchAddAccessCodeForm.value.deadline,
          count: batchAddAccessCodeForm.value.count
        })
        if (response.code === 200) {
          ElMessage.success(`成功生成 ${batchAddAccessCodeForm.value.count} 个访问码`)
          batchAddAccessCodeDialogVisible.value = false
          loadAccessCodeList()
        }
      } catch (error) {
        ElMessage.error('批量生成访问码失败')
      }
    }
  })
}

const handleAddAccessCodeDialogClose = () => {
  addAccessCodeFormRef.value?.resetFields()
}

const handleBatchAddAccessCodeDialogClose = () => {
  batchAddAccessCodeFormRef.value?.resetFields()
}

const showSetDeadlineDialog = () => {
  setDeadlineForm.value = {
    deadline: resumeData.value.deadline || ''
  }
  setDeadlineDialogVisible.value = true
}

// 监听截止时间变化，当设置了截止时间时，自动取消无限期选项
watch(() => setDeadlineForm.value.deadline, (newValue) => {
  if (newValue) {
    setDeadlineForm.value.isInfinite = false
  }
})

// 监听无限期选项变化，当设置为无限期时，清空截止时间
watch(() => setDeadlineForm.value.isInfinite, (newValue) => {
  if (newValue) {
    setDeadlineForm.value.deadline = ''
  }
})

const showShareDialog = () => {
  shareDialogVisible.value = true
}

const handleCopyLink = () => {
  if (resumeData.value.shortlink) {
    navigator.clipboard.writeText(resumeData.value.shortlink).then(() => {
      ElMessage.success('链接已复制')
    }).catch(() => {
      ElMessage.error('复制失败，请手动复制')
    })
  }
}

const handleAccessCodeSwitchChange = async (value) => {
  accessCodeSwitchLoading.value = true
  try {
    const available = value ? 1 : 0
    const response = await updateAccessCodeAvailable(available)
    if (response.code === 200) {
      ElMessage.success(value ? '访问码已启用' : '访问码已禁用')
    }
  } catch (error) {
    ElMessage.error('操作失败，请稍后重试')
    accessCodeEnabled.value = !value
  } finally {
    accessCodeSwitchLoading.value = false
  }
}

const handleSetDeadline = async () => {
  if (!setDeadlineFormRef.value) return

  await setDeadlineFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const deadline = setDeadlineForm.value.isInfinite ? null : setDeadlineForm.value.deadline
        const response = await updateDeadLine(deadline)
        if (response.code === 200) {
          ElMessage.success('截止时间设置成功')
          setDeadlineDialogVisible.value = false
          resumeData.value.deadline = deadline || ''
        }
      } catch (error) {
        ElMessage.error('设置截止时间失败')
      }
    }
  })
}

const handleSetDeadlineDialogClose = () => {
  setDeadlineFormRef.value?.resetFields()
}

const loadAccessData = async () => {
  accessDataLoading.value = true
  try {
    const params = {}
    if (!accessDateRange.value || accessDateRange.value.length !== 2) {
      accessDateRange.value = getDefaultDateRange()
    }
    if (accessDateRange.value && accessDateRange.value.length === 2) {
      params.beginTime = accessDateRange.value[0]
      params.endTime = accessDateRange.value[1]
    }
    const response = await getAccessData(params)
    if (response.code === 200) {
      accessDataList.value = response.rows || []
      accessDataTotal.value = response.total || 0
      renderAccessDataChart()
    }
  } catch (error) {
    ElMessage.error('加载访问数据失败')
  } finally {
    accessDataLoading.value = false
  }
}

const renderAccessDataChart = () => {
  if (!accessDataChartRef.value) return

  const chartInstance = echarts.init(accessDataChartRef.value)
  const dates = accessDataList.value.map(item => {
    if (!item.date) return ''
    const dateStr = String(item.date)
    return dateStr.split(' ')[0]
  })
  const counts = accessDataList.value.map(item => item.count)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>访问量: {c}'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value',
      name: '访问量'
    },
    series: [
      {
        name: '访问量',
        type: 'line',
        smooth: true,
        data: counts,
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
              { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
            ]
          }
        },
        itemStyle: {
          color: '#409EFF'
        },
        lineStyle: {
          width: 2
        }
      }
    ]
  }

  chartInstance.setOption(option)

  window.addEventListener('resize', () => {
    chartInstance.resize()
  })
}

const loadAccessLog = async () => {
  accessLogLoading.value = true
  try {
    const response = await getAccessLog({
      PageNum: accessLogPagination.value.pageNum,
      PageSize: accessLogPagination.value.pageSize
    })
    if (response.code === 200) {
      accessLogList.value = response.rows || []
      accessLogPagination.value.total = response.total || 0
    }
  } catch (error) {
    ElMessage.error('加载访问日志失败')
  } finally {
    accessLogLoading.value = false
  }
}

const handleAccessLogPageChange = (page) => {
  accessLogPagination.value.pageNum = page
  loadAccessLog()
}

const handleAccessLogSizeChange = (size) => {
  accessLogPagination.value.pageSize = size
  accessLogPagination.value.pageNum = 1
  loadAccessLog()
}


onMounted(() => {
  loadReports()
  loadLatestAnalysis()
  loadMyConfig()
  loadCvAgentConfig()
})

watch(activeTab, (newTab) => {
  if (newTab === 'accessCode') {
    loadAccessCodeList()
  } else if (newTab === 'monitoring') {
    loadAccessData()
  }
})

watch(monitoringSubTab, (newSubTab) => {
  if (newSubTab === 'accessLog') {
    loadAccessLog()
  }
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


  .resume-data-section {
    margin-top: 20px;
  }

  .resume-info {
    margin-bottom: 20px;
  }

  .ai-analysis-section {
    margin-top: 20px;
  }

  .analysis-list {
    margin: 0;
    padding-left: 18px;
    color: #606266;
    font-size: 13px;
    line-height: 1.8;
  }

  .analysis-list li {
    margin-bottom: 8px;
  }

  .analysis-list li:last-child {
    margin-bottom: 0;
  }

  .parsing-status {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 40px 20px;
    color: #909399;

    .el-icon {
      font-size: 32px;
      margin-bottom: 12px;
      color: #409eff;
    }

    p {
      margin: 4px 0;
      font-size: 14px;
    }

    .tip {
      font-size: 12px;
      color: #c0c4cc;
    }
  }

  .analysis-content {
    p {
      font-size: 14px;
      color: #303133;
      margin-bottom: 12px;
      font-weight: 500;
    }
  }

  .parse-content-text {
    white-space: pre-wrap;
    word-wrap: break-word;
    color: #606266;
    font-size: 13px;
    line-height: 1.8;
    background-color: #f5f7fa;
    padding: 12px;
    border-radius: 4px;
  }

  .deadline-item {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .access-link-item {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .share-dialog-content {
    padding: 10px 0;
  }

  .share-description {
    font-size: 14px;
    color: #606266;
    margin-bottom: 16px;
  }

  .share-link-container {
    margin-bottom: 16px;
  }

  .share-tip {
    margin-top: 12px;
  }

  .access-code-section {
    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;

      .section-title {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 0;
      }
    }

    .access-code-table {
      margin-bottom: 16px;
    }

    .pagination {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }

    .text-danger {
      color: #f56c6c;
    }
  }

  .monitoring-section {
    .monitoring-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;

      .date-range-picker {
        display: flex;
        align-items: center;
        gap: 10px;
      }
    }

    .chart-container {
      min-height: 400px;
      position: relative;

      .access-data-chart {
        width: 100%;
        height: 400px;
      }
    }

    .access-log-table {
      margin-bottom: 16px;
    }
  }

}
</style>
