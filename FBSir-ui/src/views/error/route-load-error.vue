<template>
  <div class="app-container route-load-error">
    <el-result
      icon="error"
      title="页面暂时无法加载"
      :sub-title="`后台菜单指向的页面 ${missingView} 在当前版本中不存在，请联系管理员检查菜单配置。`"
    >
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="reload">重新加载</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup name="RouteLoadError">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const missingView = computed(() => route.meta?.loadErrorView || '未知组件')

function goHome() {
  router.replace('/index')
}

function reload() {
  window.location.reload()
}
</script>

<style scoped>
.route-load-error {
  display: flex;
  min-height: calc(100vh - 124px);
  align-items: center;
  justify-content: center;
}
</style>
