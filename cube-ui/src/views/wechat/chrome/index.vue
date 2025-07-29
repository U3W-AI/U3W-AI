<template>
  <div class="ai-management-platform">
    <!-- 顶部导航区 -->
    <div class="top-nav">
      <div class="logo-area">
        <img src="../../../assets/ai/logo.png" alt="Logo" class="logo" />
        <h1 class="platform-title">主机</h1>
      </div>
      <div class="nav-buttons">
        <el-button type="primary" size="small" @click="createNewChat">
          <i class="el-icon-plus"></i>
          创建新对话
        </el-button>
        <div class="history-button">
          <el-button type="text" @click="showHistoryDrawer">
            <img
              :src="require('../../../assets/ai/celan.png')"
              alt="历史记录"
              class="history-icon"
            />
          </el-button>
        </div>
      </div>
    </div>

    <!-- 历史记录抽屉 -->
    <el-drawer
      title="历史会话记录"
      :visible.sync="historyDrawerVisible"
      direction="rtl"
      size="30%"
      :before-close="handleHistoryDrawerClose"
    >
      <div class="history-content">
        <div
          v-for="(group, date) in groupedHistory"
          :key="date"
          class="history-group"
        >
          <div class="history-date">{{ date }}</div>
          <div class="history-list">
            <div
              v-for="(item, index) in group"
              :key="index"
              class="history-item"
            >
              <div class="history-parent" @click="loadHistoryItem(item)">
                <div class="history-header">
                  <i
                    :class="[
                      'el-icon-arrow-right',
                      { 'is-expanded': item.isExpanded },
                    ]"
                    @click.stop="toggleHistoryExpansion(item)"
                  ></i>
                  <div class="history-prompt">{{ item.userPrompt }}</div>
                </div>
                <div class="history-time">
                  {{ formatHistoryTime(item.createTime) }}
                </div>
              </div>
              <div
                v-if="
                  item.children && item.children.length > 0 && item.isExpanded
                "
                class="history-children"
              >
                <div
                  v-for="(child, childIndex) in item.children"
                  :key="childIndex"
                  class="history-child-item"
                  @click="loadHistoryItem(child)"
                >
                  <div class="history-prompt">{{ child.userPrompt }}</div>
                  <div class="history-time">
                    {{ formatHistoryTime(child.createTime) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <div class="main-content">
      <el-collapse v-model="activeCollapses">
        <el-collapse-item title="AI选择配置" name="ai-selection">
          <div class="ai-selection-section">
            <div class="ai-cards">
              <el-card
                v-for="(ai, index) in aiList"
                :key="index"
                class="ai-card"
                shadow="hover"
              >
                <div class="ai-card-header">
                  <div class="ai-left">
                    <div class="ai-avatar">
                      <img :src="ai.avatar" alt="AI头像" />
                    </div>
                    <div class="ai-name">{{ ai.name }}</div>
                  </div>
                  <div class="ai-status">
                    <el-switch
                      v-model="ai.enabled"
                      active-color="#13ce66"
                      inactive-color="#ff4949"
                    >
                    </el-switch>
                  </div>
                </div>
                <div class="ai-capabilities" v-if="ai.capabilities && ai.capabilities.length > 0">
                  <!-- 通义只支持单选-->
                  <div v-if="ai.name === '通义千问'" class="button-capability-group">
                    <el-button
                      v-for="capability in ai.capabilities"
                      :key="capability.value" size="mini"
                      :type="ai.selectedCapability === capability.value ? 'primary' : 'info'"
                      :disabled="!ai.enabled"
                      :plain="ai.selectedCapability !== capability.value"
                      @click="selectSingleCapability(ai, capability.value)"
                      class="capability-button"
                    >
                      {{ capability.label }}
                    </el-button>
                  </div>
                  <!-- 其他AI -->
                  <div v-else class="button-capability-group">
                    <el-button
                      v-for="capability in ai.capabilities"
                      :key="capability.value"
                      size="mini"
                      :type="ai.selectedCapabilities.includes(capability.value) ? 'primary' : 'info'"
                      :disabled="!ai.enabled"
                      :plain="!ai.selectedCapabilities.includes(capability.value)"
                      @click="toggleCapability(ai, capability.value)"
                      class="capability-button"
                    >
                      {{ capability.label }}
                    </el-button>
                  </div>
                </div>
              </el-card>
            </div>
          </div>
        </el-collapse-item>

        <!-- 提示词输入区 -->
        <el-collapse-item title="提示词输入" name="prompt-input">
          <div class="prompt-input-section">
            <el-input
              type="textarea"
              :rows="5"
              placeholder="请输入提示词，支持Markdown格式"
              v-model="promptInput"
              resize="none"
              class="prompt-input"
            >
            </el-input>
            <div class="prompt-footer">
              <div class="word-count">字数统计: {{ promptInput.length }}</div>
              <el-button
                type="primary"
                @click="sendPrompt"
                :disabled="!canSend"
                class="send-button"
              >
                发送
              </el-button>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <!-- 执行状态展示区 -->
      <div class="execution-status-section" v-if="taskStarted">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card class="task-flow-card">
              <div slot="header" class="card-header">
                <span>任务流程</span>
              </div>
              <div class="task-flow">
                <div
                  v-for="(ai, index) in enabledAIs"
                  :key="index"
                  class="task-item"
                >
                  <div class="task-header" @click="toggleAIExpansion(ai)">
                    <div class="header-left">
                      <i
                        :class="[
                          'el-icon-arrow-right',
                          { 'is-expanded': ai.isExpanded },
                        ]"
                      ></i>
                      <span class="ai-name">{{ ai.name }}</span>
                    </div>
                    <div class="header-right">
                      <span class="status-text">{{
                        getStatusText(ai.status)
                      }}</span>
                      <i
                        :class="getStatusIcon(ai.status)"
                        class="status-icon"
                      ></i>
                    </div>
                  </div>
                  <!-- 添加进度轨迹 -->
                  <div
                    class="progress-timeline"
                    v-if="ai.progressLogs.length > 0 && ai.isExpanded"
                  >
                    <div class="timeline-scroll">
                      <div
                        v-for="(log, logIndex) in ai.progressLogs"
                        :key="logIndex"
                        class="progress-item"
                        :class="{
                          completed: log.isCompleted || logIndex > 0,
                          current: !log.isCompleted && logIndex === 0,
                        }"
                      >
                        <div class="progress-dot"></div>
                        <div
                          class="progress-line"
                          v-if="logIndex < ai.progressLogs.length - 1"
                        ></div>
                        <div class="progress-content">
                          <div class="progress-time">
                            {{ formatTime(log.timestamp) }}
                          </div>
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
              <div slot="header" class="card-header">
                <span>主机可视化</span>
                <div class="controls">
                  <el-switch
                    v-model="autoPlay"
                    active-text="自动轮播"
                    inactive-text="手动切换"
                  >
                  </el-switch>
                </div>
              </div>
              <div class="screenshots">
                <el-carousel
                  :interval="3000"
                  :autoplay="false"
                  indicator-position="outside"
                  height="700px"
                >
                  <el-carousel-item
                    v-for="(screenshot, index) in screenshots"
                    :key="index"
                  >
                    <img
                      :src="screenshot"
                      alt="执行截图"
                      class="screenshot-image"
                      @click="showLargeImage(screenshot)"
                    />
                  </el-carousel-item>
                </el-carousel>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 结果展示区 -->
      <div class="results-section" v-if="results.length > 0">
        <div class="section-header">
          <h2 class="section-title">执行结果</h2>
          <el-button type="primary" @click="showScoreDialog" size="small">
            智能评分
          </el-button>
        </div>
        <el-tabs v-model="activeResultTab" type="card">
          <el-tab-pane
            v-for="(result, index) in results"
            :key="index"
            :label="result.aiName"
            :name="'result-' + index"
          >
            <div class="result-content">
              <div class="result-header" v-if="result.shareUrl">
                <div class="result-title">{{ result.aiName }}的执行结果</div>
                <div class="result-buttons">
                  <el-button
                    size="mini"
                    type="primary"
                    icon="el-icon-link"
                    @click="openShareUrl(result.shareUrl)"
                    class="share-link-btn"
                  >
                    查看原链接
                  </el-button>
                  <el-button
                    size="mini"
                    type="success"
                    icon="el-icon-s-promotion"
                    @click="handlePushToMedia(result)"
                    class="push-media-btn"
                    :loading="pushingToMedia"
                    :disabled="pushingToMedia"
                  >
                    投递到媒体
                  </el-button>
                </div>
              </div>
              <!-- 如果有shareImgUrl则渲染图片或PDF，否则渲染markdown -->
              <div v-if="result.shareImgUrl" class="share-content">
                <!-- 渲染图片 -->
                <img
                  v-if="isImageFile(result.shareImgUrl)"
                  :src="result.shareImgUrl"
                  alt="分享图片"
                  class="share-image"
                  :style="getImageStyle(result.aiName)"
                />
                <!-- 渲染PDF -->
                <iframe
                  v-else-if="isPdfFile(result.shareImgUrl)"
                  :src="result.shareImgUrl"
                  class="share-pdf"
                  frameborder="0"
                >
                </iframe>
                <!-- 其他文件类型显示链接 -->
                <div v-else class="share-file">
                  <el-button
                    type="primary"
                    icon="el-icon-document"
                    @click="openShareUrl(result.shareImgUrl)"
                  >
                    查看文件
                  </el-button>
                </div>
              </div>
              <div
                v-else
                class="markdown-content"
                v-html="renderMarkdown(result.content)"
              ></div>
              <div class="action-buttons">
                <el-button
                  size="small"
                  type="primary"
                  @click="copyResult(result.content)"
                  >复制（纯文本）</el-button
                >
                <el-button
                  size="small"
                  type="success"
                  @click="exportResult(result)"
                  >导出（MD文件）</el-button
                >
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 大图查看对话框 -->
    <el-dialog
      :visible.sync="showImageDialog"
      width="90%"
      :show-close="true"
      :modal="true"
      center
      class="image-dialog"
      :append-to-body="true"
      @close="closeLargeImage"
    >
      <div class="large-image-container">
        <!-- 如果是单张分享图片，直接显示 -->
        <div
          v-if="currentLargeImage && !screenshots.includes(currentLargeImage)"
          class="single-image-container"
        >
          <img :src="currentLargeImage" alt="大图" class="large-image" />
        </div>
        <!-- 如果是截图轮播 -->
        <el-carousel
          v-else
          :interval="3000"
          :autoplay="false"
          indicator-position="outside"
          height="80vh"
        >
          <el-carousel-item
            v-for="(screenshot, index) in screenshots"
            :key="index"
          >
            <img :src="screenshot" alt="大图" class="large-image" />
          </el-carousel-item>
        </el-carousel>
      </div>
    </el-dialog>

    <!-- 评分弹窗 -->
    <el-dialog
      title="智能评分"
      :visible.sync="scoreDialogVisible"
      width="60%"
      height="65%"
      :close-on-click-modal="false"
      class="score-dialog"
    >
      <div class="score-dialog-content">
        <div class="score-prompt-section">
          <h3>评分提示词：</h3>
          <el-input
            type="textarea"
            :rows="10"
            placeholder="请输入评分提示词，例如：请从内容质量、逻辑性、创新性等方面进行评分"
            v-model="scorePrompt"
            resize="none"
            class="score-prompt-input"
          >
          </el-input>
        </div>
        <div class="selected-results">
          <h3>选择要评分的内容：</h3>
          <el-checkbox-group v-model="selectedResults">
            <el-checkbox
              v-for="(result, index) in results"
              :key="index"
              :label="result.aiName"
              class="result-checkbox"
            >
              {{ result.aiName }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="scoreDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleScore" :disabled="!canScore">
          开始评分
        </el-button>
      </span>
    </el-dialog>

    <!-- 投递到媒体弹窗 -->
    <el-dialog
      title="媒体投递设置"
      :visible.sync="layoutDialogVisible"
      width="60%"
      height="65%"
      :close-on-click-modal="false"
      class="layout-dialog"
    >
      <div class="layout-dialog-content">
        <!-- 媒体选择区域 -->
        <div class="media-selection-section">
          <h3>选择投递媒体：</h3>
          <el-radio-group v-model="selectedMedia" size="small" class="media-radio-group">
            <el-radio-button label="wechat">
              <i class="el-icon-chat-dot-square"></i>
              公众号
            </el-radio-button>
            <el-radio-button label="zhihu">
              <i class="el-icon-document"></i>
              知乎
            </el-radio-button>
            <el-radio-button label="toutiao">
              <i class="el-icon-edit-outline"></i>
              微头条
            </el-radio-button>
          </el-radio-group>
          <div class="media-description">
            <template v-if="selectedMedia === 'wechat'">
              <small>📝 将内容排版为适合微信公众号的HTML格式，并自动投递到草稿箱</small>
            </template>
            <template v-else-if="selectedMedia === 'zhihu'">
              <small>📖 将内容转换为知乎专业文章格式，直接投递到知乎草稿箱</small>
            </template>
            <template v-else-if="selectedMedia === 'toutiao'">
              <small>📰 将内容转换为微头条文章格式，支持文章编辑和发布</small>
            </template>
          </div>
        </div>


        <div class="layout-prompt-section">
          <h3>排版提示词：</h3>
          <el-input
            type="textarea"
            :rows="12"
            placeholder="请输入排版提示词"
            v-model="layoutPrompt"
            resize="none"
            class="layout-prompt-input"
          >
          </el-input>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="layoutDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleLayout" :disabled="!canLayout">
          排版后智能投递
        </el-button>
      </span>
    </el-dialog>

    <!-- 微头条发布流程弹窗 -->
    <el-dialog title="微头条发布流程" :visible.sync="tthFlowVisible" width="60%" height="60%" :close-on-click-modal="false"
      class="tth-flow-dialog">
      <div class="tth-flow-content">
        <div class="flow-logs-section">
          <h3>发布流程日志：</h3>
          <div class="progress-timeline">
            <div class="timeline-scroll">
              <div v-for="(log, index) in tthFlowLogs" :key="index" class="progress-item completed">
                <div class="progress-dot"></div>
                <div v-if="index < tthFlowLogs.length - 1" class="progress-line"></div>
                <div class="progress-content">
                  <div class="progress-time">{{ formatTime(log.timestamp) }}</div>
                  <div class="progress-text">{{ log.content }}</div>
                </div>
              </div>
              <div v-if="tthFlowLogs.length === 0" class="no-logs">暂无流程日志...</div>
            </div>
          </div>
        </div>
        <div class="flow-images-section">
          <h3>发布流程图片：</h3>
          <div class="flow-images-container">
            <template v-if="tthFlowImages.length > 0">
              <div v-for="(image, index) in tthFlowImages" :key="index" class="flow-image-item">
                <img :src="image" alt="流程图片" class="flow-image" @click="showLargeImage(image)">
              </div>
            </template>
            <div v-else class="no-logs">暂无流程图片...</div>
          </div>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="closeTTHFlowDialog">关闭</el-button>
      </span>
    </el-dialog>

    <!-- 微头条文章编辑弹窗 -->
    <el-dialog title="微头条文章编辑" :visible.sync="tthArticleEditVisible" width="70%" height="80%" :close-on-click-modal="false"
      class="tth-article-edit-dialog">
      <div class="tth-article-edit-content">
        <div class="article-title-section">
          <h3>文章标题：</h3>
          <el-input v-model="tthArticleTitle" placeholder="请输入文章标题" class="article-title-input"></el-input>
        </div>
        <div class="article-content-section">
          <h3>文章内容：</h3>
          <el-input type="textarea" v-model="tthArticleContent" :rows="20" placeholder="请输入文章内容"
            resize="none" class="article-content-input"></el-input>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="tthArticleEditVisible = false">关 闭</el-button>
        <el-button type="primary" @click="confirmTTHPublish" :disabled="!tthArticleTitle || !tthArticleContent">
          确定发布
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { marked } from "marked";
import {
  message,
  saveUserChatData,
  getChatHistory,
  pushAutoOffice,
  getMediaCallWord,
} from "@/api/wechat/aigc";
import { v4 as uuidv4 } from "uuid";
import websocketClient from "@/utils/websocket";
import store from "@/store";
import TurndownService from "turndown";

export default {
  name: "AIManagementPlatform",
  data() {
    return {
      userId: store.state.user.id,
      corpId: store.state.user.corp_id,
      chatId: uuidv4(),
      expandedHistoryItems: {},
      userInfoReq: {
        userPrompt: "",
        userId: "",
        corpId: "",
        taskId: "",
        roles: "",
        toneChatId: "",
        ybDsChatId: "",
        dbChatId: "",
        tyChatId: "",
        isNewChat: true,
      },
      jsonRpcReqest: {
        jsonrpc: "2.0",
        id: uuidv4(),
        method: "",
        params: {},
      },
      aiList: [
        {
          name: "DeepSeek",
          avatar: require("../../../assets/logo/Deepseek.png"),
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
        {
          name: "豆包",
          avatar: require("../../../assets/ai/豆包.png"),
          capabilities: [{ label: "深度思考", value: "deep_thinking" }],
          selectedCapabilities: ["deep_thinking"],
          enabled: true,
          status: "idle",
          progressLogs: [],
          isExpanded: true,
        },
        {
          name: "MiniMax Chat",
          avatar: require("../../../assets/ai/MiniMax.png"),
          capabilities: [
            { label: "深度思考", value: "deep_thinking" },
            { label: "联网搜索", value: "web_search" },
          ],
          selectedCapabilities: [],
          enabled: true,
          status: "idle",
          progressLogs: [],
          isExpanded: true,
        },
        {
          name: '通义千问',
          avatar: require('../../../assets/ai/qw.png'),
          capabilities: [
            { label: '深度思考', value: 'deep_thinking' },
            { label: '联网搜索', value: 'web_search' }
          ],
          selectedCapability: '',
          enabled: true,
          status: 'idle',
          progressLogs: [],
          isExpanded: true
        }
      ],
      promptInput: "",
      taskStarted: false,
      autoPlay: false,
      screenshots: [],
      results: [],
      activeResultTab: "result-0",
      activeCollapses: ["ai-selection", "prompt-input"], // 默认展开这两个区域
      showImageDialog: false,
      currentLargeImage: "",
      enabledAIs: [],
      turndownService: new TurndownService({
        headingStyle: "atx",
        codeBlockStyle: "fenced",
        emDelimiter: "*",
      }),
      scoreDialogVisible: false,
      selectedResults: [],
      scorePrompt: `请你深度阅读以下几篇内容，从多个维度进行逐项打分，输出评分结果。并在以下各篇文章的基础上博采众长，综合整理一篇更全面的文章。`,
      layoutDialogVisible: false,
      layoutPrompt: "",
      currentLayoutResult: null, // 当前要排版的结果
      historyDrawerVisible: false,
      chatHistory: [],
      pushOfficeNum: 0, // 投递到公众号的递增编号
      pushingToWechat: false, // 投递到公众号的loading状态
      selectedMedia: "wechat", // 默认选择公众号
      pushingToMedia: false, // 投递到媒体的loading状态
      // 微头条相关变量
      tthFlowVisible: false, // 微头条发布流程弹窗
      tthFlowLogs: [], // 微头条发布流程日志
      tthFlowImages: [], // 微头条发布流程图片
      tthArticleEditVisible: false, // 微头条文章编辑弹窗
      tthArticleTitle: '', // 微头条文章标题
      tthArticleContent: '', // 微头条文章内容
    };
  },
  computed: {
    canSend() {
      return (
        this.promptInput.trim().length > 0 &&
        this.aiList.some((ai) => ai.enabled)
      );
    },
    canScore() {
      return (
        this.selectedResults.length > 0 && this.scorePrompt.trim().length > 0
      );
    },
    canLayout() {
      return this.layoutPrompt.trim().length > 0;
    },
    groupedHistory() {
      const groups = {};
      const chatGroups = {};

      // 首先按chatId分组
      this.chatHistory.forEach((item) => {
        if (!chatGroups[item.chatId]) {
          chatGroups[item.chatId] = [];
        }
        chatGroups[item.chatId].push(item);
      });

      // 然后按日期分组，并处理父子关系
      Object.values(chatGroups).forEach((chatGroup) => {
        // 按时间排序
        chatGroup.sort(
          (a, b) => new Date(a.createTime) - new Date(b.createTime)
        );

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
          children: chatGroup.slice(1).map((child) => ({
            ...child,
            isParent: false,
          })),
        });
      });

      return groups;
    },
  },
  created() {
    console.log(this.userId);
    console.log(this.corpId);
    this.initWebSocket(this.userId);
    this.loadChatHistory(0); // 加载历史记录
    this.loadLastChat(); // 加载上次会话
  },
  watch: {
    // 监听媒体选择变化，自动加载对应的提示词
    selectedMedia: {
      handler(newMedia) {
        this.loadMediaPrompt(newMedia);
      },
      immediate: false
    }
  },
  methods: {
    sendPrompt() {
      if (!this.canSend) return;

      this.screenshots = [];
      // 折叠所有区域
      this.activeCollapses = [];

      this.taskStarted = true;
      this.results = []; // 清空之前的结果

      this.userInfoReq.roles = "";

      this.userInfoReq.taskId = uuidv4();
      this.userInfoReq.userId = this.userId;
      this.userInfoReq.corpId = this.corpId;
      this.userInfoReq.userPrompt = this.promptInput;

      // 获取启用的AI列表及其状态
      this.enabledAIs = this.aiList.filter((ai) => ai.enabled);

      // 将所有启用的AI状态设置为运行中
      this.enabledAIs.forEach((ai) => {
        this.$set(ai, "status", "running");
      });

      this.enabledAIs.forEach((ai) => {
        if (ai.name === "DeepSeek" && ai.enabled) {
          this.userInfoReq.roles = this.userInfoReq.roles + "deepseek,";
          if (ai.selectedCapabilities.includes("deep_thinking")) {
            this.userInfoReq.roles = this.userInfoReq.roles + "ds-sdsk,";
          }
          if (ai.selectedCapabilities.includes("web_search")) {
            this.userInfoReq.roles = this.userInfoReq.roles + "ds-lwss,";
          }
        }
        if (ai.name === "豆包") {
          this.userInfoReq.roles = this.userInfoReq.roles + "zj-db,";
          if (ai.selectedCapabilities.includes("deep_thinking")) {
            this.userInfoReq.roles = this.userInfoReq.roles + "zj-db-sdsk,";
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
        if(ai.name === '通义千问' && ai.enabled){
          this.userInfoReq.roles = this.userInfoReq.roles + 'ty-qw,';
          if (ai.selectedCapability.includes("deep_thinking")) {
            this.userInfoReq.roles = this.userInfoReq.roles + 'ty-qw-sdsk,'
          } else if (ai.selectedCapability.includes("web_search")) {
            this.userInfoReq.roles = this.userInfoReq.roles + 'ty-qw-lwss,';
          }
        }
      });

      console.log("参数：", this.userInfoReq);

      //调用后端接口
      this.jsonRpcReqest.method = "使用F8S";
      this.jsonRpcReqest.params = this.userInfoReq;
      this.message(this.jsonRpcReqest);
      this.userInfoReq.isNewChat = false;
    },

    message(data) {
      message(data).then((res) => {
        if (res.code == 201) {
          this.$message.error(res.messages || '操作失败');
        }
      });
    },
    // 处理通义单选逻辑
    selectSingleCapability(ai, capabilityValue) {
      if (!ai.enabled) return;

      if (ai.selectedCapability === capabilityValue) {
        this.$set(ai, 'selectedCapability', '');
      } else {
        this.$set(ai, 'selectedCapability', capabilityValue);
      }
      this.$forceUpdate();
    },
    toggleCapability(ai, capabilityValue) {
      if (!ai.enabled) return;

      const index = ai.selectedCapabilities.indexOf(capabilityValue);
      console.log("切换前:", ai.selectedCapabilities);
      if (index === -1) {
        // 如果不存在，则添加
        this.$set(
          ai.selectedCapabilities,
          ai.selectedCapabilities.length,
          capabilityValue
        );
      } else {
        // 如果已存在，则移除
        const newCapabilities = [...ai.selectedCapabilities];
        newCapabilities.splice(index, 1);
        this.$set(ai, "selectedCapabilities", newCapabilities);
      }
      console.log("切换后:", ai.selectedCapabilities);
      this.$forceUpdate(); // 强制更新视图
    },
    getStatusText(status) {
      switch (status) {
        case "idle":
          return "等待中";
        case "running":
          return "正在执行";
        case "completed":
          return "已完成";
        case "failed":
          return "执行失败";
        default:
          return "未知状态";
      }
    },
    getStatusIcon(status) {
      switch (status) {
        case "idle":
          return "el-icon-time";
        case "running":
          return "el-icon-loading";
        case "completed":
          return "el-icon-check success-icon";
        case "failed":
          return "el-icon-close error-icon";
        default:
          return "el-icon-question";
      }
    },
    renderMarkdown(text) {
      return marked(text);
    },
    // HTML转纯文本
    htmlToText(html) {
      const tempDiv = document.createElement("div");
      tempDiv.innerHTML = html;
      return tempDiv.textContent || tempDiv.innerText || "";
    },

    // HTML转Markdown
    htmlToMarkdown(html) {
      return this.turndownService.turndown(html);
    },

    copyResult(content) {
      // 将HTML转换为纯文本
      const plainText = this.htmlToText(content);
      const textarea = document.createElement("textarea");
      textarea.value = plainText;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
      this.$message.success("已复制纯文本到剪贴板");
    },

    exportResult(result) {
      // 将HTML转换为Markdown
      const markdown = result.content;
      const blob = new Blob([markdown], { type: "text/markdown" });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `${result.aiName}_结果_${new Date()
        .toISOString()
        .slice(0, 10)}.md`;
      link.click();
      URL.revokeObjectURL(link.href);
      this.$message.success("已导出Markdown文件");
    },

    openShareUrl(shareUrl) {
      if (shareUrl) {
        window.open(shareUrl, "_blank");
      } else {
        this.$message.warning("暂无原链接");
      }
    },
    showLargeImage(imageUrl) {
      this.currentLargeImage = imageUrl;
      this.showImageDialog = true;
      // 找到当前图片的索引，设置轮播图的初始位置
      const currentIndex = this.screenshots.indexOf(imageUrl);
      if (currentIndex !== -1) {
        this.$nextTick(() => {
          const carousel = this.$el.querySelector(".image-dialog .el-carousel");
          if (carousel && carousel.__vue__) {
            carousel.__vue__.setActiveItem(currentIndex);
          }
        });
      }
    },
    closeLargeImage() {
      this.showImageDialog = false;
      this.currentLargeImage = "";
    },
    // WebSocket 相关方法
    initWebSocket(id) {
      const wsUrl = process.env.VUE_APP_WS_API + `mypc-${id}`;
      console.log("WebSocket URL:", process.env.VUE_APP_WS_API);
      websocketClient.connect(wsUrl, (event) => {
        switch (event.type) {
          case "open":
            // this.$message.success('');
            break;
          case "message":
            this.handleWebSocketMessage(event.data);
            break;
          case "close":
            this.$message.warning("WebSocket连接已关闭");
            break;
          case "error":
            this.$message.error("WebSocket连接错误");
            break;
          case "reconnect_failed":
            this.$message.error("WebSocket重连失败，请刷新页面重试");
            break;
        }
      });
    },

    handleWebSocketMessage(data) {
      const datastr = data;
      const dataObj = JSON.parse(datastr);

      // 处理chatId消息
      if (dataObj.type === "RETURN_YBT1_CHATID" && dataObj.chatId) {
        this.userInfoReq.toneChatId = dataObj.chatId;
      } else if (dataObj.type === "RETURN_YBDS_CHATID" && dataObj.chatId) {
        this.userInfoReq.ybDsChatId = dataObj.chatId;
      } else if (dataObj.type === "RETURN_DB_CHATID" && dataObj.chatId) {
        this.userInfoReq.dbChatId = dataObj.chatId;
      } else if (dataObj.type === 'RETURN_TY_CHATID' && dataObj.chatId) {
        this.userInfoReq.tyChatId = dataObj.chatId;
      } else if (dataObj.type === "RETURN_MAX_CHATID" && dataObj.chatId) {
        this.userInfoReq.maxChatId = dataObj.chatId;
      }

      // 处理进度日志消息
      if (dataObj.type === "RETURN_PC_TASK_LOG" && dataObj.aiName) {
        const targetAI = this.enabledAIs.find(
          (ai) => ai.name === dataObj.aiName
        );
        if (targetAI) {
          // 检查是否已存在相同内容的日志，避免重复添加
          const existingLog = targetAI.progressLogs.find(log => log.content === dataObj.content);
          if (!existingLog) {
            // 将新进度添加到数组开头
            targetAI.progressLogs.unshift({
              content: dataObj.content,
              timestamp: new Date(),
              isCompleted: false,
            });
          }
        }
        return;
      }
      // 处理知乎投递任务日志
      if (dataObj.type === "RETURN_MEDIA_TASK_LOG" && dataObj.aiName === "投递到知乎") {
        const zhihuAI = this.enabledAIs.find((ai) => ai.name === "投递到知乎");
        if (zhihuAI) {
          // 检查是否已存在相同内容的日志，避免重复添加
          const existingLog = zhihuAI.progressLogs.find(log => log.content === dataObj.content);
          if (!existingLog) {
            // 将新进度添加到数组开头
            zhihuAI.progressLogs.unshift({
              content: dataObj.content,
              timestamp: new Date(),
              isCompleted: false,
            });
          }
        }
        return;
      }
      // 处理截图消息
      if (dataObj.type === "RETURN_PC_TASK_IMG" && dataObj.url) {
        // 将新的截图添加到数组开头
        this.screenshots.unshift(dataObj.url);
        return;
      }

      // 处理智能评分结果
      if (dataObj.type === "RETURN_WKPF_RES") {
        const wkpfAI = this.enabledAIs.find((ai) => ai.name === "智能评分");
        if (wkpfAI) {
          this.$set(wkpfAI, "status", "completed");
          if (wkpfAI.progressLogs.length > 0) {
            this.$set(wkpfAI.progressLogs[0], "isCompleted", true);
          }
          // 添加评分结果到results最前面
          this.results.unshift({
            aiName: "智能评分",
            content: dataObj.draftContent,
            shareUrl: dataObj.shareUrl || "",
            shareImgUrl: dataObj.shareImgUrl || "",
            timestamp: new Date(),
          });
          this.activeResultTab = "result-0";

          // 智能评分完成时，再次保存历史记录
          this.saveHistory();
        }
        return;
      }

      // 处理智能排版结果
      if (dataObj.type === "RETURN_ZNPB_RES") {
        const znpbAI = this.enabledAIs.find((ai) => ai.name === "智能排版");
        if (znpbAI) {
          this.$set(znpbAI, "status", "completed");
          if (znpbAI.progressLogs.length > 0) {
            this.$set(znpbAI.progressLogs[0], "isCompleted", true);
          }

          // 直接调用投递到公众号的方法，不添加到结果展示
          this.pushToWechatWithContent(dataObj.draftContent);

          // 智能排版完成时，保存历史记录
          this.saveHistory();
        }
        return;
      }
      // 处理知乎投递结果（独立任务）
      if (dataObj.type === "RETURN_ZHIHU_DELIVERY_RES") {
        const zhihuAI = this.enabledAIs.find((ai) => ai.name === "投递到知乎");
        if (zhihuAI) {
          this.$set(zhihuAI, "status", "completed");
          if (zhihuAI.progressLogs.length > 0) {
            this.$set(zhihuAI.progressLogs[0], "isCompleted", true);
          }

          // 添加完成日志
          zhihuAI.progressLogs.unshift({
            content: "知乎投递完成！" + (dataObj.message || ""),
            timestamp: new Date(),
            isCompleted: true,
          });

          // 知乎投递完成时，保存历史记录
          this.saveHistory();
          this.$message.success("知乎投递任务完成！");
        }
        return;
      }

      // 处理微头条排版结果
      if (dataObj.type === 'RETURN_TTH_ZNPB_RES') {
        // 微头条排版AI节点状态设为已完成
        const tthpbAI = this.enabledAIs.find(ai => ai.name === '微头条排版');
        if (tthpbAI) {
          this.$set(tthpbAI, 'status', 'completed');
          if (tthpbAI.progressLogs.length > 0) {
            this.$set(tthpbAI.progressLogs[0], 'isCompleted', true);
          }
        }
        this.tthArticleTitle = dataObj.title || '';
        this.tthArticleContent = dataObj.content || '';
        this.tthArticleEditVisible = true;
        this.saveHistory();
        return;
      }

      // 处理微头条发布流程
      if (dataObj.type === 'RETURN_TTH_FLOW') {
        // 添加流程日志
        if (dataObj.content) {
          this.tthFlowLogs.push({
            content: dataObj.content,
            timestamp: new Date(),
            type: 'flow',
          });
        }
        // 处理图片信息
        if (dataObj.shareImgUrl) {
          this.tthFlowImages.push(dataObj.shareImgUrl);
        }
        // 确保流程弹窗显示
        if (!this.tthFlowVisible) {
          this.tthFlowVisible = true;
        }
        // 检查发布结果
        if (dataObj.content === 'success') {
          this.$message.success('发布到微头条成功！');
          this.tthFlowVisible = true;
        } else if (dataObj.content === 'false' || dataObj.content === false) {
          this.$message.error('发布到微头条失败！');
          this.tthFlowVisible = false;
          this.tthArticleEditVisible = true;
        }
        return;
      }

      // 兼容后端发送的RETURN_PC_TTH_IMG类型图片消息
      if (dataObj.type === 'RETURN_PC_TTH_IMG' && dataObj.url) {
        this.tthFlowImages.push(dataObj.url);
        if (!this.tthFlowVisible) {
          this.tthFlowVisible = true;
        }
        return;
      }

      // 根据消息类型更新对应AI的状态和结果
      let targetAI = null;
      switch (dataObj.type) {
        case "RETURN_YBT1_RES":
        case "RETURN_TURBOS_RES":
        case "RETURN_TURBOS_LARGE_RES":
        case "RETURN_DEEPSEEK_RES":
          console.log("收到DeepSeek消息:", dataObj);
          targetAI = this.enabledAIs.find((ai) => ai.name === "DeepSeek");
          break;
        case "RETURN_YBDS_RES":
        case "RETURN_DB_RES":
          console.log("收到豆包消息:", dataObj);
          targetAI = this.enabledAIs.find((ai) => ai.name === "豆包");
          break;
        case "RETURN_MAX_RES":
          console.log("收到MiniMax消息:", dataObj);
          targetAI = this.enabledAIs.find((ai) => ai.name === "MiniMax Chat");
          break;
        case 'RETURN_TY_RES':
          console.log('收到通义千问消息:', data);
          targetAI = this.enabledAIs.find(ai => ai.name === '通义千问');
          break;
      }

      if (targetAI) {
        // 更新AI状态为已完成
        this.$set(targetAI, "status", "completed");

        // 将最后一条进度消息标记为已完成
        if (targetAI.progressLogs.length > 0) {
          this.$set(targetAI.progressLogs[0], "isCompleted", true);
        }

        // 添加结果到数组开头
        const resultIndex = this.results.findIndex(
          (r) => r.aiName === targetAI.name
        );
        if (resultIndex === -1) {
          this.results.unshift({
            aiName: targetAI.name,
            content: dataObj.draftContent,
            shareUrl: dataObj.shareUrl || "",
            shareImgUrl: dataObj.shareImgUrl || "",
            timestamp: new Date(),
          });
          this.activeResultTab = "result-0";
        } else {
          this.results.splice(resultIndex, 1);
          this.results.unshift({
            aiName: targetAI.name,
            content: dataObj.draftContent,
            shareUrl: dataObj.shareUrl || "",
            shareImgUrl: dataObj.shareImgUrl || "",
            timestamp: new Date(),
          });
          this.activeResultTab = "result-0";
        }
        this.saveHistory();
      }


    },

    closeWebSocket() {
      websocketClient.close();
    },

    sendMessage(data) {
      if (websocketClient.send(data)) {
        // 滚动到底部
        this.$nextTick(() => {
          this.scrollToBottom();
        });
      } else {
        this.$message.error("WebSocket未连接");
      }
    },
    toggleAIExpansion(ai) {
      this.$set(ai, "isExpanded", !ai.isExpanded);
    },

    formatTime(timestamp) {
      const date = new Date(timestamp);
      return date.toLocaleTimeString("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      });
    },
    showScoreDialog() {
      this.scoreDialogVisible = true;
      this.selectedResults = [];
    },

    handleScore() {
      if (!this.canScore) return;

      // 获取选中的结果内容并按照指定格式拼接
      const selectedContents = this.results
        .filter((result) => this.selectedResults.includes(result.aiName))
        .map((result) => {
          // 将HTML内容转换为纯文本
          const plainContent = this.htmlToText(result.content);
          return `${result.aiName}初稿：\n${plainContent}\n`;
        })
        .join("\n");

      // 构建完整的评分提示内容
      const fullPrompt = `${this.scorePrompt}\n${selectedContents}`;

      // 构建评分请求
      const scoreRequest = {
        jsonrpc: "2.0",
        id: uuidv4(),
        method: "AI评分",
        params: {
          taskId: uuidv4(),
          userId: this.userId,
          corpId: this.corpId,
          userPrompt: fullPrompt,
          roles: "zj-db-sdsk", // 默认使用豆包进行评分
        },
      };

      // 发送评分请求
      console.log("参数", scoreRequest);
      this.message(scoreRequest);
      this.scoreDialogVisible = false;

      // 创建智能评分AI节点
      const wkpfAI = {
        name: "智能评分",
        avatar: require("../../../assets/ai/yuanbao.png"),
        capabilities: [],
        selectedCapabilities: [],
        enabled: true,
        status: "running",
        progressLogs: [
          {
            content: "智能评分任务已提交，正在评分...",
            timestamp: new Date(),
            isCompleted: false,
            type: "智能评分",
          },
        ],
        isExpanded: true,
      };

      // 检查是否已存在智能评分
      const existIndex = this.enabledAIs.findIndex(
        (ai) => ai.name === "智能评分"
      );
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

      this.$forceUpdate();
      this.$message.success("评分请求已发送，请等待结果");
    },
    // 显示历史记录抽屉
    showHistoryDrawer() {
      this.historyDrawerVisible = true;
      this.loadChatHistory(1);
    },

    // 关闭历史记录抽屉
    handleHistoryDrawerClose() {
      this.historyDrawerVisible = false;
    },

    // 加载历史记录
    async loadChatHistory(isAll) {
      try {
        const res = await getChatHistory(this.userId, isAll);
        if (res.code === 200) {
          this.chatHistory = res.data || [];
        }
      } catch (error) {
        console.error("加载历史记录失败:", error);
        this.$message.error("加载历史记录失败");
      }
    },

    // 格式化历史记录时间
    formatHistoryTime(timestamp) {
      const date = new Date(timestamp);
      return date.toLocaleTimeString("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      });
    },

    // 获取历史记录日期分组
    getHistoryDate(timestamp) {
      const date = new Date(timestamp);
      const today = new Date();
      const yesterday = new Date(today);
      yesterday.setDate(yesterday.getDate() - 1);
      const twoDaysAgo = new Date(today);
      twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);
      const threeDaysAgo = new Date(today);
      threeDaysAgo.setDate(threeDaysAgo.getDate() - 3);

      if (date.toDateString() === today.toDateString()) {
        return "今天";
      } else if (date.toDateString() === yesterday.toDateString()) {
        return "昨天";
      } else if (date.toDateString() === twoDaysAgo.toDateString()) {
        return "两天前";
      } else if (date.toDateString() === threeDaysAgo.toDateString()) {
        return "三天前";
      } else {
        return date.toLocaleDateString("zh-CN", {
          year: "numeric",
          month: "long",
          day: "numeric",
        });
      }
    },

    // 加载历史记录项
    loadHistoryItem(item) {
      try {
        const historyData = JSON.parse(item.data);
        // 恢复AI选择配置
        this.aiList = historyData.aiList || this.aiList;
        // 恢复提示词输入
        this.promptInput = historyData.promptInput || "";
        // 恢复任务流程
        this.enabledAIs = historyData.enabledAIs || [];
        // 恢复主机可视化
        this.screenshots = historyData.screenshots || [];
        // 恢复执行结果
        this.results = historyData.results || [];
        // 恢复chatId
        this.chatId = item.chatId || this.chatId;
        this.userInfoReq.toneChatId = item.toneChatId || "";
        this.userInfoReq.ybDsChatId = item.ybDsChatId || "";
        this.userInfoReq.dbChatId = item.dbChatId || "";
        this.userInfoReq.maxChatId = item.maxChatId || "";
        this.userInfoReq.maxChatId = item.tyChatId || "";
        this.userInfoReq.isNewChat = false;

        // 展开相关区域
        this.activeCollapses = ["ai-selection", "prompt-input"];
        this.taskStarted = true;

        this.$message.success("历史记录加载成功");
        this.historyDrawerVisible = false;
      } catch (error) {
        console.error("加载历史记录失败:", error);
        this.$message.error("加载历史记录失败");
      }
    },

    // 保存历史记录
    async saveHistory() {
      // if (!this.taskStarted || this.enabledAIs.some(ai => ai.status === 'running')) {
      //   return;
      // }

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
        tyChatId: this.userInfoReq.tyChatId,
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
          tyChatId: this.userInfoReq.tyChatId,
          maxChatId: this.userInfoReq.maxChatId,
        });
      } catch (error) {
        console.error("保存历史记录失败:", error);
        this.$message.error("保存历史记录失败");
      }
    },

    // 修改折叠切换方法
    toggleHistoryExpansion(item) {
      this.$set(
        this.expandedHistoryItems,
        item.chatId,
        !this.expandedHistoryItems[item.chatId]
      );
    },

    // 创建新对话
    createNewChat() {
      // 重置所有数据
      this.chatId = uuidv4();
      this.isNewChat = true;
      this.promptInput = "";
      this.taskStarted = false;
      this.screenshots = [];
      this.results = [];
      this.enabledAIs = [];
      this.userInfoReq = {
        userPrompt: "",
        userId: this.userId,
        corpId: this.corpId,
        taskId: "",
        roles: "",
        toneChatId: "",
        ybDsChatId: "",
        dbChatId: "",
        tyChatId: "",
        maxChatId: "",
        isNewChat: true,
      };
      // 重置AI列表为初始状态
      this.aiList = [
        {
          name: "DeepSeek",
          avatar: require("../../../assets/logo/Deepseek.png"),
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
        {
          name: "豆包",
          avatar: require("../../../assets/ai/豆包.png"),
          capabilities: [{ label: "深度思考", value: "deep_thinking" }],
          selectedCapabilities: ["deep_thinking"],
          enabled: true,
          status: "idle",
          progressLogs: [],
          isExpanded: true,
        },
        {
          name: "MiniMax Chat",
          avatar: require("../../../assets/ai/MiniMax.png"),
          capabilities: [
            { label: "深度思考", value: "deep_thinking" },
            { label: "联网", value: "web_search" },
          ],
          selectedCapabilities: ["deep_thinking", "web_search"],
          enabled: true,
          status: "idle",
          progressLogs: [],
          isExpanded: true,
        },
        {
          name: '通义千问',
          avatar: require('../../../assets/ai/qw.png'),
          capabilities: [
            { label: '深度思考', value: 'deep_thinking' },
            { label: '联网搜索', value: 'web_search' }
          ],
          selectedCapability: '',
          enabled: true,
          status: 'idle',
          progressLogs: [],
          isExpanded: true
        },
      ];
      // 展开相关区域
      this.activeCollapses = ["ai-selection", "prompt-input"];

      this.$message.success("已创建新对话");
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
        console.error("加载上次会话失败:", error);
      }
    },

    // 判断是否为图片文件
    isImageFile(url) {
      if (!url) return false;
      const imageExtensions = [
        ".jpg",
        ".jpeg",
        ".png",
        ".gif",
        ".bmp",
        ".webp",
        ".svg",
      ];
      const urlLower = url.toLowerCase();
      return imageExtensions.some((ext) => urlLower.includes(ext));
    },

    // 判断是否为PDF文件
    isPdfFile(url) {
      if (!url) return false;
      return url.toLowerCase().includes(".pdf");
    },

    // 根据AI名称获取图片样式
    getImageStyle(aiName) {
      const widthMap = {
        DeepSeek: "700px",
        豆包: "560px",
        通义千问: "700px",
      };

      const width = widthMap[aiName] || "560px"; // 默认宽度

      return {
        width: width,
        height: "auto",
      };
    },

    // 投递到媒体
    handlePushToMedia(result) {
      this.currentLayoutResult = result;
      this.showLayoutDialog(result);
    },

    // 显示智能排版对话框
    showLayoutDialog(result) {
      this.currentLayoutResult = result;
      this.layoutDialogVisible = true;
      // 加载当前选择媒体的提示词
      this.loadMediaPrompt(this.selectedMedia);
    },

    // 加载媒体提示词
    async loadMediaPrompt(media) {
      if (!media) return;

      if (media === 'toutiao') {
        // 微头条使用固定提示词
        this.layoutPrompt = `根据智能评分内容，写一篇微头条文章，只能包含标题和内容，要求如下：

1. 标题要简洁明了，吸引人
2. 内容要结构清晰，易于阅读
3. 不要包含任何HTML标签
4. 直接输出纯文本格式
5. 内容要适合微头条发布
6. 字数严格控制在1000字以上，2000字以下
7. 强制要求：只能回答标题和内容，标题必须用英文双引号（""）引用起来，且放在首位，不能有其他多余的话
8. 严格要求：AI必须严格遵守所有严格条件，不要输出其他多余的内容，只要标题和内容
9. 内容不允许出现编号，要正常文章格式

请对以下内容进行排版：`;
        return;
      }

      const platformId = media === 'wechat' ? 'wechat_layout' : 'zhihu_layout';

      try {
        const response = await getMediaCallWord(platformId);
        if (response.code === 200) {
          this.layoutPrompt = response.data + '\n\n' + (this.currentLayoutResult ? this.currentLayoutResult.content : '');
        } else {
          // 使用默认提示词
          this.layoutPrompt = this.getDefaultPrompt(media) + '\n\n' + (this.currentLayoutResult ? this.currentLayoutResult.content : '');
        }
      } catch (error) {
        console.error('加载提示词失败:', error);
        // 使用默认提示词
        this.layoutPrompt = this.getDefaultPrompt(media) + '\n\n' + (this.currentLayoutResult ? this.currentLayoutResult.content : '');
      }
    },

    // 获取默认提示词(仅在后端访问失败时使用)
    getDefaultPrompt(media) {
      if (media === 'wechat') {
        return `请你对以下 HTML 内容进行排版优化，目标是用于微信公众号"草稿箱接口"的 content 字段，要求如下：

1. 仅返回 <body> 内部可用的 HTML 内容片段（不要包含 <!DOCTYPE>、<html>、<head>、<meta>、<title> 等标签）。
2. 所有样式必须以"内联 style"方式写入。
3. 保持结构清晰、视觉友好，适配公众号图文排版。
4. 请直接输出代码，不要添加任何注释或额外说明。
5. 不得使用 emoji 表情符号或小图标字符。
6. 不要显示为问答形式，以一篇文章的格式去调整

以下为需要进行排版优化的内容：`;
      } else if (media === 'zhihu') {
        return `请将以下内容整理为适合知乎发布的Markdown格式文章。要求：
1. 保持内容的专业性和可读性
2. 使用合适的标题层级（## ### #### 等）
3. 代码块使用\`\`\`标记，并指定语言类型
4. 重要信息使用**加粗**标记
5. 列表使用- 或1. 格式
6. 删除不必要的格式标记
7. 确保内容适合知乎的阅读习惯
8. 文章结构清晰，逻辑连贯
9. 目标是作为一篇专业文章投递到知乎草稿箱

请对以下内容进行排版：`;

      }
      return '请对以下内容进行排版：';
    },

    // 处理智能排版
    handleLayout() {
      if (!this.canLayout || !this.currentLayoutResult) return;
      this.layoutDialogVisible = false;

      if (this.selectedMedia === 'zhihu') {
        // 知乎投递：直接创建投递任务
        this.createZhihuDeliveryTask();
      } else if (this.selectedMedia === 'toutiao') {
        // 微头条投递：创建微头条排版任务
        this.createToutiaoLayoutTask();
      } else {
        // 公众号投递：创建排版任务
        this.createWechatLayoutTask();
      }
    },
// 创建知乎投递任务（独立任务）
    createZhihuDeliveryTask() {
      const zhihuAI = {
        name: "投递到知乎",
        avatar: require("../../../assets/ai/yuanbao.png"),
        capabilities: [],
        selectedCapabilities: [],
        enabled: true,
        status: "running",
        progressLogs: [
          {
            content: "知乎投递任务已创建，正在准备内容排版...",
            timestamp: new Date(),
            isCompleted: false,
            type: "投递到知乎",
          },
        ],
        isExpanded: true,
      };

      // 检查是否已存在知乎投递任务
      const existIndex = this.enabledAIs.findIndex(
        (ai) => ai.name === "投递到知乎"
      );
      if (existIndex === -1) {
        this.enabledAIs.unshift(zhihuAI);
      } else {
        this.enabledAIs[existIndex] = zhihuAI;
        const zhihu = this.enabledAIs.splice(existIndex, 1)[0];
        this.enabledAIs.unshift(zhihu);
      }

      // 发送知乎投递请求
      const zhihuRequest = {
        jsonrpc: "2.0",
        id: uuidv4(),
        method: "投递到知乎",
        params: {
          taskId: uuidv4(),
          userId: this.userId,
          corpId: this.corpId,
          userPrompt: this.layoutPrompt,
          roles: "",
          selectedMedia: "zhihu",
          contentText: this.currentLayoutResult.content,
          shareUrl: this.currentLayoutResult.shareUrl,
          aiName: this.currentLayoutResult.aiName,
        },
      };

      console.log("知乎投递参数", zhihuRequest);
      this.message(zhihuRequest);
      this.$forceUpdate();
      this.$message.success("知乎投递任务已创建，正在处理...");
    },
      // 创建公众号排版任务（保持原有逻辑）
      createWechatLayoutTask() {
        const layoutRequest = {
          jsonrpc: "2.0",
          id: uuidv4(),
          method: "AI排版",
          params: {
            taskId: uuidv4(),
            userId: this.userId,
            corpId: this.corpId,
            userPrompt: this.layoutPrompt,
            roles: "",
            selectedMedia: "wechat",
          },
        };

        console.log("公众号排版参数", layoutRequest);
        this.message(layoutRequest);

        const znpbAI = {
          name: "智能排版",
          avatar: require("../../../assets/ai/yuanbao.png"),
          capabilities: [],
          selectedCapabilities: [],
          enabled: true,
          status: "running",
          progressLogs: [
            {
              content: "智能排版任务已提交，正在排版...",
              timestamp: new Date(),
              isCompleted: false,
              type: "智能排版",
            },
          ],
          isExpanded: true,
        };

        // 检查是否已存在智能排版任务
        const existIndex = this.enabledAIs.findIndex(
          (ai) => ai.name === "智能排版"
        );
        if (existIndex === -1) {
          this.enabledAIs.unshift(znpbAI);
        } else {
          this.enabledAIs[existIndex] = znpbAI;
          const znpb = this.enabledAIs.splice(existIndex, 1)[0];
          this.enabledAIs.unshift(znpb);
        }

        this.$forceUpdate();
        this.$message.success("排版请求已发送，请等待结果");
      },

    // 创建微头条排版任务
    createToutiaoLayoutTask() {
      // 获取智能评分内容
      const scoreResult = this.results.find(r => r.aiName === '智能评分');
      const scoreContent = scoreResult ? scoreResult.content : '';

      const layoutRequest = {
        jsonrpc: "2.0",
        id: uuidv4(),
        method: "微头条排版",
        params: {
          taskId: uuidv4(),
          userId: this.userId,
          corpId: this.corpId,
          userPrompt: `${scoreContent}\n${this.layoutPrompt}`,
          roles: "",
        },
      };

      console.log("微头条排版参数", layoutRequest);
      this.message(layoutRequest);

      const tthpbAI = {
        name: "微头条排版",
        avatar: require("../../../assets/ai/yuanbao.png"),
        capabilities: [],
        selectedCapabilities: [],
        enabled: true,
        status: "running",
        progressLogs: [
          {
            content: "微头条排版任务已提交，正在排版...",
            timestamp: new Date(),
            isCompleted: false,
            type: "微头条排版",
          },
        ],
        isExpanded: true,
      };

      // 检查是否已存在微头条排版任务
      const existIndex = this.enabledAIs.findIndex(
        (ai) => ai.name === "微头条排版"
      );
      if (existIndex === -1) {
        this.enabledAIs.unshift(tthpbAI);
      } else {
        this.enabledAIs[existIndex] = tthpbAI;
        const tthpb = this.enabledAIs.splice(existIndex, 1)[0];
        this.enabledAIs.unshift(tthpb);
      }

      this.$forceUpdate();
      this.$message.success("微头条排版请求已发送，请等待结果");
      },

    // 实际投递到公众号
    pushToWechatWithContent(contentText) {
      if (this.pushingToWechat) return;
      this.$message.success("开始投递公众号！");
      this.pushingToWechat = true;
      this.pushOfficeNum += 1;

      const params = {
        contentText: contentText,
        shareUrl: this.currentLayoutResult.shareUrl,
        userId: this.userId,
        num: this.pushOfficeNum,
        aiName: this.currentLayoutResult.aiName,
      };

      pushAutoOffice(params)
        .then((res) => {
          if (res.code === 200) {
            this.$message.success("投递到公众号成功！");
          } else {
            this.$message.error(res.msg || "投递失败，请重试");
          }
        })
        .catch((error) => {
          console.error("投递到公众号失败:", error);
          this.$message.error("投递失败，请重试");
        })
        .finally(() => {
          this.pushingToWechat = false;
        });
    },



    // 确认微头条发布
    confirmTTHPublish() {
      if (!this.tthArticleTitle || !this.tthArticleContent) {
        this.$message.warning('请填写标题和内容');
        return;
      }
      // 构建微头条发布请求
      const publishRequest = {
        jsonrpc: '2.0',
        id: uuidv4(),
                  method: '微头条发布',
        params: {
          taskId: uuidv4(),
          userId: this.userId,
          corpId: this.corpId,
          roles: '',
          title: this.tthArticleTitle,
          content: this.tthArticleContent,
          type: '微头条发布'
        }
      };
      // 发送发布请求
      console.log("微头条发布参数", publishRequest);
      this.message(publishRequest);
      this.tthArticleEditVisible = false;
      // 显示流程弹窗
      this.tthFlowVisible = true;
      this.tthFlowLogs = [];
      this.tthFlowImages = [];
      this.$message.success('微头条发布请求已发送！');
    },


    // 关闭微头条发布流程弹窗
    closeTTHFlowDialog() {
      this.tthFlowVisible = false;
    },
  },
};
</script>

