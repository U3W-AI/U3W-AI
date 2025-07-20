<template>
	<view class="console-container">
		<!-- 顶部固定区域 -->
		<view class="header-fixed">
			<view class="header-content">
				<text class="header-title">AI控制台</text>
				<view class="header-actions">
					<view class="action-btn refresh-btn" @tap="refreshAiStatus">
						<image class="action-icon-img" src="https://u3w.com/chatfile/shuaxin.png" mode="aspectFit">
						</image>
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
			:show-scrollbar="false" :fast-deceleration="false">

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
											v-if="!isAiLoginEnabled(ai) && !isLoading.yuanbao && !isLoading.doubao && !isLoading.agent"
											class="login-required">需登录</text>
										<text v-if="isAiInLoading(ai)" class="loading-text">检查中...</text>
									</view>
									<switch :checked="ai.enabled && isAiLoginEnabled(ai)"
										:disabled="!isAiLoginEnabled(ai) || isAiInLoading(ai)"
										@change="toggleAI(ai, $event)" color="#409EFF" style="transform: scale(0.8);" />
								</view>
							</view>
							<view class="ai-capabilities" v-if="ai.capabilities.length > 0">
								<view v-for="capability in ai.capabilities" :key="capability.value"
									class="capability-tag"
									:class="[ai.selectedCapabilities.includes(capability.value) ? 'capability-active' : '', (!ai.enabled || !isAiLoginEnabled(ai)) ? 'capability-disabled' : '']"
									@tap="toggleCapability(ai, capability.value)">
									<text class="capability-text">{{ capability.label }}</text>
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
							<button class="collect-btn" size="mini"
								@tap="showLayoutModal">投递到公众号</button>
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
					<view class="score-prompt-section">
						<text class="score-subtitle">评分提示词：</text>
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

		<!-- 智能排版弹窗 -->
		<view v-if="layoutModalVisible" class="popup-mask" @tap="closeLayoutModal">
			<view class="score-modal" @tap.stop>
				<view class="score-header">
					<text class="score-title">智能排版</text>
					<text class="close-icon" @tap="closeLayoutModal">✕</text>
				</view>
				<view class="score-content">
					<view class="score-prompt-section">
						<text class="score-subtitle">排版提示词：</text>
						<textarea class="score-textarea" v-model="layoutPrompt"
							placeholder="请输入排版要求" maxlength="100000"></textarea>
					</view>

					<button class="score-submit-btn" :disabled="layoutPrompt.trim().length === 0" @tap="handleLayout">
						开始智能排版
					</button>
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
		message, saveUserChatData, getChatHistory,pushAutoOffice
	} from "@/api/wechat/aigc";
	import {
		v4 as uuidv4
	} from 'uuid';
	import storage from '@/utils/storage'
	import constant from '@/utils/constant'


	export default {
		name: 'MiniConsole',
		data() {
			return {
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
            avatar: 'https://communication.cn-nb1.rains3.com/Deepseek.png',
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
            isExpanded: true
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
						isExpanded: true
					}
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

				// 收录计数器
				collectNum: 0,

				// 智能排版
				layoutPrompt: '',

				// WebSocket
				socketTask: null,
				reconnectTimer: null,
				heartbeatTimer: null,
				reconnectCount: 0,
				maxReconnectCount: 5,
				isConnecting: false,
				scrollIntoView: '',

				// 弹窗状态
				historyDrawerVisible: false,
				scoreModalVisible: false,
				layoutModalVisible: false,
				currentLayoutResult: null, // 当前要排版的结果

				// AI登录状态
				aiLoginStatus: {
					doubao: false,
          deepseek: false, // DeepSeek初始为未登录状态
		          mini: false,
				},
				accounts: {
					doubao: '',
          deepseek: '',
		  mini: '',
				},
				isLoading: {
					doubao: true,
          deepseek: true, // DeepSeek初始为加载状态
		  mini: true
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
				const isCheckingStatus = this.isLoading.doubao || this.isLoading.deepseek || this.isLoading.mini;;

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
			}
		},
		onLoad() {
			this.initUserInfo();

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
			this.checkAiLoginStatus(); // 检查AI登录状态
		},

		onUnload() {
			this.closeWebSocket();
		},

		methods: {
			// 初始化用户信息
			initUserInfo() {
				// 从store获取用户信息，兼容缓存方式
			this.userId = storage.get(constant.userId);
			this.corpId = storage.get(constant.corpId);

				this.chatId = this.generateUUID();

				// 初始化请求参数
				this.userInfoReq.userId = this.userId;
				this.userInfoReq.corpId = this.corpId;

				console.log('初始化用户信息:', {
					userId: this.userId,
					corpId: this.corpId
				});
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

				const index = ai.selectedCapabilities.indexOf(capabilityValue);
				if (index === -1) {
					ai.selectedCapabilities.push(capabilityValue);
				} else {
					ai.selectedCapabilities.splice(index, 1);
				}
			},

			// 发送提示词
			sendPrompt() {
				if (!this.canSend) return;

				this.screenshots = [];
				// 折叠所有区域
				this.sectionExpanded.aiConfig = false;
				this.sectionExpanded.promptInput = false;
				// this.sectionExpanded.taskStatus = false;

				this.taskStarted = true;
				this.results = []; // 清空之前的结果

				this.userInfoReq.roles = '';
				this.userInfoReq.taskId = this.generateUUID();
				this.userInfoReq.userId = this.userId;
				this.userInfoReq.corpId = this.corpId;
				this.userInfoReq.userPrompt = this.promptInput;

				// 获取启用的AI列表及其状态
				this.enabledAIs = this.aiList.filter(ai => ai.enabled);

				// 将所有启用的AI状态设置为运行中
				this.enabledAIs.forEach(ai => {
					ai.status = 'running';
				});

				// 构建角色参数
				this.enabledAIs.forEach(ai => {
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
					if (ai.name === "MiniMax Chat") {
					  this.userInfoReq.roles = this.userInfoReq.roles + "mini-max-agent,";
					  if (ai.selectedCapabilities.includes("deep_thinking")) {
					    this.userInfoReq.roles = this.userInfoReq.roles + "max-sdsk,";
					  }
					  if (ai.selectedCapabilities.includes("web_search")) {
					    this.userInfoReq.roles = this.userInfoReq.roles + "max-lwss,";
					  }
					}
				});

				console.log("参数：", this.userInfoReq);

				// 滚动到任务状态区域
				this.scrollIntoView = 'task-status';

				//调用后端接口
				this.jsonRpcReqest.id = this.generateUUID();
				this.jsonRpcReqest.method = "使用F8S";
				this.jsonRpcReqest.params = this.userInfoReq;
				this.message(this.jsonRpcReqest);
				this.userInfoReq.isNewChat = false;

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

			if (this.isConnecting) {
				console.log('WebSocket正在连接中，跳过重复连接');
				return;
			}

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

			this.reconnectCount++;
			const delay = Math.min(1000 * Math.pow(2, this.reconnectCount), 30000); // 指数退避，最大30秒

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
					this.socketTask.send({
						data: JSON.stringify(data)
					});
				} else {
					console.warn('WebSocket未连接，无法发送消息');
				}
			},

			// 调用后端message接口
			message(data) {
				message(data).then(res => {
					if (res.code == 201) {
						uni.showToast({
							title: res.messages,
							icon: 'none',
							duration: 1500,
						});
					}
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
				this.socketTask.close();
				this.socketTask = null;
			}

			// 重置状态
			this.isConnecting = false;
			this.reconnectCount = 0;
		},

					// 处理WebSocket消息
		handleWebSocketMessage(data) {
			try {
				const datastr = data;
				const dataObj = JSON.parse(datastr);

				// 忽略心跳响应
				if (dataObj.type === 'HEARTBEAT_RESPONSE' || dataObj.type === 'HEARTBEAT') {
					return;
				}

					// 处理chatId消息
					if (dataObj.type === 'RETURN_DB_CHATID' && dataObj.chatId) {
						this.userInfoReq.dbChatId = dataObj.chatId;
					}else if (dataObj.type === "RETURN_MAX_CHATID" && dataObj.chatId) {
          this.userInfoReq.maxChatId = dataObj.chatId;
        }

					// 处理进度日志消息
					if (dataObj.type === 'RETURN_PC_TASK_LOG' && dataObj.aiName) {
						const targetAI = this.enabledAIs.find(ai => ai.name === dataObj.aiName);
						if (targetAI) {
							// 将新进度添加到数组开头
							targetAI.progressLogs.unshift({
								content: dataObj.content,
								timestamp: new Date(),
								isCompleted: false
							});
						}
						return;
					}

					// 处理截图消息
					if (dataObj.type === 'RETURN_PC_TASK_IMG' && dataObj.url) {
						// 将新的截图添加到数组开头
						this.screenshots.unshift(dataObj.url);
						return;
					}

					// 处理智能评分结果
					if (dataObj.type === 'RETURN_WKPF_RES') {
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

							// 折叠所有区域当智能评分完成时
							this.sectionExpanded.aiConfig = false;
							this.sectionExpanded.promptInput = false;
							this.sectionExpanded.taskStatus = false;

							// 智能评分完成时，再次保存历史记录
							this.saveHistory();
						}
						return;
					}

					// 处理智能排版结果
					if (dataObj.type === 'RETURN_ZNPB_RES') {
						console.log("收到智能排版结果", dataObj);
						console.log("当前 currentLayoutResult:", this.currentLayoutResult);
						
						const znpbAI = this.enabledAIs.find(ai => ai.name === '智能排版');
						if (znpbAI) {
							znpbAI.status = 'completed';
							if (znpbAI.progressLogs.length > 0) {
								znpbAI.progressLogs[0].isCompleted = true;
							}

							// 不添加到结果展示，直接调用推送方法
							this.handlePushToWechat(dataObj.draftContent);

							// 智能排版完成时，保存历史记录
							this.saveHistory();
						}
						return;
					}

					// 处理AI登录状态消息
					this.handleAiStatusMessage(datastr, dataObj);

					// 处理AI结果
					this.handleAIResult(dataObj);

				} catch (error) {
					console.error('WebSocket消息处理错误', error);
				}
			},

			handleAiStatusMessage(datastr, dataObj) {
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
				// 处理MiniMax Chat登录状态
				else if (datastr.includes("RETURN_MAX_STATUS") && dataObj.status != "") {
				  this.isLoading.mini = false;
				  if (!datastr.includes("false")) {
				    this.aiLoginStatus.mini = true;
				    this.accounts.mini = dataObj.status;
				  } else {
				    this.aiLoginStatus.mini = false;
				    // 禁用相关AI
				    this.disableAIsByLoginStatus("mini");
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
			},

			handleAIResult(dataObj) {
				let targetAI = null;

				// 根据消息类型匹配AI
				switch (dataObj.type) {
					case 'RETURN_DB_RES':
						console.log('收到消息:', dataObj);
						targetAI = this.enabledAIs.find(ai => ai.name === '豆包');
						break;
          case 'RETURN_DEEPSEEK_RES':
            console.log('收到DeepSeek消息:', dataObj);
            targetAI = this.enabledAIs.find(ai => ai.name === 'DeepSeek');
            // 如果找不到DeepSeek，可能是因为它不在enabledAIs中，尝试添加它
            if (!targetAI) {
              targetAI = {
                name: 'DeepSeek',
                avatar: 'https://communication.cn-nb1.rains3.com/Deepseek.png',
                capabilities: [{
                  label: '深度思考',
                  value: 'deep_thinking'
                },
                  {
                    label: '联网搜索',
                    value: 'web_search'
                  }],
                selectedCapabilities: ['deep_thinking', 'web_search'],
                enabled: true,
                status: 'running',
                progressLogs: [{
                  content: 'DeepSeek响应已接收',
                  timestamp: new Date(),
                  isCompleted: true
                }],
                isExpanded: true
              };
              this.enabledAIs.push(targetAI);
            }
            break;
			case "RETURN_MAX_RES":
			  console.log("收到消息:", dataObj);
			  targetAI = this.enabledAIs.find((ai) => ai.name === "MiniMax Chat");
			  break;
				}

				if (targetAI) {
					// 更新AI状态为已完成
					targetAI.status = 'completed';

					// 将最后一条进度消息标记为已完成
					if (targetAI.progressLogs.length > 0) {
						targetAI.progressLogs[0].isCompleted = true;
					}

					// 添加结果到数组开头
					const resultIndex = this.results.findIndex(r => r.aiName === targetAI.name);
					if (resultIndex === -1) {
						this.results.unshift({
							aiName: targetAI.name,
							content: dataObj.draftContent,
							shareUrl: dataObj.shareUrl || '',
							shareImgUrl: dataObj.shareImgUrl || '',
							timestamp: new Date()
						});
						this.activeResultIndex = 0;
					} else {
						this.results.splice(resultIndex, 1);
						this.results.unshift({
							aiName: targetAI.name,
							content: dataObj.draftContent,
							shareUrl: dataObj.shareUrl || '',
							shareImgUrl: dataObj.shareImgUrl || '',
							timestamp: new Date()
						});
						this.activeResultIndex = 0;
					}

					// 折叠所有区域当有结果返回时
					this.sectionExpanded.aiConfig = false;
					this.sectionExpanded.promptInput = false;
					this.sectionExpanded.taskStatus = false;

					// 滚动到结果区域
					this.scrollIntoView = 'results';

					// 保存历史记录
					this.saveHistory();
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
					this.userInfoReq.maxChatId = item.maxChatId || "";
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
					maxChatId: this.userInfoReq.maxChatId,
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
						maxChatId: this.userInfoReq.maxChatId,
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
			showScoreModal() {
				this.selectedResults = [];
				this.scoreModalVisible = true;
			},

			closeScoreModal() {
				this.scoreModalVisible = false;
			},

			// 智能排版相关方法
			showLayoutModal() {
				if (!this.currentResult) {
					uni.showToast({
						title: '没有可排版的内容',
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
				console.log("showLayoutModal", this.currentLayoutResult);


				// 设置默认提示词
				this.layoutPrompt = `请你对以下 HTML 内容进行排版优化，目标是用于微信公众号“草稿箱接口”的 content 字段，要求如下：

1. 仅返回 <body> 内部可用的 HTML 内容片段（不要包含 <!DOCTYPE>、<html>、<head>、<meta>、<title> 等标签）。
2. 所有样式必须以“内联 style”方式写入。
3. 保持结构清晰、视觉友好，适配公众号图文排版。
4. 请直接输出代码，不要添加任何注释或额外说明。
5. 不得使用 emoji 表情符号或小图标字符。
6. 不要显示为问答形式，以一篇文章的格式去调整 \n\n以下为需要进行排版优化的内容：\n`+ this.currentResult.content ;
				this.layoutModalVisible = true;
			},

			closeLayoutModal() {
				this.layoutModalVisible = false;
			},

			handleLayout() {
				if (this.layoutPrompt.trim().length === 0) return;

				// 构建智能排版请求
				const layoutRequest = {
					jsonrpc: '2.0',
					id: this.generateUUID(),
					method: 'AI排版',
					params: {
						taskId: this.generateUUID(),
						userId: this.userId,
						corpId: this.corpId,
						userPrompt: this.layoutPrompt,
						roles: 'zj-db-sdsk' // 默认使用豆包进行排版
					}
				};

				// 发送排版请求
				console.log("智能排版参数", layoutRequest);
				this.message(layoutRequest);
				this.closeLayoutModal();

				// 创建智能排版AI节点
				const znpbAI = {
					name: '智能排版',
					avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
					capabilities: [],
					selectedCapabilities: [],
					enabled: true,
					status: 'running',
					progressLogs: [
						{
							content: '智能排版任务已提交，正在排版...',
							timestamp: new Date(),
							isCompleted: false,
							type: '智能排版'
						}
					],
					isExpanded: true
				};

				// 检查是否已存在智能排版
				const existIndex = this.enabledAIs.findIndex(ai => ai.name === '智能排版');
				if (existIndex === -1) {
					// 如果不存在，添加到数组开头
					this.enabledAIs.unshift(znpbAI);
				} else {
					// 如果已存在，更新状态和日志
					this.enabledAIs[existIndex] = znpbAI;
					// 将智能排版移到数组开头
					const znpb = this.enabledAIs.splice(existIndex, 1)[0];
					this.enabledAIs.unshift(znpb);
				}

				uni.showToast({
					title: '排版请求已发送，请等待结果',
					icon: 'success'
				});
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
							title: `投递成功(${this.collectNum})`,
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

			handleScore() {
				if (!this.canScore) return;

				// 获取选中的结果内容并按照指定格式拼接
				const selectedContents = this.results
					.filter(result => this.selectedResults.includes(result.aiName))
					.map(result => {
						// 将HTML内容转换为纯文本（小程序版本简化处理）
						const plainContent = result.content.replace(/<[^>]*>/g, '');
						return `${result.aiName}初稿：\n${plainContent}\n`;
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
						roles: 'zj-db-sdsk' // 默认使用豆包进行评分
					}
				};

				// 发送评分请求
				console.log("参数", scoreRequest);
				this.message(scoreRequest);
				this.closeScoreModal();

				// 创建智能评分AI节点
				const wkpfAI = {
					name: '智能评分',
					avatar: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
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

				// 检查是否已存在智能评分
				const existIndex = this.enabledAIs.findIndex(ai => ai.name === '智能评分');
				if (existIndex === -1) {
					// 如果不存在，添加到数组开头
					this.enabledAIs.unshift(wkpfAI);
				} else {
					// 如果已存在，更新状态和日志
					this.enabledAIs[existIndex] = wkpfAI;
					// 将智能评分移到数组开头
					const wkpf = this.enabledAIs.splice(existIndex, 1)[0];
					this.enabledAIs.unshift(wkpf);
				}

				uni.showToast({
					title: '评分请求已发送，请等待结果',
					icon: 'success'
				});
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
					maxChatId: '',
					isNewChat: true
				};
				// 重置AI列表为初始状态
				this.aiList = [{
            name: 'DeepSeek',
            avatar: 'https://communication.cn-nb1.rains3.com/Deepseek.png',
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
            isExpanded: true
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
						isExpanded: true
					},
					{
					  name: "MiniMax Chat",
					  avatar:
					    "https://yzg.meooota.com/profile/MiniMax%20Chat.png",
					  capabilities: [
					    { label: "深度思考", value: "deep_thinking" },
					    { label: "联网搜索", value: "web_search" },
					  ],
					  selectedCapabilities: ["deep_thinking", "web_search"],
					  enabled: true,
					  status: "idle",
					  progressLogs: [],
					  isExpanded: true,
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
		// 检查MiniMax登录状态
		this.sendWebSocketMessage({
		  type: "PLAY_CHECK_MAX_LOGIN",
		  userId: this.userId,
		  corpId: this.corpId,
		});
			},

			getPlatformIcon(type) {
				const icons = {
					yuanbao: 'https://u3w.com/chatfile/yuanbao.png',
					doubao: 'https://u3w.com/chatfile/%E8%B1%86%E5%8C%85.png',
					agent: 'https://u3w.com/chatfile/yuanbao.png'
				};
				return icons[type] || '';
			},

			getPlatformName(type) {
				const names = {
					yuanbao: '腾讯元宝',
					doubao: '豆包',
					agent: '智能体'
				};
				return names[type] || '';
			},





			refreshAiStatus() {
				// 重置所有AI状态为加载中
				this.isLoading = {
					doubao: true,
          deepseek: true,
		  mini: true
				};

				// 重置登录状态
				this.aiLoginStatus = {
					doubao: false,
          deepseek: false,
		  mini: false
				};

				// 重置账户信息
				this.accounts = {
					doubao: '',
          deepseek: '',
		  mini: ''
				};

				// 显示刷新提示
				uni.showToast({
					title: '正在刷新连接状态...',
					icon: 'loading',
					duration: 1500
				});

				// 重新建立WebSocket连接
				this.closeWebSocket();
				setTimeout(() => {
					this.initWebSocket();
					// 延迟检查AI状态，确保WebSocket重新连接
					setTimeout(() => {
						this.sendAiStatusCheck();
					}, 2000);
				}, 500);
			},

			// 判断AI是否已登录可用
			isAiLoginEnabled(ai) {
				switch (ai.name) {
					case '豆包':
						return this.aiLoginStatus.doubao; // 豆包登录状态
          case 'DeepSeek':
            return this.aiLoginStatus.deepseek; // 使用实际的DeepSeek登录状态
			case "MiniMax Chat":
			  return this.aiLoginStatus.mini; // MiniMax Chat登录状态
					default:
						return false;
				}
			},

			// 判断AI是否在加载状态
			isAiInLoading(ai) {
				switch (ai.name) {
					case '豆包':
						return this.isLoading.doubao;
          case 'DeepSeek':
            return this.isLoading.deepseek; // 使用实际的DeepSeek加载状态
			case "MiniMax Chat":
			  return this.isLoading.mini;
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

			// 格式化时间
			formatTime(timestamp) {
				try {
					console.log('formatTime 输入:', timestamp, typeof timestamp);

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

					console.log('formatTime 解析结果:', date, date.getTime());

					if (isNaN(date.getTime())) {
						return '时间未知';
					}

					// 使用更简洁的时间格式，避免显示时区信息
					const hour = date.getHours().toString().padStart(2, '0');
					const minute = date.getMinutes().toString().padStart(2, '0');
					const second = date.getSeconds().toString().padStart(2, '0');

					const timeString = `${hour}:${minute}:${second}`;

					console.log('formatTime 输出:', timeString);
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
		padding-top: calc(10px + var(--status-bar-height));
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
		height: calc(100vh - 52px - var(--status-bar-height));
		padding-top: calc(52px + var(--status-bar-height));
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

	.score-textarea {
		width: 100%;
		height: 120px;
		padding: 10px;
		border: 1px solid #dcdfe6;
		border-radius: 8px;
		font-size: 14px;
		resize: none;
		box-sizing: border-box;
		margin-top: 10px;
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
</style>
