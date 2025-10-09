<template>
  <div class="min-h-screen bg-gray-50 py-8" v-loading="loading">
 

    <div class="max-w-7xl mx-auto px-4">
      <!-- 对话列表 -->
      <div class="space-y-8" @scroll="handleScroll" ref="scrollContainer" style="overflow-y: auto; max-height: 80vh;">
        <!-- 单个对话块 -->
        <div v-for="(item, index) in dialogList" :key="index" class="dialog-card">
          <!-- 用户提问区域 -->
          <div class="user-question">
            <div class="user-info">
              <div class="user-name">{{ item.userName }}</div>
              <br />
              <div style="font-weight: bold;" class="question-text">{{ item.question }}</div>
              <br />
              <div class="question-time">{{ item.questionTime }}</div>
            </div>
          </div>

          <!-- AI回答卡片网格 -->
          <div class="response-grid">
            <div v-for="(model, mIndex) in item.aiResponses" :key="mIndex" class="response-card" @click="showModelResponse(model)">
              <!-- 模型标题 -->
              <div class="model-title">
                <i class="fas fa-robot model-icon"></i>
                <h3 class="model-name">{{ model.name }}</h3>
              </div>
              <!-- 预览内容 -->
              <div class="preview-content" v-html="renderMarkdown(model.preview)"></div>
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
          <div class="prose" v-html="renderMarkdown(selectedModel.content)"></div>
        </div>
        <div class="modal-footer">
          <div class="response-time-footer">{{ selectedModel.responseTime }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getNodeLog } from "@/api/wechat/aigc";
import { marked } from 'marked';

export default {
  data() {
    return {
      // 遮罩层
      loading: true,
      total:0,
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
    getList() {
      this.loading = true;
      getNodeLog(this.queryParams).then(res => {
        // 添加新数据到列表
        this.total = res.data.total;
        if (this.queryParams.page === 1) {
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
      if (container.scrollHeight - container.scrollTop === container.clientHeight) {
        this.queryParams.page += 1; // 下一页
        this.getList();
      }
    },
    renderMarkdown(content) {
      // 检查是否是 JSON 字符串
      try {
        const jsonData = JSON.parse(content);
        if (Array.isArray(jsonData)) {
          // 如果是数组，转换为 Markdown 列表
          return marked(jsonData.map(item => {
            let markdown = '';
            if (item.title) markdown += `### ${item.title}\n\n`;
            if (item.abstract) markdown += `${item.abstract}\n\n`;
            if (item.content) markdown += `${item.content}\n\n`;
            if (item.publish_time) markdown += `发布时间: ${item.publish_time}\n\n`;
            if (item.url) markdown += `[查看原文](${item.url})\n\n`;
            return markdown;
          }).join('---\n\n'));
        } else if (typeof jsonData === 'object') {
          // 如果是单个对象，转换为 Markdown 格式
          let markdown = '';
          if (jsonData.title) markdown += `### ${jsonData.title}\n\n`;
          if (jsonData.abstract) markdown += `${jsonData.abstract}\n\n`;
          if (jsonData.content) markdown += `${jsonData.content}\n\n`;
          if (jsonData.publish_time) markdown += `发布时间: ${jsonData.publish_time}\n\n`;
          if (jsonData.url) markdown += `[查看原文](${jsonData.url})\n\n`;
          return marked(markdown);
        }
      } catch (e) {
        // 如果不是 JSON，直接渲染原始内容
        return marked(content);
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

.response-card img{
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
  -webkit-line-clamp: 3; /* 限制显示3行 */
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis; /* 超出显示省略号 */
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
  top: 1rem;  /* 上部内边距 */
  right: 1rem;  /* 右侧内边距 */
}

.close-button i {
  font-size: 1.5rem;  /* 增大关闭按钮图标的大小 */
  color: #6b7280;  /* 确保图标的颜色 */
}

.close-button:hover {
  color: #f87171;  /* 鼠标悬停时颜色变化 */
}

/* 滚动容器的样式 */
#scrollContainer {
  overflow-y: auto;
  max-height: 80vh; /* 设置最大高度 */
}

/* 保持弹窗样式不变 */
</style>
