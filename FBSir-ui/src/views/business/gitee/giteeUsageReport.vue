<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="统计日期">
        <el-date-picker
          v-model="dateRange"
          value-format="YYYY-MM-DD"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :disabled-date="disableFutureDate"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Refresh" :loading="generateLoading" @click="handleGenerate">手动生成</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <div class="report-charts">
      <div ref="trendChartRef" class="chart-panel"></div>
      <div ref="scoreChartRef" class="chart-panel"></div>
    </div>

    <el-table v-loading="loading" :data="reportList">
      <el-table-column label="统计日期" align="center" prop="reportDate" width="120">
        <template #default="scope">
          <span>{{ parseTime(scope.row.reportDate, "{y}-{m}-{d}") }}</span>
        </template>
      </el-table-column>
      <el-table-column label="当日新增绑定" align="center" prop="newBindCount" />
      <el-table-column label="当日评测总次数" align="center" prop="dailyEvaluationCount" />
      <el-table-column label="活跃评测用户数" align="center" prop="dailyActiveUserCount" />
      <el-table-column label="累计绑定用户数" align="center" prop="totalBindCount" />
      <el-table-column label="评分区间分布" align="left">
        <template #default="scope">
          <div class="score-tags">
            <template v-if="getScoreTags(scope.row).length">
              <el-tag
                v-for="(item, index) in getScoreTags(scope.row)"
                :key="index"
                size="small"
                class="score-tag"
              >
                {{ item.scoreRange }}: {{ item.userCount }}
              </el-tag>
            </template>
            <span v-else>暂无</span>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script setup name="GiteeUsageReport">
import * as echarts from "echarts"
import { generateUsageReport, listUsageReport } from "@/api/business/gitee/report"
import { parseTime } from "@/utils/FBSir"

const { proxy } = getCurrentInstance()

const reportList = ref([])
const loading = ref(false)
const generateLoading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const dateRange = ref([])
const trendChartRef = ref(null)
const scoreChartRef = ref(null)
let trendChart = null
let scoreChart = null

const queryParams = ref({
  pageNum: 1,
  pageSize: 10
})

function getList() {
  loading.value = true
  listUsageReport(proxy.addDateRange({ ...queryParams.value }, dateRange.value)).then(response => {
    reportList.value = response.rows || []
    total.value = response.total || 0
    nextTick(() => {
      renderCharts()
    })
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleExport() {
  const params = proxy.addDateRange({ ...queryParams.value }, dateRange.value)
  proxy.download("business/gitee/admin/usage-report/export", params, `gitee_usage_report_${new Date().getTime()}.xlsx`)
}

function handleGenerate() {
  const reportDate = Array.isArray(dateRange.value) && dateRange.value.length ? dateRange.value[1] : undefined
  generateLoading.value = true
  generateUsageReport(reportDate).then(() => {
    proxy.$modal.msgSuccess("报表生成成功")
    getList()
  }).finally(() => {
    generateLoading.value = false
  })
}

function disableFutureDate(date) {
  const endOfToday = new Date()
  endOfToday.setHours(23, 59, 59, 999)
  return date.getTime() > endOfToday.getTime()
}

function getScoreTags(row) {
  if (!row?.scoreDistribution) {
    return []
  }
  try {
    const parsed = JSON.parse(row.scoreDistribution)
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    return []
  }
}

function renderCharts() {
  if (!trendChartRef.value || !scoreChartRef.value) {
    return
  }
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  if (!scoreChart) {
    scoreChart = echarts.init(scoreChartRef.value)
  }

  const list = [...reportList.value].slice(0, 14).reverse()
  const labels = list.map(item => parseTime(item.reportDate, "{m}-{d}") || "")
  const newBind = list.map(item => item.newBindCount || 0)
  const evalCount = list.map(item => item.dailyEvaluationCount || 0)
  const activeUsers = list.map(item => item.dailyActiveUserCount || 0)

  trendChart.setOption({
    title: { text: "近14天趋势", left: "center" },
    tooltip: { trigger: "axis" },
    legend: { data: ["新增绑定", "评测次数", "活跃用户"], bottom: 0 },
    grid: { left: 40, right: 20, top: 50, bottom: 40 },
    xAxis: { type: "category", data: labels },
    yAxis: { type: "value" },
    series: [
      { name: "新增绑定", type: "bar", data: newBind },
      { name: "评测次数", type: "line", data: evalCount, smooth: true },
      { name: "活跃用户", type: "line", data: activeUsers, smooth: true }
    ]
  })

  const latest = reportList.value[0]
  const scoreTags = getScoreTags(latest)
  scoreChart.setOption({
    title: { text: "最新评分分布", left: "center" },
    tooltip: { trigger: "item" },
    legend: { bottom: 0 },
    series: [
      {
        type: "pie",
        radius: ["35%", "65%"],
        label: { formatter: "{b}: {c}" },
        data: scoreTags.map(item => ({
          name: item.scoreRange,
          value: item.userCount || 0
        }))
      }
    ]
  })
}

function resizeCharts() {
  if (trendChart) {
    trendChart.resize()
  }
  if (scoreChart) {
    scoreChart.resize()
  }
}

onMounted(() => {
  window.addEventListener("resize", resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts)
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
  if (scoreChart) {
    scoreChart.dispose()
    scoreChart = null
  }
})

getList()
</script>

<style scoped>
.report-charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.chart-panel {
  width: 100%;
  height: 320px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.score-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
}

.score-tag {
  margin-right: 6px;
}
</style>
