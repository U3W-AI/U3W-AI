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
      <el-form-item label="用户名">
        <el-input v-model="queryParams.applicantName" placeholder="请输入用户名" clearable style="width: 110px;" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="有效期">
        <el-date-picker
          v-model="validDateRange"
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

    <el-table v-loading="loading" :data="certificateIssuanceList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="证书编号" align="center" prop="certificateId" />
      <el-table-column label="证书名称" align="center" prop="templateName" />
      <el-table-column label="证书类型" align="center" prop="certificateType" />
      <el-table-column label="姓名" align="center">
        <template #default="scope">
          {{ getRealName(scope.row.applicationData) }}
        </template>
      </el-table-column>
      <el-table-column label="用户名" align="center">
        <template #default="scope">
          {{ scope.row.userName || scope.row.applicantName || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="院校/企业" align="center" prop="company">
        <template #default="scope">
          <span>{{ getSchoolOrCompany(scope.row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="有效期" align="center" prop="validTo" width="180">
        <template #default="scope">
          <span>{{ getValidPeriodDisplay(scope.row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="领取状态" align="center" prop="receiveStatus">
        <template #default="scope">
          <el-tag
            v-if="scope.row.receiveStatus === 'received'"
            type="success"
          >已领取</el-tag>
          <el-tag
            v-else
            type="info"
          >未领取</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            size="small"
            type="text"
            icon="View"
            @click="handleView(scope.row)"
            v-hasPermi="['business:certificate:certificateManagement:query']"
          >查看</el-button>
          <el-button
            size="small"
            type="text"
            icon="Download"
            @click="handleDownload(scope.row)"
            v-hasPermi="['business:certificate:certificateManagement:download']"
          >下载</el-button>
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

    <!-- 查看或修改证书发放对话框 -->
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

      <el-form v-else ref="form" :model="form" :rules="rules" label-width="100px">
        <!-- 发放日期字段 -->
        <el-form-item label="发放日期" prop="issueDate">
          <el-input v-model="form.issueDate" :disabled="true" />
        </el-form-item>

        <!-- 动态显示证书内容中的其他字段 -->
        <div v-if="form.certificateContent">
          <el-form-item
            v-for="(value, key) in parsedCertificateContent"
            :key="key"
            v-if="key !== '发放日期'"
            :label="key"
          >
            <span v-if="key === '有效期'">
              {{ formatValidPeriod(value) }}
            </span>
            <span v-else>
              {{ value }}
            </span>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">关 闭</el-button>
          <!-- 预览证书模式下不需要确认按钮，仅保留关闭按钮 -->
          <el-button type="primary" @click="submitForm" v-if="!form.issuanceId && title !== '预览证书'">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.search-form {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  flex-wrap: nowrap;
  :deep(.el-form-item) {
    margin-right: 0px;
    margin-left: 0px;
    margin-bottom: 0;
    flex-shrink: 0;
    min-width: 110px;
  }
  :deep(.el-form-item__label) {
    font-size: 14px; /* 增大字号 */
    padding-right: 2px;
  }
  :deep(.el-input--small) {
    height: 30px;
    :deep(input) {
      padding: 0 4px;
      font-size: 14px; /* 增大字号 */
    }
  }
  :deep(.el-select--small) {
    height: 30px;
    :deep(input) {
      font-size: 14px; /* 增大字号 */
    }
  }
  :deep(.el-date-editor--small) {
    height: 30px;
    :deep(input) {
      font-size: 14px; /* 增大字号 */
      padding: 0 6px;
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
    padding: 1px 4px !important;
  }
  :deep(.el-input--small .el-input__wrapper) {
    padding: 1px 4px !important;
  }
  :deep(.el-select .el-input__wrapper) {
    padding: 1px 4px !important;
  }
}

.certificate-content {
  padding: 20px;
  min-height: 300px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: linear-gradient(to bottom right, #f9f9f9, #ffffff);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
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
</style>

<script>
// 导入基于申请表的证书发放API
import { listCertificateApplicationIssuance, getCertificateApplicationIssuance } from "@/api/business/certificate/certificateApplicationIssuance";
import { getCertificateApplication, getUserPoints, receiveCertificate, listCertificateApplication, delCertificateApplication } from "@/api/business/certificate/certificateApplication";  // 修正导入位置
import { getCertificateTemplate } from "@/api/business/certificate/certificateTemplate";
import { getUser } from "@/api/system/user";  // 新增导入
import { getPointsRuleByCode } from "@/api/system/points";
import useUserStore from "@/store/modules/user";
import CommonCertificatePreview from "@/views/business/certificate/components/commonCertificatePreview.vue";

export default {
  name: "CertificateManagement",
  components: {
    CommonCertificatePreview
  },
  dicts: ['certificate_status'],
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
      // 证书发放表格数据
      certificateIssuanceList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 有效期范围
      validDateRange: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        certificateNumber: null,
        applicantName: null,
        company: null,
        certificateType: null,
        templateName: null
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        applicationId: [
          { required: true, message: "申请ID不能为空", trigger: "blur" }
        ],
        certificateNumber: [
          { required: true, message: "证书编号不能为空", trigger: "blur" }
        ],
        issueDate: [
          { required: true, message: "发放日期不能为空", trigger: "blur" }
        ]
      },
      // 解析后的证书内容
      parsedCertificateContent: {},
      // 用户积分
      userPoints: 0,
      // 领取证书所需积分
      receiveCertificatePoints: 0,
      // 预览相关
      certificateBgImageForPreview: '',
      fieldPositionsForPreview: []
    };
  },
  created() {
    this.getList();
    this.loadUserPoints();
    this.loadReceiveCertificatePoints();
  },
  methods: {
    /** 查询证书发放列表 */
    getList() {
      this.loading = true;
      // 使用基于申请表的证书发放API
      listCertificateApplicationIssuance(this.queryParams).then(response => {
        // 直接使用后端返回的数据，后端已处理好所有关联信息
        const allData = response.rows;

        // 获取所有唯一用户ID列表
        const userIds = [...new Set(allData.map(item => item.userId || item.createBy).filter(id => id))];
        
        // 批量获取用户信息
        const userPromises = userIds.map(userId => 
          getUser(userId).catch(() => ({ data: { userName: '未知用户' } }))
        );
        
        // 等待所有用户信息获取完成
        Promise.all(userPromises).then(userResponses => {
          // 创建用户ID到用户名的映射
          const userMap = {};
          userResponses.forEach((response, index) => {
            if (response && response.data) {
              userMap[userIds[index]] = response.data.userName || response.data.nickName || '未知用户';
            } else {
              userMap[userIds[index]] = '未知用户';
            }
          });

          // 将用户名添加到数据中
          const enhancedData = allData.map(item => ({
            ...item,
            userName: userMap[item.userId || item.createBy] || item.applicantName || '—'
          }));

          this.certificateIssuanceList = enhancedData;
          this.total = response.total;
          this.loading = false;
        }).catch(error => {
          console.error('获取用户信息失败:', error);
          
          // 即使获取用户信息失败，也显示原始数据
          const enhancedData = allData.map(item => ({
            ...item,
            userName: item.applicantName || '—'
          }));
          
          this.certificateIssuanceList = enhancedData;
          this.total = response.total;
          this.loading = false;
        });
      }).catch(error => {
        console.error('获取证书发放列表失败:', error);
        this.certificateIssuanceList = [];
        this.total = 0;
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


    /** 准备已发放证书的预览数据 */
    async preparePreviewData(certificateIssuance) {
      try {
        console.log('preparePreviewData - certificateIssuance:', certificateIssuance);
        // 从applicationId获取对应的申请数据
        const applicationResponse = await getCertificateApplication(certificateIssuance.applicationId);
        const applicationData = applicationResponse.data;
        
        console.log('preparePreviewData - applicationData:', applicationData);
        console.log('preparePreviewData - templateId:', applicationData.templateId, '类型:', typeof applicationData.templateId);

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

        // 添加字段值到位置配置中
        let parsedCertificateContent = {};
        if (certificateIssuance.certificateContent) {
          try {
            parsedCertificateContent = JSON.parse(certificateIssuance.certificateContent);
          } catch (e) {
            console.error('解析证书内容失败:', e);
            parsedCertificateContent = {};
          }
        }

        // 为每个字段位置添加对应的值，同时保留原有样式属性
        const fieldPositionsWithValue = fieldPositions.map(position => {
          let fieldValue = parsedCertificateContent[position.fieldName];

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

    /** 加载用户积分 */
    loadUserPoints() {
      getUserPoints().then(response => {
        // 从AjaxResult的data字段中获取积分值
        this.userPoints = response.data || 0;
      }).catch(error => {
        console.error('获取用户积分失败:', error);
        this.userPoints = 0;
      });
    },

    /** 加载领取证书所需积分 */
    loadReceiveCertificatePoints() {
      // 获取RECEIVE_CERTIFICATE规则的积分值
      getPointsRuleByCode('RECEIVE_CERTIFICATE').then(response => {
        if(response && response.pointsValue !== undefined) {
          // 积分规则中的值通常为负数表示扣减，取绝对值得到所需积分
          this.receiveCertificatePoints = Math.abs(response.pointsValue) || 10;
        } else {
          this.receiveCertificatePoints = 10; // 默认值
        }
      }).catch(error => {
        console.error('获取领取证书积分规则失败:', error);
        this.receiveCertificatePoints = 10; // 默认值
      });
    },

    // 获取院校或企业信息
    getSchoolOrCompany(row) {
      if (!row) return '无';

      // 优先使用后端处理好的company字段（已包含院校/企业信息）
      if (row.company && row.company !== '无') {
        return row.company;
      }

      // 如果后端返回的是'无'，仍然尝试解析applicationData作为备选方案
      if (row.applicationData) {
        try {
          const appData = JSON.parse(row.applicationData);
          // 优先返回院校信息，其次企业信息，都没有则返回'无'
          if (appData && typeof appData === 'object') {
            const keys = Object.keys(appData);

            // 尝试查找院校字段，处理可能存在的特殊字符
            const schoolKey = keys.find(key => {
              const cleanedKey = key.replace(/[\ufeff\u0000-\u001f\u007f-\u009f]/g, '');
              return cleanedKey.includes('院校');
            });
            if (schoolKey && appData[schoolKey]) {
              return appData[schoolKey];
            }

            // 尝试查找企业字段，处理可能存在的特殊字符
            const companyKey = keys.find(key => {
              const cleanedKey = key.replace(/[\ufeff\u0000-\u001f\u007f-\u009f]/g, '');
              return cleanedKey.includes('企业');
            });
            if (companyKey && appData[companyKey]) {
              return appData[companyKey];
            }
          }
        } catch (e) {
          console.error('解析申请数据失败:', e);
        }
      }

      // 如果都获取不到，则返回'无'
      return '无';
    },

    // 获取有效期显示内容
    getValidPeriodDisplay(row) {
      if (!row) return '长期有效';

      // 优先从申请数据中解析有效期字段，与预览证书页面保持一致
      if (row.applicationData) {
        try {
          const appData = JSON.parse(row.applicationData);
          if (appData && typeof appData === 'object') {
            // 检查是否存在'有效期'字段（包括可能包含特殊字符的情况）
            let validPeriod = appData['有效期'];
            if (validPeriod === undefined || validPeriod === null) {
              const keys = Object.keys(appData);
              // 检查各种可能包含'有效期'的字段名，包括带有特殊字符的情况
              const validPeriodKey = keys.find(key => {
                const cleanedKey = key.replace(/[\ufeff\u0000-\u001f\u007f-\u009f\u0004]/g, '');
                return cleanedKey.includes('有效期');
              });
              if (validPeriodKey) {
                validPeriod = appData[validPeriodKey];
              }
            }

            // 如果找到了有效期字段
            if (validPeriod !== undefined && validPeriod !== null) {
              // 如果有效期字段为空字符串或只包含空白字符，显示"长期有效"
              if (validPeriod === '' || (typeof validPeriod === 'string' && validPeriod.trim() === '')) {
                return '长期有效';
              }

              // 如果是ISO时间格式，转换为指定格式
              if (typeof validPeriod === 'string' && validPeriod.includes('T') && validPeriod.includes('Z')) {
                const date = new Date(validPeriod);
                const year = date.getFullYear();
                const month = String(date.getMonth() + 1).padStart(2, '0');
                const day = String(date.getDate()).padStart(2, '0');
                return `${year}-${month}-${day}`;
              }

              // 返回处理后的有效期值
              return validPeriod;
            }
          }
        } catch (e) {
          console.error('解析申请数据失败:', e);
        }
      }

      // 如果没有从申请数据中获取到有效期，检查后端返回的日期字段
      if (row.validFrom) {
        // 如果是ISO时间格式，转换为指定格式
        if (typeof row.validFrom === 'string' && row.validFrom.includes('T') && row.validFrom.includes('Z')) {
          const date = new Date(row.validFrom);
          const year = date.getFullYear();
          const month = String(date.getMonth() + 1).padStart(2, '0');
          const day = String(date.getDate()).padStart(2, '0');
          return `${year}-${month}-${day}`;
        }
        // 如果是普通日期格式，直接取日期部分
        if (typeof row.validFrom === 'string' && row.validFrom.includes(' ')) {
          return row.validFrom.split(' ')[0];
        }
        // 如果已经是日期格式，直接返回
        if (typeof row.validFrom === 'string') {
          return row.validFrom;
        }
      }

      // 默认返回"长期有效"
      return '长期有效';
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

    // 获取姓名
    getRealName(applicationData) {
      if (!applicationData) {
        return '无';
      }

      try {
        const parsedData = JSON.parse(applicationData);
        // 检查是否有'姓名'字段
        if (parsedData['姓名']) {
          return parsedData['姓名'];
        }

        // 检查可能包含特殊字符的姓名字段
        const keys = Object.keys(parsedData);
        const nameKey = keys.find(key => {
          const cleanedKey = key.replace(/[\ufeff\u0000-\u001f\u007f-\u009f\u0004]/g, '');
          return cleanedKey === '姓名';
        });
        if (nameKey) {
          return parsedData[nameKey];
        }
      } catch (e) {
        console.error('解析申请数据失败:', e);
      }

      return '无';
    },

    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        issuanceId: null,
        applicationId: null,
        certificateNumber: null,
        certificateContent: null,
        issueDate: null,
        validFrom: null,
        validTo: null,
        status: "ACTIVE",
        remark: null
      };
      this.parsedCertificateContent = {};
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
      this.validDateRange = [];
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.applicationId)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },

    /** 查看按钮操作 */
    async handleView(row) {
      this.reset();
      if (!row) {
        this.$modal.msgError("无法获取证书信息");
        return;
      }

      // 现在数据来自重构后的API，直接使用数据
      if (row.issuanceId || row.applicationId) {
        try {
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
            this.$modal.msgError('申请数据中未找到有效的模板ID，无法预览证书');
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

          // 将申请数据映射到表单结构
          this.form = {
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

          // 如果有有效期字段，更新validFrom和validTo
          if (parsedData['有效期']) {
            const validPeriod = parsedData['有效期'];
            if (validPeriod && validPeriod.trim() !== '') {
              // 如果是ISO时间格式，转换为指定格式
              if (validPeriod.includes('T') && validPeriod.includes('Z')) {
                const date = new Date(validPeriod);
                const year = date.getFullYear();
                const month = String(date.getMonth() + 1).padStart(2, '0');
                const day = String(date.getDate()).padStart(2, '0');
                const hours = String(date.getHours()).padStart(2, '0');
                const minutes = String(date.getMinutes()).padStart(2, '0');
                this.form.validFrom = `${year}-${month}-${day} ${hours}:${minutes}`;
                this.form.validTo = `${year}-${month}-${day} ${hours}:${minutes}`;
              } else {
                this.form.validFrom = validPeriod;
                this.form.validTo = validPeriod;
              }
            }
          }

          // 设置解析后的证书内容
          this.parsedCertificateContent = parsedData;

          // 准备预览数据
          await this.preparePreviewDataForApplication(appData);
          this.open = true;
          this.title = "预览证书";
        } catch (error) {
          this.$modal.msgError("获取证书信息失败: " + error.message);
        }
      } else {
        this.$modal.msgError("无法获取证书信息：缺少必要的标识符");
        return;
      }
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          // 由于现在所有数据都来自certificate_application表，不再使用issuance相关的API
          // 这里可能需要根据实际业务逻辑进行调整
          // 暂时保留原逻辑，但实际业务中可能需要修改
          this.$modal.msgWarning("当前模式下不支持直接修改发放信息，如需修改请通过申请流程处理");
          this.open = false;
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const applicationIds = row.applicationId || this.ids;
      this.$confirm('是否确认删除证书申请编号为"' + applicationIds + '"的数据项?', "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(function() {
          return delCertificateApplication(applicationIds);
        }).then(() => {
          this.getList();
          this.$modal.msgSuccess("删除成功");
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