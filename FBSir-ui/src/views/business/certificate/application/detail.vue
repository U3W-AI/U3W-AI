<template>
  <div class="app-container">
    <h3>申请详情 - {{ application.templateName }}</h3>
    
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>申请信息</span>
        </div>
      </template>
      
      <div v-if="application" class="application-info">
        <!-- 申请状态信息 -->
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请编号">{{ application.applicationCode || application.applicationNumber }}</el-descriptions-item>
          <el-descriptions-item label="申请状态">
            <el-tag 
              v-if="isDraftStatus(application.applicationStatus)" 
              type="info"
            >草稿</el-tag>
            <el-tag 
              v-else-if="isPendingStatus(application.applicationStatus)" 
              type="warning"
            >待审核</el-tag>
            <el-tag 
              v-else-if="isApprovedStatus(application.applicationStatus)" 
              type="success"
            >已批准</el-tag>
            <el-tag 
              v-else-if="isRejectedStatus(application.applicationStatus)" 
              type="danger"
            >已驳回</el-tag>
            <el-tag 
              v-else-if="isIssuedStatus(application.applicationStatus)" 
              type="success"
            >已发放</el-tag>
            <el-tag 
              v-else
              type="info"
            >未知状态</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ parseTime(application.createTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</el-descriptions-item>
          <el-descriptions-item label="扣除积分">{{ application.pointsDeducted || 0 }}分</el-descriptions-item>
        </el-descriptions>

        <!-- 动态表单字段 -->
        <div v-if="dynamicFields && dynamicFields.length > 0" class="form-section">
          <h4>{{ application.templateName }} - 申请信息</h4>
          <div class="application-content">
            <template v-for="(field, index) in filteredDynamicFields" :key="field.fieldName">
              <div v-if="index % 2 === 0" class="content-row">
                <div class="content-item">
                  <span class="content-label">{{ field.fieldName }}：</span>
                  <span class="content-value">{{ dynamicForm[field.fieldName] || '无' }}</span>
                </div>
                <div class="content-item" v-if="filteredDynamicFields[index + 1]">
                  <span class="content-label">{{ filteredDynamicFields[index + 1]?.fieldName }}：</span>
                  <span class="content-value">{{ dynamicForm[filteredDynamicFields[index + 1]?.fieldName] || '无' }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>
        
        
        <!-- 上传材料 -->
        <div v-if="requiredMaterials && requiredMaterials.length > 0" class="materials-section">
          <h4>上传材料</h4>
          <div v-for="(material, index) in requiredMaterials" :key="`material_${index}`" style="margin-bottom: 15px;">
            <el-form-item :label="material.materialName">
              <div v-if="uploadedMaterials[material.materialName]">
                <el-link 
                  :href="`${baseUrl}/profile/${uploadedMaterials[material.materialName]}`" 
                  type="primary" 
                  :underline="false"
                  target="_blank"
                >
                  <i class="el-icon-document"></i> 查看材料
                </el-link>
              </div>
              <div v-else class="no-material">未上传</div>
            </el-form-item>
          </div>
        </div>
        
        <!-- 补充说明 -->
        <div class="remark-section">
          <el-form :model="application" label-width="120px">
            <el-form-item label="补充说明">
              <el-input 
                v-model="application.remark" 
                type="textarea" 
                :rows="4" 
                readonly
                placeholder="暂无补充说明"
                style="width: 500px;"
              />
            </el-form-item>
          </el-form>
        </div>
      </div>
      
      <div v-else class="empty-data">暂无申请信息</div>
      
      <!-- 返回按钮 -->
      <div style="text-align: center; margin-top: 20px;">
        <el-button @click="$router.back()">返回</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import { getCertificateApplication } from "@/api/business/certificate/certificateApplication";
import { getToken } from "@/utils/auth";
import { parseTime } from "@/utils/FBSir";

export default {
  name: "CertificateApplicationDetail",
  data() {
    return {
      application: null,
      dynamicForm: {}, // 动态表单数据
      dynamicFields: [], // 动态表单字段配置
      requiredMaterials: [], // 必需材料
      uploadedMaterials: {}, // 上传的材料
      baseUrl: process.env.VITE_APP_BASE_API || ''
    };
  },
  computed: {
    applicationId() {
      return this.$route.params.applicationId;
    },
    
    // 过滤掉materials字段的动态字段
    filteredDynamicFields() {
      if (!this.dynamicFields) return [];
      return this.dynamicFields.filter(field => field.fieldName.toLowerCase() !== 'materials');
    }
  },
  created() {
    this.fetchApplicationDetail();
  },
  methods: {
    // 获取申请详情
    fetchApplicationDetail() {
      const id = this.applicationId;
      getCertificateApplication(id).then(response => {
        this.application = response.data;
        this.parseApplicationData();
      }).catch(error => {
        console.error('获取申请详情失败:', error);
      });
    },

    // 解析申请数据
    parseApplicationData() {
      if (this.application) {
        // 解析申请信息(JSON格式)
        let applicationInfo = {};
        if (this.application.applicationData) {
          try {
            applicationInfo = JSON.parse(this.application.applicationData);
          } catch (e) {
            console.error('解析申请数据失败:', e);
          }
        } else if (this.application.applicationInfo) {
          try {
            applicationInfo = JSON.parse(this.application.applicationInfo);
          } catch (e) {
            console.error('解析申请信息失败:', e);
          }
        }
        this.dynamicForm = { ...applicationInfo };

        // 获取模板字段配置 - 这里需要从后端获取模板信息来获取字段配置
        this.fetchTemplateFields();
        
        // 解析上传材料
        if (this.application.materials) {
          try {
            this.uploadedMaterials = typeof this.application.materials === 'string' 
              ? JSON.parse(this.application.materials) 
              : this.application.materials;
          } catch (e) {
            console.error('解析上传材料失败:', e);
            this.uploadedMaterials = {};
          }
        }
        

      }
    },

    // 获取模板字段配置
    async fetchTemplateFields() {
      if (this.application && this.application.templateId) {
        // 由于没有直接获取模板详情的API，我们暂时使用模板信息中的配置
        // 在实际应用中，这里应该调用获取模板详情的API
        if (this.application.templateFields) {
          try {
            const templateFieldsConfig = JSON.parse(this.application.templateFields);
            if (templateFieldsConfig && templateFieldsConfig.formFields) {
              this.dynamicFields = templateFieldsConfig.formFields;
            } else if (Array.isArray(templateFieldsConfig)) {
              this.dynamicFields = templateFieldsConfig;
            }
          } catch (e) {
            console.error('解析模板字段配置失败:', e);
          }
        }
        
        // 解析必需材料
        if (this.application.applyRequiredFields) {
          try {
            let rawMaterials = JSON.parse(this.application.applyRequiredFields || '[]');
            this.requiredMaterials = rawMaterials.map(material => ({
              ...material,
              materialName: material.materialName || material.name || material.label || '未知材料'
            }));
          } catch (e) {
            console.error('解析申请必需字段失败:', e);
          }
        }
      }
    },

    // 时间格式化
    parseTime(time, cFormat) {
      return parseTime(time, cFormat);
    },

    /** 判断是否为草稿状态 */
    isDraftStatus(status) {
      return status === '0' || status === 'DRAFT';
    },
    
    /** 判断是否为待审核状态 */
    isPendingStatus(status) {
      return status === '1' || status === 'SUBMITTED' || status === 'UNDER_REVIEW';
    },
    
    /** 判断是否为已批准状态 */
    isApprovedStatus(status) {
      return status === '2' || status === 'APPROVED';
    },
    
    /** 判断是否为已驳回状态 */
    isRejectedStatus(status) {
      return status === '3' || status === 'REJECTED';
    },
    
    /** 判断是否为已发放状态 */
    isIssuedStatus(status) {
      return status === '4' || status === 'ISSUED';
    }
  }
};
</script>

<style scoped>
.empty-data {
  text-align: center;
  color: #999;
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.text {
  font-size: 14px;
}

.item {
  margin-bottom: 18px;
}

.box-card {
  width: 100%;
  margin-top: 20px;
}

.form-section, .materials-section, .remark-section {
  margin-top: 20px;
}



/* 动态字段网格布局 */
.dynamic-fields-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr); /* 每行显示2个字段 */
  gap: 15px; /* 设置间距 */
  margin-top: 10px;
}

.field-item {
  display: flex;
  align-items: center; /* 垂直居中对齐 */
  margin-bottom: 10px;
  width: 100%; /* 确保占满宽度 */
}

.field-label {
  font-weight: bold;
  min-width: 80px; /* 减少标签宽度 */
  text-align: right;
  margin-right: 10px;
}

.field-value {
  flex: 1;
  min-width: 200px; /* 设置最小宽度 */
}

.no-material {
  color: #999;
  font-style: italic;
}
</style>