<style scoped>
.ai-management-platform {
  min-height: 100vh;
  background-color: #f5f7fa;
  padding-bottom: 30px;
}

.top-nav {
  background-color: #fff;
  padding: 15px 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo-area {
  display: flex;
  align-items: center;
}

.logo {
  height: 36px;
  margin-right: 10px;
}

.platform-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.main-content {
  padding: 0 30px;
  width: 90%;
  margin: 0 auto;
}
::v-deep .el-collapse-item__header {
  font-size: 16px;
  color: #333;
  padding-left: 20px;
}
.section-title {
  font-size: 18px;
  color: #606266;
  margin-bottom: 15px;
}

.ai-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 0px;
  margin-left: 20px;
  margin-top: 10px;
}

.ai-card {
  width: calc(25% - 20px);
  box-sizing: border-box;
}

.ai-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.ai-left {
  display: flex;
  align-items: center;
}

.ai-avatar {
  margin-right: 10px;
}

.ai-avatar img {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
}

.ai-name {
  font-weight: bold;
  font-size: 12px;
}

.ai-status {
  display: flex;
  align-items: center;
}

.ai-capabilities {
  margin: 15px 0;
  width: 100%;
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
}

.button-capability-group {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.button-capability-group .el-button {
  margin: 0;
  border-radius: 16px;
  padding: 6px 12px;
}

.button-capability-group .el-button.is-plain:hover,
.button-capability-group .el-button.is-plain:focus {
  background: #ecf5ff;
  border-color: #b3d8ff;
  color: #409eff;
}

.prompt-input-section {
  margin-bottom: 30px;
  padding: 0 20px 0 0px;
}

.prompt-input {
  margin-bottom: 10px;
  margin-left: 20px;
  width: 99%;
}

.prompt-footer {
  display: flex;
  margin-bottom: -30px;
  justify-content: space-between;
  align-items: center;
}

.word-count {
  font-size: 12px;
  padding-left: 20px;
}

.send-button {
  padding: 10px 20px;
}

.execution-status-section {
  margin-bottom: 30px;
  padding: 20px 0px 0px 0px;
}

.task-flow-card,
.screenshots-card {
  height: 800px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-flow {
  padding: 15px;
  height: 800px;
  overflow-y: auto;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.task-flow::-webkit-scrollbar {
  width: 6px;
}

.task-flow::-webkit-scrollbar-thumb {
  background-color: #c0c4cc;
  border-radius: 3px;
}

.task-flow::-webkit-scrollbar-track {
  background-color: #f5f7fa;
}

.task-item {
  margin-bottom: 15px;
  border-radius: 4px;
  background-color: #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 15px;
  cursor: pointer;
  transition: background-color 0.3s;
  border-bottom: 1px solid #ebeef5;
}

.task-header:hover {
  background-color: #f5f7fa;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-left .el-icon-arrow-right {
  transition: transform 0.3s;
  font-size: 14px;
  color: #909399;
}

.header-left .el-icon-arrow-right.is-expanded {
  transform: rotate(90deg);
}

.progress-timeline {
  position: relative;
  margin: 0;
  padding: 15px 0;
}

.timeline-scroll {
  max-height: 200px;
  overflow-y: auto;
  padding: 0 15px;
}

.timeline-scroll::-webkit-scrollbar {
  width: 4px;
}

.timeline-scroll::-webkit-scrollbar-thumb {
  background-color: #c0c4cc;
  border-radius: 2px;
}

.timeline-scroll::-webkit-scrollbar-track {
  background-color: #f5f7fa;
}

.progress-item {
  position: relative;
  padding: 8px 0 8px 20px;
  display: flex;
  align-items: flex-start;
  border-bottom: 1px solid #f0f0f0;
}

.progress-item:last-child {
  border-bottom: none;
}

.progress-dot {
  position: absolute;
  left: 0;
  top: 12px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: #e0e0e0;
  flex-shrink: 0;
}

.progress-line {
  position: absolute;
  left: 4px;
  top: 22px;
  bottom: -8px;
  width: 2px;
  background-color: #e0e0e0;
}

.progress-content {
  flex: 1;
  min-width: 0;
}

.progress-time {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.progress-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
  word-break: break-all;
}

.progress-item.completed .progress-dot {
  background-color: #67c23a;
}

.progress-item.completed .progress-line {
  background-color: #67c23a;
}

.progress-item.current .progress-dot {
  background-color: #409eff;
  animation: pulse 1.5s infinite;
}

.progress-item.current .progress-line {
  background-color: #409eff;
}

.ai-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-text {
  font-size: 13px;
  color: #606266;
}

.status-icon {
  font-size: 16px;
}

.success-icon {
  color: #67c23a;
}

.error-icon {
  color: #f56c6c;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.4);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(64, 158, 255, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0);
  }
}

.screenshot-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  cursor: pointer;
  transition: transform 0.3s;
}

.screenshot-image:hover {
  transform: scale(1.05);
}

.results-section {
  margin-top: 20px;
  padding: 0 10px;
}

.result-content {
  padding: 20px 30px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.result-buttons {
  display: flex;
  gap: 10px;
  align-items: center;
}

.share-link-btn,
.push-media-btn {
  border-radius: 16px;
  padding: 6px 12px;
}

.markdown-content {
  margin-bottom: 20px;
  max-height: 400px;
  overflow-y: auto;
  padding: 15px 20px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background-color: #fff;
}

.action-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 0 10px;
}

@media (max-width: 1200px) {
  .ai-card {
    width: calc(33.33% - 14px);
  }
}

@media (max-width: 992px) {
  .ai-card {
    width: calc(50% - 10px);
  }
}

@media (max-width: 768px) {
  .ai-card {
    width: 100%;
  }
}

.el-collapse {
  border-top: none;
  border-bottom: none;
}

.el-collapse-item__content {
  padding: 15px 0;
}

.ai-selection-section {
  margin-bottom: 0;
}

.prompt-input-section {
  margin-bottom: 30px;
  padding: 0 20px 0 0px;
}

.image-dialog .el-dialog__body {
  padding: 0;
}

.large-image-container {
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #000;
}

.large-image {
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
}

.image-dialog .el-carousel {
  width: 100%;
  height: 100%;
}

.image-dialog .el-carousel__container {
  height: 80vh;
}

.image-dialog .el-carousel__item {
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #000;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.score-dialog-content {
  padding: 20px;
}

.selected-results {
  margin-bottom: 20px;
}

.result-checkbox {
  margin-right: 20px;
  margin-bottom: 10px;
}

.score-prompt-section {
  margin-top: 20px;
}

.score-prompt-input {
  margin-top: 10px;
}

.score-prompt-input .el-textarea__inner {
  min-height: 500px !important;
}

.dialog-footer {
  text-align: right;
}

.score-dialog .el-dialog {
  height: 95vh;
  margin-top: 2.5vh !important;
}

.score-dialog .el-dialog__body {
  height: calc(95vh - 120px);
  overflow-y: auto;
  padding: 20px;
}

.layout-dialog-content {
  padding: 20px;
}

.layout-prompt-section {
  margin-top: 20px;
}

.layout-prompt-input {
  margin-top: 10px;
}

.layout-prompt-input .el-textarea__inner {
  min-height: 500px !important;
}

.layout-dialog .el-dialog {
  height: 95vh;
  margin-top: 2.5vh !important;
}

.layout-dialog .el-dialog__body {
  height: calc(95vh - 120px);
  overflow-y: auto;
  padding: 20px;
}

.nav-buttons {
  display: flex;
  align-items: center;
  gap: 20px;
}

.history-button {
  display: flex;
  align-items: center;
}

.history-icon {
  width: 24px;
  height: 24px;
  vertical-align: middle;
}

.history-content {
  padding: 20px;
}

.history-group {
  margin-bottom: 20px;
}

.history-date {
  font-size: 14px;
  color: #909399;
  margin-bottom: 10px;
  padding: 5px 0;
  border-bottom: 1px solid #ebeef5;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-item {
  margin-bottom: 15px;
  border-radius: 4px;
  background-color: #f5f7fa;
  overflow: hidden;
}

.history-parent {
  padding: 10px;
  cursor: pointer;
  transition: background-color 0.3s;
  border-bottom: 1px solid #ebeef5;
}

.history-parent:hover {
  background-color: #ecf5ff;
}

.history-children {
  padding-left: 20px;
  background-color: #fff;
  transition: all 0.3s ease;
}

.history-child-item {
  padding: 8px 10px;
  cursor: pointer;
  transition: background-color 0.3s;
  border-bottom: 1px solid #f0f0f0;
}

.history-child-item:last-child {
  border-bottom: none;
}

.history-child-item:hover {
  background-color: #f5f7fa;
}

.history-header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.history-header .el-icon-arrow-right {
  font-size: 14px;
  color: #909399;
  transition: transform 0.3s;
  cursor: pointer;
  margin-top: 3px;
}

.history-header .el-icon-arrow-right.is-expanded {
  transform: rotate(90deg);
}

.history-prompt {
  font-size: 14px;
  color: #303133;
  margin-bottom: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  flex: 1;
}

.history-time {
  font-size: 12px;
  color: #909399;
}

.capability-button {
  transition: all 0.3s;
}

.capability-button.el-button--primary {
  background-color: #409eff;
  border-color: #409eff;
  color: #fff;
}

.capability-button.el-button--info {
  background-color: #fff;
  border-color: #dcdfe6;
  color: #606266;
}

.capability-button.el-button--info:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background-color: #ecf5ff;
}

