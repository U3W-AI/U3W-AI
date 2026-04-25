<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="关键字" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="名称/编码搜索"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 场景包卡片列表 -->
    <div v-loading="loading" class="pack-grid">
      <el-empty v-if="packList.length === 0 && !loading" description="暂无可领取的场景包" :image-size="80" />
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="pack in packList" :key="pack.id">
          <el-card shadow="hover" class="pack-card" :class="{ 'pack-card--claimed': pack.claimed }">
            <div class="pack-info">
              <div class="pack-name" :title="pack.packName">{{ pack.packName }}</div>
              <div class="pack-code">
                <el-tag size="small" type="info">{{ pack.packCode }}</el-tag>
              </div>
              <div class="pack-desc" v-if="pack.description">{{ pack.description }}</div>
              <div class="pack-meta">
                <span v-if="pack.currentVersion">v{{ pack.currentVersion }}</span>
                <span class="free-badge">免费</span>
              </div>
            </div>
            <div class="pack-action">
              <el-button
                v-if="!pack.claimed"
                type="primary"
                icon="Download"
                size="small"
                @click="handleClaim(pack)"
                :loading="claimingId === pack.id"
              >领取</el-button>
              <el-tag v-else type="success" effect="plain" size="large" class="claimed-tag">
                <el-icon><Check /></el-icon> 已领取
              </el-tag>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 分页 -->
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 领取结果弹窗 -->
    <el-dialog title="领取结果" v-model="resultOpen" width="450px" append-to-body>
      <el-result
        :icon="claimSuccess ? 'success' : 'error'"
        :title="claimSuccess ? '领取成功' : claimMessage"
      >
        <template #extra v-if="claimSuccess && claimResult">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="场景包">{{ claimResult.packName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="到期时间">{{ parseTime(claimResult.expiresAt) || '永久' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-result>
      <template #footer>
        <el-button type="primary" @click="resultOpen = false">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="FbsClaimablePacks">
import { ref, reactive, onMounted } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { listClaimablePacks, claimScenePack } from "@/api/business/fbs/mySelfService"
import { parseTime } from '@/utils/WxFbsir'

const { proxy } = getCurrentInstance()

const packList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const claimingId = ref(null)
const claimSuccess = ref(false)
const claimMessage = ref('')
const claimResult = ref(null)
const resultOpen = ref(false)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 12,
    keyword: undefined
  }
})

const { queryParams } = toRefs(data)

/** 获取列表 */
function getList() {
  loading.value = true
  listClaimablePacks(queryParams.value).then(response => {
    packList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 领取场景包 */
function handleClaim(pack) {
  claimingId.value = pack.id
  claimScenePack({ packId: pack.id }).then(response => {
    claimSuccess.value = true
    claimResult.value = response.data || {}
    claimMessage.value = ''
    resultOpen.value = true
    // 更新该卡片的 claimed 状态（不再移除）
    pack.claimed = true
    proxy.$modal.msgSuccess("领取成功")
  }).catch(error => {
    const msg = (error && error.message) || '领取失败'
    claimSuccess.value = false
    claimMessage.value = msg
    claimResult.value = null
    resultOpen.value = true
  }).finally(() => {
    claimingId.value = null
  })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.app-container {
  .mb8 {
    margin-bottom: 8px;
  }

  .pack-grid {
    min-height: 200px;
  }

  .pack-card {
    margin-bottom: 16px;
    transition: transform 0.2s;

    &.pack-card--claimed {
      opacity: 0.7;
      background: #f5f7fa;
    }

    &:hover {
      transform: translateY(-2px);
    }

    .pack-info {
      margin-bottom: 12px;

      .pack-name {
        font-size: 15px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 8px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .pack-code {
        margin-bottom: 8px;
      }

      .pack-desc {
        font-size: 12px;
        color: #909399;
        line-height: 1.5;
        margin-bottom: 8px;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .pack-meta {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 12px;
        color: #909399;

        .free-badge {
          color: #67c23a;
          font-weight: 600;
        }
      }
    }

    .pack-action {
      text-align: right;
      border-top: 1px solid #ebeef5;
      padding-top: 12px;

      .claimed-tag {
        font-size: 14px;
        padding: 8px 16px;
      }
    }
  }
}
</style>
