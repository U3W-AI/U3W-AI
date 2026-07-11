<template>
  <div class="druid-monitor">
    <div v-if="checking" class="druid-monitor__checking">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <p>正在检查数据监控服务…</p>
    </div>

    <template v-else-if="available || forceOpen">
      <el-alert
        v-if="forceOpen && !available"
        title="未能确认 Druid 服务状态，已按您的选择尝试直接打开。"
        type="warning"
        show-icon
        :closable="false"
      />
      <i-frame v-model:src="url" />
    </template>

    <el-result
      v-else
      icon="info"
      title="数据监控未启用"
      sub-title="当前后端未提供 Druid 监控页面，或该地址暂时不可达。请由管理员确认 Druid 配置和反向代理。"
    >
      <template #extra>
        <el-button type="primary" @click="checkAvailability">重新检查</el-button>
        <el-button @click="forceOpen = true">仍要尝试打开</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup name="DruidMonitor">
import { onMounted, ref } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import iFrame from '@/components/iFrame'

const url = ref(import.meta.env.VITE_APP_BASE_API + '/druid/login.html')
const checking = ref(true)
const available = ref(false)
const forceOpen = ref(false)

async function checkAvailability() {
  checking.value = true
  available.value = false
  forceOpen.value = false
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), 8000)
  try {
    const response = await fetch(url.value, {
      method: 'GET',
      credentials: 'same-origin',
      signal: controller.signal
    })
    const contentType = (response.headers.get('content-type') || '').toLowerCase()
    const body = await response.text()
    available.value = response.ok && contentType.includes('text/html') && /druid/i.test(body)
  } catch {
    available.value = false
  } finally {
    window.clearTimeout(timeoutId)
    checking.value = false
  }
}

onMounted(checkAvailability)
</script>

<style scoped>
.druid-monitor {
  min-height: calc(100vh - 84px);
}

.druid-monitor__checking {
  display: flex;
  min-height: calc(100vh - 124px);
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
}

.druid-monitor__checking p {
  margin-top: 12px;
}
</style>