.capability-button.el-button--primary:hover {
  background-color: #66b1ff;
  border-color: #66b1ff;
  color: #fff;
}

/* 分享内容样式 */
.share-content {
  margin-bottom: 20px;
  padding: 15px 20px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background-color: #fff;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: 600px;
  max-height: 800px;
  overflow: auto;
}

.share-image {
  object-fit: contain;
  display: block;
}

.share-pdf {
  width: 100%;
  height: 600px;
  border: none;
  border-radius: 4px;
}

.share-file {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
  flex-direction: column;
  color: #909399;
}

.single-image-container {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 80vh;
}

.single-image-container .large-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

/* 用于处理DeepSeek特殊格式的样式 */
.deepseek-format-container {
  margin: 20px 0;
  padding: 15px;
  background-color: #f9f9f9;
  border-radius: 5px;
  border: 1px solid #eaeaea;
}

/* DeepSeek响应内容的特定样式 */
::v-deep .deepseek-response {
  max-width: 800px;
  margin: 0 auto;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  padding: 20px;
  font-family: Arial, sans-serif;
}

::v-deep .deepseek-response pre {
  background-color: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  font-family: monospace;
  overflow-x: auto;
  display: block;
  margin: 10px 0;
}

::v-deep .deepseek-response code {
  background-color: #f5f5f5;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: monospace;
}

