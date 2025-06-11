<template>
  <div class="app-container">
    <el-row :gutter="24">
      <!-- 左侧输入区域 -->
      <el-col :span="24">
        <div class="input-container">
          <el-input
            v-model="editForm.inputContent"
            placeholder="请输入内容"
            type="textarea"
            class="cont"
            :autosize="{ minRows: 30, maxRows: 30 }"
          ></el-input>
          <el-button  @click="generatePreview">生成预览</el-button>
          <el-input
            v-model="resContent"
            placeholder="预览内容"
            class="cont"
            type="textarea"
            :autosize="{ minRows: 30, maxRows: 30 }"
            readonly
          ></el-input>
          <el-button class="res" type="primary" @click="copyContent">一键复制</el-button>
          <el-button class="res" type="primary" @click="downloadFile">一键下载</el-button>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { textFilter } from "@/api/wechat/comment";

export default {
  name: "User",
  data() {
    return {
      editForm: {
        inputContent: "",
      },
      resContent: "",
    };
  },
  methods: {
    generatePreview() {
      textFilter(this.editForm).then(response => {
        this.resContent = response.data;
        this.$modal.msgSuccess("设置成功");
      });
    },
    copyContent() {
      const textarea = document.createElement("textarea");
      textarea.value = this.resContent;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
      this.$modal.msgSuccess("复制成功");
    },
    downloadFile() {
      const blob = new Blob([this.resContent], { type: "text/plain;charset=utf-8" });
      const link = document.createElement("a");
      const url = URL.createObjectURL(blob);
      link.href = url;
      link.download = "preview.txt";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      this.$modal.msgSuccess("下载成功");
    },
  },
};
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.input-container{
  display: flex;
  align-items: center;
}
.cont{
  padding: 20px;
}
.res{
  display: flex;
  align-items: center;
}
</style>
