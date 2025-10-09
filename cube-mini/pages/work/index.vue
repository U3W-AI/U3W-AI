<template>
	<view class="console-container">
		<!-- 顶部固定区域 -->
		<view class="header-fixed">
			<view class="header-content" :style="{ paddingTop: statusBarHeight + 'px' }">
				<text class="header-title">AI控制台</text>
				<view class="header-actions">
					<view class="action-btn refresh-btn" @tap="refreshAiStatus">
						<image class="action-icon-img" src="https://u3w.com/chatfile/shuaxin.png" mode="aspectFit">
						</image>

            <!-- 连接状态指示器 -->
            <view class="connection-indicator" :class="[socketTask ? 'connected' : 'disconnected']"></view>
					</view>
					<view class="action-btn history-btn" @tap="showHistoryDrawer">
						<image class="action-icon-img" src="https://u3w.com/chatfile/lishi.png" mode="aspectFit">
						</image>
					</view>
					<view class="action-btn new-chat-btn" @tap="createNewChat">
						<image class="action-icon-img" src="https://u3w.com/chatfile/chuangjian.png" mode="aspectFit">
						</image>
					</view>
				</view>
			</view>


		</view>

		<!-- 主体滚动区域 -->
		<scroll-view class="main-scroll" scroll-y :scroll-into-view="scrollIntoView" :enhanced="true" :bounces="true"
			:show-scrollbar="false" :fast-deceleration="false"
			:style="{ height: 'calc(100vh - ' + (statusBarHeight + 100) + 'px)', paddingTop: (statusBarHeight + 60) + 'px' }">

			<!-- AI配置区块 -->
			<view class="section-block" id="ai-config">
				<view class="section-header" @tap="toggleSection('aiConfig')">
					<text class="section-title">AI选择配置</text>
					<text class="section-arrow">
						{{ sectionExpanded.aiConfig ? '▼' : '▶' }}
					</text>
				</view>
				<view class="section-content" v-if="sectionExpanded.aiConfig">
					<view class="ai-grid">
						<view v-for="(ai, index) in aiList" :key="index" class="ai-card"
							:class="[ai.enabled && isAiLoginEnabled(ai) ? 'ai-enabled' : '', !isAiLoginEnabled(ai) ? 'ai-disabled' : '']">
							<view class="ai-header">
								<!-- <image class="ai-avatar" :src="ai.avatar" mode="aspectFill" :class="[!isAiLoginEnabled(ai) ? 'avatar-disabled' : '']"></image> -->
								<view class="ai-info">
									<view class="ai-name-container">
										<text class="ai-name" :class="[!isAiLoginEnabled(ai) ? 'name-disabled' : '']">{{
											ai.name }}</text>
                    <text
                        v-if="!isAiLoginEnabled(ai) && !isAiInLoading(ai)"
                          class="login-required"
                    >
                      需登录
                    </text>
										<text v-if="isAiInLoading(ai)" class="loading-text">检查中...</text>
									</view>
									<switch :checked="ai.enabled && isAiLoginEnabled(ai)"
										:disabled="!isAiLoginEnabled(ai) || isAiInLoading(ai)"
										@change="toggleAI(ai, $event)" color="#409EFF" style="transform: scale(0.8);" />
								</view>
							</view>
							<!-- 腾讯元宝模型选择 -->
							<view v-if="ai.name === '腾讯元宝' && ai.models" class="model-selection">
								<text class="model-label">模型选择:</text>
								<picker mode="selector" :range="ai.models" range-key="label" :value="getModelIndex(ai)" 
									@change="onModelChange(ai, $event)" :disabled="!ai.enabled || !isAiLoginEnabled(ai)">
									<view class="model-picker">
										<text class="model-text">{{ getSelectedModelLabel(ai) }}</text>
										<text class="picker-arrow">▼</text>
									</view>
								</picker>
							</view>
              <view class="ai-capabilities" v-if="ai.capabilities.length > 0">
                <!-- 通义千问和知乎直答使用单选按钮逻辑 -->
                <view v-if="ai.name === '通义千问' || ai.name === '知乎直答'" class="capability-tags-container">
                  <view v-for="(capability, capIndex) in ai.capabilities"
                        :key="capIndex"
                        class="capability-tag"
                        :class="[ai.selectedCapability === capability.value ? 'capability-active' : '', (!ai.enabled || !isAiLoginEnabled(ai)) ? 'capability-disabled' : '']"
                        @tap="selectSingleCapability(ai, capability.value)">
                    <text class="capability-text">{{ capability.label }}</text>
                  </view>
                </view>
                <!-- 其他ai使用原有逻辑 -->
                <view v-else class="capability-tags-container">
                  <view v-for="(capability, capIndex) in ai.capabilities"
                        :key="capIndex"
                        class="capability-tag"
                        :class="[
                          ai.isSingleSelect
                            ? (ai.selectedCapabilities === capability.value ? 'capability-active' : '')
                            : (ai.selectedCapabilities.includes(capability.value) ? 'capability-active' : ''),
                          (!ai.enabled || !isAiLoginEnabled(ai)) ? 'capability-disabled' : ''
                        ]"
                        @tap="toggleCapability(ai, capability.value)">
                    <text class="capability-text">{{ capability.label }}</text>
                  </view>
                </view>
              </view>
						</view>
					</view>
				</view>
			</view>

			<!-- 提示词输入区块 -->
			<view class="section-block" id="prompt-input">
				<view class="section-header" @tap="toggleSection('promptInput')">
					<text class="section-title">提示词输入</text>
					<text class="section-arrow">
						{{ sectionExpanded.promptInput ? '▼' : '▶' }}
					</text>
				</view>
				<view class="section-content" v-if="sectionExpanded.promptInput">
					<textarea class="prompt-textarea" v-model="promptInput" placeholder="请输入提示词" maxlength="2000"
						show-confirm-bar="false" auto-height></textarea>
					<view class="prompt-footer">
						<text class="word-count">{{ promptInput.length }}/2000</text>
						<button class="send-btn" :class="[!canSend ? 'send-btn-disabled' : '']" :disabled="!canSend"
							@tap="sendPrompt">
							发送
						</button>
					</view>
				</view>
			</view>

			<!-- 执行状态区块 -->
			<view class="section-block" v-if="taskStarted" id="task-status">
				<view class="section-header" @tap="toggleSection('taskStatus')">
					<text class="section-title">任务执行状态</text>
					<text class="section-arrow">
						{{ sectionExpanded.taskStatus ? '▼' : '▶' }}
					</text>
				</view>
				<view class="section-content" v-if="sectionExpanded.taskStatus">
					<!-- 任务流程 -->
					<view class="task-flow">
						<view v-for="(ai, index) in enabledAIs" :key="index" class="task-item">
							<view class="task-header" @tap="toggleTaskExpansion(ai)">
								<view class="task-left">
									<text class="task-arrow">
										{{ ai.isExpanded ? '▼' : '▶' }}
									</text>
									<image class="task-avatar" :src="ai.avatar" mode="aspectFill"></image>
									<text class="task-name">{{ ai.name }}</text>
								</view>
								<view class="task-right">
									<text class="status-text">{{ getStatusText(ai.status) }}</text>
									<text class="status-icon" :class="[getStatusIconClass(ai.status)]">
										{{ getStatusEmoji(ai.status) }}
									</text>
								</view>
							</view>
							<!-- 进度日志 -->
							<view class="progress-logs" v-if="ai.isExpanded && ai.progressLogs.length > 0">
								<view v-for="(log, logIndex) in ai.progressLogs" :key="logIndex" class="progress-item">
									<view class="progress-dot" :class="[log.isCompleted ? 'dot-completed' : '']"></view>
									<view class="progress-content">
										<text class="progress-time">{{ formatTime(log.timestamp) }}</text>
										<text class="progress-text">{{ log.content }}</text>
									</view>
								</view>
							</view>
						</view>
					</view>

					<!-- 主机可视化 -->
					<!-- 	<view class="screenshots-section" v-if="screenshots.length > 0">
						<view class="screenshots-header">
							<text class="section-subtitle">主机可视化</text>
							<switch :checked="autoPlay" @change="toggleAutoPlay" color="#409EFF"
								style="transform: scale(0.8);" />
							<text class="auto-play-text">自动轮播</text>
						</view>
						<swiper class="screenshots-swiper" :autoplay="autoPlay" :interval="3000" :duration="500"
							indicator-dots indicator-color="rgba(255,255,255,0.5)" indicator-active-color="#409EFF">
							<swiper-item v-for="(screenshot, index) in screenshots" :key="index">
								<image class="screenshot-image" :src="screenshot" mode="aspectFit"
									@tap="previewImage(screenshot)"></image>
							</swiper-item>
						</swiper>
					</view> -->
				</view>
			</view>

			<!-- 结果展示区块 -->
			<view class="section-block" v-if="results.length > 0" id="results">
				<view class="section-header">
					<text class="section-title">执行结果</text>
					<button class="score-btn" size="mini" @tap="showScoreModal">智能评分</button>
				</view>
				<view class="section-content">
					<!-- 结果选项卡 -->
					<scroll-view class="result-tabs" scroll-x>
						<view class="tab-container">
							<view v-for="(result, index) in results" :key="index" class="result-tab"
								:class="[activeResultIndex === index ? 'tab-active' : '']"
								@tap="switchResultTab(index)">
								<text class="tab-text">{{ result.aiName }}</text>
							</view>
						</view>
					</scroll-view>

					<!-- 结果内容 -->
					<view class="result-content" v-if="currentResult">
						<!-- 结果标题 -->
						<!-- <view class="result-header">
							<text class="result-title">{{ currentResult.aiName }}的执行结果</text>
						</view> -->

						<!-- 操作按钮 -->
						<view class="result-actions">
							<button class="share-link-btn" size="mini" v-if="currentResult.shareUrl"
								@tap="openShareUrl(currentResult.shareUrl)">
								复制原链接
							</button>
							<button class="action-btn-small" size="mini"
								@tap="copyResult(currentResult.content)">复制(纯文本)</button>
							<button v-if="!currentResult.aiName.includes('智能排版')" class="collect-btn" size="mini"
								@tap="showLayoutModal">智能排版</button>
							<button v-else class="collect-btn" size="mini"
								@tap="handleDirectPushToWechat(currentResult)">投递到{{ getMediaLabelFromResult(currentResult) }}</button>
						</view>

						<!-- 分享图片或内容 -->
						<view class="result-body">
							<!-- 图片内容 -->
							<view v-if="currentResult.shareImgUrl && isImageFile(currentResult.shareImgUrl)"
								class="result-image-container">
								<image class="result-image" :src="currentResult.shareImgUrl" mode="widthFix"
									@tap="previewImage(currentResult.shareImgUrl)"></image>
							</view>
							<!-- PDF文件内容 -->
							<view v-else-if="currentResult.shareImgUrl && isPdfFile(currentResult.shareImgUrl)"
								class="result-pdf-container">
								<view class="pdf-placeholder">
									<view class="pdf-icon">📄</view>
									<text class="pdf-text">PDF文件</text>
									<view class="pdf-actions">
										<button class="pdf-btn download-btn" size="mini"
											@tap="openPdfFile(currentResult.shareImgUrl)">
											打开文件
										</button>
										<button class="pdf-btn copy-btn" size="mini"
											@tap="copyPdfUrl(currentResult.shareImgUrl)">
											复制链接
										</button>
									</view>
								</view>
							</view>
              <!-- 文字内容 -->
              <view v-else class="result-text">
                <!-- 特殊处理DeepSeek响应 -->
                <rich-text v-if="currentResult.aiName === 'DeepSeek'" :nodes="currentResult.content"></rich-text>
                <rich-text v-else :nodes="renderMarkdown(currentResult.content)"></rich-text>
              </view>
						</view>
					</view>
				</view>
			</view>
		</scroll-view>

		<!-- 历史记录抽屉 -->
		<view v-if="historyDrawerVisible" class="drawer-mask" @tap="closeHistoryDrawer">
			<view class="drawer-container" @tap.stop>
				<view class="drawer-content">
					<view class="drawer-header">
						<text class="drawer-title">历史会话记录</text>
						<text class="close-icon" @tap="closeHistoryDrawer">✕</text>
					</view>
					<scroll-view class="history-list" scroll-y>
						<view v-for="(group, date) in groupedHistory" :key="date" class="history-group">
							<text class="history-date">{{ date }}</text>
							<view v-for="(item, index) in group" :key="index" class="history-item"
								@tap="loadHistoryItem(item)">
								<text class="history-prompt">{{ item.userPrompt }}</text>
								<text class="history-time">{{ formatHistoryTime(item.createTime) }}</text>
							</view>
						</view>
					</scroll-view>
				</view>
			</view>
		</view>

		<!-- 智能评分弹窗 -->
		<view v-if="scoreModalVisible" class="popup-mask" @tap="closeScoreModal">
			<view class="score-modal" @tap.stop>
				<view class="score-header">
					<text class="score-title">智能评分</text>
					<text class="close-icon" @tap="closeScoreModal">✕</text>
				</view>
				<view class="score-content">
					<!-- AI选择 -->
					<view class="ai-selection-section">
						<text class="score-subtitle">选择评分AI：</text>
						<view class="ai-radio-group">
							<view class="ai-radio-item"
								:class="{'active': scoreAI === '豆包'}"
								@tap="selectScoreAI('豆包')">
								<text class="ai-icon">🤖</text>
								<text class="ai-text">豆包</text>
							</view>
							<view class="ai-radio-item"
								:class="{'active': scoreAI === 'DeepSeek'}"
								@tap="selectScoreAI('DeepSeek')">
								<text class="ai-icon">🧠</text>
								<text class="ai-text">DeepSeek</text>
							</view>
						</view>
					</view>
					<view class="score-prompt-section">
						<text class="score-subtitle">评分提示词：</text>
					<!-- 评分模板选择器 -->
					<view class="template-selector" v-if="scorePromptList.length > 0">
						<picker mode="selector" :range="scorePromptList" range-key="name" :value="getScorePromptIndex()" 
							@change="onScorePromptChange">
							<view class="picker-display">
								<text class="picker-text">{{ selectedScorePrompt || '选择评分模板（可选）' }}</text>
								<text class="picker-arrow">▼</text>
							</view>
						</picker>
					</view>
						<textarea class="score-textarea" v-model="scorePrompt"
							placeholder="请输入评分提示词，例如：请从内容质量、逻辑性、创新性等方面进行评分" maxlength="1000"></textarea>
					</view>
					<view class="score-selection">
						<text class="score-subtitle">选择要评分的内容：</text>
						<checkbox-group @change="toggleResultSelection">
							<view class="score-checkboxes">
								<label v-for="(result, index) in results" :key="index" class="checkbox-item">
									<checkbox :value="result.aiName"
										:checked="selectedResults.includes(result.aiName)" />
									<text class="checkbox-text">{{ result.aiName }}</text>
								</label>
							</view>
						</checkbox-group>
					</view>

					<button class="score-submit-btn" :disabled="!canScore" @tap="handleScore">
						开始评分
					</button>
				</view>
			</view>
		</view>

    <!-- 媒体投递弹窗 -->
    <view v-if="layoutModalVisible" class="popup-mask" @tap="closeLayoutModal">
      <view class="score-modal" @tap.stop>
        <view class="score-header">
          <text class="score-title">智能排版设置</text>
          <text class="close-icon" @tap="closeLayoutModal">✕</text>
        </view>
        <view class="score-content">
          <!-- AI选择 -->
          <view class="ai-selection-section">
            <text class="score-subtitle">选择排版AI：</text>
            <view class="ai-radio-group">
              <view class="ai-radio-item"
                    :class="{'active': layoutAI === '豆包'}"
                    @tap="selectLayoutAI('豆包')">
                <text class="ai-icon">🤖</text>
                <text class="ai-text">豆包</text>
              </view>
              <view class="ai-radio-item"
                    :class="{'active': layoutAI === 'DeepSeek'}"
                    @tap="selectLayoutAI('DeepSeek')">
                <text class="ai-icon">🧠</text>
                <text class="ai-text">DeepSeek</text>
              </view>
            </view>
          </view>

          <!-- 媒体选择 -->
          <view class="media-selection-section">
            <text class="score-subtitle">选择投递媒体：</text>
            <view class="media-radio-group">
              <view class="media-radio-item"
                    :class="{'active': selectedMedia === 'wechat_layout'}"
                    @tap="selectMedia('wechat_layout')">
                <text class="media-icon">📱</text>
                <text class="media-text">公众号</text>
              </view>
              <view class="media-radio-item"
                    :class="{'active': selectedMedia === 'zhihu_layout'}"
                    @tap="selectMedia('zhihu_layout')">
                <text class="media-icon">📝</text>
                <text class="media-text">知乎</text>
              </view>
            </view>
            <view class="media-description">
              <text v-if="selectedMedia === 'wechat_layout'" class="description-text">
                📝 将内容排版为适合微信公众号的HTML格式，完成后可手动投递到草稿箱
              </text>
              <text v-else-if="selectedMedia === 'zhihu_layout'" class="description-text">
                📝 将内容排版为适合知乎的文本格式，完成后可手动投递到草稿箱
              </text>
            </view>
          </view>

          <button class="score-submit-btn" :disabled="!currentLayoutResult" @tap="handleLayout">
            开始排版
          </button>
        </view>
			</view>
		</view>

		<!-- 微头条文章编辑弹窗 -->
		<view v-if="tthArticleEditVisible" class="popup-mask" @tap="closeTthArticleEditModal">
			<view class="score-modal" @tap.stop>
				<view class="score-header">
					<text class="score-title">微头条文章编辑</text>
					<text class="close-icon" @tap="closeTthArticleEditModal">✕</text>
				</view>
				<view class="score-content">
					<view class="score-prompt-section">
						<text class="score-subtitle">文章标题：</text>
						<input type="text" v-model="tthArticleTitle" placeholder="请输入文章标题" maxlength="100" />
					</view>
					<view class="score-prompt-section">
						<text class="score-subtitle">文章内容：</text>
						<textarea
							class="score-textarea"
							:class="{ 'content-exceeded': isTthArticleContentExceeded }"
							v-model="tthArticleContent"
							placeholder="请输入文章内容"
							:maxlength="-1"
							:auto-height="true"
							:show-confirm-bar="false"
							:hold-keyboard="true"
							:adjust-position="false"
							@focus="handleTextareaFocus"
							rows="5">
						</textarea>
						<view class="char-count" :class="{ 'char-count-exceeded': isTthArticleContentExceeded }">
							{{ tthArticleContentLength }}/2000
						</view>
					</view>
					<button class="score-submit-btn" @tap="confirmTTHPublish">
						发布文章
					</button>
				</view>
			</view>
		</view>

		<!-- 微头条发布流程弹窗 -->
		<view v-if="tthFlowVisible" class="popup-mask" @tap="closeTthFlowDialog">
			<view class="score-modal" @tap.stop>
				<view class="score-header">
					<text class="score-title">微头条发布流程</text>
					<text class="close-icon" @tap="closeTthFlowDialog">✕</text>
				</view>
				<view class="score-content">
					<view class="score-prompt-section">
						<text class="score-subtitle">发布流程日志：</text>
						<scroll-view style="max-height: 200px;" scroll-y>
							<view v-for="(log, index) in tthFlowLogs" :key="index" style="margin-bottom: 10px;">
								<text style="color: #666;">{{ formatTime(log.timestamp) }}</text>
								<text style="margin-left: 10px;">{{ log.content }}</text>
							</view>
							<view v-if="tthFlowLogs.length === 0" style="text-align: center; color: #999; padding: 20px;">暂无流程日志...</view>
						</scroll-view>
					</view>
					<view class="score-prompt-section" v-if="tthFlowImages.length > 0">
						<text class="score-subtitle">发布流程图片：</text>
						<scroll-view style="max-height: 200px;" scroll-x>
							<image v-for="(img, idx) in tthFlowImages" :key="idx" :src="img" style="width: 120px; height: 120px; margin-right: 10px; border-radius: 8px;" mode="aspectFill" @tap="previewImage(img)" />
						</scroll-view>
					</view>
					<view style="display: flex; justify-content: center; margin-top: 20px;">
						<button class="score-submit-btn" style="width: 200px;" @tap="closeTthFlowDialog">关闭</button>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import {
		marked
	} from 'marked';
	import {
		message, saveUserChatData, getChatHistory, pushAutoOffice, getMediaCallWord, updateMediaCallWord, getScoreWord
  } from "@/api/wechat/aigc";
	import {
		v4 as uuidv4
	} from 'uuid';
	import storage from '@/utils/storage'
	import constant from '@/utils/constant'
  import { getToken } from '@/utils/auth';
  import { getCorpId, ensureCorpIdOnShow, ensureLatestCorpId } from '@/utils/corpId'

	export default {
		name: 'MiniConsole',
		data() {
			return {
				// 系统信息
				statusBarHeight: 0,
				
				// 用户信息
				userId: '',
				corpId: '',
				chatId: '',
				expandedHistoryItems: {},
				userInfoReq: {
					userPrompt: '',
					userId: '',
					corpId: '',
					taskId: '',
					roles: '',
					toneChatId: '',
					ybDsChatId: '',
					dbChatId: '',
          tyChatId: '',

          baiduChatId: '',
          zhzdChatId: '',
					isNewChat: true
				},
				jsonRpcReqest: {
					jsonrpc: '2.0',
					id: '',
					method: '',
					params: {}
				},

				// 区域展开状态
				sectionExpanded: {
					aiConfig: true,
					promptInput: true,
					taskStatus: true
				},

				// AI配置（参考PC端完整配置）
				aiList: [{
            name: 'DeepSeek',
            avatar: 'https://u3w.com/chatfile/Deepseek.png',
            capabilities: [{
              label: '深度思考',
              value: 'deep_thinking'
            },
              {
                label: '联网搜索',
                value: 'web_search'
              }
            ],
            selectedCapabilities: ['deep_thinking', 'web_search'],
            enabled: true,
            status: 'idle',
            progressLogs: [],
            isExpanded: true,
            isSingleSelect: false,  // 添加单选标记
          },
					{
						name: '豆包',
						avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
						capabilities: [{
							label: '深度思考',
							value: 'deep_thinking'
						}],
						selectedCapabilities: ['deep_thinking'],
						enabled: true,
						status: 'idle',
						progressLogs: [],
						isExpanded: true,
            isSingleSelect: false,  // 添加单选标记
					},
					{
						name: '腾讯元宝',
						avatar: 'https://u3w.com/chatfile/yuanbao.png',
						capabilities: [{
								label: '深度思考',
								value: 'deep_thinking'
							},
							{
								label: '联网搜索',
								value: 'web_search'
							}
						],
						selectedCapabilities: ['deep_thinking', 'web_search'],
						selectedModel: 'hunyuan', // 默认选择混元
						models: [
							{ label: '混元', value: 'hunyuan' },
							{ label: 'DeepSeek', value: 'deepseek' }
						],
						enabled: true,
						status: 'idle',
						progressLogs: [],
						isExpanded: true,
						isSingleSelect: false
					},
          {
            name: '通义千问',
            avatar: 'https://u3w.com/chatfile/TongYi.png',
            capabilities: [
              {
                label: '深度思考',
                value: 'deep_thinking'
              },
              {
                label: '联网搜索',
                value: 'web_search'
              }
            ],
            selectedCapability: '',
            enabled: true,
            status: 'idle',
            progressLogs: [],
            isExpanded: true
          },


          {
            name: "秘塔",
            avatar: 'https://www.aitool6.com/wp-content/uploads/2023/06/9557d1-2.jpg',
            capabilities: [
              { label: "极速", value: "fast" },
              { label: "极速思考", value: "fast_thinking" },
              { label: "长思考", value: "long_thinking" },
            ],
            selectedCapabilities:"fast",
            enabled: true,
            status: "idle",
            progressLogs: [],
            isExpanded: true,
            isSingleSelect: true  // 添加单选标记
          },

          {
            name: "知乎直答",
            avatar: 'https://u3w.com/chatfile/ZHZD.png',
            capabilities: [
              {
                label: "智能思考",
                value: "smart_thinking"
              },
              {
                label: "深度思考",
                value: "deep_thinking"
              },
              {
                label: "快速回答",
                value: "fast_answer"
              },
            ],
            selectedCapability: "smart_thinking", // 改为单选，默认智能思考
            enabled: true,
            status: 'idle',
            progressLogs: [],
            isExpanded: true,
            isSingleSelect: true, // 设为单选模式
          },
          {
            name: "百度AI",
            avatar: 'https://u3w.com/chatfile/baiduAI.png',
            capabilities: [
              { label: "深度搜索", value: "web_search" }
            ],
            selectedCapabilities: [],
            enabled: true,
            status: "idle",
            progressLogs: [],
            isExpanded: true,
          },



				],

				// 输入和任务状态
				promptInput: '',
				taskStarted: false,
				enabledAIs: [],

				// 可视化
				screenshots: [],
				autoPlay: false,

				// 结果
				results: [],
				activeResultIndex: 0,

				// 历史记录
				chatHistory: [],

				// 评分
				selectedResults: [],
				scorePrompt: '请你深度阅读以下几篇公众号文章，从多个维度进行逐项打分，输出评分结果。并在以下各篇文章的基础上博采众长，综合整理一篇更全面的文章。',
			scoreAI: '豆包', // 默认选择豆包作为评分AI
			scorePromptList: [], // 评分提示词模板列表
			selectedScorePrompt: '', // 选中的评分提示词模板名称

				// 收录计数器
				collectNum: 0,

				// 媒体投递
        layoutPrompt: '',
        layoutAI: '豆包', // 默认选择豆包作为排版AI
        selectedMedia: 'wechat_layout', // 默认选择公众号

				// 微头条相关
				tthArticleEditVisible: false, // 微头条文章编辑弹窗
				tthArticleTitle: '', // 微头条文章标题
				tthArticleContent: '', // 微头条文章内容
				tthFlowVisible: false, // 微头条发布流程弹窗
				tthFlowLogs: [], // 微头条发布流程日志
				tthFlowImages: [], // 微头条发布流程图片
				tthScoreContent: '', // 智能评分内容

			// 媒体列表配置
			mediaList: [
				{
					name: "wechat_layout",
					label: "公众号",
				},
				{
					name: "zhihu_layout",
					label: "知乎",
				},
				{
					name: "weitoutiao_layout",
					label: "微头条",
				},
				{
					name: "baijiahao_layout",
					label: "百家号",
				}
			],

				// WebSocket
				socketTask: null,
				reconnectTimer: null,
				heartbeatTimer: null,
				reconnectCount: 0,
				maxReconnectCount: 5,
				isConnecting: false,
				isRefreshing: false, // 防重复刷新标志
				scrollIntoView: '',

				// 弹窗状态
				historyDrawerVisible: false,
				scoreModalVisible: false,
				layoutModalVisible: false,
				currentLayoutResult: null, // 当前要排版的结果

				// AI登录状态
				aiLoginStatus: {
					yuanbao: false,
					doubao: false,
          deepseek: false,
          tongyi: false,
          metaso: false,
          zhzd: false,
          baidu: false
				},
				accounts: {
					yuanbao: '',
					doubao: '',
          deepseek: '',
          tongyi: '',
          metaso: '',
          zhzd: '',
          baidu: ''
				},
				isLoading: {
					yuanbao: true,
					doubao: true,
          deepseek: true,
          tongyi: true,
		      metaso: true,
          zhzd: true,
          baidu: true
				}
			};
		},

		computed: {
			canSend() {
				// 检查是否有输入内容
				const hasInput = this.promptInput.trim().length > 0;

				// 检查是否有可用的AI（既启用又已登录）
				const hasAvailableAI = this.aiList.some(ai => ai.enabled && this.isAiLoginEnabled(ai));

				// 检查是否正在加载AI状态（如果正在加载，禁用发送按钮）
				const isCheckingStatus = this.isLoading.yuanbao || this.isLoading.doubao || this.isLoading.deepseek || this.isLoading.tongyi || this.isLoading.metaso || this.isLoading.zhzd || this.isLoading.baidu;

				return hasInput && hasAvailableAI && !isCheckingStatus;
			},

			canScore() {
				const hasSelected = this.selectedResults.length > 0;
				const hasPrompt = this.scorePrompt.trim().length > 0;
				console.log('canScore - selectedResults:', this.selectedResults);
				console.log('canScore - scorePrompt length:', this.scorePrompt.trim().length);
				console.log('canScore - hasSelected:', hasSelected, 'hasPrompt:', hasPrompt);
				return hasSelected && hasPrompt;
			},

			currentResult() {
				return this.results[this.activeResultIndex] || null;
			},

			groupedHistory() {
				const groups = {};
				const chatGroups = {};

				// 首先按chatId分组
				this.chatHistory.forEach(item => {
					if (!chatGroups[item.chatId]) {
						chatGroups[item.chatId] = [];
					}
					chatGroups[item.chatId].push(item);
				});

				// 然后按日期分组，并处理父子关系
				Object.values(chatGroups).forEach(chatGroup => {
					// 按时间排序
					chatGroup.sort((a, b) => new Date(a.createTime) - new Date(b.createTime));

					// 获取最早的记录作为父级
					const parentItem = chatGroup[0];
					const date = this.getHistoryDate(parentItem.createTime);

					if (!groups[date]) {
						groups[date] = [];
					}

					// 添加父级记录
					groups[date].push({
						...parentItem,
						isParent: true,
						isExpanded: this.expandedHistoryItems[parentItem.chatId] || false,
						children: chatGroup.slice(1).map(child => ({
							...child,
							isParent: false
						}))
					});
				});

				return groups;
			},

			// 微头条文章内容字符数
			tthArticleContentLength() {
				return this.tthArticleContent ? this.tthArticleContent.length : 0;
			},

			// 检查微头条文章内容是否超过2000字
			isTthArticleContentExceeded() {
				return this.tthArticleContentLength > 2000;
			}
		},
		watch: {
			// 监听微头条文章内容变化，确保textarea正确显示
			tthArticleContent: {
				handler(newVal, oldVal) {
					// 当内容变化时，确保textarea正确显示
					this.$nextTick(() => {
						const textarea = this.$el.querySelector('.score-textarea');
						if (textarea && textarea.value !== newVal) {
							textarea.value = newVal;
						}
					});
				},
				immediate: false
			}
		},
		async onLoad() {
			// 获取状态栏高度（小程序环境）
			// #ifdef MP-WEIXIN
			const windowInfo = wx.getWindowInfo();
			this.statusBarHeight = windowInfo.statusBarHeight || 0;
			// #endif
			
			// #ifdef H5
			const systemInfo = uni.getSystemInfoSync();
			this.statusBarHeight = systemInfo.statusBarHeight || 0;
			// #endif
			
			await this.initUserInfo();

			// 监听企业ID更新事件
			uni.$on('corpIdUpdated', this.handleCorpIdUpdated);

			// 检查用户信息是否完整
			if (!this.userId || !this.corpId) {
				console.log('用户信息不完整，跳转到登录页面');
				uni.showModal({
					title: '提示',
					content: '请先登录后再使用',
					showCancel: false,
					confirmText: '去登录',
					success: () => {
						uni.navigateTo({
							url: '/pages/login/index'
						});
					}
				});
				return;
			}

			this.initWebSocket();
			this.loadChatHistory(0); // 加载历史记录
			this.loadLastChat(); // 加载上次会话
			this.initEnabledAIs(); // 初始化AI列表
			this.checkAiLoginStatus(); // 检查AI登录状态
		},

		onShow() {
			// 页面显示时确保企业ID最新
			this.ensureLatestCorpId();
		},

		onUnload() {
			// 移除事件监听
			uni.$off('corpIdUpdated', this.handleCorpIdUpdated);
			this.closeWebSocket();
		},

		methods: {
			// 从result的aiName中提取媒体标签
			getMediaLabelFromResult(result) {
				if (!result || !result.aiName) return '公众号';
				// 从 "智能排版公众号" 或 "智能排版知乎" 中提取媒体名称
				const mediaLabel = result.aiName.substring(4); // 去掉"智能排版"
				return mediaLabel || '公众号';
			},
			
			// 处理textarea获得焦点事件
			handleTextareaFocus() {
				// 确保textarea内容正确显示
				this.$nextTick(() => {
					const textarea = this.$el.querySelector('.score-textarea');
					if (textarea && textarea.value !== this.tthArticleContent) {
						textarea.value = this.tthArticleContent;
						// 触发input事件确保v-model同步
						textarea.dispatchEvent(new Event('input', { bubbles: true }));
					}
				});
			},

			// 初始化用户信息
			async initUserInfo() {
				// 从store获取用户信息，兼容缓存方式
				this.userId = storage.get(constant.userId);
				// 使用企业ID工具获取最新企业ID
				try {
					this.corpId = await getCorpId();
				} catch (error) {
					console.warn('获取企业ID失败，使用缓存值:', error);
					this.corpId = storage.get(constant.corpId);
				}

				this.chatId = this.generateUUID();

				// 初始化请求参数
				this.userInfoReq.userId = this.userId;
				this.userInfoReq.corpId = this.corpId;

				console.log('初始化用户信息:', {
					userId: this.userId,
					corpId: this.corpId
				});
			},

			// 初始化启用的AI列表
			initEnabledAIs() {
				// 初始化时显示所有AI，不过滤登录状态
				this.enabledAIs = this.aiList.filter(ai => ai.enabled);
				console.log('初始化AI列表:', this.enabledAIs.map(ai => ai.name));
			},

			// 确保企业ID最新
			async ensureLatestCorpId() {
				try {
					const result = await ensureCorpIdOnShow();
					if (result.corpId !== this.corpId) {
						console.log('企业ID已更新:', result.corpId);
						this.corpId = result.corpId;
						this.userInfoReq.corpId = this.corpId;
					}
				} catch (error) {
					console.error('确保企业ID最新失败:', error);
				}
			},

			// 处理企业ID更新事件
			handleCorpIdUpdated(data) {
				const newCorpId = data.corpId;
				if (newCorpId && newCorpId !== this.corpId) {
					console.log('小程序接收到企业ID更新事件，更新本地corpId:', newCorpId);
					this.corpId = newCorpId;
					this.userInfoReq.corpId = newCorpId;
					uni.showToast({
						title: `主机ID已自动更新: ${newCorpId}`,
						icon: 'success'
					});
				}
			},

			// 生成UUID
			generateUUID() {
				return uuidv4();
			},

			// 切换区域展开状态
			toggleSection(section) {
				this.sectionExpanded[section] = !this.sectionExpanded[section];
			},

			// 切换AI启用状态
			toggleAI(ai, event) {
				// 检查AI是否已登录
				if (!this.isAiLoginEnabled(ai)) {
					uni.showModal({
						title: '提示',
						content: `${ai.name}需要先登录，请前往PC端进行登录后再使用`,
						showCancel: false,
						confirmText: '知道了'
					});
					return;
				}
				ai.enabled = event.detail.value;
			},

			// 腾讯元宝模型选择相关方法
			getModelIndex(ai) {
				if (!ai.models || !ai.selectedModel) return 0;
				return ai.models.findIndex(model => model.value === ai.selectedModel);
			},

			getSelectedModelLabel(ai) {
				if (!ai.models || !ai.selectedModel) return '请选择模型';
				const model = ai.models.find(model => model.value === ai.selectedModel);
				return model ? model.label : '请选择模型';
			},

			onModelChange(ai, event) {
				if (!ai.enabled || !this.isAiLoginEnabled(ai)) return;
				const index = event.detail.value;
				if (ai.models && ai.models[index]) {
					ai.selectedModel = ai.models[index].value;
					uni.showToast({
						title: `已切换到${ai.models[index].label}`,
						icon: 'success',
						duration: 1500
					});
				}
			},

			// 切换AI能力
			toggleCapability(ai, capabilityValue) {
				// 检查AI是否已登录和启用
				if (!this.isAiLoginEnabled(ai)) {
					uni.showModal({
						title: '提示',
						content: `${ai.name}需要先登录，请前往PC端进行登录后再使用`,
						showCancel: false,
						confirmText: '知道了'
					});
					return;
				}

				if (!ai.enabled) return;

        // 单选逻辑（针对秘塔AI）
        if (ai.isSingleSelect) {
          // 直接设置为当前选中值，实现单选效果
          ai.selectedCapabilities = capabilityValue;
        }
        // 其他AI保持多选逻辑
        else {
          const index = ai.selectedCapabilities.indexOf(capabilityValue);
          if (index === -1) {
            ai.selectedCapabilities.push(capabilityValue);
          } else {
            ai.selectedCapabilities.splice(index, 1);
          }
        }
			},
      // 通义千问切换能力
      selectSingleCapability(ai, capabilityValue) {
        if (!ai.enabled || !this.isAiLoginEnabled(ai)) return;

        if (ai.selectedCapability === capabilityValue) {
          ai.selectedCapability = '';
        } else {
          ai.selectedCapability = capabilityValue;
        }
      },

			// 发送提示词
			sendPrompt() {
			console.log('🚀 [sendPrompt] ========== 开始发送任务 ==========');
			console.log('🚀 [sendPrompt] canSend状态:', this.canSend);
			
			if (!this.canSend) {
				console.warn('⚠️ [sendPrompt] canSend为false，停止发送');
				return;
			}

			console.log('📝 [sendPrompt] 用户输入提示词:', this.promptInput);
			console.log('👤 [sendPrompt] 用户ID:', this.userId);
			console.log('🏢 [sendPrompt] 企业ID:', this.corpId);

				this.screenshots = [];
				// 折叠所有区域
				this.sectionExpanded.aiConfig = false;
				this.sectionExpanded.promptInput = false;
				// this.sectionExpanded.taskStatus = false;

				this.taskStarted = true;
				this.results = []; // 清空之前的结果
			console.log('🗑️ [sendPrompt] 清空之前的结果和截图');

				this.userInfoReq.roles = '';
				this.userInfoReq.taskId = this.generateUUID();
				this.userInfoReq.userId = this.userId;
				this.userInfoReq.corpId = this.corpId;
				this.userInfoReq.userPrompt = this.promptInput;
			console.log('🎫 [sendPrompt] 生成任务ID:', this.userInfoReq.taskId);

				// 获取启用的AI列表及其状态
				this.enabledAIs = this.aiList.filter(ai => ai.enabled  && this.isAiLoginEnabled(ai));
			console.log('🤖 [sendPrompt] 筛选启用的AI数量:', this.enabledAIs.length);
			console.log('🤖 [sendPrompt] 启用的AI列表:', this.enabledAIs.map(ai => ai.name).join(', '));

				// 将所有启用的AI状态设置为运行中
				this.enabledAIs.forEach(ai => {
					ai.status = 'running';
				console.log(`▶️ [sendPrompt] 设置${ai.name}状态为: running`);
				});

				// 构建角色参数
				this.enabledAIs.forEach(ai => {
					if (ai.name === '腾讯元宝') {
						if (ai.selectedModel === 'hunyuan') {
							// 混元模型
							this.userInfoReq.roles = this.userInfoReq.roles + 'yb-hunyuan-pt,';
							if (ai.selectedCapabilities.includes("deep_thinking")) {
								this.userInfoReq.roles = this.userInfoReq.roles + 'yb-hunyuan-sdsk,';
							}
							if (ai.selectedCapabilities.includes("web_search")) {
								this.userInfoReq.roles = this.userInfoReq.roles + 'yb-hunyuan-lwss,';
							}
						} else if (ai.selectedModel === 'deepseek') {
							// DeepSeek模型
							this.userInfoReq.roles = this.userInfoReq.roles + 'yb-deepseek-pt,';
							if (ai.selectedCapabilities.includes("deep_thinking")) {
								this.userInfoReq.roles = this.userInfoReq.roles + 'yb-deepseek-sdsk,';
							}
							if (ai.selectedCapabilities.includes("web_search")) {
								this.userInfoReq.roles = this.userInfoReq.roles + 'yb-deepseek-lwss,';
							}
						}
					}
          if (ai.name === 'DeepSeek') {
            this.userInfoReq.roles = this.userInfoReq.roles + 'deepseek,';
            if (ai.selectedCapabilities.includes("deep_thinking")) {
              this.userInfoReq.roles = this.userInfoReq.roles + 'ds-sdsk,';
            }
            if (ai.selectedCapabilities.includes("web_search")) {
              this.userInfoReq.roles = this.userInfoReq.roles + 'ds-lwss,';
            }
          }
					if (ai.name === '豆包') {
						this.userInfoReq.roles = this.userInfoReq.roles + 'zj-db,';
						if (ai.selectedCapabilities.includes("deep_thinking")) {
							this.userInfoReq.roles = this.userInfoReq.roles + 'zj-db-sdsk,';
						}
					}
          if (ai.name === '秘塔') {
            this.userInfoReq.roles = this.userInfoReq.roles + 'mita,';
            if (ai.selectedCapabilities.includes("fast")) {
              this.userInfoReq.roles = this.userInfoReq.roles + 'metaso-jisu,';
            }
            if (ai.selectedCapabilities.includes("fast_thinking")) {
              this.userInfoReq.roles = this.userInfoReq.roles + 'metaso-jssk,';
            }
            if (ai.selectedCapabilities.includes("long_thinking")) {
              this.userInfoReq.roles = this.userInfoReq.roles + 'metaso-csk,';
            }
          }

          if(ai.name === '通义千问' && ai.enabled){
            this.userInfoReq.roles = this.userInfoReq.roles + 'ty-qw,';
            if (ai.selectedCapability === "deep_thinking") {
              this.userInfoReq.roles = this.userInfoReq.roles + 'ty-qw-sdsk,'
            } else if (ai.selectedCapability === "web_search") {
              this.userInfoReq.roles = this.userInfoReq.roles + 'ty-qw-lwss,';
            }
          }
          if(ai.name === '百度AI' && ai.enabled){
            if(this.isAiLoginEnabled(ai)){
              this.userInfoReq.roles = this.userInfoReq.roles + 'baidu-agent,';
              if (ai.selectedCapabilities.includes("web_search")) {
                this.userInfoReq.roles = this.userInfoReq.roles + 'baidu-sdss,';
              }
            }
          }
          if (ai.name === "知乎直答") {
            if (this.isAiLoginEnabled(ai)) {
              this.userInfoReq.roles = this.userInfoReq.roles + "zhzd-chat,";
              // 使用单选思考模式
              if (ai.selectedCapability === "deep_thinking") {
                this.userInfoReq.roles = this.userInfoReq.roles + "zhzd-sdsk,";
              } else if (ai.selectedCapability === "fast_answer") {
                this.userInfoReq.roles = this.userInfoReq.roles + "zhzd-ks,";
              } else if (ai.selectedCapability === "smart_thinking") {
                this.userInfoReq.roles = this.userInfoReq.roles + "zhzd-zn,";
              } else {
                // 默认智能思考
                this.userInfoReq.roles = this.userInfoReq.roles + "zhzd-zn,";
              }
            }
          }


				});

			console.log('🎯 [sendPrompt] 最终角色参数roles:', this.userInfoReq.roles);
			console.log('📦 [sendPrompt] 完整请求参数:', JSON.stringify(this.userInfoReq));

				// 滚动到任务状态区域
				this.scrollIntoView = 'task-status';

				//调用后端接口
				this.jsonRpcReqest.id = this.generateUUID();
				this.jsonRpcReqest.method = "使用F8S";
				this.jsonRpcReqest.params = this.userInfoReq;
			
			console.log('📡 [sendPrompt] 准备发送JSON-RPC请求');
			console.log('📡 [sendPrompt] JSON-RPC请求:', JSON.stringify(this.jsonRpcReqest));
			
				this.message(this.jsonRpcReqest);
				this.userInfoReq.isNewChat = false;

			console.log('✅ [sendPrompt] 任务已提交到后端');
			console.log('🚀 [sendPrompt] ========== 任务发送完成 ==========');

				uni.showToast({
					title: '任务已提交',
					icon: 'success'
				});
			},

					// WebSocket相关方法
		initWebSocket() {
			// 检查用户信息是否完整
			if (!this.userId || !this.corpId) {
				console.log('用户信息不完整，跳转到登录页面');
				uni.showModal({
					title: '提示',
					content: '请先登录后再使用',
					showCancel: false,
					confirmText: '去登录',
					success: () => {
						uni.navigateTo({
							url: '/pages/login/index'
						});
					}
				});
				return;
			}

			if (this.isConnecting || this.socketTask) {
				console.log('WebSocket正在连接中或已存在连接，跳过重复连接');
				return;
			}

			// 先清理可能存在的旧连接和定时器
			this.closeWebSocket();
			
			this.isConnecting = true;

			// 使用PC端的WebSocket连接方式
		    const wsUrl = `${process.env.VUE_APP_WS_API || 'wss://u3w.com/cubeServer/websocket?clientId='}mypc-${this.userId}`;
			// const wsUrl = `${process.env.VUE_APP_WS_API || 'ws://127.0.0.1:8081/websocket?clientId='}mypc-${this.userId}`;
			console.log('WebSocket URL:', wsUrl);

			this.socketTask = uni.connectSocket({
				url: wsUrl,
				success: () => {
					console.log('WebSocket连接成功');
				},
				fail: (err) => {
					console.error('WebSocket连接失败', err);
					this.isConnecting = false;
					this.handleReconnect();
				}
			});

			this.socketTask.onOpen(() => {
				console.log('WebSocket连接已打开');
				this.isConnecting = false;
				this.reconnectCount = 0; // 重置重连次数

				uni.showToast({
					title: '连接成功',
					icon: 'success',
					duration: 1000
				});

				// 开始心跳检测
				this.startHeartbeat();
			});

			this.socketTask.onMessage((res) => {
				this.handleWebSocketMessage(res.data);
			});

			this.socketTask.onError((err) => {
				console.error('WebSocket连接错误', err);
				this.isConnecting = false;
				uni.showToast({
					title: 'WebSocket连接错误',
					icon: 'none'
				});
				this.handleReconnect();
			});

			this.socketTask.onClose(() => {
				console.log('WebSocket连接已关闭');
				this.isConnecting = false;
				this.stopHeartbeat(); // 停止心跳

				uni.showToast({
					title: 'WebSocket连接已关闭',
					icon: 'none'
				});

				// 尝试重连
				this.handleReconnect();
			});
		},

		// 处理重连
		handleReconnect() {
			if (this.reconnectCount >= this.maxReconnectCount) {
				console.log('WebSocket重连次数已达上限');
				uni.showModal({
					title: '连接失败',
					content: '网络连接不稳定，请检查网络后手动刷新页面',
					showCancel: false,
					confirmText: '知道了'
				});
				return;
			}

			// 如果已经在连接中，不要重复重连
			if (this.isConnecting) {
				console.log('WebSocket正在连接中，跳过重连');
				return;
			}

			this.reconnectCount++;
			const delay = Math.min(2000 * Math.pow(2, this.reconnectCount), 30000); // 增加基础延迟到2秒

			console.log(`WebSocket将在${delay}ms后进行第${this.reconnectCount}次重连`);

			this.reconnectTimer = setTimeout(() => {
				console.log(`开始第${this.reconnectCount}次重连`);
				this.initWebSocket();
			}, delay);
		},

		// 开始心跳检测
		startHeartbeat() {
			this.stopHeartbeat(); // 先停止之前的心跳

			this.heartbeatTimer = setInterval(() => {
				if (this.socketTask) {
					this.sendWebSocketMessage({
						type: 'HEARTBEAT',
						timestamp: Date.now()
					});
				}
			}, 30000); // 每30秒发送一次心跳
		},

		// 停止心跳检测
		stopHeartbeat() {
			if (this.heartbeatTimer) {
				clearInterval(this.heartbeatTimer);
				this.heartbeatTimer = null;
			}
		},

			sendWebSocketMessage(data) {
				if (this.socketTask) {
					console.log('发送WebSocket消息:', data);
					this.socketTask.send({
						data: JSON.stringify(data)
					});
				} else {
					console.warn('WebSocket未连接，无法发送消息');
				}
			},

			// 调用后端message接口
			message(data) {
			console.log('📞 [message] 调用后端API, 方法:', data.method);
			console.log('📞 [message] 请求数据:', JSON.stringify(data));
			
				message(data).then(res => {
				console.log('📞 [message] 收到后端响应, code:', res.code);
				console.log('📞 [message] 响应数据:', JSON.stringify(res));
				
					if (res.code == 201) {
					console.warn('⚠️ [message] 后端返回错误, messages:', res.messages);
						uni.showToast({
							title: res.messages,
							icon: 'none',
							duration: 1500,
						});
				} else if (res.code == 200) {
					console.log('✅ [message] 后端处理成功');
					}
			}).catch(error => {
				console.error('❌ [message] 调用后端API失败:', error);
				});
			},

					closeWebSocket() {
			// 清理重连定时器
			if (this.reconnectTimer) {
				clearTimeout(this.reconnectTimer);
				this.reconnectTimer = null;
			}

			// 停止心跳检测
			this.stopHeartbeat();

			// 关闭WebSocket连接
			if (this.socketTask) {
				try {
					this.socketTask.close({
						code: 1000,
						reason: '手动关闭连接'
					});
				} catch (e) {
					console.log('关闭WebSocket连接时出错:', e);
				}
				this.socketTask = null;
			}

			// 重置状态
			this.isConnecting = false;
			this.reconnectCount = 0;
			this.isRefreshing = false; // 重置刷新标志
		},

					// 处理WebSocket消息
		handleWebSocketMessage(data) {
			try {
				const datastr = data;
				console.log('📨 [WebSocket] 收到原始消息, 长度:', datastr.length, '字符');
				console.log('📨 [WebSocket] 消息前200字符:', datastr.substring(0, 200));
				
				const dataObj = JSON.parse(datastr);

				console.log('📨 [WebSocket] 收到消息, 类型:', dataObj.type);
				console.log('📨 [WebSocket] aiName:', dataObj.aiName);

				// 忽略心跳响应
				if (dataObj.type === 'HEARTBEAT_RESPONSE' || dataObj.type === 'HEARTBEAT') {
					return;
				}

        // 处理chatId消息
        if (dataObj.type === 'RETURN_YBT1_CHATID' && dataObj.chatId) {
          this.userInfoReq.toneChatId = dataObj.chatId;
        } else if (dataObj.type === 'RETURN_YBDS_CHATID' && dataObj.chatId) {
          this.userInfoReq.ybDsChatId = dataObj.chatId;
        } else if (dataObj.type === 'RETURN_DB_CHATID' && dataObj.chatId) {
						this.userInfoReq.dbChatId = dataObj.chatId;
        }
        // else if (dataObj.type === 'RETURN_TY_CHATID' && dataObj.chatId) {
        //   this.userInfoReq.tyChatId = dataObj.chatId;
        // }
        else if (dataObj.type === "RETURN_METASO_CHATID" && dataObj.chatId) {
          this.userInfoReq.metasoChatId = dataObj.chatId;
        }

				// 处理进度日志消息
				if (dataObj.type === 'RETURN_PC_TASK_LOG' && dataObj.aiName) {
				console.log(`📋 [进度日志] AI: ${dataObj.aiName}, 内容: ${dataObj.content}`);
					
					// 只处理当前任务的日志消息
					if (dataObj.taskId && dataObj.taskId !== this.userInfoReq.taskId) {
						console.log(`⚠️ [进度日志] 忽略其他任务的日志`);
						return;
					}
					
					const targetAI = this.enabledAIs.find(ai => ai.name === dataObj.aiName);
					if (targetAI && targetAI.status === 'running') {
					console.log(`✅ [进度日志] 找到目标AI: ${targetAI.name}, 添加进度日志`);
						// 检查是否已存在相同内容的日志，避免重复添加
						const existingLog = targetAI.progressLogs.find(log => log.content === dataObj.content);
						if (!existingLog) {
							// 将新进度添加到数组开头
							targetAI.progressLogs.unshift({
								content: dataObj.content,
								timestamp: new Date(),
								isCompleted: false,
								taskId: this.userInfoReq.taskId
							});
						console.log(`📊 [进度日志] ${targetAI.name}当前进度日志数量: ${targetAI.progressLogs.length}`);
						}
				} else {
					console.warn(`⚠️ [进度日志] 未找到AI或AI未在运行: ${dataObj.aiName}`);
					}
					return;
				}

				// 处理截图消息
				if (dataObj.type === 'RETURN_PC_TASK_IMG' && dataObj.url) {
					// 只处理当前任务的截图
					if (dataObj.taskId && dataObj.taskId !== this.userInfoReq.taskId) {
						return;
					}
					// 将新的截图添加到数组开头
					this.screenshots.unshift(dataObj.url);
					return;
				}

				// 处理智能评分结果
				if (dataObj.type === 'RETURN_WKPF_RES') {
					console.log("✅ 收到智能评分结果", dataObj);
					
					const wkpfAI = this.enabledAIs.find(ai => ai.name === '智能评分');
					if (wkpfAI) {
						wkpfAI.status = 'completed';
						if (wkpfAI.progressLogs.length > 0) {
							wkpfAI.progressLogs[0].isCompleted = true;
						}
						
						// 添加评分结果到results最前面
						this.results.unshift({
							aiName: '智能评分',
							content: dataObj.draftContent,
							shareUrl: dataObj.shareUrl || '',
							shareImgUrl: dataObj.shareImgUrl || '',
							timestamp: new Date()
						});
						this.activeResultIndex = 0;
						
						// 折叠所有区域
						this.sectionExpanded.aiConfig = false;
						this.sectionExpanded.promptInput = false;
						this.sectionExpanded.taskStatus = false;
						
						// 滚动到结果区域
						this.scrollIntoView = 'results';
						
						// 智能评分完成时，保存历史记录
						this.saveHistory();
						
						uni.showToast({
							title: '智能评分完成',
							icon: 'success',
							duration: 2000
						});
						console.log('✅ 智能评分结果处理完成');
					}
					return;
				}

				// 处理智能排版结果
				if (dataObj.type === 'RETURN_ZNPB_RES') {
					console.log("收到智能排版结果", dataObj);
					console.log("当前 selectedMedia:", this.selectedMedia);

					const znpbAI = this.enabledAIs.find(ai => ai.name === '智能排版');
					if (znpbAI) {
						znpbAI.status = 'completed';
						if (znpbAI.progressLogs.length > 0) {
							znpbAI.progressLogs[0].isCompleted = true;
						}

						// 根据selectedMedia获取媒体名称标签
						const mediaItem = this.mediaList.find(media => media.name === this.selectedMedia);
						const mediaLabel = mediaItem ? mediaItem.label : '';

						// 添加排版结果到结果展示，aiName包含媒体类型
						this.results.unshift({
							aiName: '智能排版' + mediaLabel,
							content: dataObj.draftContent,
							shareUrl: dataObj.shareUrl || '',
							shareImgUrl: dataObj.shareImgUrl || '',
							timestamp: new Date()
						});
						this.activeResultIndex = 0;

						// 折叠所有区域
						this.sectionExpanded.aiConfig = false;
						this.sectionExpanded.promptInput = false;
						this.sectionExpanded.taskStatus = false;
						
						// 滚动到结果区域
						this.scrollIntoView = 'results';

						// 智能排版完成时，保存历史记录
						this.saveHistory();
						
						uni.showToast({
							title: `${mediaLabel}排版完成，可手动投递`,
							icon: 'success'
						});
					}
					return;
				}
        // 处理媒体投递任务日志
		if (dataObj.type === 'RETURN_MEDIA_TASK_LOG') {
		  console.log("收到媒体任务日志", dataObj);
          const mediaAI = this.enabledAIs.find(ai => ai.name === '媒体投递');
          if (mediaAI && mediaAI.status === 'running') { // 只在运行状态时添加日志
		    // 检查是否已存在相同内容的日志，避免重复添加
            const existingLog = mediaAI.progressLogs.find(log => log.content === dataObj.content);
		    if (!existingLog) {
              // 将新进度添加到数组开头
              mediaAI.progressLogs.unshift({
		        content: dataObj.content,
		        timestamp: new Date(),
		        isCompleted: false,
                type: '媒体投递'
		      });

		      // 强制更新UI
		      this.$forceUpdate();
		    }
		  }
		  return;
		}

       // 处理媒体投递结果（包括知乎、百家号等）
       if (dataObj.type.includes('DELIVERY_RES')) {
         console.log("收到媒体投递完成结果", dataObj);
         const mediaAI = this.enabledAIs.find(ai => ai.name === '媒体投递');
         if (mediaAI) {
           mediaAI.status = 'completed';
           if (mediaAI.progressLogs.length > 0) {
             mediaAI.progressLogs[0].isCompleted = true;
           }
         }
         uni.showToast({
           title: dataObj.message || '媒体投递完成',
           icon: 'success'
         });
         return;
       }
		

        // 处理微头条排版结果
        if (dataObj.type === 'RETURN_TTH_ZNPB_RES') {
          // 设置微头条排版AI节点状态为completed
          const tthZnpbAI = this.enabledAIs.find(ai => ai.name === '微头条排版');
          if (tthZnpbAI) {
            tthZnpbAI.status = 'completed';
            if (tthZnpbAI.progressLogs.length > 0) {
              tthZnpbAI.progressLogs[0].isCompleted = true;
            }
          }
          this.tthArticleTitle = dataObj.title || '';
          this.tthArticleContent = dataObj.content || '';
          this.tthArticleEditVisible = true;

          // 确保textarea正确显示内容
          this.$nextTick(() => {
            // 强制更新textarea内容
            const textarea = this.$el.querySelector('.score-textarea');
            if (textarea) {
              textarea.value = this.tthArticleContent;
              // 触发input事件确保v-model同步
              textarea.dispatchEvent(new Event('input', { bubbles: true }));
            }
          });

          if (this.saveHistory) {
            this.saveHistory();
          }
          uni.showToast({ title: '微头条排版完成，请确认标题和内容', icon: 'success' });
          return;
        }

        // 处理微头条发布流程
        if (dataObj.type === 'RETURN_TTH_FLOW') {
          if (dataObj.content) {
            this.tthFlowLogs.push({
              content: dataObj.content,
              timestamp: new Date(),
              type: 'flow'
            });
          }
          if (dataObj.shareImgUrl) {
            this.tthFlowImages.push(dataObj.shareImgUrl);
          }
          if (!this.tthFlowVisible) {
            this.tthFlowVisible = true;
          }
          if (dataObj.content === 'success') {
            uni.showToast({ title: '发布到微头条成功！', icon: 'success' });
            this.tthFlowVisible = true;
          }
          if (dataObj.content === 'fail') {
            uni.showToast({ title: '发布到微头条失败！', icon: 'none' });
            this.tthFlowVisible = false;
            this.tthArticleEditVisible = true;
          }
          return;
        }



					// 处理AI登录状态消息
				console.log('🔐 [状态检查] 检查AI登录状态消息');
					this.handleAiStatusMessage(datastr, dataObj);

					// 处理AI结果
				console.log('🎯 [结果处理] 调用handleAIResult处理AI结果');
					this.handleAIResult(dataObj);

				} catch (error) {
					console.error('❌ WebSocket消息处理错误:', error);
					console.error('❌ 错误堆栈:', error.stack);
					console.error('❌ 消息长度:', data ? data.length : 0);
					
					// 尝试显示错误提示
					uni.showToast({
						title: '消息处理失败:' + error.message,
						icon: 'none',
						duration: 3000
					});
				}
			},

			handleAiStatusMessage(datastr, dataObj) {
				// 处理腾讯元宝登录状态
				if (datastr.includes("RETURN_YB_STATUS") && dataObj.status != '') {
					this.isLoading.yuanbao = false;
					if (!datastr.includes("false")) {
						this.aiLoginStatus.yuanbao = true;
						this.accounts.yuanbao = dataObj.status;
					} else {
						this.aiLoginStatus.yuanbao = false;
						// 禁用相关AI
						this.disableAIsByLoginStatus('yuanbao');
					}
					// 更新AI启用状态
					this.updateAiEnabledStatus();
				}
				// 处理豆包登录状态
				if (datastr.includes("RETURN_DB_STATUS") && dataObj.status != '') {
					this.isLoading.doubao = false;
					if (!datastr.includes("false")) {
						this.aiLoginStatus.doubao = true;
						this.accounts.doubao = dataObj.status;
					} else {
						this.aiLoginStatus.doubao = false;
						// 禁用相关AI
						this.disableAIsByLoginStatus('doubao');
					}
					// 更新AI启用状态
					this.updateAiEnabledStatus();
				}



        // 处理秘塔登录状态
        else if (datastr.includes("RETURN_METASO_STATUS") && dataObj.status != "") {
          this.isLoading.metaso = false;
          if (!datastr.includes("false")) {
            this.aiLoginStatus.metaso = true;
            this.accounts.metaso = dataObj.status;
          } else {
            this.aiLoginStatus.metaso = false;
            // 禁用相关AI
            this.disableAIsByLoginStatus("metaso");
          }
          // 更新AI启用状态
          this.updateAiEnabledStatus();
        }
        // 处理DeepSeek登录状态
        else if (datastr.includes("RETURN_DEEPSEEK_STATUS")) {
          console.log("收到DeepSeek登录状态消息:", dataObj);
          this.isLoading.deepseek = false;
          if (dataObj.status && dataObj.status !== 'false' && dataObj.status !== '') {
            this.aiLoginStatus.deepseek = true;
            this.accounts.deepseek = dataObj.status;
            console.log("DeepSeek登录成功，账号:", dataObj.status);

            // 查找DeepSeek AI实例
            const deepseekAI = this.aiList.find(ai => ai.name === 'DeepSeek');

          } else {
            this.aiLoginStatus.deepseek = false;
            this.accounts.deepseek = '';
            console.log("DeepSeek未登录");

            // 如果未登录，确保DeepSeek被禁用
            const deepseekAI = this.aiList.find(ai => ai.name === 'DeepSeek');

          }
          // 强制更新UI
          this.$forceUpdate();
        }
        else if (datastr.includes("RETURN_TY_STATUS") && dataObj.status != "") {
          this.isLoading.tongyi = false;
          if (!datastr.includes("false")) {
            this.aiLoginStatus.tongyi = true;
            this.accounts.tongyi = dataObj.status;
          } else {
            this.aiLoginStatus.tongyi = false;
            // 禁用相关AI
            this.disableAIsByLoginStatus("tongyi");
          }
          // 更新AI启用状态
          this.updateAiEnabledStatus();
        }


        // 处理知乎直答登录状态
        else if (datastr.includes("RETURN_ZHZD_STATUS") && dataObj.status != "") {
          console.log('收到知乎直答登录状态响应:', datastr, dataObj);
          this.isLoading.zhzd = false;
          if (!datastr.includes("false")) {
            this.aiLoginStatus.zhzd = true;
            this.accounts.zhzd = dataObj.status;
          } else {
            this.aiLoginStatus.zhzd = false;
            // 禁用相关AI
            this.disableAIsByLoginStatus("zhzd");
          }
          // 更新AI启用状态
          this.updateAiEnabledStatus();
        }
        // 处理百度AI登录状态
        else if (datastr.includes("RETURN_BAIDU_STATUS") && dataObj.status != "") {
          this.isLoading.baidu = false;
          if (!datastr.includes("false")) {
            this.aiLoginStatus.baidu = true;
            this.accounts.baidu = dataObj.status;
          } else {
            this.aiLoginStatus.baidu = false;
            // 禁用相关AI
            this.disableAIsByLoginStatus("baidu");
          }
          // 更新AI启用状态
          this.updateAiEnabledStatus();
        }

			},

			handleAIResult(dataObj) {
			console.log('🔍 [handleAIResult] 开始处理AI结果, 消息类型:', dataObj.type);
			console.log('🔍 [handleAIResult] 完整数据对象:', JSON.stringify(dataObj));
			console.log('🔍 [handleAIResult] 当前enabledAIs数量:', this.enabledAIs.length);
			console.log('🔍 [handleAIResult] enabledAIs列表:', this.enabledAIs.map(ai => `${ai.name}(${ai.status})`).join(', '));
			
				let targetAI = null;

				// 根据消息类型匹配AI
				switch (dataObj.type) {
					case 'RETURN_YBT1_RES':
					console.log('✅ 匹配到腾讯元宝混元消息');
						targetAI = this.enabledAIs.find(ai => ai.name === '腾讯元宝');
						break;
					case 'RETURN_YBDS_RES':
					console.log('✅ 匹配到腾讯元宝DeepSeek消息');
						targetAI = this.enabledAIs.find(ai => ai.name === '腾讯元宝');
						break;
					case 'RETURN_DB_RES':
					console.log('✅ 匹配到豆包消息');
						targetAI = this.enabledAIs.find(ai => ai.name === '豆包');
						break;
          case 'RETURN_DEEPSEEK_RES':
					console.log('✅ 匹配到DeepSeek消息');
            targetAI = this.enabledAIs.find(ai => ai.name === 'DeepSeek');
					// 如果找不到DeepSeek，可能是因为它不在enabledAIs中（兼容处理）
            if (!targetAI) {
						console.warn('⚠️ DeepSeek不在enabledAIs中，动态添加');
              targetAI = {
                name: 'DeepSeek',
                avatar: 'https://u3w.com/chatfile/Deepseek.png',
                capabilities: [{
                  label: '深度思考',
                  value: 'deep_thinking'
							}, {
                    label: '联网搜索',
                    value: 'web_search'
                  }],
                selectedCapabilities: ['deep_thinking', 'web_search'],
                enabled: true,
                status: 'running',
                progressLogs: [{
                  content: 'DeepSeek响应已接收',
                  timestamp: new Date(),
								isCompleted: false
                }],
                isExpanded: true
              };
						this.enabledAIs.unshift(targetAI);
            }
            break;
					case 'RETURN_ZNPB_RES':
						console.log('✅ 匹配到智能排版结果消息');
						// 智能排版结果，找到"智能排版"AI
						targetAI = this.enabledAIs.find(ai => ai.name === '智能排版');
						if (!targetAI) {
							console.warn('⚠️ 智能排版AI不在enabledAIs中，动态添加');
							targetAI = {
								name: '智能排版',
								avatar: 'https://u3w.com/chatfile/Deepseek.png',
								capabilities: [],
								selectedCapabilities: [],
								enabled: true,
								status: 'running',
								progressLogs: [{
									content: '智能排版响应已接收',
									timestamp: new Date(),
									isCompleted: false
								}],
								isExpanded: true
							};
							this.enabledAIs.unshift(targetAI);
            }
            break;
          case 'RETURN_TY_RES':
					console.log('✅ 匹配到通义千问消息');
            targetAI = this.enabledAIs.find(ai => ai.name === '通义千问');
            break;
				case 'RETURN_METASO_RES':
					console.log('✅ 匹配到秘塔消息');
            targetAI = this.enabledAIs.find((ai) => ai.name === "秘塔");
            break;
          case 'RETURN_ZHZD_RES':
					console.log('✅ 匹配到知乎直答消息');
            targetAI = this.enabledAIs.find(ai => ai.name === '知乎直答');
            break;
          case 'RETURN_BAIDU_RES':
					console.log('✅ 匹配到百度AI消息');
            targetAI = this.enabledAIs.find(ai => ai.name === '百度AI');
            break;
				default:
					console.log('⚠️ 未匹配到任何AI类型, 消息类型:', dataObj.type);
					return;
				}

			if (targetAI) {
			console.log(`✅ 找到目标AI: ${targetAI.name}, 当前状态: ${targetAI.status}`);
			
				// 只处理当前任务的结果
				if (dataObj.taskId && dataObj.taskId !== this.userInfoReq.taskId) {
					console.log(`⚠️ 忽略其他任务的消息, 当前taskId: ${this.userInfoReq.taskId}, 消息taskId: ${dataObj.taskId}`);
					return;
				}
				
				// 检查AI是否还在运行状态，避免重复处理
				if (targetAI.status !== 'running') {
					console.log(`⚠️ AI状态不是running，跳过处理, 当前状态: ${targetAI.status}`);
					return;
				}
			
				// 更新AI状态为已完成
				targetAI.status = 'completed';
			console.log(`🔄 更新${targetAI.name}状态为: completed`);

				// 将最后一条进度消息标记为已完成
				if (targetAI.progressLogs.length > 0) {
					targetAI.progressLogs[0].isCompleted = true;
				console.log(`📝 标记${targetAI.name}最后一条进度日志为已完成`);
				}

				// 添加结果到数组开头
				const resultIndex = this.results.findIndex(r => r.aiName === targetAI.name && r.taskId === this.userInfoReq.taskId);
			console.log(`🔍 检查results中是否已存在${targetAI.name}的结果, 索引: ${resultIndex}`);
			
				if (resultIndex === -1) {
				console.log(`➕ 添加${targetAI.name}的新结果到results`);
					this.results.unshift({
						aiName: targetAI.name,
						content: dataObj.draftContent,
						shareUrl: dataObj.shareUrl || '',
						shareImgUrl: dataObj.shareImgUrl || '',
						timestamp: new Date(),
						taskId: this.userInfoReq.taskId
					});
					this.activeResultIndex = 0;
				} else {
				console.log(`🔄 替换${targetAI.name}的现有结果`);
					this.results.splice(resultIndex, 1);
					this.results.unshift({
						aiName: targetAI.name,
						content: dataObj.draftContent,
						shareUrl: dataObj.shareUrl || '',
						shareImgUrl: dataObj.shareImgUrl || '',
						timestamp: new Date(),
						taskId: this.userInfoReq.taskId
					});
					this.activeResultIndex = 0;
				}

			console.log(`📊 当前results数量: ${this.results.length}`);
			console.log(`📊 results列表:`, this.results.map(r => r.aiName).join(', '));

				// 折叠所有区域当有结果返回时
				this.sectionExpanded.aiConfig = false;
				this.sectionExpanded.promptInput = false;
				this.sectionExpanded.taskStatus = false;
			console.log('📐 折叠所有配置区域');

				// 滚动到结果区域
				this.scrollIntoView = 'results';
			console.log('📜 设置滚动到结果区域');

				// 保存历史记录
				this.saveHistory();
			console.log('💾 保存历史记录');
			
			console.log(`✅ ${targetAI.name}结果处理完成`);
		} else {
			console.error(`❌ 未找到对应的AI, 消息类型: ${dataObj.type}`);
			console.error('❌ 可能的原因: 1) AI未启用 2) AI名称不匹配 3) enabledAIs为空');
			}
		},

			// 状态相关方法
			getStatusText(status) {
				const statusMap = {
					'idle': '等待中',
					'running': '正在执行',
					'completed': '已完成',
					'failed': '执行失败'
				};
				return statusMap[status] || '未知状态';
			},

			getStatusIconClass(status) {
				const classMap = {
					'idle': 'status-idle',
					'running': 'status-running',
					'completed': 'status-completed',
					'failed': 'status-failed'
				};
				return classMap[status] || 'status-unknown';
			},

			getStatusEmoji(status) {
				const emojiMap = {
					'idle': '⏳',
					'running': '🔄',
					'completed': '✅',
					'failed': '❌'
				};
				return emojiMap[status] || '❓';
			},

			// 切换任务展开状态
			toggleTaskExpansion(ai) {
				ai.isExpanded = !ai.isExpanded;
			},

			// 切换自动播放
			toggleAutoPlay(event) {
				this.autoPlay = event.detail.value;
			},

			// 预览图片
			previewImage(url) {
				uni.previewImage({
					current: url,
					urls: [url]
				});
			},

			// 结果相关方法
			switchResultTab(index) {
				this.activeResultIndex = index;
			},

			renderMarkdown(text) {
				try {
          // 对于DeepSeek响应，添加特殊的CSS类
          if (this.currentResult && this.currentResult.aiName === 'DeepSeek') {
            // 检查是否已经包含了deepseek-response类
            if (text && text.includes('class="deepseek-response"')) {
              return text; // 已经包含了特殊类，直接返回
            }
            const renderedHtml = marked(text);
            return `<div class="deepseek-response">${renderedHtml}</div>`;
          }
					return marked(text);
				} catch (error) {
					return text;
				}
			},

			isImageFile(url) {
				if (!url) return false;
				const imageExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.svg'];
				const urlLower = url.toLowerCase();
				return imageExtensions.some(ext => urlLower.includes(ext));
			},

			// 判断是否为PDF文件
			isPdfFile(url) {
				if (!url) return false;
				return url.toLowerCase().includes('.pdf');
			},

			copyResult(content) {
				uni.setClipboardData({
					data: content,
					success: () => {
						uni.showToast({
							title: '已复制到剪贴板',
							icon: 'success'
						});
					}
				});
			},



			// shareResult(result) {
			// 	uni.share({
			// 		provider: 'weixin',
			// 		scene: 'WXSceneSession',
			// 		type: 0,
			// 		title: `${result.aiName}的执行结果`,
			// 		summary: result.content.substring(0, 100),
			// 		success: () => {
			// 			uni.showToast({
			// 				title: '分享成功',
			// 				icon: 'success'
			// 			});
			// 		}
			// 	});
			// },

			exportResult(result) {
				// 小程序环境下的导出功能可以通过分享或复制实现
				this.copyResult(result.content);
			},

			openShareUrl(url) {
				uni.setClipboardData({
					data: url,
					success: () => {
						uni.showToast({
							title: '原链接已复制',
							icon: 'success'
						});
					},
					fail: () => {
						uni.showToast({
							title: '复制失败',
							icon: 'none'
						});
					}
				});
			},

			// 复制PDF链接
			copyPdfUrl(url) {
				uni.setClipboardData({
					data: url,
					success: () => {
						uni.showToast({
							title: 'PDF链接已复制',
							icon: 'success'
						});
					},
					fail: () => {
						uni.showToast({
							title: '复制失败',
							icon: 'none'
						});
					}
				});
			},

			// 打开PDF文件
			openPdfFile(url) {
				uni.showLoading({
					title: '正在下载PDF...'
				});

				// 尝试下载并打开文件
				uni.downloadFile({
					url: url,
					success: (res) => {
						uni.hideLoading();
						if (res.statusCode === 200) {
							// 打开文件
							uni.openDocument({
								filePath: res.tempFilePath,
								success: () => {
									uni.showToast({
										title: 'PDF已打开',
										icon: 'success'
									});
								},
								fail: () => {
									// 如果无法打开，提示并复制链接
									uni.showModal({
										title: '提示',
										content: '无法在当前环境打开PDF文件，已复制链接到剪贴板，请在浏览器中打开',
										showCancel: false,
										success: () => {
											uni.setClipboardData({
												data: url
											});
										}
									});
								}
							});
						} else {
							uni.showToast({
								title: '下载失败',
								icon: 'none'
							});
						}
					},
					fail: () => {
						uni.hideLoading();
						// 下载失败，提示并复制链接
						uni.showModal({
							title: '提示',
							content: '下载失败，已复制PDF链接到剪贴板，请在浏览器中打开',
							showCancel: false,
							success: () => {
								uni.setClipboardData({
									data: url
								});
							}
						});
					}
				});
			},

			// 历史记录相关方法
			showHistoryDrawer() {
				this.historyDrawerVisible = true;
				this.loadChatHistory(1);
			},

			closeHistoryDrawer() {
				this.historyDrawerVisible = false;
			},

			async loadChatHistory(isAll) {
				try {
					const res = await getChatHistory(this.userId, isAll);
					if (res.code === 200) {
						this.chatHistory = res.data || [];
					}
				} catch (error) {
					console.error('加载历史记录失败:', error);
					uni.showToast({
						title: '加载历史记录失败',
						icon: 'none'
					});
				}
			},

			loadHistoryItem(item) {
				try {
					const historyData = JSON.parse(item.data);
					// 恢复AI选择配置
					this.aiList = historyData.aiList || this.aiList;
					// 恢复提示词输入
					this.promptInput = historyData.promptInput || item.userPrompt;
					// 恢复任务流程
					this.enabledAIs = historyData.enabledAIs || [];
					// 恢复主机可视化
					this.screenshots = historyData.screenshots || [];
					// 恢复执行结果
					this.results = historyData.results || [];
					// 恢复chatId
					this.chatId = item.chatId || this.chatId;
					this.userInfoReq.toneChatId = item.toneChatId || '';
					this.userInfoReq.ybDsChatId = item.ybDsChatId || '';
					this.userInfoReq.dbChatId = item.dbChatId || '';
          // this.userInfoReq.tyChatId = item.tyChatId || '';

          this.userInfoReq.metasoChatId = item.metasoChatId || "";


          this.userInfoReq.isNewChat = false;

					// 不再根据AI登录状态更新AI启用状态，保持原有选择

					// 展开相关区域
					this.sectionExpanded.aiConfig = true;
					this.sectionExpanded.promptInput = true;
					this.sectionExpanded.taskStatus = true;
					this.taskStarted = true;

					this.closeHistoryDrawer();
					uni.showToast({
						title: '历史记录加载成功',
						icon: 'success'
					});
				} catch (error) {
					console.error('加载历史记录失败:', error);
					uni.showToast({
						title: '加载失败',
						icon: 'none'
					});
				}
			},

			// 加载上次会话
			async loadLastChat() {
				try {
					const res = await getChatHistory(this.userId, 0);
					if (res.code === 200 && res.data && res.data.length > 0) {
						// 获取最新的会话记录
						const lastChat = res.data[0];
						this.loadHistoryItem(lastChat);
					}
				} catch (error) {
					console.error('加载上次会话失败:', error);
				}
			},

			async saveHistory() {
				const historyData = {
					aiList: this.aiList,
					promptInput: this.promptInput,
					enabledAIs: this.enabledAIs,
					screenshots: this.screenshots,
					results: this.results,
					chatId: this.chatId,
					toneChatId: this.userInfoReq.toneChatId,
					ybDsChatId: this.userInfoReq.ybDsChatId,
					dbChatId: this.userInfoReq.dbChatId,
            // tyChatId: this.userInfoReq.tyChatId,

            metasoChatId: this.userInfoReq.metasoChatId,

            baiduChatId:this.userInfoReq.baiduChatId,
					zhzdChatId: this.userInfoReq.zhzdChatId,
				};

				try {
					await saveUserChatData({
						userId: this.userId,
						userPrompt: this.promptInput,
						data: JSON.stringify(historyData),
						chatId: this.chatId,
						toneChatId: this.userInfoReq.toneChatId,
						ybDsChatId: this.userInfoReq.ybDsChatId,
						dbChatId: this.userInfoReq.dbChatId,
            // tyChatId: this.userInfoReq.tyChatId,

            metasoChatId: this.userInfoReq.metasoChatId,

            baiduChatId:this.userInfoReq.baiduChatId,
						zhzdChatId: this.userInfoReq.zhzdChatId,
					});
				} catch (error) {
					console.error('保存历史记录失败:', error);
					uni.showToast({
						title: '保存历史记录失败',
						icon: 'none'
					});
				}
			},

			getHistoryDate(timestamp) {
				try {
					console.log('getHistoryDate 输入:', timestamp, typeof timestamp);

					if (!timestamp) {
						return '未知日期';
					}

					let date;

					if (typeof timestamp === 'number') {
						date = new Date(timestamp);
					} else if (typeof timestamp === 'string') {
						// 处理 "2025-6-23 14:53:12" 这种格式
						const match = timestamp.match(/(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2}):(\d{1,2})/);
						if (match) {
							const [, year, month, day, hour, minute, second] = match;
							date = new Date(
								parseInt(year),
								parseInt(month) - 1,
								parseInt(day),
								parseInt(hour),
								parseInt(minute),
								parseInt(second)
							);
						} else {
							// 如果正则不匹配，尝试其他方式
							const fixedTimestamp = timestamp.replace(/\s/g, 'T');
							date = new Date(fixedTimestamp);

							if (isNaN(date.getTime())) {
								date = new Date(timestamp);
							}
						}
					} else {
						date = new Date(timestamp);
					}

					console.log('getHistoryDate 解析结果:', date, date.getTime());

					if (isNaN(date.getTime())) {
						return '未知日期';
					}

					const today = new Date();
					const yesterday = new Date(today);
					yesterday.setDate(yesterday.getDate() - 1);

					if (date.toDateString() === today.toDateString()) {
						return '今天';
					} else if (date.toDateString() === yesterday.toDateString()) {
						return '昨天';
					} else {
						return date.toLocaleDateString('zh-CN');
					}
				} catch (error) {
					console.error('格式化日期错误:', error, timestamp);
					return '未知日期';
				}
			},

			// 格式化历史记录时间
			formatHistoryTime(timestamp) {
				try {
					console.log('formatHistoryTime 输入:', timestamp, typeof timestamp);

					let date;

					if (!timestamp) {
						return '时间未知';
					}

					// 如果是数字，直接创建Date对象
					if (typeof timestamp === 'number') {
						date = new Date(timestamp);
					} else if (typeof timestamp === 'string') {
						// 处理ISO 8601格式：2025-06-25T07:18:54.110Z
						if (timestamp.includes('T') && (timestamp.includes('Z') || timestamp.includes('+'))) {
							date = new Date(timestamp);
						}
						// 处理 "2025-6-26 08:46:26" 这种格式
						else {
							const match = timestamp.match(/(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2}):(\d{1,2})/);
							if (match) {
								const [, year, month, day, hour, minute, second] = match;
								// 注意：Date构造函数的month参数是0-11，所以要减1
								date = new Date(
									parseInt(year),
									parseInt(month) - 1,
									parseInt(day),
									parseInt(hour),
									parseInt(minute),
									parseInt(second)
								);
							} else {
								// 如果正则不匹配，尝试其他方式
								const fixedTimestamp = timestamp.replace(/\s/g, 'T');
								date = new Date(fixedTimestamp);

								if (isNaN(date.getTime())) {
									date = new Date(timestamp);
								}
							}
						}
					} else if (timestamp instanceof Date) {
						date = timestamp;
					} else {
						date = new Date(timestamp);
					}

					console.log('formatHistoryTime 解析结果:', date, date.getTime());

					if (isNaN(date.getTime())) {
						return '时间未知';
					}

					// 使用更简洁的时间格式，避免显示时区信息
					const hour = date.getHours().toString().padStart(2, '0');
					const minute = date.getMinutes().toString().padStart(2, '0');

					const timeString = `${hour}:${minute}`;

					console.log('formatHistoryTime 输出:', timeString);
					return timeString;

				} catch (error) {
					console.error('格式化时间错误:', error, timestamp);
					return '时间未知';
				}
			},

			// 修改折叠切换方法
			toggleHistoryExpansion(item) {
				this.expandedHistoryItems[item.chatId] = !this.expandedHistoryItems[item.chatId];
				this.$forceUpdate(); // 强制更新视图
			},

			// 智能评分相关方法
		async showScoreModal() {
				this.selectedResults = [];
				this.scoreModalVisible = true;
			
			// 加载评分提示词模板列表
			try {
				const { getAllScorePrompt } = require('@/api/wechat/aigc.js');
				const response = await getAllScorePrompt();
				if (response.code === 200) {
					this.scorePromptList = response.data || [];
				}
			} catch (error) {
				console.error('加载评分提示词模板失败:', error);
				// 失败也不影响使用，用户可以手动输入
			}
			},

			closeScoreModal() {
				this.scoreModalVisible = false;
			},
		
		// 获取当前选中的评分模板索引
		getScorePromptIndex() {
			if (!this.selectedScorePrompt) return 0;
			const index = this.scorePromptList.findIndex(prompt => prompt.name === this.selectedScorePrompt);
			return index === -1 ? 0 : index;
		},
		
		// 评分模板选择变化
		onScorePromptChange(event) {
			const index = event.detail.value;
			if (this.scorePromptList[index]) {
				this.selectedScorePrompt = this.scorePromptList[index].name;
				this.scorePrompt = this.scorePromptList[index].prompt;
			}
			},

			// 媒体投递相关方法
			showLayoutModal() {
				if (!this.currentResult) {
					uni.showToast({
						title: '没有可投递的内容',
						icon: 'none'
					});
					return;
				}
				console.log("showLayoutModal", this.currentResult);
				// 深度拷贝当前结果，避免引用被修改
				this.currentLayoutResult = {
					aiName: this.currentResult.aiName,
					content: this.currentResult.content,
					shareUrl: this.currentResult.shareUrl,
					shareImgUrl: this.currentResult.shareImgUrl,
					timestamp: this.currentResult.timestamp
				};

        // 默认选择公众号
        this.selectedMedia = 'wechat_layout';
        // 设置layoutPrompt为当前结果内容（参考网页端逻辑）
        this.layoutPrompt = this.currentLayoutResult.content;
        this.layoutModalVisible = true;
      },
      // 选择排版AI
      selectLayoutAI(ai) {
        this.layoutAI = ai;
        console.log('选择排版AI:', ai);
      },
      
      // 选择媒体
      selectMedia(media) {
        this.selectedMedia = media;
        console.log('选择媒体:', media);
			},

			closeLayoutModal() {
				this.layoutModalVisible = false;
			},

      handleLayout() {
        if (!this.currentLayoutResult) return;

        this.closeLayoutModal();

        // 根据选择的媒体创建排版任务
        if (this.selectedMedia === 'wechat_layout') {
        this.createWechatLayoutTask();
        } else if (this.selectedMedia === 'zhihu_layout') {
          this.createZhihuLayoutTask();
        }
      },

      // 创建知乎排版任务
      createZhihuLayoutTask() {
        // 构建知乎排版请求
        const layoutRequest = {
          jsonrpc: '2.0',
          id: this.generateUUID(),
          method: 'AI排版',
          params: {
            taskId: this.generateUUID(),
            userId: this.userId,
            corpId: this.corpId,
            userPrompt: this.currentLayoutResult.content,
            roles: '',
            selectedMedia: 'zhihu_layout'
          }
        };

        // 根据选择的AI设置roles参数
        const selectedAI = this.aiList.find(ai => ai.name === this.layoutAI);
        if (selectedAI) {
          if (selectedAI.name === '豆包') {
            layoutRequest.params.roles = 'zj-db,';
            if (selectedAI.selectedCapabilities.includes('deep_thinking')) {
              layoutRequest.params.roles += 'zj-db-sdsk,';
            }
          } else if (selectedAI.name === 'DeepSeek') {
            layoutRequest.params.roles = 'deepseek,';
            if (selectedAI.selectedCapabilities.includes('deep_thinking')) {
              layoutRequest.params.roles += 'ds-sdsk,';
            }
            if (selectedAI.selectedCapabilities.includes('web_search')) {
              layoutRequest.params.roles += 'ds-lwss,';
            }
          }
        }

        // 发送排版请求
        console.log("知乎排版参数", layoutRequest);
        this.message(layoutRequest);

        // 创建智能排版AI节点
        const znpbAI = {
          name: '智能排版',
          avatar: selectedAI ? selectedAI.avatar : 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
          capabilities: [],
          selectedCapabilities: [],
          enabled: true,
          status: 'running',
          progressLogs: [
            {
              content: '知乎排版任务已提交，正在排版...',
              timestamp: new Date(),
              isCompleted: false,
              type: '智能排版'
            }
          ],
          isExpanded: true
        };

        this.addOrUpdateTaskAI(znpbAI, '智能排版');

        uni.showToast({
          title: '知乎排版任务已提交',
          icon: 'success'
        });
      },

	  // 创建百家号投递任务
	  createBaijiahaoDeliveryTask() {
	    // 组合完整的提示词：数据库提示词 + 原文内容
	    const fullPrompt = this.layoutPrompt + '\n\n' + this.currentLayoutResult.content;

	    // 构建百家号投递请求
	    const baijiahaoRequest = {
	      jsonrpc: '2.0',
	      id: this.generateUUID(),
	      method: '投递到百家号',
	      params: {
	        taskId: this.generateUUID(),
	        userId: this.userId,
	        corpId: this.corpId,
	        userPrompt: fullPrompt,
	        aiName: this.currentLayoutResult.aiName,
	        content: this.currentLayoutResult.content
	      }
	    };

	    console.log("百家号投递参数", baijiahaoRequest);
	    this.message(baijiahaoRequest);

	    // 创建投递到百家号任务节点
	    const baijiahaoAI = {
	      name: '投递到百家号',
	      avatar: 'https://my-image-hosting.oss-cn-beijing.aliyuncs.com/baojiahao.png',
	      capabilities: [],
	      selectedCapabilities: [],
	      enabled: true,
	      status: 'running',
	      progressLogs: [
	        {
	          content: '投递到百家号任务已提交，正在处理...',
	          timestamp: new Date(),
	          isCompleted: false,
	          type: '投递到百家号'
	        }
	      ],
	      isExpanded: true
	    };

	    this.addOrUpdateTaskAI(baijiahaoAI, '投递到百家号');

	    uni.showToast({
	      title: '百家号投递任务已提交',
	      icon: 'success'
	    });
	  },
	  



      // 创建微头条排版任务
      createToutiaoLayoutTask() {
        // 组合完整的提示词：数据库提示词 + 原文内容
        const fullPrompt = this.layoutPrompt + '\n\n' + this.currentLayoutResult.content;

        // 构建微头条排版请求
        const layoutRequest = {
          jsonrpc: '2.0',
          id: this.generateUUID(),
          method: '微头条排版',
          params: {
            taskId: this.generateUUID(),
            userId: this.userId,
            corpId: this.corpId,
            userPrompt: fullPrompt,
            roles: ''
          }
        };

        console.log("微头条排版参数", layoutRequest);
        this.message(layoutRequest);

        // 创建微头条排版AI节点
        const tthZnpbAI = {
          name: '微头条排版',
          avatar: 'https://u3w.com/chatfile/TouTiao.png',
          capabilities: [],
          selectedCapabilities: [],
          enabled: true,
          status: 'running',
          progressLogs: [
            {
              content: '微头条排版任务已提交，正在排版...',
              timestamp: new Date(),
              isCompleted: false,
              type: '微头条排版'
            }
          ],
          isExpanded: true
        };

        this.addOrUpdateTaskAI(tthZnpbAI, '微头条排版');

        uni.showToast({
          title: '微头条排版任务已提交',
          icon: 'success'
        });
      },

      // 创建公众号排版任务（参考web端实现）
        createWechatLayoutTask() {
          // 构建智能排版请求
				const layoutRequest = {
					jsonrpc: '2.0',
					id: this.generateUUID(),
					method: 'AI排版',
					params: {
						taskId: this.generateUUID(),
						userId: this.userId,
						corpId: this.corpId,
						userPrompt: this.currentLayoutResult.content,
						roles: '',
						selectedMedia: 'wechat_layout'
					}
				};

        // 根据选择的AI设置roles（参考web端逻辑）
        const selectedAI = this.aiList.find(ai => ai.name === this.layoutAI);
        if (selectedAI) {
          if (selectedAI.name === '豆包') {
            layoutRequest.params.roles = 'zj-db,';
            if (selectedAI.selectedCapabilities.includes('deep_thinking')) {
              layoutRequest.params.roles += 'zj-db-sdsk,';
            }
          } else if (selectedAI.name === 'DeepSeek') {
            layoutRequest.params.roles = 'deepseek,';
            if (selectedAI.selectedCapabilities.includes('deep_thinking')) {
              layoutRequest.params.roles += 'ds-sdsk,';
            }
            if (selectedAI.selectedCapabilities.includes('web_search')) {
              layoutRequest.params.roles += 'ds-lwss,';
            }
          }
        }

				// 发送排版请求
				console.log("公众号排版参数", layoutRequest);
				this.message(layoutRequest);

				// 创建智能排版AI节点
				const znpbAI = {
					name: '智能排版',
					avatar: selectedAI ? selectedAI.avatar : 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
					capabilities: [],
					selectedCapabilities: [],
					enabled: true,
					status: 'running',
					progressLogs: [
						{
							content: '公众号排版任务已提交，正在排版...',
							timestamp: new Date(),
							isCompleted: false,
							type: '智能排版'
						}
					],
					isExpanded: true
				};
          this.addOrUpdateTaskAI(znpbAI, '智能排版');

          uni.showToast({
            title: '公众号排版请求已发送，请等待结果',
            icon: 'success'
          });
        },

        // 添加或更新任务AI
        addOrUpdateTaskAI(aiNode, taskName) {
          const existIndex = this.enabledAIs.findIndex(ai => ai.name === taskName);
          if (existIndex === -1) {
            // 如果不存在，添加到数组开头
            this.enabledAIs.unshift(aiNode);
          } else {
            // 如果已存在，先移除旧的，再将新的添加到开头
            this.enabledAIs.splice(existIndex, 1);
            this.enabledAIs.unshift(aiNode);
          }
          this.$forceUpdate();
        },


			// 直接投递已排版的内容到公众号
			async handleDirectPushToWechat(result) {
				try {
					console.log("handleDirectPushToWechat 开始执行", result);

					// 验证内容是否为空
					if (!result.content || result.content.trim() === '') {
						uni.showToast({
							title: '投递内容为空，请先进行AI排版生成内容',
							icon: 'none'
						});
						return;
					}

					// 提取媒体类型：从 aiName 中去掉 "智能排版" 四个字
					const mediaLabel = result.aiName.substring(4); // 去掉"智能排版"
					const mediaItem = this.mediaList.find(media => media.label === mediaLabel);
					const mediaName = mediaItem ? mediaItem.name : '';

					console.log("提取的媒体类型:", mediaLabel, mediaName);

					// 根据媒体类型进行不同的处理
					if (mediaName.includes('wechat')) {
						// 公众号投递：直接调用API
					uni.showLoading({
							title: '正在投递到公众号...'
					});

					this.collectNum++;

					const params = {
						contentText: result.content,
						userId: this.userId,
						shareUrl: result.shareUrl || '',
						aiName: result.aiName || '',
						num: this.collectNum
					};

						console.log("公众号投递参数", params);

				const res = await pushAutoOffice(params);

				uni.hideLoading();

				if (res.code === 200) {
					uni.showToast({
								title: '投递到公众号成功',
						icon: 'success'
					});
				} else {
					uni.showToast({
						title: res.message || '投递失败',
								icon: 'none'
							});
						}
					} else if (mediaName.includes('zhihu')) {
						// 知乎投递：通过WebSocket发送投递请求
						uni.showLoading({
							title: '正在投递到知乎...'
						});

						const mediaRequest = {
							jsonrpc: "2.0",
							id: this.generateUUID(),
							method: "媒体投递",
							params: {
								taskId: this.generateUUID(),
								userId: this.userId,
								corpId: this.corpId,
								aiName: result.aiName,
								userPrompt: result.content,
								selectedMedia: "zhihu_layout"
							}
						};

						console.log("知乎投递参数", mediaRequest);

						this.message(mediaRequest);

						// 创建媒体投递任务节点
						const mediaDeliveryAI = {
							name: '媒体投递',
							avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
							capabilities: [],
							selectedCapabilities: [],
							enabled: true,
							status: 'running',
							progressLogs: [{
								content: '知乎投递任务已提交，正在投递...',
								timestamp: new Date(),
								isCompleted: false,
								type: '媒体投递'
							}],
							isExpanded: true
						};

					// 添加或更新媒体投递任务
					this.addOrUpdateTaskAI(mediaDeliveryAI, '媒体投递');

					uni.hideLoading();

					uni.showToast({
						title: '媒体投递请求已发送，请等待结果',
						icon: 'success'
					});
					} else {
						uni.showToast({
							title: '不支持的媒体类型',
						icon: 'none'
					});
				}
			} catch (error) {
				uni.hideLoading();
					console.error('投递失败:', error);
				uni.showToast({
					title: '投递失败',
					icon: 'none'
				});
			}
		},

		// 推送到公众号
		async handlePushToWechat(contentText) {
				try {
					console.log("handlePushToWechat 开始执行", this.currentLayoutResult);

					if (!this.currentLayoutResult) {
						console.error("currentLayoutResult 为空，无法投递");
						uni.showToast({
							title: '投递失败：缺少原始结果信息',
							icon: 'none'
						});
						return;
					}

					uni.showLoading({
						title: '正在投递...'
					});

					// 自增计数器
					this.collectNum++;

					const params = {
						contentText: contentText,
						userId: this.userId,
						shareUrl: this.currentLayoutResult.shareUrl || '',
						aiName: this.currentLayoutResult.aiName || '',
						num: this.collectNum
					};

								console.log("投递参数", params);

			const res = await pushAutoOffice(params);

			uni.hideLoading();

			if (res.code === 200) {
				uni.showToast({
					title: '投递成功',
					icon: 'success'
				});
			} else {
				uni.showToast({
					title: res.message || '投递失败',
					icon: 'none'
				});
			}
		} catch (error) {
			uni.hideLoading();
			console.error('投递到公众号失败:', error);
			uni.showToast({
				title: '投递失败',
						icon: 'none'
					});
				}
			},

			toggleResultSelection(event) {
				const values = event.detail.value;
				console.log('toggleResultSelection - 选中的values:', values);
				console.log('toggleResultSelection - 当前scorePrompt:', this.scorePrompt.trim());
				this.selectedResults = values;
				console.log('toggleResultSelection - 更新后的selectedResults:', this.selectedResults);
				console.log('toggleResultSelection - canScore状态:', this.canScore);
			},

		async handleScore() {
				if (!this.canScore) return;

			try {
				// 调用getScoreWord API获取评分后缀
				const { getScoreWord } = require('@/api/wechat/aigc.js');
				const response = await getScoreWord();
				const scoreSuffix = response.code === 200 ? response.data : '初稿：';

				// 获取选中的结果内容并按照指定格式拼接
				const selectedContents = this.results
					.filter(result => this.selectedResults.includes(result.aiName))
					.map(result => {
						// 将HTML内容转换为纯文本（小程序版本简化处理）
						const plainContent = result.content.replace(/<[^>]*>/g, '');
							return `${result.aiName}${scoreSuffix}${plainContent}\n`;
					})
					.join('\n');

				// 构建完整的评分提示内容
				const fullPrompt = `${this.scorePrompt}\n${selectedContents}`;

				// 构建评分请求
				const scoreRequest = {
					jsonrpc: '2.0',
					id: this.generateUUID(),
					method: 'AI评分',
					params: {
						taskId: this.generateUUID(),
						userId: this.userId,
						corpId: this.corpId,
						userPrompt: fullPrompt,
							roles: ''
						}
					};

					// 根据选择的AI设置roles参数
					const selectedAI = this.aiList.find(ai => ai.name === this.scoreAI);
					if (selectedAI) {
						if (selectedAI.name === '豆包') {
							scoreRequest.params.roles = 'zj-db,';
							if (selectedAI.selectedCapabilities.includes('deep_thinking')) {
								scoreRequest.params.roles += 'zj-db-sdsk,';
							}
						} else if (selectedAI.name === 'DeepSeek') {
							scoreRequest.params.roles = 'deepseek,';
							if (selectedAI.selectedCapabilities.includes('deep_thinking')) {
								scoreRequest.params.roles += 'ds-sdsk,';
							}
							if (selectedAI.selectedCapabilities.includes('web_search')) {
								scoreRequest.params.roles += 'ds-lwss,';
							}
						}
					}

				// 发送评分请求
					console.log("评分参数", scoreRequest);
				this.message(scoreRequest);
				this.closeScoreModal();

				// 创建智能评分AI节点
				const wkpfAI = {
					name: '智能评分',
						avatar: selectedAI ? selectedAI.avatar : 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
					capabilities: [],
					selectedCapabilities: [],
					enabled: true,
					status: 'running',
					progressLogs: [
						{
							content: '智能评分任务已提交，正在评分...',
							timestamp: new Date(),
							isCompleted: false,
							type: '智能评分'
						}
					],
					isExpanded: true
				};

				// 添加或更新智能评分任务
				this.addOrUpdateTaskAI(wkpfAI, '智能评分');

				uni.showToast({
					title: '评分请求已发送，请等待结果',
					icon: 'success'
				});
				} catch (error) {
					console.error('智能评分失败:', error);
					uni.showToast({
						title: '评分请求失败',
						icon: 'none'
					});
				}
			},
			
			// 选择评分AI
			selectScoreAI(ai) {
				this.scoreAI = ai;
				console.log('选择评分AI:', ai);
			},

			// 创建新对话
			createNewChat() {
				// 重置所有数据
				this.chatId = this.generateUUID();
				this.promptInput = '';
				this.taskStarted = false;
				this.screenshots = [];
				this.results = [];
				this.enabledAIs = [];
				this.userInfoReq = {
					userPrompt: '',
					userId: this.userId,
					corpId: this.corpId,
					taskId: '',
					roles: '',
					toneChatId: '',
					ybDsChatId: '',
					dbChatId: '',
          tyChatId: '',

          metasoChatId: '',

          baiduChatId:'',
          zhzdChatId: '',
					isNewChat: true
				};
				// 重置AI列表为初始状态
				this.aiList = [{
            name: 'DeepSeek',
            avatar: 'https://u3w.com/chatfile/Deepseek.png',
            capabilities: [{
              label: '深度思考',
              value: 'deep_thinking'
            },
              {
                label: '联网搜索',
                value: 'web_search'
              }
            ],
            selectedCapabilities: ['deep_thinking', 'web_search'],
            enabled: true,
            status: 'idle',
            progressLogs: [],
            isExpanded: true,
          isSingleSelect: false,  // 添加单选标记
          },
					{
						name: '豆包',
						avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
						capabilities: [{
							label: '深度思考',
							value: 'deep_thinking'
						}],
						selectedCapabilities: ['deep_thinking'],
						enabled: true,
						status: 'idle',
						progressLogs: [],
						isExpanded: true,
            isSingleSelect: false,  // 添加单选标记
					},
          {
            name: '通义千问',
            avatar: 'https://u3w.com/chatfile/TongYi.png',
            capabilities: [
              {
                label: '深度思考',
                value: 'deep_thinking'
              },
              {
                label: '联网搜索',
                value: 'web_search'
              }
            ],
            selectedCapability: '',
            enabled: true,
            status: 'idle',
            progressLogs: [],
            isExpanded: true
          },

          {
            name: '秘塔',
            avatar: 'https://www.aitool6.com/wp-content/uploads/2023/06/9557d1-2.jpg',
            capabilities: [
              {label: '极速', value: 'fast'},
              {label: '极速思考', value: 'fast_thinking'},
              {label: '长思考', value: 'long_thinking'},
            ],
            selectedCapabilities: "fast",
            enabled: true,
            status: 'idle',
            progressLogs: [],
            isExpanded: true,
            isSingleSelect: true,  // 添加单选标记
          },

          {
            name: "知乎直答",
            avatar: 'https://u3w.com/chatfile/ZHZD.png',
            capabilities: [
              {
                label: "智能思考",
                value: "smart_thinking"
              },
              {
                label: "深度思考",
                value: "deep_thinking"
              },
              {
                label: "快速回答",
                value: "fast_answer"
              },
            ],
            selectedCapability: "smart_thinking", // 改为单选，默认智能思考
            enabled: true,
            status: 'idle',
            progressLogs: [],
            isExpanded: true,
            isSingleSelect: true, // 设为单选模式
          },
          {
            name: "百度AI",
            avatar: 'https://u3w.com/chatfile/baiduAI.png',
            capabilities: [
              { label: "深度搜索", value: "web_search" }
            ],
            selectedCapabilities: [],
            enabled: true,
            status: "idle",
            progressLogs: [],
            isExpanded: true,
          },

					{
						name: '腾讯元宝',
						avatar: 'https://u3w.com/chatfile/yuanbao.png',
						capabilities: [{
								label: '深度思考',
								value: 'deep_thinking'
							},
							{
								label: '联网搜索',
								value: 'web_search'
							}
						],
						selectedCapabilities: ['deep_thinking', 'web_search'],
						selectedModel: 'hunyuan', // 默认选择混元
						models: [
							{ label: '混元', value: 'hunyuan' },
							{ label: 'DeepSeek', value: 'deepseek' }
						],
						enabled: true,
						status: 'idle',
						progressLogs: [],
						isExpanded: true,
						isSingleSelect: false
					},

				];
				// 不再根据AI登录状态更新AI启用状态，保持原有选择

				// 展开相关区域
				this.sectionExpanded.aiConfig = true;
				this.sectionExpanded.promptInput = true;
				this.sectionExpanded.taskStatus = true;

				uni.showToast({
					title: '已创建新对话',
					icon: 'success'
				});
			},

			// AI状态相关方法
			checkAiLoginStatus() {
				// 延迟检查，确保WebSocket连接已建立
				setTimeout(() => {
					this.sendAiStatusCheck();
					// 不再更新AI启用状态，保持原有选择
				}, 2000);
			},

			sendAiStatusCheck() {
				// 检查腾讯元宝登录状态
				this.sendWebSocketMessage({
					type: 'PLAY_CHECK_YB_LOGIN',
					userId: this.userId,
					corpId: this.corpId
				});

				// 检查豆包登录状态
				this.sendWebSocketMessage({
					type: 'PLAY_CHECK_DB_LOGIN',
					userId: this.userId,
					corpId: this.corpId
				});

        // 检查DeepSeek登录状态
        this.sendWebSocketMessage({
          type: 'PLAY_CHECK_DEEPSEEK_LOGIN',
          userId: this.userId,
          corpId: this.corpId
        });

        // 检查通义千问登录状态
        this.sendWebSocketMessage({
          type: 'PLAY_CHECK_QW_LOGIN',
          userId: this.userId,
          corpId: this.corpId
        });



        // 检查秘塔登录状态
        this.sendWebSocketMessage({
          type: "PLAY_CHECK_METASO_LOGIN",
          userId: this.userId,
          corpId: this.corpId,
        });



        // 检查百度AI登录状态
        this.sendWebSocketMessage({
          type: 'PLAY_CHECK_BAIDU_LOGIN',
          userId: this.userId,
          corpId: this.corpId
        });

        // 检查知乎直答登录状态
        console.log('发送知乎直答登录检验请求:', {
          type: 'PLAY_CHECK_ZHZD_LOGIN',
          userId: this.userId,
          corpId: this.corpId
        });
        this.sendWebSocketMessage({
          type: 'PLAY_CHECK_ZHZD_LOGIN',
          userId: this.userId,
          corpId: this.corpId
        });

			},

			getPlatformIcon(type) {
				const icons = {
					yuanbao: 'https://u3w.com/chatfile/yuanbao.png',
					doubao: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
					agent: 'https://u3w.com/chatfile/yuanbao.png',
          tongyi: 'https://u3w.com/chatfile/TongYi.png'
				};
				return icons[type] || '';
			},

			getPlatformName(type) {
				const names = {
					yuanbao: '腾讯元宝',
					doubao: '豆包',
					agent: '智能体',
          // tongyi: '通义千问',
				};
				return names[type] || '';
			},





			async refreshAiStatus() {
				// 防止重复刷新
				if (this.isRefreshing) {
					console.log('正在刷新中，跳过重复操作');
					return;
				}
				
				this.isRefreshing = true;
				
				try {
					// 首先确保企业ID最新
					try {
						const result = await ensureLatestCorpId();
						console.log('刷新按钮：主机ID已更新为最新值:', result.corpId);
						if (result.corpId !== this.corpId) {
							console.log('检测到主机ID变更，从', this.corpId, '更新为', result.corpId);
							this.corpId = result.corpId;
							this.userInfoReq.corpId = result.corpId;
							// 更新本地存储，确保一致性
							storage.set(constant.corpId, result.corpId);
							console.log('本地主机ID存储已同步:', result.corpId);
						} else {
							console.log('主机ID无变化，当前值:', this.corpId);
						}
					} catch (error) {
						console.error('确保企业ID最新失败:', error);
					}

				// 重置所有AI状态为加载中
				this.isLoading = {
					yuanbao: true,
					doubao: true,
          deepseek: true,
          tongyi: true,
          metaso: true,

          baidu: true,
          zhzd: true,
				};

				// 重置登录状态
				this.aiLoginStatus = {
					yuanbao: false,
					doubao: false,
          deepseek: false,
          tongyi: false,
          metaso: false,
          baidu: false,
          zhzd: false,
				};

				// 重置账户信息
				this.accounts = {
					yuanbao: '',
					doubao: '',
          deepseek: '',
          tongyi: '',
		      metaso: '',
          baidu: '',
          zhzd: '',
				};

				// 显示刷新提示
				uni.showToast({
					title: '正在刷新连接状态...',
					icon: 'loading',
					duration: 1500
				});

				// 重新建立WebSocket连接，使用最新的主机ID
				console.log('准备重新建立WebSocket连接，使用主机ID:', this.corpId);
				this.closeWebSocket();
				setTimeout(() => {
					console.log('开始初始化WebSocket，主机ID:', this.corpId);
					this.initWebSocket();
					// 延迟检查AI状态，确保WebSocket重新连接
					setTimeout(() => {
						console.log('发送AI状态检查，主机ID:', this.corpId);
						this.sendAiStatusCheck();
						// 重置刷新标志
						this.isRefreshing = false;
					}, 2000);
				}, 500);
				} catch (error) {
					console.error('刷新AI状态时发生错误:', error);
					this.isRefreshing = false;
					uni.showToast({
						title: '刷新失败，请重试',
						icon: 'none'
					});
				}
			},

			// 判断AI是否已登录可用
			isAiLoginEnabled(ai) {
				switch (ai.name) {
					case '腾讯元宝':
						return this.aiLoginStatus.yuanbao; // 腾讯元宝登录状态
					case '豆包':
						return this.aiLoginStatus.doubao; // 豆包登录状态
          case 'DeepSeek':
            return this.aiLoginStatus.deepseek; // 使用实际的DeepSeek登录状态
          case '通义千问':
            return this.aiLoginStatus.tongyi;   // 通义登录状态
          case "秘塔":
            return this.aiLoginStatus.metaso; // 秘塔登录状态
          case "知乎直答":
            return this.aiLoginStatus.zhzd; // 知乎直答登录状态
          case "百度AI":
            return this.aiLoginStatus.baidu; // 百度AI登录状态

          default:
						return false;
				}
			},

			// 判断AI是否在加载状态
			isAiInLoading(ai) {
				switch (ai.name) {
					case '腾讯元宝':
						return this.isLoading.yuanbao;
					case '豆包':
						return this.isLoading.doubao;
          case 'DeepSeek':
            return this.isLoading.deepseek; // 使用实际的DeepSeek加载状态
          case '通义千问':
            return this.isLoading.tongyi;
          case "秘塔":
            return this.isLoading.metaso;
          case "知乎直答":
            return this.isLoading.zhzd;
          case "百度AI":
            return this.isLoading.baidu;

          default:
						return false;
				}
			},

			// 根据登录状态禁用相关AI（已废弃，不再修改enabled状态）
			disableAIsByLoginStatus(loginType) {
				// 不再修改enabled状态，只通过UI控制操作权限
				console.log(`AI ${loginType} 登录状态已更新，但保持原有选择`);
			},

			// 根据当前AI登录状态更新AI启用状态（已废弃，不再修改enabled状态）
			updateAiEnabledStatus() {
				// 不再修改enabled状态，只通过UI控制操作权限
				console.log('AI登录状态已更新，但保持原有选择');
			},

			// 微头条相关方法
			// 微头条文章编辑相关方法
			showTthArticleEditModal() {
				this.tthArticleEditVisible = true;
			},

			closeTthArticleEditModal() {
				this.tthArticleEditVisible = false;
			},

			confirmTTHPublish() {
				if (!this.tthArticleTitle || !this.tthArticleContent) {
					uni.showToast({ title: '请填写标题和内容', icon: 'none' });
					return;
				}
				const publishRequest = {
					jsonrpc: '2.0',
					id: this.generateUUID(),
					method: '微头条发布',
					params: {
						taskId: this.generateUUID(),
						userId: this.userId,
						corpId: this.corpId,
						roles: '',
						title: this.tthArticleTitle,
						content: this.tthArticleContent,
						type: '微头条发布'
					}
				};
				this.message(publishRequest);
				this.tthArticleEditVisible = false;
				this.tthFlowVisible = true;
				this.tthFlowLogs = [];
				this.tthFlowImages = [];
				uni.showToast({ title: '微头条发布请求已发送！', icon: 'success' });
			},



			// 微头条发布流程相关方法
			closeTthFlowDialog() {
				this.tthFlowVisible = false;
				this.tthFlowLogs = [];
				this.tthFlowImages = [];
			},

			// HTML转纯文本方法
			htmlToText(html) {
				if (!html) return '';
				return html.replace(/<[^>]*>/g, '');
			},

			// 格式化时间
			formatTime(timestamp) {
				try {
					if (!timestamp) {
						return '时间未知';
					}

					let date;

					if (typeof timestamp === 'number') {
						date = new Date(timestamp);
					} else if (typeof timestamp === 'string') {
						// 处理ISO 8601格式：2025-06-25T07:18:54.110Z
						if (timestamp.includes('T') && (timestamp.includes('Z') || timestamp.includes('+'))) {
							date = new Date(timestamp);
						}
						// 处理 "2025-6-23 14:53:12" 这种格式
						else {
							const match = timestamp.match(/(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2}):(\d{1,2})/);
							if (match) {
								const [, year, month, day, hour, minute, second] = match;
								date = new Date(
									parseInt(year),
									parseInt(month) - 1,
									parseInt(day),
									parseInt(hour),
									parseInt(minute),
									parseInt(second)
								);
							} else {
								// 如果正则不匹配，尝试其他方式
								const fixedTimestamp = timestamp.replace(/\s/g, 'T');
								date = new Date(fixedTimestamp);

								if (isNaN(date.getTime())) {
									date = new Date(timestamp);
								}
							}
						}
					} else if (timestamp instanceof Date) {
						date = timestamp;
					} else {
						date = new Date(timestamp);
					}

					if (isNaN(date.getTime())) {
						return '时间未知';
					}

					// 使用更简洁的时间格式，避免显示时区信息
					const hour = date.getHours().toString().padStart(2, '0');
					const minute = date.getMinutes().toString().padStart(2, '0');
					const second = date.getSeconds().toString().padStart(2, '0');

					const timeString = `${hour}:${minute}:${second}`;

					return timeString;

				} catch (error) {
					console.error('格式化时间错误:', error, timestamp);
					return '时间未知';
				}
			}
		}
	};
