<template>
  <div class="app-container" v-loading="loading">
    <el-row :gutter="20">
      <!-- 左侧输入区域 -->
      <el-col :span="16">
        <div class="input-container">
          <el-input
            v-model="userPro.userPrompt"
            placeholder="请输入内容"
            class="large-input"
            size="large"
          ></el-input>
          <el-button type="primary" @click="generatePreview">生成</el-button>
        </div>

        <el-form ref="form" :model="form" label-width="80px">
          <el-form-item label="攻略标题" prop="strategyTitle">
            <el-input v-model="form.strategyTitle" placeholder="请输入攻略标题" />
          </el-form-item>
          <el-form-item label="内容">
            <editor v-model="form.strategyContent" :min-height="192"/>
          </el-form-item>
          <el-form-item label="攻略概述" prop="desc">
            <el-input v-model="form.desc" placeholder="请输入攻略概述，建议100字以内" />
          </el-form-item>
          <el-form-item label="作者" prop="author">
            <el-input v-model="form.author" placeholder="请输入作者" />
          </el-form-item>
          <el-form-item v-if="false" label="浏览id" prop="viewId">
            <el-input v-model="form.viewId" placeholder="请输入浏览id" />
          </el-form-item>
          <el-form-item v-if="false" label="点赞id" prop="likeId">
            <el-input v-model="form.likeId" placeholder="请输入点赞id" />
          </el-form-item>
          <el-form-item v-if="false" label="收藏id" prop="collectionsId">
            <el-input v-model="form.collectionsId" placeholder="请输入收藏id" />
          </el-form-item>
          <el-form-item label="攻略图" prop="picUrl">
            <image-upload v-model="form.picUrl"/>
          </el-form-item>
          <el-form-item v-if="false" label="评论id" prop="commentId">
            <el-input v-model="form.commentId" placeholder="请输入评论id" />
          </el-form-item>
          <el-form-item label="标签" prop="tag">
            <el-input v-model="form.tag" placeholder="请输入标签,多个标签用英文逗号隔开" />
          </el-form-item>
        </el-form>
          <el-button type="primary" @click="submitForm">发 布</el-button>
          <el-button @click="cancel">取 消</el-button>
      </el-col>

      <!-- 右侧预览区域 -->
<!--      <el-col :span="8">-->
<!--        <div class="phone-preview">-->
<!--          <div class="phone-header">小程序预览</div>-->
<!--          <div class="phone-screen">-->
<!--            <div class="preview-content">-->
<!--              <p>{{ inputContent || "这里显示生成的内容..." }}</p>-->
<!--            </div>-->
<!--          </div>-->
<!--        </div>-->
<!--      </el-col>-->
    </el-row>
  </div>
</template>

<script>
import {genStrategy, updateStrategy} from "@/api/wechat/strategy";

export default {
  name: "User",
  data() {
    return {
      loading: false,
      form: {},
      userPro:{
        userPrompt: "", // 输入框内容
      },

    };
  },
  methods: {
    generatePreview() {
      this.loading = true;
      genStrategy(this.userPro).then(response => {
        this.form=response.data
        this.loading = false;
      });
    },
  },
};
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.input-container {
  display: flex;
  align-items: center;
}

.large-input {
  width: 100%;
  max-width: 400px;
  margin-right: 10px;
}

.phone-preview {
  position: relative;
  width: 280px;
  height: 500px;
  border: 16px solid #333;
  border-radius: 36px;
  padding: 20px;
  background-color: #f0f0f0;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.phone-header {
  font-weight: bold;
  text-align: center;
  margin-bottom: 10px;
  color: #666;
}

.phone-screen {
  width: 100%;
  height: 100%;
  background: white;
  border-radius: 24px;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-content {
  text-align: center;
  font-size: 16px;
  color: #333;
  word-wrap: break-word;
}
</style>
