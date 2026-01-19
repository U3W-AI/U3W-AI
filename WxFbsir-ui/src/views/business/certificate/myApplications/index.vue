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



    <el-table v-loading="loading" :data="myApplicationList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="申请编号" align="center" prop="applicationCode" />
      <el-table-column label="证书名称" align="center" prop="templateName" />
      <el-table-column label="证书类型" align="center" prop="certificateType" />
      <el-table-column label="姓名" align="center" prop="realName">
        <template #default="scope">
          {{ getRealName(scope.row.applicationData) }}
        </template>
      </el-table-column>
      <el-table-column label="审核状态" align="center" prop="applicationStatus">
        <template #default="scope">
          <el-tag
              v-if="scope.row.applicationStatus === '1'"
              type="info"
              effect="plain"
          >已提交</el-tag>
          <el-tag
              v-else-if="scope.row.applicationStatus === '2'"
              type="success"
              effect="plain"
          >已通过</el-tag>
          <el-tag
              v-else-if="scope.row.applicationStatus === '3'"
              type="danger"
              effect="plain"
          >已驳回</el-tag>
          <el-tag
              v-else-if="scope.row.applicationStatus === '4'"
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
      <el-table-column label="通过日期" align="center" prop="approveTime" width="180">
        <template #default="scope">
          <span>{{ scope.row.approveTime ? parseTime(scope.row.approveTime, '{y}-{m}-{d} {h}:{i}') : '——' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="领取状态" align="center" prop="receiveStatus">
        <template #default="scope">
          <el-tag
              v-if="scope.row.receiveStatus === 'received'"
              type="success"
          >已领取</el-tag>
          <el-tag
              v-else-if="scope.row.receiveStatus === 'not_received'"
              type="info"
          >未领取</el-tag>
          <el-tag
              v-else
              type="info"
          >——</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
              size="small"
              type="text"
              icon="View"
              @click="handleView(scope.row)"
              v-hasPermi="['business:certificate:review:query']"
          >详情</el-button>
          <el-button
              size="small"
              type="text"
              icon="Edit"
              @click="handlePreviewCertificate(scope.row)"
              v-if="isApprovedStatus(scope.row.applicationStatus) || isIssuedStatus(scope.row.applicationStatus)"
              v-hasPermi="['business:certificate:review:edit']"
          >预览证书</el-button>
          <el-button
              size="small"
              type="text"
              icon="Delete"
              @click="handleCancel(scope.row)"
              v-if="scope.row.applicationStatus === '1'"
              v-hasPermi="['business:certificate:application:remove']"
          >撤销</el-button>
          <el-button
              size="small"
              type="text"
              icon="Download"
              @click="handleReceive(scope.row)"
              v-if="(isApprovedStatus(scope.row.applicationStatus) || isIssuedStatus(scope.row.applicationStatus)) && scope.row.receiveStatus !== 'received'"
          >领取</el-button>
          <el-button
              size="small"
              type="text"
              icon="Download"
              @click="handleDownload(scope.row)"
              v-if="isIssuedStatus(scope.row.applicationStatus) && scope.row.receiveStatus === 'received'"
              v-hasPermi="['business:certificate:application:download']"
          >下载</el-button>
          <el-button
              size="small"
              type="text"
              @click="handleReapply(scope.row)"
              v-if="isRejectedStatus(scope.row.applicationStatus)"
              v-hasPermi="['business:certificate:application:add']"
          >重新申请</el-button>
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

    <!-- 申请详情弹窗 -->
    <el-dialog :title="title" v-model="open" width="70%" append-to-body class="certificate-preview-dialog">
      <div v-if="title === '预览证书'" class="certificate-content">
        <common-certificate-preview
            :certificateBgImage="certificateBgImageForPreview"
            :fieldPositions="fieldPositionsForPreview"
            :certificateData="form"
            :previewWidth="600"
            :previewHeight="450"
        />
      </div>

      <el-form v-else ref="form" :model="form" label-width="100px">
        <h4>申请内容</h4>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="证书名称：">{{ form.templateName }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户名：">{{ currentUserInfo.nickName || currentUserInfo.userName || form.applicantName }}</el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系电话：">{{ currentUserInfo.phonenumber || form.phone }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请日期：">{{ parseTime(form.createTime, '{y}-{m}-{d} {h}:{i}') }}</el-form-item>
          </el-col>
        </el-row>

        <!-- 显示申请时填写的动态字段（来自applicationData），排除materials字段）-->
        <el-form-item label="申请内容：" v-if="form.applicationData">
          <div class="application-content-grid">
            <template v-for="(item, index) in Object.entries(parsedApplicationData)" :key="index">
              <div v-if="cleanKey(item[0]).toLowerCase() !== 'materials'" class="content-item">
                <span class="content-label">{{ cleanKey(item[0]) }}：</span>
                <span class="content-value">
                  <span v-if="cleanKey(item[0]).includes('有效期')">
                    {{ formatValidPeriod(item[1]) }}
                  </span>
                  <span v-else>
                    {{ item[1] || '无' }}
                  </span>
                </span>
              </div>
            </template>
          </div>
        </el-form-item>

        <!-- 显示上传材料（来自applicationMaterials）-->
        <el-form-item label="上传材料：" v-if="hasUploadedMaterials">
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

        <el-form-item label="审核意见：">
          <div v-if="form.applicationStatus !== '1'">{{ form.reviewRemark || form.reviewOpinion || '无' }}</div>
          <div v-else>无</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 积分获取弹窗 -->
    <PointsGetDialog
        v-model:visible="showPointsGetDialog"
        :required-points="receiveCertificatePoints"
        :operation-type="'receiveCertificate'"
        :application-id="currentApplicationId"
        @close="closePointsGetDialog"
        @points-changed="handlePointsChanged" />
  </div>
</template>

<script>
import { getMyApplications, getCertificateApplication, receiveCertificate, getUserPoints } from "@/api/business/certificate/certificateApplication.js";
import { getCertificateApplicationIssuance } from "@/api/business/certificate/certificateApplicationIssuance.js";
import { getPointsRuleByCode } from "@/api/system/points.js";
import { getUserProfile } from "@/api/system/user.js";
import { getApplicationByApplicationId } from "@/api/business/certificate/applicationReview.js";  // 新增导入
import { getCertificateTemplate } from "@/api/business/certificate/certificateTemplate.js";
import useUserStore from "@/store/modules/user.js";
import CommonCertificatePreview from "@/views/business/certificate/components/commonCertificatePreview.vue";
import PointsGetDialog from '@/components/PointsGetDialog/index.vue';

export default {
  name: "MyApplications",
  components: {
    CommonCertificatePreview,
    PointsGetDialog
  },
  dicts: ['certificate_application_status'],
  data() {
    return {
      // 遮罩层
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
      // 我的申请表格数据
      myApplicationList: [],
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
      // 弹窗相关
      open: false,
      title: "查看申请",
      form: {},
      // 基础URL
      baseUrl: import.meta.env.VITE_APP_BASE_API,
      // 用户积分
      userPoints: 0,
      // 领取证书所需积分
      receiveCertificatePoints: 0,
      // 当前用户信息
      currentUserInfo: {},
      // 预览相关
      certificateBgImageForPreview: '',
      fieldPositionsForPreview: [],
      // 积分获取弹窗相关
      showPointsGetDialog: false,
      currentApplicationId: null
    };
  },
  computed: {
    parsedApplicationData() {
      if (this.form.applicationData) {
        try {
          // 解析applicationData为对象
          const parsed = JSON.parse(this.form.applicationData);

          // 为兼容性考虑，处理可能存在的特殊字符
          const cleanedData = {};
          for (const [key, value] of Object.entries(parsed)) {
            cleanedData[this.cleanKey(key)] = value;
          }

          return cleanedData;
        } catch (e) {
          console.error('解析申请数据失败:', e);
          return {};
        }
      }
      return {};
    },
    hasUploadedMaterials() {
      if (!this.form.applicationMaterials) return false;
      if (typeof this.form.applicationMaterials === 'string') {
        if (this.form.applicationMaterials.trim() === '') return false;
        try {
          const parsed = JSON.parse(this.form.applicationMaterials);
          return parsed && Object.keys(parsed).length > 0;
        } catch (e) {
          return this.form.applicationMaterials.trim() !== '';
        }
      }
      return this.form.applicationMaterials && Object.keys(this.form.applicationMaterials).length > 0;
    }
  },
  created() {
    this.getList();
    this.loadUserPoints();
    this.loadReceiveCertificatePoints();
    // 检查是否从积分获取页面返回
    this.checkReturnFromPointsPage();
  },

  watch: {
    // 监听用户积分变化
    userPoints: {
      handler(newVal) {
        // 如果是从积分获取页面返回且积分已增加，询问是否继续操作
        if (this.$route.query.from === 'receiveCertificate' &&
            this.$route.query.applicationId &&
            newVal >= this.receiveCertificatePoints) {
          this.continueOperation();
        }
      }
    }
  },

  methods: {
    /** 查询我的申请列表 */
    getList() {
      this.loading = true;

      // 为了能够筛选证书类型，先获取全部数据
      const params = {
        pageNum: 1,
        pageSize: 9999, // 获取所有数据用于前端筛选
        applicantName: this.queryParams.applicantName,
        applicationStatus: this.queryParams.applicationStatus,
        certificateType: this.queryParams.certificateType,
        templateName: this.queryParams.templateName,
      };

      if (this.dateRange && this.dateRange.length === 2) {
        params.beginTime = this.dateRange[0];
        params.endTime = this.dateRange[1];
      }

      getMyApplications(params).then(response => {
        let allData = response.rows;
        this.total = response.total;

        // 对于证书类型筛选
        if (this.queryParams.certificateType) {
          allData = allData.filter(item => item.certificateType === this.queryParams.certificateType);
        }

        // 实现分页
        const startIndex = (this.queryParams.pageNum - 1) * this.queryParams.pageSize;
        const endIndex = startIndex + this.queryParams.pageSize;
        this.myApplicationList = allData.slice(startIndex, endIndex);

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

    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.applicationId)
      this.single = selection.length!==1
      this.multiple = !selection.length
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


    /** 判断是否为待审核状态 */
    isPendingStatus(status) {
      return status === '1';
    },

    /** 判断是否为已批准状态 */
    isApprovedStatus(status) {
      return status === '2';
    },

    /** 判断是否为已驳回状态 */
    isRejectedStatus(status) {
      return status === '3';
    },

    /** 判断是否为已发放状态 */
    isIssuedStatus(status) {
      return status === '4';
    },

    // 获取状态标签
    getStatusLabel(status) {
      switch(status) {
        case '1':
          return '已提交';
        case '2':
          return '已通过';
        case '3':
          return '已驳回';
        case '4':
          return '已发放';
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
    },

    /** 查看按钮操作 */
    async handleView(row) {
      if (!row.applicationId) {
        this.$modal.msgError("申请ID不能为空");
        return;
      }

      try {
        // 使用与申请审核页面相同的API来获取申请详情，确保包含上传材料信息
        const response = await getApplicationByApplicationId(row.applicationId);
        let appData = response.data;
        
        this.form = appData;

        // 然后再获取用户信息
        const userResponse = await getUserProfile();
        this.currentUserInfo = userResponse.data || {};

        this.open = true;
        this.title = "查看申请";
      } catch (error) {
        console.error('获取申请信息失败:', error);
        this.$modal.msgError("获取申请信息失败: " + error.message);
      }
    },

    /** 预览证书按钮操作 */
    handlePreviewCertificate(row) {
      if (!row.applicationId) {
        this.$modal.msgError("申请ID不能为空");
        return;
      }

      // 获取申请详情
      getCertificateApplication(row.applicationId).then(response => {
        const applicationData = response.data;
        
        // 检查并验证 templateId
        if (!applicationData.templateId || applicationData.templateId === 'null' || isNaN(Number(applicationData.templateId)) || Number(applicationData.templateId) <= 0) {
          this.$modal.msgError('申请数据中未找到有效的模板ID，无法预览证书');
          return;
        }

        // 如果是已发放的证书，使用issuance数据
        if (applicationData.issuanceId) {
          // 调用证书管理页面的预览功能
          this.$router.push({
            path: '/business/certificate/issuance',
            query: { id: applicationData.issuanceId }
          });
        } else if (applicationData.applicationStatus === '2' || applicationData.applicationStatus === '4') {
          // 如果是已批准或已发放状态的申请，使用申请数据预览
          // 与证书管理页面的预览功能保持一致
          this.form = applicationData;
          // 准备预览数据
          this.preparePreviewDataForApplication(applicationData);
          this.open = true;
          this.title = "预览证书";
        } else {
          this.$modal.msgError("当前状态无法预览证书");
        }
      }).catch(error => {
        this.$modal.msgError("获取申请信息失败: " + error.message);
      });
    },

    /** 准备申请数据的预览数据 */
    async preparePreviewDataForApplication(applicationData) {
      try {
        // 检查并验证 templateId
        if (!applicationData.templateId || applicationData.templateId === 'null' || isNaN(Number(applicationData.templateId)) || Number(applicationData.templateId) <= 0) {
          this.$modal.msgError('申请数据中未找到有效的模板ID，无法预览证书');
          return;
        }

        // 尝试将 templateId 转换为整数
        const templateId = parseInt(applicationData.templateId, 10);
        if (isNaN(templateId) || templateId <= 0) {
          this.$modal.msgError('模板ID不是有效的数字，无法预览证书');
          return;
        }
        
        // 获取证书模板
        const templateResponse = await getCertificateTemplate(templateId);
        const templateData = templateResponse.data;

        // 设置证书背景图片
        this.certificateBgImageForPreview = templateData.certificateBgImage;

        // 解析字段位置配置
        let fieldPositions = [];
        if (templateData.fieldPositions) {
          try {
            fieldPositions = JSON.parse(templateData.fieldPositions);
          } catch (e) {
            console.error('解析字段位置配置失败:', e);
            fieldPositions = [];
          }
        }

        // 解析申请数据中的内容
        let parsedApplicationData = {};
        if (applicationData.applicationData) {
          try {
            parsedApplicationData = JSON.parse(applicationData.applicationData);
          } catch (e) {
            console.error('解析申请数据失败:', e);
            parsedApplicationData = {};
          }
        }

        // 为每个字段位置添加对应的值，同时保留原有样式属性
        const fieldPositionsWithValue = fieldPositions.map(position => {
          let fieldValue = parsedApplicationData[position.fieldName];

          // 特殊处理有效期字段：如果为空字符串则显示'长期有效'
          if (position.fieldName === '有效期') {
            fieldValue = fieldValue === '' || fieldValue === undefined || fieldValue === null ? '长期有效' : fieldValue;
          } else {
            fieldValue = fieldValue || '';
          }

          return {
            // 保留原有的所有属性（位置、大小、字体等）
            ...position,
            // 只更新字段值
            fieldValue: fieldValue
          };
        });

        this.fieldPositionsForPreview = fieldPositionsWithValue;
      } catch (error) {
        console.error('准备预览数据失败:', error);
        this.$modal.msgError('准备预览数据失败: ' + error.message);
      }
    },



    // 清理字段名中的特殊字符
    cleanKey(key) {
      // 处理可能存在的特殊字符，如\u0004等
      return key.replace(/[\ufeff\u0000-\u001f\u007f-\u009f\u0004]/g, '');
    },

    // 格式化有效期显示
    formatValidPeriod(value) {
      // 如果值为空、空字符串或已经是'长期有效'，返回'长期有效'
      if (!value || value === '' || value === '长期有效') {
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
    },

    // 检查是否为JSON字符串
    isJsonString(str) {
      try {
        JSON.parse(str);
        return true;
      } catch (e) {
        return false;
      }
    },

    // 取消按钮
    cancel() {
      this.open = false;
    },

    /** 撤销申请 */
    handleCancel(row) {
      this.$confirm(`确认要撤销"${row.templateName}"的申请吗？`, "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        // 实现撤销申请逻辑
      }).catch(() => {});
    },


    /** 下载证书 */
    async handleDownload(row) {
      try {
        // 获取证书数据
        let certificateData;
        if (row.issuanceId || row.applicationId) {
          let appData;
          let applicationId;
          
          if (row.issuanceId) {
            // 已发放的证书：先获取发放详情，再获取完整的申请数据
            const issuanceResponse = await getCertificateApplicationIssuance(row.issuanceId);
            const issuanceData = issuanceResponse.data;
            applicationId = issuanceData.applicationId;
            
            // 获取完整的申请数据（包含templateId）
            const applicationResponse = await getCertificateApplication(applicationId);
            appData = applicationResponse.data;
            
            // 合并发放数据到申请数据中
            Object.assign(appData, {
              certificateId: issuanceData.certificateId || issuanceData.certificateNumber || "待分配",
              certificateContent: issuanceData.certificateContent || appData.applicationData,
              issueDate: issuanceData.issuanceTime || issuanceData.approvedDate || issuanceData.createTime,
              validFrom: issuanceData.validFrom,
              validTo: issuanceData.validTo,
              receiveStatus: issuanceData.receiveStatus || "not_received",
              status: issuanceData.status,
              remark: issuanceData.remark
            });
          } else {
            // 已批准但未发放的申请：直接获取申请数据
            applicationId = row.applicationId;
            const applicationResponse = await getCertificateApplication(applicationId);
            appData = applicationResponse.data;
          }
          
          // 检查并验证 templateId
          if (!appData.templateId || appData.templateId === 'null' || isNaN(Number(appData.templateId)) || Number(appData.templateId) <= 0) {
            this.$modal.msgError('申请数据中未找到有效的模板ID，无法下载证书');
            return;
          }

          // 解析applicationData中的JSON字段
          let parsedData = {};
          if (appData.applicationData) {
            try {
              parsedData = JSON.parse(appData.applicationData);
            } catch (e) {
              console.error('解析申请数据失败:', e);
            }
          }

          certificateData = {
            applicationId: appData.applicationId,
            certificateNumber: appData.certificateNumber || appData.certificateId || "待分配",
            templateName: appData.templateName,
            applicantName: parsedData['姓名'] || appData.applicantName,
            phone: appData.phone,
            company: parsedData['企业'] || appData.company,
            issueDate: appData.issuanceTime || appData.approvedDate || appData.createTime,
            validFrom: appData.validFrom,
            validTo: appData.validTo,
            receiveStatus: appData.receiveStatus || "not_received",
            certificateContent: appData.certificateContent || appData.applicationData,
            status: appData.status,
            remark: appData.remark
          };

          // 解析证书内容
          if (appData.applicationData) {
            try {
              this.parsedCertificateContent = JSON.parse(appData.applicationData);
            } catch (e) {
              console.error('解析证书内容失败:', e);
              this.parsedCertificateContent = {};
            }
          }

          this.parsedCertificateContent = parsedData;

          // 准备预览数据以获取模板配置
          await this.preparePreviewDataForApplication(appData);
        } else {
          this.$modal.msgError("无法获取证书信息：缺少必要的标识符");
          return;
        }

        // 生成PDF证书，使用模板配置
        this.generateCertificatePDFWithTemplate(certificateData);
      } catch (error) {
        console.error('下载证书失败:', error);
        this.$modal.msgError('下载证书失败: ' + error.message);
      }
    },

    /** 生成证书PDF */
    async generateCertificatePDF(certificateData, parsedData) {
      try {
        // 动态导入jsPDF和html2canvas
        const [jsPDFModule, html2canvasModule] = await Promise.all([
          import('jspdf'),
          import('html2canvas')
        ]);

        // 根据jsPDF版本确定正确的构造函数
        let jsPDF;
        if (jsPDFModule.default) {
          jsPDF = jsPDFModule.default;
        } else {
          jsPDF = jsPDFModule;
        }

        const html2canvas = html2canvasModule.default || html2canvasModule;

        // 创建一个临时的HTML元素来生成证书内容
        const tempDiv = document.createElement('div');
        tempDiv.className = 'certificate-content-pdf';
        tempDiv.style.cssText = `
          width: 800px;
          padding: 40px;
          background: white;
          border: 1px solid #ddd;
          border-radius: 8px;
          box-shadow: none;
          position: fixed;
          top: -9999px;
          left: -9999px;
          font-family: SimSun, SimHei, Arial, sans-serif;
        `;

        // 构建证书HTML内容
        const title = certificateData.templateName || (parsedData && parsedData['证书名称']) || "证书";
        let contentHtml = `
          <h2 style="text-align: center; font-size: 28px; font-weight: bold; margin: 20px 0;">${title}</h2>
          <div style="margin: 30px 0; text-align: left; font-size: 18px; line-height: 2;">
        `;

        if (parsedData) {
          // 遍历解析后的证书内容，除了发放日期、有效期和materials字段
          for (const [key, value] of Object.entries(parsedData)) {
            if (key !== '发放日期' && key !== '有效期' && key.toLowerCase() !== 'materials') {
              contentHtml += `<div><strong>${key}：</strong>${value}</div>`;
            }
          }

          // 有效期放在最后
          if (parsedData['有效期'] && '有效期'.toLowerCase() !== 'materials') {
            contentHtml += `<div><strong>有效期：</strong>${this.formatValidPeriod(parsedData['有效期'])}</div>`;
          }
        }

        contentHtml += `</div>`;

        // 添加认证机构和认证日期
        const issueDate = certificateData.issueDate || (parsedData && parsedData['发放日期']) || '--';
        contentHtml += `
          <div style="display: flex; justify-content: space-between; margin-top: 60px; padding-top: 20px; border-top: 1px dashed #ccc; color: #666; font-size: 16px;">
            <span>认证机构：认证易</span>
            <span>认证日期：${issueDate}</span>
          </div>
        `;

        tempDiv.innerHTML = contentHtml;
        document.body.appendChild(tempDiv);

        // 使用html2canvas将HTML元素转换为canvas
        const canvas = await html2canvas(tempDiv, {
          scale: 2, // 提高清晰度
          useCORS: true,
          allowTaint: true,
          backgroundColor: '#fff',
          logging: false
        });

        // 从canvas获取图片数据
        const imgData = canvas.toDataURL('image/png');
        const imgWidth = 210; // A4宽度(mm)
        const imgHeight = canvas.height * imgWidth / canvas.width;

        // 创建PDF实例
        const pdf = new jsPDF({
          orientation: 'portrait',
          unit: 'mm',
          format: 'a4'
        });

        // 计算合适的图片尺寸，确保内容完整显示
        const pageWidth = 210; // A4宽度
        const pageHeight = 297; // A4高度
        const margin = 20;
        const availableWidth = pageWidth - (margin * 2);
        const availableHeight = pageHeight - (margin * 2);

        // 根据可用空间调整图片尺寸
        let scaledWidth = availableWidth;
        let scaledHeight = (canvas.height * scaledWidth) / canvas.width;

        // 如果高度超出可用空间，按高度缩放
        if (scaledHeight > availableHeight) {
          scaledHeight = availableHeight;
          scaledWidth = (canvas.width * scaledHeight) / canvas.height;
        }

        // 添加图片到PDF，居中显示
        const x = (pageWidth - scaledWidth) / 2;
        const y = margin;

        pdf.addImage(imgData, 'PNG', x, y, scaledWidth, scaledHeight);
        pdf.save(`${title}_${certificateData.certificateNumber || 'certificate'}.pdf`);

        // 清理临时元素
        document.body.removeChild(tempDiv);

        this.$modal.msgSuccess('证书PDF下载成功！');
      } catch (error) {
        console.error('生成PDF失败:', error);
        this.$modal.msgError('PDF生成失败: ' + error.message);
      }
    },

    /** 使用模板配置生成证书PDF */
    async generateCertificatePDFWithTemplate(certificateData) {
      try {
        // 动态导入jsPDF和html2canvas
        const [jsPDFModule, html2canvasModule] = await Promise.all([
          import('jspdf'),
          import('html2canvas')
        ]);

        // 根据jsPDF版本确定正确的构造函数
        let jsPDF;
        if (jsPDFModule.default) {
          jsPDF = jsPDFModule.default;
        } else {
          jsPDF = jsPDFModule;
        }

        const html2canvas = html2canvasModule.default || html2canvasModule;

        // 首先获取背景图片的真实尺寸
        const bgImage = new Image();
        bgImage.src = this.certificateBgImageForPreview;

        // 等待图片加载完成
        await new Promise((resolve) => {
          bgImage.onload = resolve;
          bgImage.onerror = () => {
            console.warn('背景图片加载失败，使用默认尺寸');
            resolve();
          };
        });
       // 使用背景图片的实际尺寸
        const imageActualWidth = bgImage.naturalWidth || 800;
        const imageActualHeight = bgImage.naturalHeight || 600;

        // 计算显示宽度和高度，限制最大尺寸并保持宽高比
        const maxWidth = 800;
        const maxHeight = 600;

        // 计算缩放比例，确保图片适合预设尺寸
        const displayScale = Math.min(
          maxWidth / imageActualWidth,
          maxHeight / imageActualHeight,
          1 // 不放大小于预设尺寸的图片
        );

        const displayWidth = imageActualWidth * displayScale;
        const displayHeight = imageActualHeight * displayScale;

        // 计算缩放比例，与预览组件保持一致
        const imgContainerWidth = 800; // 设计稿宽度
        const imgContainerHeight = 600; // 设计稿高度

        // 应用缩放以适配不同的图片尺寸
        const scaleX = displayWidth / 800; // 使用设计稿宽度作为基准
        const scaleY = displayHeight / 600; // 使用设计稿高度作为基准

        // 创建一个临时的HTML元素来生成带模板配置的证书
        const tempDiv = document.createElement('div');
        tempDiv.className = 'certificate-content-pdf';
        tempDiv.style.cssText = `
          width: ${displayWidth}px;
          height: ${displayHeight}px;
          position: fixed;
          top: -9999px;
          left: -9999px;
          font-family: SimSun, SimHei, Arial, sans-serif;
        `;

        // 创建证书包装器
        const wrapperDiv = document.createElement('div');
        wrapperDiv.style.cssText = `
          position: relative;
          width: 100%;
          height: 100%;
          overflow: hidden;
          background: white;
        `;

        // 创建背景图片
        const bgImg = document.createElement('img');
        bgImg.src = this.certificateBgImageForPreview; // 直接使用变量，不是字符串
        bgImg.alt = '证书底版';
        bgImg.style.cssText = `
          width: 100%;
          height: 100%;
          object-fit: contain;
          z-index: 0;
        `;

        // 创建内容容器
        const contentDiv = document.createElement('div');
        contentDiv.style.cssText = `
          position: absolute;
          top: 0;
          left: 0;
          z-index: 2;
          width: 100%;
          height: 100%;
        `;

        // 将元素按顺序添加到DOM结构中
        tempDiv.appendChild(wrapperDiv);
        wrapperDiv.appendChild(bgImg);
        wrapperDiv.appendChild(contentDiv);

        // 使用与预览组件一致的坐标转换逻辑
        this.fieldPositionsForPreview.forEach(position => {
          const fieldDiv = document.createElement('div');
          // 使用与CommonCertificatePreview组件中相同的字段值获取逻辑
          let fieldValue;
          // 优先使用position中的fieldValue（如果存在）
          if (position.fieldValue !== undefined && position.fieldValue !== null) {
            // 特殊处理有效期字段：如果为空字符串则显示'长期有效'
            if (position.fieldName === '有效期') {
              fieldValue = (position.fieldValue === '' || position.fieldValue === undefined || position.fieldValue === null)
                ? '长期有效'
                : position.fieldValue;
            } else {
              fieldValue = position.fieldValue;
            }
          } else {
            // 如果没有fieldValue，则从parsedCertificateContent中获取
            if (this.parsedCertificateContent && typeof this.parsedCertificateContent === 'object') {
              fieldValue = this.parsedCertificateContent[position.fieldName];
            }
            // 特殊处理有效期字段
            if (position.fieldName === '有效期') {
              fieldValue = (fieldValue === '' || fieldValue === undefined || fieldValue === null)
                ? '长期有效'
                : fieldValue;
            } else {
              fieldValue = fieldValue || '';
            }
          }

          fieldDiv.textContent = fieldValue;

          // 使用与预览组件一致的缩放和偏移计算逻辑
          // 获取编辑器中的缩放比例（基于800x600画布）
          const editorCanvasWidth = 800;
          const editorCanvasHeight = 600;
          const editorScale = Math.min(
            editorCanvasWidth / imageActualWidth,
            editorCanvasHeight / imageActualHeight,
            1 // 不放大小于预设尺寸的图片
          );

          // 获取当前预览的缩放比例
          const currentScale = Math.min(
            displayWidth / imageActualWidth,
            displayHeight / imageActualHeight,
            1 // 不放大小于预设尺寸的图片
          );

          // 计算图片在编辑器画布中的偏移量（与编辑器中onImageLoad方法一致）
          const editorImageOffsetX = (editorCanvasWidth - imageActualWidth * editorScale) / 2;
          const editorImageOffsetY = (editorCanvasHeight - imageActualHeight * editorScale) / 2;

          // 计算图片在当前预览中的偏移量
          const currentImageOffsetX = (displayWidth - imageActualWidth * currentScale) / 2;
          const currentImageOffsetY = (displayHeight - imageActualHeight * currentScale) / 2;

          // 计算缩放比率
          const scaleRatio = currentScale / editorScale;

          // 将编辑器中保存的坐标转换为相对于图片的坐标，再转换为当前预览坐标
          // 首先将编辑器坐标转换为相对于图片的坐标
          const imageBasedX = (position.x - editorImageOffsetX) * scaleRatio;
          const imageBasedY = (position.y - editorImageOffsetY) * scaleRatio;

          // 再加上当前预览中图片的偏移量
          const finalX = currentImageOffsetX + imageBasedX;
          const finalY = currentImageOffsetY + imageBasedY;

          // 计算缩放后的尺寸
          const scaledWidth = (position.width || 120) * scaleRatio;
          const scaledHeight = (position.height || 30) * scaleRatio;
          const scaledFontSize = (position.fontSize || 16) * scaleRatio;

          fieldDiv.style.cssText = `
            position: absolute;
            left: ${finalX}px;
            top: ${finalY}px;
            width: ${scaledWidth}px;
            height: ${scaledHeight}px;
            z-index: ${position.zIndex || 1};
            font-size: ${scaledFontSize}px;
            color: ${position.color || '#000'};
            font-weight: ${position.fontWeight || 'normal'};
            font-style: ${position.fontStyle || 'normal'};
            font-family: ${position.fontFamily || 'SimSun, serif'};
            display: flex;
            align-items: center;
            justify-content: center;
            text-align: center;
            overflow: hidden;
            word-break: break-all;
            white-space: normal;
            box-sizing: border-box;
            line-height: 1.2;
          `;

          contentDiv.appendChild(fieldDiv);
        });

        document.body.appendChild(tempDiv);

        // 等待一段时间让浏览器渲染DOM和图片
        await new Promise(resolve => setTimeout(resolve, 500));

        // 使用html2canvas将HTML元素转换为canvas
        html2canvas(tempDiv, {
          scale: 2, // 提高清晰度
          useCORS: true,
          allowTaint: true,
          backgroundColor: 'transparent',
          logging: false
        }).then(canvas => {
          // 从canvas获取图片数据
          const imgData = canvas.toDataURL('image.png');
          const imgWidth = 210; // A4宽度(mm)
          const imgHeight = canvas.height * imgWidth / canvas.width;

          // 创建PDF实例
          const pdf = new jsPDF({
            orientation: 'portrait',
            unit: 'mm',
            format: 'a4'
          });

          // 计算合适的图片尺寸，确保内容完整显示
          const pageWidth = 210; // A4宽度
          const pageHeight = 297; // A4高度
          const margin = 20;
          const availableWidth = pageWidth - (margin * 2);
          const availableHeight = pageHeight - (margin * 2);

          // 根据可用空间调整图片尺寸
          let scaledWidth = availableWidth;
          let scaledHeight = (canvas.height * scaledWidth) / canvas.width;

          // 如果高度超出可用空间，按高度缩放
          if (scaledHeight > availableHeight) {
            scaledHeight = availableHeight;
            scaledWidth = (canvas.width * scaledHeight) / canvas.height;
          }

          // 添加图片到PDF，居中显示
          const x = (pageWidth - scaledWidth) / 2;
          const y = margin;

          pdf.addImage(imgData, 'PNG', x, y, scaledWidth, scaledHeight);

          // 获取证书名称
          const title = certificateData.templateName || this.parsedCertificateContent['证书名称'] || "证书";
          pdf.save(`${title}_${certificateData.certificateNumber || 'certificate'}.pdf`);

          // 清理临时元素
          document.body.removeChild(tempDiv);

          this.$modal.msgSuccess('证书PDF生成成功！');
        }).catch(error => {
          console.error('html2canvas转换失败:', error);
          document.body.removeChild(tempDiv);
          this.$modal.msgError('PDF生成失败: ' + error.message);
        });
      } catch (error) {
        console.error('生成PDF失败:', error);
        this.$modal.msgError('生成PDF失败: ' + error.message);
      }
    },
    /** 重新申请 */
    handleReapply(row) {
      // 跳转到申请页面并预填信息
      // 获取原申请数据以预填充表单
      getCertificateApplication(row.applicationId).then(response => {
        const applicationData = response.data;
        this.$router.push({
          path: '/certificate/application',
          query: {
            templateId: row.templateId,
            reapply: true,
            applicationId: row.applicationId
          },
          state: { applicationData }  // 通过路由状态传递数据
        });
      }).catch(error => {
        console.error('获取申请数据失败:', error);
        // 即使获取失败也跳转到申请页面，只是无法预填充数据
        this.$router.push({
          path: '/certificate/application',
          query: {
            templateId: row.templateId,
            reapply: true,
            applicationId: row.applicationId
          }
        });
      });
    },

    /** 加载用户积分 */
    loadUserPoints() {
      getUserPoints().then(response => {
        this.userPoints = response.data || 0;
      }).catch(error => {
        console.error('获取用户积分失败:', error);
        this.userPoints = 0;
      });
    },

    /** 加载领取证书所需积分 */
    loadReceiveCertificatePoints() {
      getPointsRuleByCode('RECEIVE_CERTIFICATE').then(response => {
        if(response && response.data && response.data.pointsValue !== undefined) {
          // 积分规则中的值通常为负数表示扣减，取绝对值得到所需积分
          this.receiveCertificatePoints = Math.abs(response.data.pointsValue) || 10;
        } else {
          this.receiveCertificatePoints = 10; // 默认值
        }
      }).catch(error => {
        console.error('获取领取证书积分规则失败:', error);
        this.receiveCertificatePoints = 10; // 默认值
      });
    },

    /** 检查是否从积分获取页面返回 */
    checkReturnFromPointsPage() {
      // 如果是从积分获取页面返回，检查积分是否足够
      if (this.$route.query.from === 'receiveCertificate' &&
          this.$route.query.applicationId) {
        // 清除路由参数，避免重复提示
        const newQuery = { ...this.$route.query };
        delete newQuery.from;
        delete newQuery.applicationId;

        if (JSON.stringify(newQuery) !== JSON.stringify({})) {
          this.$router.replace({ query: newQuery });
        }

        // 检查积分是否已足够执行操作
        if (this.userPoints >= this.receiveCertificatePoints) {
          this.continueOperation();
        }
      }
    },

    /** 继续执行原操作 */
    continueOperation() {
      // 询问用户是否继续执行原操作
      this.$confirm(`您已获取积分，当前积分为 ${this.userPoints} 分，是否继续领取证书？`, "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        // 执行领取证书操作
        const applicationId = this.$route.query.applicationId;
        if (applicationId) {
          this.performReceiveCertificate(applicationId);
        }
      }).catch(() => {
        // 用户取消操作
        console.log('用户取消继续操作');
      });
    },

    /** 执行领取证书操作 */
    async performReceiveCertificate(applicationId) {
      try {
        const response = await receiveCertificate(applicationId);
        if (response.code === 200) {
          this.$modal.msgSuccess("证书领取成功！");
          // 更新列表数据
          this.getList();
          // 更新用户积分
          this.loadUserPoints();

          // 查找对应的申请行用于下载
          const row = this.myApplicationList.find(item => item.applicationId == applicationId);
          if (row) {
            // 领取成功后自动下载证书
            this.$nextTick(() => {
              // 延迟一点时间以确保状态更新
              setTimeout(() => {
                this.handleDownload(row);
              }, 500);
            });
          }
        } else {
          this.$modal.msgError(response.msg || "领取失败");
        }
      } catch (error) {
        this.$modal.msgError("证书领取失败: " + error.message);
      }
    },

    /** 领取证书 */
    async handleReceive(row) {
      // 检查用户积分
      if (this.userPoints < this.receiveCertificatePoints) {
        // 积分不足，打开积分获取弹窗
        this.currentApplicationId = row.applicationId;
        this.showPointsGetDialog = true;
        return;
      }

      // 积分充足，显示确认对话框
      this.$confirm(`确认要领取"${row.templateName}"证书吗？此操作需要消耗 ${this.receiveCertificatePoints} 积分，您的当前积分为 ${this.userPoints} 分。`, "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        // 执行领取证书操作
        receiveCertificate(row.applicationId).then(response => {
          if (response.code === 200) {
            this.$modal.msgSuccess("证书领取成功！");
            // 更新列表数据
            this.getList();
            // 更新用户积分
            this.loadUserPoints();


            // 领取成功后自动下载证书
            this.$nextTick(() => {
              // 延迟一点时间以确保状态更新
              setTimeout(() => {
                this.handleDownload(row);
              }, 500);
            });
          } else {
            this.$modal.msgError(response.msg || "领取失败");
          }
        }).catch(error => {
          this.$modal.msgError("证书领取失败: " + error.message);
        });
      }).catch(() => {
        // 用户取消操作
      });
    },

    /** 关闭获取积分弹窗 */
    closePointsGetDialog() {
      this.showPointsGetDialog = false;
      this.currentApplicationId = null;
    },

    /** 积分发生变化时的回调 */
    handlePointsChanged(newPoints) {
      this.userPoints = newPoints;

      // 如果积分已足够，询问是否继续执行原操作
      if (newPoints >= this.receiveCertificatePoints) {
        this.$confirm(`您已获取积分，当前积分为 ${newPoints} 分，是否继续领取证书？`, "提示", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          // 关闭积分获取弹窗
          this.closePointsGetDialog();
          // 执行领取证书操作
          this.performReceiveCertificate(this.currentApplicationId);
        }).catch(() => {
          // 用户取消操作，但不关闭积分获取弹窗
          console.log('用户取消继续操作');
        });
      }
    },

    /** 获取字段值用于PDF生成，与CommonCertificatePreview组件逻辑保持一致 */
    getFieldValueForPdf(fieldName) {
      // 从parsedCertificateContent中获取值
      if (this.parsedCertificateContent && typeof this.parsedCertificateContent === 'object') {
        if (this.parsedCertificateContent[fieldName]) {
          return this.parsedCertificateContent[fieldName];
        }
      }

      // 特殊处理：有效期字段如果找不到值则显示'长期有效'
      if (fieldName === '有效期') {
        return '长期有效';
      }

      // 如果没有找到对应值，返回字段名作为默认值
      return fieldName;
    }
  }
};
</script>

<style scoped>
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

/* 证书预览样式 */
.certificate-content {
  padding: 20px;
  min-height: 300px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: white;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  display: flex;
  justify-content: center;
  align-items: center;
}

.certificate-title {
  text-align: center;
  font-size: 24px;
  font-weight: bold;
  margin: 20px 0;
  color: #333;
  text-transform: uppercase;
  letter-spacing: 2px;
}

.certificate-fields {
  margin: 30px 0;
  text-align: left;
}

.field-item {
  margin: 12px 0;
  font-size: 16px;
  line-height: 1.6;
}

.certification-info {
  display: flex;
  justify-content: space-between;
  margin-top: 40px;
  padding-top: 20px;
  border-top: 1px dashed #ccc;
  color: #666;
}

.left-text {
  float: left;
}

.right-text {
  float: right;
}

.dialog-footer {
  text-align: center;
}

.certificate-preview-dialog :deep(.el-dialog__body) {
  padding: 20px;
}
</style>