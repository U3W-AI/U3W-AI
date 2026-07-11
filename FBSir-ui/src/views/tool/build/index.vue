<template>
  <div class="app-container form-build-unavailable">
    <el-result
      icon="warning"
      title="表单构建器暂未开放"
      sub-title="当前版本尚未包含可视化表单设计器。如需生成业务页面，可先使用代码生成功能。"
    >
      <template #extra>
        <el-button type="primary" @click="goToGenerator">前往代码生成</el-button>
        <el-button @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup name="FormBuildUnavailable">
import { useRouter } from 'vue-router'

const router = useRouter()

function goToGenerator() {
  router.push('/tool/gen')
}

function goHome() {
  router.push('/index')
}
</script>

<style scoped>
.form-build-unavailable {
  display: flex;
  min-height: calc(100vh - 124px);
  align-items: center;
  justify-content: center;
}
</style>