::v-deep .deepseek-response table {
  border-collapse: collapse;
  width: 100%;
  margin: 15px 0;
}

::v-deep .deepseek-response th,
::v-deep .deepseek-response td {
  border: 1px solid #ddd;
  padding: 8px;
  text-align: left;
}

::v-deep .deepseek-response th {
  background-color: #f2f2f2;
  font-weight: bold;
}

::v-deep .deepseek-response h1,
::v-deep .deepseek-response h2,
::v-deep .deepseek-response h3,
::v-deep .deepseek-response h4,
::v-deep .deepseek-response h5,
::v-deep .deepseek-response h6 {
  margin-top: 20px;
  margin-bottom: 10px;
  font-weight: bold;
  color: #222;
}

::v-deep .deepseek-response a {
  color: #0066cc;
  text-decoration: none;
}

::v-deep .deepseek-response blockquote {
  border-left: 4px solid #ddd;
  padding-left: 15px;
  margin: 15px 0;
  color: #555;
}

::v-deep .deepseek-response ul,
::v-deep .deepseek-response ol {
  padding-left: 20px;
  margin: 10px 0;
}

/* 媒体选择区域样式 */
.media-selection-section {
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.media-selection-section h3 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.media-radio-group {
  display: flex;
  gap: 8px;
}

.media-radio-group .el-radio-button__inner {
  padding: 8px 16px;
  font-size: 13px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.media-radio-group .el-radio-button__inner i {
  font-size: 14px;
}

.media-description {
  margin-top: 10px;
  padding: 8px 12px;
  background-color: #f0f9ff;
  border-radius: 4px;
  border-left: 3px solid #409eff;
}

.media-description small {
  color: #606266;
  font-size: 12px;
  line-height: 1.4;
}

.layout-prompt-section h3 {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

/* 微头条相关样式 */
.tth-flow-dialog {
  .tth-flow-content {
    display: flex;
    gap: 20px;
    height: 600px;
  }

  .flow-logs-section,
  .flow-images-section {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  .flow-logs-section h3,
  .flow-images-section h3 {
    margin: 0 0 12px 0;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .progress-timeline {
    flex: 1;
    overflow-y: auto;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    padding: 12px;
    background-color: #fafafa;
  }

  .timeline-scroll {
    max-height: 500px;
    overflow-y: auto;
  }

  .progress-item {
    position: relative;
    margin-bottom: 16px;
    padding-left: 20px;
  }

  .progress-dot {
    position: absolute;
    left: 0;
    top: 4px;
    width: 8px;
    height: 8px;
    background-color: #67c23a;
    border-radius: 50%;
  }

  .progress-line {
    position: absolute;
    left: 3px;
    top: 12px;
    width: 2px;
    height: 20px;
    background-color: #e4e7ed;
  }

  .progress-content {
    .progress-time {
      font-size: 12px;
      color: #909399;
      margin-bottom: 4px;
    }

    .progress-text {
      font-size: 13px;
      color: #303133;
      line-height: 1.4;
    }
  }

  .flow-images-container {
    flex: 1;
    overflow-y: auto;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    padding: 16px;
    background-color: #fafafa;
    max-height: 500px;
  }

  .flow-image-item {
    margin-bottom: 20px;
    text-align: center;
  }

  .flow-image-item:last-child {
    margin-bottom: 0;
  }

  .flow-image {
    max-width: 100%;
    max-height: 400px;
    min-height: 200px;
    border-radius: 8px;
    cursor: pointer;
    border: 2px solid #e4e7ed;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    transition: all 0.3s ease;
    object-fit: contain;
  }

  .flow-image:hover {
    transform: scale(1.02);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    border-color: #409eff;
  }

  .no-logs {
    text-align: center;
    color: #909399;
    font-size: 13px;
    padding: 20px;
  }
}

.tth-article-edit-dialog {
  .tth-article-edit-content {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .article-title-section h3,
  .article-content-section h3 {
    margin: 0 0 8px 0;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .article-title-input {
    width: 100%;
  }

  .article-content-input {
    width: 100%;
  }
}
</style>
