<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px" class="search-form">
      <el-form-item label="证书类型">
        <el-select v-model="queryParams.certificateType" placeholder="请选择证书类型" clearable style="width: 120px;" @change="handleQuery">
          <el-option label="职业类" value="职业类" />
          <el-option label="学历类" value="学历类" />
          <el-option label="企业类" value="企业类" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>
      <el-form-item label="证书名称">
        <el-input v-model="queryParams.templateName" placeholder="请输入证书名称" clearable style="width: 120px;" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="审核状态">
        <el-select v-model="queryParams.applicationStatus" placeholder="请选择审核状态" clearable style="width: 120px;" @change="handleQuery">
          <el-option label="已提交" value="1" />
          <el-option label="已通过" value="2" />
          <el-option label="已驳回" value="3" />
          <el-option label="已发放" value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户名">
        <el-input v-model="queryParams.applicantName" placeholder="请输入用户名" clearable style="width: 120px;" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="申请日期">
        <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px;"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
      </el-form-item>
      <el-form-item>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 删除了刷新权限、搜索和重置按钮 -->

    <el-table v-loading="loading" :data="applicationReviewList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="申请编号" align="center" prop="applicationCode" />
      <el-table-column label="证书名称" align="center" prop="templateName" />
      <el-table-column label="证书类型" align="center" prop="certificateType" />
      <el-table-column label="姓名" align="center" prop="realName">
        <template #default="scope">
          {{ getRealName(scope.row.applicationData) }}
        </template>
      </el-table-column>
      <el-table-column label="用户名" align="center" prop="applicantName" />
      <el-table-column label="审核状态" align="center" prop="applicationStatus">
        <template #default="scope">
          <el-tag
              v-if="scope.row.applicationStatus === '1' || scope.row.applicationStatus === 'SUBMITTED'"
              type="info"
              effect="plain"
          >已提交</el-tag>
          <el-tag
              v-else-if="scope.row.applicationStatus === '2' || scope.row.applicationStatus === 'APPROVED'"
              type="success"
              effect="plain"
          >已通过</el-tag>
          <el-tag
              v-else-if="scope.row.applicationStatus === '3' || scope.row.applicationStatus === 'REJECTED'"
              type="danger"
              effect="plain"
          >已驳回</el-tag>
          <el-tag
              v-else-if="scope.row.applicationStatus === '4' || scope.row.applicationStatus === 'ISSUED'"
              type="primary"
              effect="plain"
          >已发放</el-tag>
          <el-tag
              v-else
              type="warning"
              effect="plain"
          >{{ getStatusLabel(scope.row.applicationStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请日期" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d} {h}:{i}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
              size="small"
              link
              icon="View"
              @click="handleView(scope.row)"
              v-hasPermi="['business:certificate:review:query']"
          >详情</el-button>
          <el-button
              size="small"
              link
              icon="Edit"
              @click="handleUpdate(scope.row)"
              v-if="scope.row.applicationStatus === 'SUBMITTED' || scope.row.applicationStatus === '1'"
              v-hasPermi="['business:certificate:review:edit']"
          >通过</el-button>
          <el-button
              size="small"
              link
              icon="Close"
              @click="handleReject(scope.row)"
              v-if="scope.row.applicationStatus === 'SUBMITTED' || scope.row.applicationStatus === '1'"
              v-hasPermi="['business:certificate:review:edit']"
          >驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
        v-show="total>0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
    />

    <!-- 审核详情弹窗 -->
    <el-dialog title="申请审核详情" v-model="open" width="700px" append-to-body>
      <el-form ref="form" :model="form" label-width="100px">
        <h4>申请内容</h4>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="证书名称：">{{ form.templateName }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户名：">{{ form.applicantName }}</el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系电话：">{{ form.phone }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请日期：">{{ parseTime(form.createTime, '{y}-{m}-{d} {h}:{i}') }}</el-form-item>
          </el-col>
        </el-row>

        <!-- 显示申请时填写的动态字段（来自applicationData），排除materials字段）-->
        <el-form-item label="申请内容：" v-if="form.applicationData">
          <div class="application-content-grid">
            <template v-for="(item, index) in Object.entries(parsedApplicationData)" :key="index">
              <div v-if="item[0].toLowerCase() !== 'materials'" class="content-item">
                <span class="content-label">{{ item[0] }}：</span>
                <span class="content-value">{{ formatFieldValue(item[0], item[1]) || '无' }}</span>
              </div>
            </template>
          </div>
        </el-form-item>

        <!-- 显示上传材料（来自applicationMaterials）-->
        <el-form-item label="上传材料：" v-if="applicationMaterialsExist">
          <div v-if="isJsonString(form.applicationMaterials)">
            <div v-for="(fileName, materialName) in JSON.parse(form.applicationMaterials)" :key="materialName" style="margin-bottom: 10px;">
              <!-- 根据文件上传服务说明，上传接口返回完整URL，直接使用fileName即可 -->
              <el-link type="primary" :href="fileName" target="_blank">{{ materialName }}: {{ fileName }}</el-link>
            </div>
          </div>
          <div v-else-if="typeof form.applicationMaterials === 'string' && form.applicationMaterials.trim() !== ''">{{ form.applicationMaterials }}</div>
          <div v-else>暂无上传材料</div>
        </el-form-item>



        <el-form-item label="补充说明：">
          <div>{{ form.remark || '无' }}</div>
        </el-form-item>

        <!-- 审核意见显示区域 - 仅在非已提交状态下显示 -->
        <el-form-item label="审核意见：" v-if="form.applicationStatus !== 'SUBMITTED' && form.applicationStatus !== '1'">
          <div>{{ form.reviewRemark || form.reviewOpinion || '无' }}</div>
        </el-form-item>

        <!-- 审核意见输入框 - 仅在已提交状态下显示 -->
        <el-form-item label="审核意见：" v-if="form.applicationStatus === 'SUBMITTED' || form.applicationStatus === '1'">
          <el-input v-model="form.reviewComment" type="textarea" placeholder="请输入审核意见（驳回时必填，最多200字）" :rows="4" :maxlength="200" show-word-limit />
        </el-form-item>

        <!-- 积分不足提示区域 -->
        <el-form-item v-if="showInsufficientPointsMsg && (form.applicationStatus === 'SUBMITTED' || form.applicationStatus === '1')">
          <el-alert
              :title="insufficientPointsMessage"
              type="warning"
              :closable="false"
              show-icon>
            <template #default>
              <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                <span>{{ insufficientPointsMessage }}</span>
                <el-button size="small" type="primary" @click="openPointsGetDialog">去获取积分</el-button>
              </div>
            </template>
          </el-alert>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">关 闭</el-button>
          <el-button type="primary" @click="handleApprove" v-if="form.applicationStatus === 'SUBMITTED' || form.applicationStatus === '1'">确认通过</el-button>
          <el-button type="danger" @click="handleRejectConfirm" v-if="form.applicationStatus === 'SUBMITTED' || form.applicationStatus === '1'">确认驳回</el-button>
        </div>
      </template>

    </el-dialog>

    <!-- 积分获取弹窗 -->
    <PointsGetDialog
        v-model:visible="showPointsGetDialog"
        :required-points="issueCertificatePoints"
        :operation-type="'approveApplication'"
        :application-id="form.applicationId"
        @close="closePointsGetDialog"
        @points-changed="handlePointsChanged" />
  </div>
</template>

<style scoped>
.search-form {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  flex-wrap: nowrap;
  :deep(.el-form-item) {
    margin-right: 2px; /* 减小右边距 */
    margin-left: 2px; /* 减小左边距 */
    margin-bottom: 0;
    flex-shrink: 0;
    min-width: 100px; /* 减小最小宽度 */
  }
  :deep(.el-form-item__label) {
    font-size: 14px; /* 增大字号 */
    padding-right: 2px;
  }
  :deep(.el-input--small) {
    height: 30px;
    :deep(input) {
      padding: 0 2px; /* 减小内边距 */
      font-size: 14px; /* 增大字号 */
    }
  }
  :deep(.el-select--small) {
    height: 30px;
    :deep(input) {
      font-size: 14px; /* 增大字号 */
      padding: 0 2px; /* 减小内边距 */
    }
  }
  :deep(.el-date-editor--small) {
    height: 30px;
    :deep(input) {
      font-size: 14px; /* 增大字号 */
      padding: 0 2px; /* 减小内边距 */
    }
  }
  :deep(.el-button--small) {
    padding: 5px 8px;
    font-size: 12px;
    min-width: 50px;
    height: 30px;
  }
  :deep(.el-input__wrapper) {
    height: 30px;
    padding: 1px 2px !important; /* 减小内边距 */
  }
  :deep(.el-input--small .el-input__wrapper) {
    padding: 1px 2px !important; /* 减小内边距 */
  }
  :deep(.el-select .el-input__wrapper) {
    padding: 1px 2px !important; /* 减小内边距 */
  }
}

.application-content-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 20px;
  margin-top: 10px;
}

.content-item {
  display: flex;
  align-items: center;
}

.content-label {
  font-weight: bold;
  min-width: 80px;
}

.content-value {
  color: #666;
}
</style>

<script>
import { listApplicationReview, getApplicationReview, getApplicationByApplicationId, updateApplicationReview, reviewApplication } from "@/api/business/certificate/applicationReview.js";
import { getPointsRuleByCode } from "@/api/system/points.js";  // 已有导入
import { getUserPoints } from "@/api/business/points";  // 修改导入路径
import useUserStore from "@/store/modules/user.js";
import PointsGetDialog from '@/components/PointsGetDialog/index.vue';

export default {
  name: "ApplicationReview",
  dicts: ['certificate_application_status'],
  components: {
    PointsGetDialog
  },
  data() {
    return {
      // 遌溉层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 申请审核表格数据
      applicationReviewList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 日期范围
      dateRange: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        applicantName: null,
        applicationStatus: null,
        certificateType: null,
        templateName: null
      },
      // 表单参数
      form: {},
      // 基础URL
      baseUrl: import.meta.env.VITE_APP_BASE_API,
      // 发放证书所需积分
      issueCertificatePoints: 0,  // 新增：发放证书所需积分
      // 用户积分
      userPoints: 0,  // 新增：用户当前积分
      // 积分不足提示相关
      showInsufficientPointsMsg: false,  // 是否显示积分不足提示
      insufficientPointsMessage: '',  // 积分不足提示消息
      // 积分获取弹窗相关
      showPointsGetDialog: false  // 是否显示积分获取弹窗
    };
  },
  created() {
    this.getList();
    // 检查是否从积分获取页面返回
    this.checkReturnFromPointsPage();
  },

  computed: {
    parsedApplicationData() {
      if (this.form.applicationData) {
        try {
          return JSON.parse(this.form.applicationData);
        } catch (e) {
          console.error('解析申请数据失败:', e);
          return {};
        }
      }
      return {};
    },
    parsedApplicationContent() {
      if (this.form.applicationContent) {
        try {
          return JSON.parse(this.form.applicationContent);
        } catch (e) {
          console.error('解析申请内容失败:', e);
          return {};
        }
      }
      return {};
    },
    applicationMaterialsExist() {
      return this.form.applicationMaterials &&
          ((typeof this.form.applicationMaterials === 'string' && this.form.applicationMaterials.trim() !== '') ||
              (typeof this.form.applicationMaterials === 'object' && Object.keys(this.form.applicationMaterials).length > 0));
    },

  },

  watch: {
    // 监听用户积分变化
    userPoints: {
      handler(newVal) {
        // 如果是从积分获取页面返回且积分已增加，询问是否继续操作
        if (this.$route.query.from === 'approveApplication' &&
            this.$route.query.applicationId &&
            newVal >= this.issueCertificatePoints) {
          this.continueOperation();
        }
      },
      immediate: true
    }
  },

  methods: {
    formatFieldValue(key, value) {
      // 处理可能包含特殊字符的有效期字段
      const cleanedKey = key.replace(/[\u0000-\u001F\u007F-\u009F]/g, '');
      if (cleanedKey.includes('有效期')) {
        // 如果有效期字段为空字符串或只包含空白字符，显示"长期有效"
        if (!value || (typeof value === 'string' && value.trim() === '')) {
          return '长期有效';
        }
        // 如果是ISO时间格式，转换为指定格式
        if (typeof value === 'string' && value.includes('T') && value.includes('Z')) {
          const date = new Date(value);
          const year = date.getFullYear();
          const month = String(date.getMonth() + 1).padStart(2, '0');
          const day = String(date.getDate()).padStart(2, '0');
          return `${year}-${month}-${day}`;
        }
        return value;
      }
      return value;
    },
    isJsonString(str) {
      try {
        JSON.parse(str);
        return true;
      } catch (e) {
        return false;
      }
    },
    /** 查询申请审核列表 */
    getList() {
      this.loading = true;

      // 为了能够筛选证书类型，先获取全部数据
      const params = {
        pageNum: 1,
        pageSize: 9999, // 获取所有数据用于前端筛选
        applicantName: this.queryParams.applicantName,
        applicationStatus: this.queryParams.applicationStatus,
        templateName: this.queryParams.templateName,
      };

      if (this.dateRange && this.dateRange.length === 2) {
        params.beginTime = this.dateRange[0];
        params.endTime = this.dateRange[1];
      }

      listApplicationReview(params).then(response => {
        let allData = response.rows;
        this.total = response.total;

        // 对于证书类型筛选
        if (this.queryParams.certificateType) {
          allData = allData.filter(item => item.certificateType === this.queryParams.certificateType);
        }

        // 实现分页
        const startIndex = (this.queryParams.pageNum - 1) * this.queryParams.pageSize;
        const endIndex = startIndex * 1 + this.queryParams.pageSize;
        this.applicationReviewList = allData.slice(startIndex, endIndex);

        this.loading = false;
      });
    },
    // 添加刷新权限的方法
    refreshPermissions() {
      // 调用store中的refreshPermission方法来重新获取用户权限
      useUserStore().refreshPermission().then(() => {
        this.$modal.msgSuccess("权限已刷新，请刷新页面查看效果");
      }).catch(error => {
        this.$modal.msgError("权限刷新失败: " + error.message);
      });
    },

    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        applicationId: null,
        applicantName: null,
        templateId: null,
        applicationData: null,
        applicationStatus: null,
        reviewTime: null,
        reviewComment: null
      };
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.dateRange = [];
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.applicationId)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 修改按钮操作（通过审核）*/
    handleUpdate(row) {
      if (!row.applicationId) {
        this.$modal.msgError("申请ID不能为空");
        return;
      }
      this.reset();
      getApplicationByApplicationId(row.applicationId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "审核认证申请";
      });
    },
    /** 驳回按钮操作 */
    handleReject(row) {
      if (!row.applicationId) {
        this.$modal.msgError("申请ID不能为空");
        return;
      }
      this.reset();
      getApplicationByApplicationId(row.applicationId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "驳回认证申请";
      });
    },
    /** 查看按钮操作 */
    handleView(row) {
      if (!row.applicationId) {
        this.$modal.msgError("申请ID不能为空");
        return;
      }
      this.reset();
      getApplicationByApplicationId(row.applicationId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "查看认证申请";
      });
    },
    /** 检查是否从积分获取页面返回 */
    checkReturnFromPointsPage() {
      // 如果是从积分获取页面返回，检查积分是否足够
      if (this.$route.query.from === 'approveApplication' &&
          this.$route.query.applicationId) {
        // 清除路由参数，避免重复提示
        const newQuery = { ...this.$route.query };
        delete newQuery.from;
        delete newQuery.applicationId;

        if (JSON.stringify(newQuery) !== JSON.stringify({})) {
          this.$router.replace({ query: newQuery });
        }

        // 检查积分是否已足够执行操作
        if (this.userPoints >= this.issueCertificatePoints) {
          this.continueOperation();
        }
      }
    },

    /** 继续执行原操作 */
    continueOperation() {
      // 询问用户是否继续执行原操作
      this.$confirm(`您已获取积分，当前积分为 ${this.userPoints} 分，是否继续审批操作？`, "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        // 执行审批操作
        const applicationId = this.$route.query.applicationId;
        if (applicationId) {
          // 获取完整的申请数据并执行审批
          getApplicationByApplicationId(applicationId).then(response => {
            this.form = response.data;
            this.executeApprove();
          }).catch(error => {
            this.$modal.msgError("获取申请信息失败: " + error.message);
          });
        }
      }).catch(() => {
        // 用户取消操作
        console.log('用户取消继续操作');
      });
    },

    /** 通过审核 */
    handleApprove() {
      // 检查积分是否足够
      if (this.issueCertificatePoints > 0 && this.userPoints < this.issueCertificatePoints) {
        // 积分不足，显示提示信息而不是弹窗
        this.showInsufficientPointsMsg = true;
        this.insufficientPointsMessage = `审核通过将发放证书，此操作需要消耗 ${this.issueCertificatePoints} 积分，您的当前积分为 ${this.userPoints} 分，积分不足！`;
        return;
      }

      // 积分充足，显示确认对话框
      if (this.issueCertificatePoints > 0) {
        this.$confirm(`确认要通过审核吗？此操作将发放证书并消耗 ${this.issueCertificatePoints} 积分，您的当前积分为 ${this.userPoints} 分。`, "提示", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          this.executeApprove();
        }).catch(() => {
          // 用户取消操作
        });
      } else {
        // 如果没有积分规则，直接执行审核
        this.executeApprove();
      }
    },

    /** 打开获取积分弹窗 */
    openPointsGetDialog() {
      this.showPointsGetDialog = true;
    },

    /** 关闭获取积分弹窗 */
    closePointsGetDialog() {
      this.showPointsGetDialog = false;
    },

    /** 积分发生变化时的回调 */
    handlePointsChanged(newPoints) {
      this.userPoints = newPoints;

      // 如果积分已足够，询问是否继续执行原操作
      if (newPoints >= this.issueCertificatePoints) {
        this.$confirm(`您已获取积分，当前积分为 ${newPoints} 分，是否继续审批操作？`, "提示", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          // 关闭积分获取弹窗
          this.closePointsGetDialog();
          // 执行审批操作
          this.executeApprove();
        }).catch(() => {
          // 用户取消操作，但不关闭积分获取弹窗
          console.log('用户取消继续操作');
        });
      }
    },

    /** 执行审核操作 */
    executeApprove() {
      // 将状态设置为 APPROVED (数字2)
      this.form.applicationStatus = '2';
      this.form.reviewTime = new Date().toISOString().slice(0, 19).replace('T', ' ');

      // 将审核意见同步到reviewOpinion字段
      this.form.reviewOpinion = this.form.reviewComment;

      reviewApplication(this.form).then(response => {
        this.$modal.msgSuccess("审核通过成功");
        this.open = false;
        this.getList();
      });
    },
    /** 确认驳回 */
    handleRejectConfirm() {
      if (!this.form.reviewComment) {
        this.$alert('请填写驳回原因', '提示', {
          confirmButtonText: '确定',
        });
        return;
      }

      // 将状态设置为 REJECTED (数字3)
      this.form.applicationStatus = '3';
      this.form.reviewTime = new Date().toISOString().slice(0, 19).replace('T', ' ');

      // 将审核意见同步到reviewOpinion字段
      this.form.reviewOpinion = this.form.reviewComment;

      reviewApplication(this.form).then(response => {
        this.$modal.msgSuccess("驳回成功");
        this.open = false;
        this.getList();
      });
    },

    // 获取状态标签
    getStatusLabel(status) {
      switch(status) {
        case 'SUBMITTED':
        case '1':
          return '已提交';
        case 'APPROVED':
        case '2':
          return '已通过';
        case 'REJECTED':
        case '3':
          return '已驳回';
        case 'ISSUED':
        case '4':
          return '已发放';
        case 'UNDER_REVIEW':
          return '审核中';
        case 'DRAFT':
          return '草稿';
        default:
          return status;
      }
    },

    // 获取真实姓名
    getRealName(applicationData) {
      if (!applicationData) return '';
      try {
        const data = typeof applicationData === 'string' ? JSON.parse(applicationData) : applicationData;
        // 优先查找姓名字段，如果没有则查找其他可能的姓名相关字段
        return data.姓名 || data.name || data.realName || data.userName || '';
      } catch (e) {
        console.error('解析申请数据失败:', e);
        return '';
      }
    }
  }
};
</script>
