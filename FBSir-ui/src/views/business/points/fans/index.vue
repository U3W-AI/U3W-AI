<template>
  <div class="points-fans-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="title">粉丝管理</div>
          <div class="actions">
            <el-button type="primary" size="small" @click="refreshData">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </div>
      </template>
      
      <div class="table-container">
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true">
          <el-form-item label="用户名" prop="userName">
            <el-input
              v-model="queryParams.userName"
              placeholder="请输入用户名"
              clearable
              @keyup.enter="handleQuery"
              style="width: 240px"
            />
          </el-form-item>
          <el-form-item label="手机号码" prop="phonenumber">
            <el-input
              v-model="queryParams.phonenumber"
              placeholder="请输入手机号码"
              clearable
              @keyup.enter="handleQuery"
              style="width: 240px"
            />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model="queryParams.email"
              placeholder="请输入邮箱"
              clearable
              @keyup.enter="handleQuery"
              style="width: 240px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
            <el-button icon="RefreshRight" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        
        <el-table
          v-loading="loading"
          :data="fansList"
          style="width: 100%"
          stripe
          border
        >
          <el-table-column type="index" label="序号" width="50" />
          <el-table-column prop="userName" label="用户名" min-width="120" />
          <el-table-column prop="nickName" label="昵称" min-width="120" />
          <el-table-column prop="phonenumber" label="手机号码" min-width="130" />
          <el-table-column prop="email" label="邮箱" min-width="150" />
          <el-table-column prop="points" label="当前积分" width="100">
            <template #default="scope">
              <span class="points-value">{{ scope.row.points || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80">
            <template #default="scope">
              <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'">
                {{ scope.row.status === '0' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="160" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="scope">
              <el-button
                type="info"
                size="small"
                @click="handleViewPointsRecord(scope.row)" v-hasPermi="['points:fans:detail']"
              >
                <el-icon><Document /></el-icon>积分明细
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <pagination
          v-show="total > 0"
          :total="total"
          :page.sync="queryParams.pageNum"
          :limit.sync="queryParams.pageSize"
          @pagination="handleQuery"
        />
      </div>
    </el-card>
    
    <!-- 积分明细对话框 -->
    <el-dialog
      v-model="pointsRecordVisible"
      title="积分明细"
      width="800px"
      center
    >
      <div class="points-record-header">
        <div>用户名: {{ selectedUser.userName }}</div>
        <div>当前积分: {{ selectedUser.points || 0 }}</div>
      </div>
      <el-table
        v-loading="recordLoading"
        :data="pointsRecordList"
        style="width: 100%"
        stripe
        border
      >
        <el-table-column prop="createTime" label="变动时间" min-width="160" />
        <el-table-column prop="ruleName" label="变动原因" min-width="150">
          <template #default="scope">
            {{ scope.row.ruleName || scope.row.ruleCode }}
          </template>
        </el-table-column>
        <el-table-column prop="changeAmount" label="变动积分" width="120">
          <template #default="scope">
            <span :class="scope.row.changeAmount > 0 ? 'positive' : 'negative'">
              {{ scope.row.changeAmount > 0 ? '+' : '' }}{{ scope.row.changeAmount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="变动后积分" width="120" />
        <el-table-column prop="remark" label="备注" min-width="200" />
      </el-table>
      
      <pagination
        v-show="recordTotal > 0"
        :total="recordTotal"
        :page.sync="recordQueryParams.pageNum"
        :limit.sync="recordQueryParams.pageSize"
        @pagination="handleQueryPointsRecord"
      />
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="pointsRecordVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { getPointsFansList, getPointsFansRecord } from '@/api/business/points'
import { Document, Refresh } from '@element-plus/icons-vue'
import Pagination from '@/components/Pagination'

export default {
  name: 'PointsFans',
  components: {
    Pagination,
    Document,
    Refresh
  },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 粉丝列表
      fansList: [],
      // 总条数
      total: 0,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userName: null,
        phonenumber: null,
        email: null
      },
      // 选中的用户
      selectedUser: {},
      // 积分明细对话框显示控制
      pointsRecordVisible: false,
      // 积分明细加载状态
      recordLoading: false,
      // 积分明细列表
      pointsRecordList: [],
      // 积分明细总条数
      recordTotal: 0,
      // 积分明细查询参数
      recordQueryParams: {
        pageNum: 1,
        pageSize: 10,
        userId: null
      }
    }
  },
  created() {
    this.handleQuery()
  },
  methods: {
    // 查询粉丝列表
    handleQuery() {
      this.loading = true
      getPointsFansList(this.queryParams).then(response => {
        this.fansList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    // 重置查询
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams.pageNum = 1
      this.handleQuery()
    },
    // 刷新数据
    refreshData() {
      this.handleQuery()
    },
    // 查看积分明细
    handleViewPointsRecord(row) {
      this.selectedUser = row
      this.recordQueryParams.userId = row.userId
      this.recordQueryParams.pageNum = 1
      this.handleQueryPointsRecord()
      this.pointsRecordVisible = true
    },
    // 查询积分明细
    handleQueryPointsRecord() {
      this.recordLoading = true
      getPointsFansRecord(this.recordQueryParams).then(response => {
        this.pointsRecordList = response.rows
        this.recordTotal = response.total
        this.recordLoading = false
      })
    }
  }
}
</script>

<style scoped>
.points-fans-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: bold;
}

.table-container {
  margin-top: 20px;
}

.points-value {
  color: #409eff;
  font-weight: bold;
}

.user-info {
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.points-record-header {
  margin-bottom: 20px;
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.positive {
  color: #67c23a;
}

.negative {
  color: #f56c6c;
}
</style>
