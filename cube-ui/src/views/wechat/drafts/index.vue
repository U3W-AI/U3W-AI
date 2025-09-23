<template>
  <div class="min-h-screen bg-gray-50 py-8" v-loading="loading">
    <el-row style="margin-left:1%">
      <!--用户数据-->
      <el-col>
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
          <el-form-item prop="keyWord">
            <el-input v-model="queryParams.keyWord" placeholder="请输入问题关键字" clearable style="width: 240px;"
              @keyup.enter.native="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
          </el-form-item>
        </el-form>
      </el-col>
    </el-row>

    <div class="max-w-7xl mx-auto px-4">
      <!-- 对话列表 -->
      <div class="space-y-8" @scroll="handleScroll" ref="scrollContainer" style="overflow-y: auto; max-height: 80vh;">
        <!-- 单个对话块 -->
        <div v-for="(item, index) in dialogList" :key="index" class="dialog-card">
          <!-- 用户提问区域 -->
          <div class="user-question">
            <div class="user-avatar">
              <img :src="item.userAvatar" :alt="item.userName" class="avatar-img" />
            </div>
            <div class="user-info">
              <div class="user-name">{{ item.userName }}</div>
              <br />
              <div style="font-weight: bold;" class="question-text">{{ item.question }}</div>
              <br />
              <div class="question-time">{{ item.questionTime }}</div>
            </div>
          </div>

          AI回答卡片网格
          <div class="response-grid">
            <div v-for="(model, mIndex) in item.aiResponses" :key="mIndex" class="response-card"
              @click="showModelResponse(model)">
              <!-- 模型标题 -->
              <div class="model-title">
                <i class="fas fa-robot model-icon"></i>
                <h3 class="model-name">{{ model.name }}</h3>
              </div>
              <!-- 预览内容 -->
              <div class="preview-content">
                <template v-if="extractPlainText(model.preview).isImage">
                  <img :src="extractPlainText(model.preview).url" class="ai-image" :alt="`图片预览`" />
                </template>
                <template v-else>
                  <div class="prose markdown-body" v-html="renderMarkdown(model.content)"></div>
                  <!-- {{ extractPlainText(model.preview).text }} -->
                </template>
              </div>
              <!-- <div class="preview-content" v-else>{{ extractPlainText(model.preview) }}</div> -->
              <!-- 时间 -->
              <div class="response-time">{{ model.responseTime }}</div>
            </div>
          </div>


        </div>
      </div>
    </div>

    <!-- 模态框 -->
    <div v-if="showModal" class="modal-overlay" @click="closeModal">
      <div class="modal-container" @click.stop>
        <div class="modal-header">
          <h2 class="modal-title">{{ selectedModel.name }}</h2>
          <!-- 关闭按钮 -->
          <button @click="closeModal" class="close-button">
            X
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="modal-content">
          <template v-if="extractPlainText(selectedModel.content).isImage">
            <img :src="extractPlainText(selectedModel.content).url" style="max-width: 100%; height: auto;" />
          </template>
          <template v-else>
            <div class="prose markdown-body" v-html="renderMarkdown(selectedModel.content)"></div>
          </template>
        </div>
        <div class="modal-footer">
          <div class="response-time-footer">{{ selectedModel.responseTime }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import { getPlayWrighDrafts } from "@/api/wechat/aigc";
  import { marked } from 'marked';

  // 配置 marked
  marked.setOptions({
    breaks: true, // 支持 GitHub 风格的换行
    gfm: true,    // 启用 GitHub 风格的 Markdown
    headerIds: false, // 禁用标题 ID
    mangle: false,    // 禁用标题 ID 混淆
    sanitize: false   // 允许 HTML 标签
  });

  export default {
    data() {
      return {
        // 遮罩层
        loading: true,
        total: 0,
        queryParams: {
          page: 1,
          limit: 3, // 默认每页查询 3 条数据
          keyWord: '',
          flowStatus: '',
          id: ''
        },
        showModal: false,
        selectedModel: null,
        dialogList: []
      };
    },
    created() {
      this.getList();
    },
    methods: {
      // 修改 markdown 渲染方法
      renderMarkdown(content) {
        return marked(content);
      },
      // 提取纯文本内容，去除HTML标签
      // extractPlainText(content) {
      //   if (!content) return '';
      //   // 创建临时DOM元素来解析HTML
      //   const tempDiv = document.createElement('div');
      //   tempDiv.innerHTML = content;
      //   // 获取纯文本内容
      //   let plainText = tempDiv.textContent || tempDiv.innerText || '';
      //   // 清理多余的空白字符
      //   plainText = plainText.replace(/\s+/g, ' ').trim();
      //   return plainText;
      // },

      // 提取内容，如果是图片链接则返回图片信息，否则返回纯文本
      extractPlainText(content) {
        if(!content) return { isImage: false, text: '' };

        const trimmedContent = content.trim();
        
        // 检查内容是否是图片链接 - 支持带参数的URL和各种图片格式
        const imageRegex = /\.(jpg|jpeg|png|gif|bmp|webp)(\?.*)?$/i;
        // 检查是否是以http开头且包含图片扩展名的URL
        const urlImageRegex = /^https?:\/\/.*\.(jpg|jpeg|png|gif|bmp|webp)(\?.*)?$/i;
        
        if(imageRegex.test(trimmedContent) || urlImageRegex.test(trimmedContent)) {
          // 返回图片标记对象
          return {
            isImage: true,
            url: trimmedContent
          };
        }

        // 处理文本内容
        const tempDiv = document.createElement('div');
        tempDiv.innerHTML = content;
        let plainText = tempDiv.textContent || tempDiv.innerText || '';
        plainText = plainText.replace(/\s+/g, ' ').trim();

        if(plainText.length > 100) {
          plainText = plainText.substring(0, 100) + '...';
        }

        return {
          isImage: false,
          text: plainText
        };
      },
      getList() {
        this.loading = true;
        getPlayWrighDrafts(this.queryParams).then(res => {
          // 添加新数据到列表
          this.total = res.data.total;
          if(this.queryParams.page === 1) {
            this.dialogList = res.data.list;
          } else {
            res.data.list.forEach(item => {
              this.dialogList.push(item);
            });
          }
          this.loading = false;
        });
      },
      handleQuery() {
        this.queryParams.page = 1; // 重置为第一页
        this.getList();
      },
      showModelResponse(model) {
        this.selectedModel = model;
        this.showModal = true;
      },
      closeModal() {
        this.showModal = false;
        this.selectedModel = null;
      },
      // 滚动加载更多
      handleScroll(event) {
        const container = event.target;
        // 判断是否滚动到底部
        if(container.scrollHeight - container.scrollTop === container.clientHeight) {
          this.queryParams.page += 1; // 下一页
          this.getList();
        }
      }
    }
  };
</script>


<style>

  /* 全局样式 */
  * {
    box-sizing: border-box;
  }

  body {
    font-family: Arial, sans-serif;
    margin: 0;
    padding: 0;
  }

  .min-h-screen {
    min-height: 100vh;
  }

  .bg-gray-50 {
    background-color: #f9fafb;
  }

  .py-8 {
    padding-top: 2rem;
    padding-bottom: 2rem;
  }

  .max-w-7xl {
    max-width: 100rem;
  }

  .mx-auto {
    margin-left: auto;
    margin-right: auto;
  }

  .px-4 {
    padding-left: 1rem;
    padding-right: 1rem;
  }

  /* 对话卡片 */
  .dialog-card {
    background-color: white;
    border-radius: 1rem;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    padding: 1.5rem;
    transition: all 0.3s ease;
    margin-bottom: 2rem;
  }

  /* 用户提问区域 */
  .user-question {
    display: flex;
    align-items: flex-start;
    gap: 1rem;
    margin-bottom: 1.5rem;
  }

  .user-avatar {
    width: 2.5rem;
    height: 2.5rem;
    border-radius: 50%;
    overflow: hidden;
    flex-shrink: 0;
  }

  .avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .user-info {
    flex: 1;
  }

  .user-name {
    font-size: 1.125rem;

    color: #1f2937;
  }

  .question-text {
    font-size: 1rem;
    color: #4b5563;
    margin-top: 0.25rem;
    /* 限制问题文本显示行数 */
    display: -webkit-box;
    -webkit-line-clamp: 2;
    /* 限制显示2行 */
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1.5;
    max-height: 3em;
    /* 2行的最大高度 */
    word-break: break-word;
    /* 支持长单词换行 */
  }

  .question-time {
    font-size: 0.875rem;
    color: #6b7280;
    margin-top: 0.25rem;
  }

  /* AI回答卡片网格 */
  .response-grid {
    display: grid;
    grid-template-columns: repeat(1, 1fr);
    gap: 1rem;
  }

  @media (min-width: 768px) {
    .response-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  @media (min-width: 1024px) {
    .response-grid {
      grid-template-columns: repeat(4, 1fr);
    }
  }

  /* AI回答卡片 */
  .response-card {
    background-color: white;
    border: 1px solid #e5e7eb;
    border-radius: 0.75rem;
    padding: 1rem;
    cursor: pointer;
    width: 350px;
    height: 250px;
    transition: box-shadow 0.3s ease;
  }

  .response-card img {
    width: 300px;
    height: 150px;

  }

  .response-card:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }

  .model-title {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 0.75rem;
  }

  .model-icon {
    font-size: 1.25rem;
    color: #3b82f6;
  }

  .model-name {
    font-size: 1rem;
    font-weight: 500;
    color: #1f2937;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .preview-content {
    font-size: 0.875rem;
    color: #4b5563;
    line-height: 1.5;
    margin-bottom: 1.75rem;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    /* 限制显示3行 */
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-overflow: ellipsis;
    /* 超出显示省略号 */
    word-break: break-word;
    /* 支持长单词换行 */
    white-space: normal;
    /* 允许正常换行 */
  }

  .ai-image {
    max-width: 100%;
    max-height: 200px;
    object-fit: contain;
    border-radius: 4px;
    transition: all 0.3s ease;
    object-fit: cover;
  }

  .ai-image:hover {
    transform: scale(1.02);
  }

  .image-loading,
  .image-error {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 150px;
    color: #666;
    background-color: #f5f5f5;
    border-radius: 4px;
  }

  .image-loading i,
  .image-error i {
    margin-right: 8px;
    font-size: 1.2rem;
  }

  .image-error {
    color: #e74c3c;
  }

  .response-time {
    font-size: 0.75rem;
    color: #6b7280;
    text-align: right;
  }

  /* 模态框 */
  .modal-overlay {
    position: fixed;
    inset: 0;
    background-color: rgba(0, 0, 0, 0.5);
    display: flex;
    justify-content: center;
    align-items: center;
    z-index: 50;
  }

  .modal-container {
    background-color: white;
    border-radius: 1rem;
    max-width: 60rem;
    width: 100%;
    max-height: 80vh;
    margin: 1rem;
    overflow: hidden;
    margin-left: 400px;
  }

  .modal-header {
    padding: 1.5rem;
    border-bottom: 1px solid #e5e7eb;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .modal-title {
    font-size: 1.25rem;
    font-weight: 600;
    color: #1f2937;
  }


  .modal-content {
    padding: 1.5rem;
    overflow-y: auto;
    max-height: 60vh;
  }

  .modal-footer {
    padding: 1.5rem;
    border-top: 1px solid #e5e7eb;
  }

  .response-time-footer {
    font-size: 0.875rem;
    color: #6b7280;
  }

  /* line-clamp 样式 */
  .line-clamp-3 {
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .prose {

    color: #374151;
    line-height: 1.75;
  }

  .prose p {
    margin-top: 1.25em;
    margin-bottom: 1.25em;
  }

  /* 弹窗关闭按钮 */
  .close-button {
    background: none;
    border: none;
    font-size: 1.25rem;
    color: #6b7280;
    top: 1rem;
    /* 上部内边距 */
    right: 1rem;
    /* 右侧内边距 */
  }

  .close-button i {
    font-size: 1.5rem;
    /* 增大关闭按钮图标的大小 */
    color: #6b7280;
    /* 确保图标的颜色 */
  }

  .close-button:hover {
    color: #f87171;
    /* 鼠标悬停时颜色变化 */
  }

  /* 滚动容器的样式 */
  #scrollContainer {
    overflow-y: auto;
    max-height: 80vh;
    /* 设置最大高度 */
  }

  /* 更新 markdown 样式 */
  .markdown-body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
    font-size: 16px;
    line-height: 1.5;
    word-wrap: break-word;
    color: #24292e;
  }

  .markdown-body h1,
  .markdown-body h2,
  .markdown-body h3,
  .markdown-body h4,
  .markdown-body h5,
  .markdown-body h6 {
    margin-top: 24px;
    margin-bottom: 16px;
    font-weight: 600;
    line-height: 1.25;
  }

  .markdown-body h1 {
    font-size: 2em;
  }

  .markdown-body h2 {
    font-size: 1.5em;
  }

  .markdown-body h3 {
    font-size: 1.25em;
  }

  .markdown-body h4 {
    font-size: 1em;
  }

  .markdown-body h5 {
    font-size: 0.875em;
  }

  .markdown-body h6 {
    font-size: 0.85em;
  }

  .markdown-body code {
    padding: 0.2em 0.4em;
    margin: 0;
    font-size: 85%;
    background-color: rgba(27, 31, 35, 0.05);
    border-radius: 3px;
    font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  }

  .markdown-body pre {
    padding: 16px;
    overflow: auto;
    font-size: 85%;
    line-height: 1.45;
    background-color: #f6f8fa;
    border-radius: 3px;
    margin-top: 0;
    margin-bottom: 16px;
  }

  .markdown-body pre code {
    padding: 0;
    margin: 0;
    font-size: 100%;
    word-break: normal;
    white-space: pre;
    background: transparent;
    border: 0;
  }

  .markdown-body blockquote {
    padding: 0 1em;
    color: #6a737d;
    border-left: 0.25em solid #dfe2e5;
    margin: 0 0 16px 0;
  }

  .markdown-body ul,
  .markdown-body ol {
    padding-left: 2em;
    margin-top: 0;
    margin-bottom: 16px;
  }

  .markdown-body table {
    display: block;
    width: 100%;
    overflow: auto;
    border-spacing: 0;
    border-collapse: collapse;
    margin-top: 0;
    margin-bottom: 16px;
  }

  .markdown-body table th,
  .markdown-body table td {
    padding: 6px 13px;
    border: 1px solid #dfe2e5;
  }

  .markdown-body table tr {
    background-color: #fff;
    border-top: 1px solid #c6cbd1;
  }

  .markdown-body table tr:nth-child(2n) {
    background-color: #f6f8fa;
  }

  .markdown-body img {
    max-width: 100%;
    box-sizing: content-box;
    background-color: #fff;
    margin: 16px 0;
  }

  .markdown-body hr {
    height: 0.25em;
    padding: 0;
    margin: 24px 0;
    background-color: #e1e4e8;
    border: 0;
  }

  .markdown-body a {
    color: #0366d6;
    text-decoration: none;
  }

  .markdown-body a:hover {
    text-decoration: underline;
  }
</style>