</script>

<style scoped>
	.console-container {
		height: 100vh;
		background-color: #f5f7fa;
		display: flex;
		flex-direction: column;
	}

	/* 顶部固定区域 */
	.header-fixed {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		z-index: 1000;
		background-color: #fff;
		border-bottom: 1px solid #ebeef5;
	}

	.header-content {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 10px 15px;
		/* padding-top 通过内联样式动态设置 */
	}

	.header-title {
		font-size: 18px;
		font-weight: 600;
		color: #303133;
	}

	.header-actions {
		display: flex;
		gap: 10px;
	}

	.action-btn {
		width: 36px;
		height: 36px;
		border-radius: 18px;
		display: flex;
		align-items: center;
		justify-content: center;
		transition: all 0.3s ease;
		position: relative;
		overflow: hidden;
	}

	.action-btn:active {
		transform: scale(0.92);
		opacity: 0.7;
	}
  .connection-indicator {
    position: absolute;
    top: -2px;
    right: -2px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    border: 1px solid #fff;
    z-index: 1;
  }

  .connection-indicator.connected {
    background-color: #52c41a;
    box-shadow: 0 0 4px rgba(82, 196, 26, 0.6);
  }

  .connection-indicator.disconnected {
    background-color: #ff4d4f;
    box-shadow: 0 0 4px rgba(255, 77, 79, 0.6);
  }

	.action-icon {
		font-size: 18px;
		color: #ffffff;
		font-weight: 500;
		text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: 1;
		position: relative;
	}

	.action-icon-img {
		width: 20px;
		height: 20px;
		z-index: 1;
		position: relative;
	}

	/* 创建新会话图标更大 */
	.new-chat-btn .action-icon-img {
		width: 24px;
		height: 24px;
	}

	/* 移除渐变背景，使用原生图标 */
	.refresh-btn,
	.history-btn,
	.new-chat-btn {
		background: transparent;
		box-shadow: none;
	}



	/* 主体滚动区域 */
	.main-scroll {
		flex: 1;
		/* height 和 padding-top 通过内联样式动态设置 */
		padding-bottom: 20px;
		box-sizing: border-box;
	}

	/* 区块样式 */
	.section-block {
		margin: 10px 15px;
		background-color: #fff;
		border-radius: 8px;
		overflow: hidden;
		box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
	}

	.section-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 15px;
		border-bottom: 1px solid #ebeef5;
		background-color: #fafafa;
	}

	.section-title {
		font-size: 16px;
		font-weight: 600;
		color: #303133;
	}

	.section-arrow {
		font-size: 14px;
		color: #909399;
		transition: transform 0.3s;
	}

	.task-arrow {
		font-size: 12px;
		color: #909399;
		transition: transform 0.3s;
		margin-right: 8px;
	}

	.close-icon {
		font-size: 18px;
		color: #909399;
		cursor: pointer;
	}

	.section-content {
		padding: 15px;
	}

	/* AI配置区域 */
	.ai-grid {
		display: flex;
		flex-wrap: wrap;
		gap: 10px;
	}

	.ai-card {
		width: calc(50% - 5px);
		border: 1px solid #ebeef5;
		border-radius: 8px;
		padding: 10px;
		transition: all 0.3s;
		min-height: 65px;
		box-sizing: border-box;
	}

	.ai-card.ai-enabled {
		border-color: #409EFF;
		background-color: #f0f8ff;
	}

	.ai-card.ai-disabled {
		background-color: #fafafa;
		border-color: #e4e7ed;
		border-style: dashed;
		pointer-events: none;
	}

	.ai-avatar.avatar-disabled {
		opacity: 0.7;
		filter: grayscale(30%);
	}

	.ai-name.name-disabled {
		color: #373839;
	}

	.login-required {
		font-size: 9px;
		color: red;
		margin-top: 2px;
		line-height: 1;
	}

	.loading-text {
		font-size: 9px;
		color: #409EFF;
		margin-top: 2px;
		line-height: 1;
	}

	.capability-tag.capability-disabled {
		opacity: 0.5;
		background-color: #f5f5f5;
		border-color: #e4e7ed;
		pointer-events: none;
	}

	.capability-tag.capability-disabled .capability-text {
		color: #c0c4cc;
	}

	.ai-header {
		display: flex;
		align-items: flex-start;
		margin-bottom: 8px;
		min-height: 24px;
	}

	.ai-avatar {
		width: 24px;
		height: 24px;
		border-radius: 12px;
		margin-right: 8px;
	}

	.ai-info {
		flex: 1;
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.ai-name-container {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		min-width: 0;
	}

	.ai-name {
		font-size: 12px;
		font-weight: 500;
		color: #303133;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		max-width: 100%;
	}

	.ai-capabilities {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.capability-tag {
		padding: 2px 6px;
		border-radius: 10px;
		border: 1px solid #dcdfe6;
		background-color: #fff;
		transition: all 0.3s;
	}

	.capability-tag.capability-active {
		background-color: #409EFF;
		border-color: #409EFF;
	}

	.capability-text {
		font-size: 10px;
		color: #606266;
	}

	.capability-tag.capability-active .capability-text {
		color: #fff;
	}

	/* 提示词输入区域 */
	.prompt-textarea {
		width: 100%;
		min-height: 80px;
		padding: 10px;
		border: 1px solid #dcdfe6;
		border-radius: 4px;
		font-size: 14px;
		line-height: 1.5;
		resize: none;
		box-sizing: border-box;
	}

	.prompt-footer {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-top: 10px;
	}

	.word-count {
		font-size: 12px;
		color: #909399;
	}

	.send-btn {
		background-color: #409EFF;
		color: #fff;
		border: none;
		border-radius: 20px;
		padding: 6px 0;
		font-size: 14px;
		width: 50%;
		height: 30px;
		display: flex;
		margin-left: 50%;
		align-items: center;
		justify-content: center;
	}

	.send-btn-disabled {
		background-color: #c0c4cc;
	}

	/* 任务执行状态 */
	.task-flow {
		margin-bottom: 15px;
	}

	.task-item {
		border: 1px solid #ebeef5;
		border-radius: 8px;
		margin-bottom: 10px;
		overflow: hidden;
	}

	.task-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 12px;
		background-color: #fafafa;
		border-bottom: 1px solid #ebeef5;
	}

	.task-left {
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.task-avatar {
		width: 20px;
		height: 20px;
		border-radius: 10px;
	}

	.task-name {
		font-size: 14px;
		font-weight: 500;
		color: #303133;
	}

	.task-right {
		display: flex;
		align-items: center;
		gap: 5px;
	}

	.status-text {
		font-size: 12px;
		color: #606266;
	}

	.status-icon {
		font-size: 14px;
	}

	.status-completed {
		color: #67c23a;
	}

	.status-failed {
		color: #f56c6c;
	}

	.status-running {
		color: #409EFF;
		animation: rotate 1s linear infinite;
	}

	.status-idle {
		color: #909399;
	}

	@keyframes rotate {
		from {
			transform: rotate(0deg);
		}

		to {
			transform: rotate(360deg);
		}
	}

	/* 进度日志 */
	.progress-logs {
		padding: 10px 15px;
		max-height: 150px;
		overflow-y: auto;
	}

	.progress-item {
		display: flex;
		align-items: flex-start;
		margin-bottom: 8px;
		position: relative;
	}

	.progress-item:last-child {
		margin-bottom: 0;
	}

	.progress-dot {
		width: 8px;
		height: 8px;
		border-radius: 4px;
		background-color: #dcdfe6;
		margin-right: 10px;
		margin-top: 6px;
		flex-shrink: 0;
	}

	.progress-dot.dot-completed {
		background-color: #67c23a;
	}

	.progress-content {
		flex: 1;
		min-width: 0;
	}

	.progress-time {
		font-size: 10px;
		color: #909399;
		margin-bottom: 2px;
	}

	.progress-text {
		font-size: 12px;
		color: #606266;
		line-height: 1.4;
		word-break: break-all;
	}

	/* 主机可视化 */
	.screenshots-section {
		margin-top: 15px;
	}

	.screenshots-header {
		display: flex;
		align-items: center;
		margin-bottom: 10px;
		gap: 10px;
	}

	.section-subtitle {
		font-size: 14px;
		font-weight: 500;
		color: #303133;
	}

	.auto-play-text {
		font-size: 12px;
		color: #606266;
	}

	.screenshots-swiper {
		height: 200px;
		border-radius: 8px;
		overflow: hidden;
	}

	.screenshot-image {
		width: 100%;
		height: 100%;
	}

	/* 结果展示区域 - 简洁标签页风格 */

	.result-tabs {
		white-space: nowrap;
		margin-bottom: 20px;
		border-bottom: 1px solid #ebeef5;
	}

	.tab-container {
		display: flex;
		gap: 0;
		padding: 0 15px;
	}

	.result-tab {
		flex-shrink: 0;
		padding: 12px 20px;
		position: relative;
		transition: all 0.3s ease;
		background: transparent;
		border: none;
	}

	.result-tab::after {
		content: '';
		position: absolute;
		bottom: 0;
		left: 50%;
		width: 0;
		height: 2px;
		background: #409EFF;
		transition: all 0.3s ease;
		transform: translateX(-50%);
	}

	.result-tab.tab-active::after {
		width: 80%;
	}

	.tab-text {
		font-size: 14px;
		color: #909399;
		font-weight: 400;
		transition: all 0.3s ease;
		white-space: nowrap;
	}

	.result-tab.tab-active .tab-text {
		color: #409EFF;
		font-weight: 500;
	}

	.result-tab:active {
		transform: translateY(1px);
	}

	.result-content {
		margin-top: 10px;
	}

	.result-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 10px;
		padding-bottom: 8px;
		border-bottom: 1px solid #ebeef5;
	}

	.result-title {
		font-size: 14px;
		font-weight: 500;
		color: #303133;
	}



	.result-body {
		margin-bottom: 15px;
	}

	.result-image-container {
		display: flex;
		justify-content: center;
	}

	.result-image {
		max-width: 100%;
		border-radius: 8px;
	}

	/* PDF文件容器样式 */
	.result-pdf-container {
		background-color: #f9f9f9;
		border-radius: 8px;
		border: 2px dashed #dcdfe6;
		overflow: hidden;
	}

	.pdf-placeholder {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 20px;
	}

	.pdf-icon {
		font-size: 48px;
		margin-bottom: 10px;
	}

	.pdf-text {
		font-size: 14px;
		color: #606266;
		margin-bottom: 15px;
	}

	.pdf-actions {
		display: flex;
		gap: 10px;
		justify-content: center;
	}

	.pdf-btn {
		border-radius: 4px;
		padding: 8px 16px;
		font-size: 12px;
		height: auto;
		line-height: 1.2;
		flex: 1;
		max-width: 100px;
	}

	.download-btn {
		background-color: #f6ffed;
		color: #52c41a;
		border: 1px solid #b7eb8f;
	}

	.copy-btn {
		background-color: #fff7e6;
		color: #fa8c16;
		border: 1px solid #ffd591;
	}

	.result-text {
		padding: 10px;
		background-color: #f9f9f9;
		border-radius: 8px;
		font-size: 14px;
		line-height: 1.6;
		max-height: 300px;
		overflow-y: auto;
	}

	.result-actions {
		display: flex;
		justify-content: flex-end;
		gap: 8px;
		flex-wrap: wrap;
		margin-bottom: 15px;
	}

	.action-btn-small, .share-link-btn, .collect-btn {
		border: 1px solid #dcdfe6;
		border-radius: 12px;
		padding: 4px 12px;
		font-size: 12px;
		height: auto;
		line-height: 1.2;
		min-width: 60px;
		text-align: center;
		transition: all 0.3s ease;
	}

	.action-btn-small {
		background-color: #f5f7fa;
		color: #606266;
		border-color: #dcdfe6;
	}

	.share-link-btn {
		background-color: #67c23a;
		color: #fff;
		border-color: #67c23a;
	}

	.collect-btn {
		background-color: #e6a23c;
		color: #fff;
		border-color: #e6a23c;
	}

	/* 按钮悬停和点击效果 */
	.action-btn-small:active {
		opacity: 0.8;
		transform: scale(0.95);
	}

	.share-link-btn:active {
		opacity: 0.8;
		transform: scale(0.95);
	}

	.collect-btn:active {
		opacity: 0.8;
		transform: scale(0.95);
	}

	/* 智能评分按钮在标题栏 */
	.score-btn {
		background-color: #409EFF;
		color: #fff;
		border: none;
		border-radius: 12px;
		padding: 4px 12px;
		font-size: 12px;
		height: auto;
		line-height: 1.2;
		margin-left: 57%;
		flex-shrink: 0;
	}

	/* 历史记录抽屉 */
	.drawer-mask {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		background-color: rgba(0, 0, 0, 0.5);
		z-index: 999;
		display: flex;
		justify-content: flex-end;
	}

	.drawer-container {
		width: 280px;
		height: 100vh;
		background-color: #fff;
	}

	.drawer-content {
		margin-top: 120rpx;
		height: 100vh;
		background-color: #fff;
		display: flex;
		flex-direction: column;
		box-sizing: border-box;
	}

	.drawer-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 15px;
		border-bottom: 1px solid #ebeef5;
	}

	.drawer-title {
		font-size: 16px;
		font-weight: 600;
		color: #303133;
	}

	.history-list {
		flex: 1;
		padding: 10px;
		height: calc(100vh - 60px);
		box-sizing: border-box;
	}

	.history-group {
		margin-bottom: 15px;
	}

	.history-date {
		font-size: 12px;
		color: #909399;
		margin-bottom: 8px;
		padding: 5px 0;
		border-bottom: 1px solid #f0f0f0;
	}

	.history-item {
		background-color: #f9f9f9;
		border-radius: 8px;
		padding: 10px;
		margin-bottom: 8px;
	}

	.history-prompt {
		font-size: 14px;
		color: #303133;
		line-height: 1.4;
		margin-bottom: 5px;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.history-time {
		font-size: 10px;
		color: #909399;
	}

	/* 智能评分弹窗 */
	.popup-mask {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		background-color: rgba(0, 0, 0, 0.5);
		z-index: 999;
		display: flex;
		align-items: flex-end;
	}

	.score-modal {
		width: 100%;
		background-color: #fff;
		border-radius: 20px 20px 0 0;
		max-height: 80vh;
		display: flex;
		flex-direction: column;
	}

	.score-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 15px;
		border-bottom: 1px solid #ebeef5;
	}

	.score-title {
		font-size: 16px;
		font-weight: 600;
		color: #303133;
	}

	.score-content {
		flex: 1;
		padding: 15px;
		overflow-y: auto;
	}

	.score-selection {
		margin-bottom: 20px;
	}

	.score-subtitle {
		font-size: 14px;
		font-weight: 500;
		color: #303133;
		margin-bottom: 10px;
	}

	.score-checkboxes {
		margin-top: 30rpx;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.checkbox-item {
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.checkbox-text {
		font-size: 14px;
		color: #606266;
	}

	.score-prompt-section {
		margin-bottom: 20px;
	}

	/* 模板选择器样式 */
	.template-selector {
		margin: 10px 0;
	}

	.picker-display {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 10px 12px;
		background-color: #f5f7fa;
		border: 1px solid #dcdfe6;
		border-radius: 8px;
		font-size: 14px;
	}

	.picker-text {
		color: #606266;
		flex: 1;
	}

	.picker-arrow {
		color: #909399;
		font-size: 12px;
		margin-left: 8px;
	}

	.score-textarea {
		width: 100%;
		min-height: 120px;
		max-height: 300px;
		padding: 10px;
		border: 1px solid #dcdfe6;
		border-radius: 8px;
		font-size: 14px;
		resize: vertical;
		box-sizing: border-box;
		margin-top: 10px;
		word-wrap: break-word;
		overflow-y: auto;
	}

	/* 微头条文章内容超过2000字时的样式 */
	.score-textarea.content-exceeded {
		border-color: #f56c6c;
		background-color: #fef0f0;
	}

	/* 字符计数样式 */
	.char-count {
		text-align: right;
		font-size: 12px;
		color: #909399;
		margin-top: 5px;
	}

	/* 字符计数超过限制时的样式 */
	.char-count-exceeded {
		color: #f56c6c;
		font-weight: bold;
	}

	.score-submit-btn {
		width: 100%;
		background-color: #409EFF;
		color: #fff;
		border: none;
		border-radius: 8px;
		padding: 12px;
		font-size: 16px;
	}

	.score-submit-btn[disabled] {
		background-color: #c0c4cc;
	}

	/* 响应式布局 */
	@media (max-width: 375px) {
		.ai-card {
			width: 100%;
		}

		.header-content {
			padding: 8px 12px;
		}

		.section-block {
			margin: 8px 12px;
		}
	}

	/* 响应式布局 */
	@media (max-width: 375px) {
		.ai-card {
			width: 100%;
		}

		.header-content {
			padding: 8px 12px;
		}

		.section-block {
			margin: 8px 12px;
		}
	}

  /* DeepSeek响应内容的特定样式 */
  .deepseek-format-container {
    margin: 20px 0;
    padding: 15px;
    background-color: #f9f9f9;
    border-radius: 5px;
    border: 1px solid #eaeaea;
  }

  .result-text .deepseek-response {
    max-width: 100%;
    margin: 0 auto;
    background-color: #fff;
    border-radius: 8px;
    padding: 10px;
    font-family: Arial, sans-serif;
  }

  .result-text .deepseek-response pre {
    background-color: #f5f5f5;
    padding: 10px;
    border-radius: 4px;
    font-family: monospace;
    overflow-x: auto;
    display: block;
    margin: 10px 0;
    font-size: 12px;
  }

  .result-text .deepseek-response code {
    background-color: #f5f5f5;
    padding: 2px 4px;
    border-radius: 3px;
    font-family: monospace;
    font-size: 12px;
  }

  .result-text .deepseek-response table {
    border-collapse: collapse;
    width: 100%;
    margin: 15px 0;
  }

  .result-text .deepseek-response th,
  .result-text .deepseek-response td {
    border: 1px solid #ddd;
    padding: 8px;
    text-align: left;
    font-size: 12px;
  }

  .result-text .deepseek-response th {
    background-color: #f2f2f2;
    font-weight: bold;
  }

  .result-text .deepseek-response h1,
  .result-text .deepseek-response h2,
  .result-text .deepseek-response h3,
  .result-text .deepseek-response h4,
  .result-text .deepseek-response h5,
  .result-text .deepseek-response h6 {
    margin-top: 20px;
    margin-bottom: 10px;
    font-weight: bold;
    color: #222;
  }

  .result-text .deepseek-response a {
    color: #0066cc;
    text-decoration: none;
  }

  .result-text .deepseek-response blockquote {
    border-left: 4px solid #ddd;
    padding-left: 15px;
    margin: 15px 0;
    color: #555;
  }

  .result-text .deepseek-response ul,
  .result-text .deepseek-response ol {
    padding-left: 20px;
    margin: 10px 0;
  }

  /* 媒体选择样式 */
  /* AI选择样式 */
  .ai-selection-section {
    margin-bottom: 20px;
  }

  .ai-radio-group {
    display: flex;
    gap: 10px;
    margin: 10px 0;
  }

  .ai-radio-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 15px 10px;
    border: 1px solid #e0e0e0;
    border-radius: 8px;
    background-color: #f9f9f9;
    transition: all 0.3s ease;
  }

  .ai-radio-item.active {
    border-color: #409eff;
    background-color: #ecf5ff;
  }

  .ai-icon {
    font-size: 24px;
    margin-bottom: 5px;
  }

  .ai-text {
    font-size: 14px;
    color: #333;
    font-weight: 500;
  }

  .media-selection-section {
    margin-bottom: 20px;
  }

  .media-radio-group {
    display: flex;
    gap: 10px;
    margin: 10px 0;
  }

  .media-radio-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 15px 10px;
    border: 1px solid #e0e0e0;
    border-radius: 8px;
    background-color: #f9f9f9;
    transition: all 0.3s ease;
  }

  .media-radio-item.active {
    border-color: #409eff;
    background-color: #ecf5ff;
  }

  .media-icon {
    font-size: 24px;
    margin-bottom: 5px;
  }

  .media-text {
    font-size: 14px;
    color: #333;
    font-weight: 500;
  }

  .media-description {
    margin-top: 10px;
    padding: 8px 12px;
    background-color: #f0f9ff;
    border-radius: 4px;
    border-left: 3px solid #409eff;
  }

  .description-text {
    font-size: 12px;
    color: #666;
    line-height: 1.4;
  }

  /* 微头条按钮样式 */
  .media-radio-item.active {
    background: linear-gradient(135deg, #ff6b35, #f7931e);
  }

  /* 腾讯元宝模型选择样式 */
  .model-selection {
    margin-top: 12rpx;
    padding: 8rpx 12rpx;
    background-color: #f8f9ff;
    border-radius: 8rpx;
    border: 1rpx solid #e1e8ff;
  }

  .model-label {
    font-size: 12px;
    color: #666;
    margin-bottom: 8rpx;
    display: block;
  }

  .model-picker {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8rpx 12rpx;
    background-color: #fff;
    border: 1rpx solid #ddd;
    border-radius: 6rpx;
    min-height: 32rpx;
  }

  .model-text {
    font-size: 14px;
    color: #333;
    flex: 1;
  }

  .picker-arrow {
    font-size: 12px;
    color: #999;
    margin-left: 8rpx;
  }

  /* 禁用状态样式 */
  .ai-card.ai-disabled .model-picker {
    background-color: #f5f5f5;
    border-color: #e0e0e0;
  }

  .ai-card.ai-disabled .model-text,
  .ai-card.ai-disabled .picker-arrow {
    color: #ccc;
  }
</style>

