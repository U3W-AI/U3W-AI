<template>
  <div class="app-container home">
    <el-row :gutter="20" justify="center">
      <el-col :xs="24" :sm="22" :md="20" :lg="18" :xl="16">
        <div class="welcome-section">
          <h1 class="welcome-title">欢迎使用福帮手</h1>
          <p class="welcome-subtitle">智能化的内容创作与发布助手</p>
          
          <el-row :gutter="20" class="feature-cards">
            <el-col :xs="24" :sm="12" :md="8">
              <el-card shadow="hover" class="feature-card">
                <div class="feature-icon">
                  <el-icon :size="40" color="#409EFF"><EditPen /></el-icon>
                </div>
                <h3>AI写作助手</h3>
                <p>利用AI技术，快速生成高质量文章内容，提升创作效率</p>
              </el-card>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-card shadow="hover" class="feature-card">
                <div class="feature-icon">
                  <el-icon :size="40" color="#67C23A"><Connection /></el-icon>
                </div>
                <h3>一键发布</h3>
                <p>支持直接发布到微信公众号草稿箱，简化发布流程</p>
              </el-card>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-card shadow="hover" class="feature-card">
                <div class="feature-icon">
                  <el-icon :size="40" color="#E6A23C"><DataAnalysis /></el-icon>
                </div>
                <h3>内容管理</h3>
                <p>统一管理文章内容，查看发布记录，轻松追踪内容状态</p>
              </el-card>
            </el-col>
          </el-row>

          <el-divider />

          <div class="quick-start">
            <h2>快速开始</h2>
            <el-steps :active="3" align-center>
              <el-step title="配置公众号" description="在个人中心配置您的公众号信息" />
              <el-step title="创建内容" description="使用AI助手创作或导入文章" />
              <el-step title="发布文章" description="一键发布到公众号草稿箱" />
            </el-steps>
          </div>

          <div class="help-section">
            <el-alert
              title="使用提示"
              type="info"
              :closable="false"
              show-icon
            >
              <template #default>
                <p>1. 首次使用需要先在"个人中心"配置公众号信息</p>
                <p>2. 使用"内容管理 - 每日助手"功能创建和优化文章</p>
                <p>3. 查看"发布记录"了解文章的发布状态</p>
              </template>
            </el-alert>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { getCurrentInstance, ref } from "vue"

const { proxy } = getCurrentInstance()
const version = ref("1.0.0")

function goTarget(url) {
  window.open(url, "_blank")
}
</script>

<style scoped lang="scss">
.home {
  font-family: "open sans", "Helvetica Neue", Helvetica, Arial, sans-serif;
  color: #676a6c;
  overflow-x: hidden;
  padding: 20px 0;

  .welcome-section {
    text-align: center;
    padding: 40px 20px;

    .welcome-title {
      font-size: 36px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 10px;
    }

    .welcome-subtitle {
      font-size: 18px;
      color: #909399;
      margin-bottom: 50px;
    }

    .feature-cards {
      margin-bottom: 50px;

      .feature-card {
        margin-bottom: 20px;
        transition: transform 0.3s;
        cursor: pointer;

        &:hover {
          transform: translateY(-5px);
        }

        .feature-icon {
          margin-bottom: 15px;
        }

        h3 {
          font-size: 20px;
          font-weight: 600;
          color: #303133;
          margin-bottom: 10px;
        }

        p {
          font-size: 14px;
          color: #606266;
          line-height: 1.6;
          margin: 0;
        }
      }
    }

    .quick-start {
      margin: 50px 0;

      h2 {
        font-size: 28px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 30px;
      }
    }

    .help-section {
      margin-top: 40px;
      text-align: left;

      p {
        margin: 8px 0;
        font-size: 14px;
        line-height: 1.8;
      }
    }
  }
}
</style>
