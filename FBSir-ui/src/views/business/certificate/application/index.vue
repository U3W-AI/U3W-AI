<template>
  <div class="app-container">
    <!-- 搜索区域和工具栏 -->
    <el-form :model="selectForm" ref="queryForm" size="small" :inline="true" label-width="120px">
      <el-form-item label="证书模板">
        <el-select v-model="selectForm.templateId" placeholder="请选择证书模板" @change="onTemplateSelect" style="width: 300px;">
          <el-option
            v-for="item in templateList.filter(t => t.status === '0')"
            :key="item.templateId"
            :label="`${item.templateName} (申请需${applyCertificatePoints}分)`"
            :value="item.templateId"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="startApplication" :disabled="!selectForm.templateId">开始申请</el-button>
      </el-form-item>
    </el-form>

    <!-- 证书列表 -->
    <el-table v-loading="templateLoading" :data="filteredTemplates.filter(t => t.status === '0')" style="width: 100%; margin-top: 20px;">  // 只显示已上架的证书模板
      <el-table-column prop="templateName" label="证书名称" align="center" />
      <el-table-column prop="certificateType" label="证书类型" align="center" />
      <el-table-column label="操作" width="150" align="center">
        <template #default="scope">
          <el-button size="small" @click="selectTemplate(scope.row)" type="primary" :disabled="scope.row.status !== '0'">申请</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 分页组件 -->
    <div style="display: flex; justify-content: flex-end; margin-top: 20px;">
      <el-pagination
        v-show="pagination.total > 0 || templateList.length > 0"
        :background="true"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :layout="'total, sizes, prev, pager, next, jumper'"
        :page-sizes="[10, 20, 30, 50]"
        :pager-count="7"
        :total="filteredTemplates.filter(t => t.status === '0').length"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
    
    <!-- 调试信息 -->
    <div v-if="templateLoading" style="margin-top: 10px; color: #999;">正在加载数据...</div>
    <div v-else-if="!templateList.filter(t => t.status === '0').length && pagination.total === 0" style="margin-top: 10px; color: #999;">暂无已上架的证书模板</div>
    
    <!-- 第三步：申请信息填写 -->
    <el-dialog 
      :title="selectedTemplate.templateName ? selectedTemplate.templateName + ' - 申请信息填写' : '申请信息填写'" 
      v-model="showApplicationDialog" 
      width="80%" 
      top="5vh" 
      :before-close="handleCloseDialog"
    >
      <div v-if="insufficientPoints" class="points-warning">
        <el-alert
          title="积分不足！"
          type="error"
          :description="`当前积分${userPoints}分，申请此证书需${applyCertificatePoints}分，差额${Math.abs(applyCertificatePoints - userPoints)}分`"
          show-icon>
          <template #default>
            <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
              <span>积分不足！当前积分{{userPoints}}分，申请此证书需{{applyCertificatePoints}}分，差额{{Math.abs(applyCertificatePoints - userPoints)}}分</span>
              <el-button type="primary" @click="openPointsGetDialog" size="small">去获取积分</el-button>
            </div>
          </template>
        </el-alert>
      </div>
      
      <el-form 
        :model="dynamicForm" 
        :rules="dynamicFormRules" 
        ref="applicationFormRef" 
        label-width="120px"
      >
        <!-- 动态表单字段 -->
        <div class="dynamic-fields-grid">
          <!-- 证书描述 -->
          <div class="field-item full-width" v-if="selectedTemplate && selectedTemplate.remark">
            <el-form-item label="证书描述：" class="field-form-item">
              <el-input 
                v-model="selectedTemplate.remark" 
                type="textarea" 
                :rows="4" 
                readonly
                style="width: 500px;" 
              />
            </el-form-item>
          </div>
          
          <div v-for="field in dynamicFields" :key="field.fieldName" class="field-item">
            <el-form-item 
              :label="field.fieldName" 
              :prop="field.fieldName"
              class="field-form-item"
            >
              <el-input 
                v-if="field.fieldType === 'input' || field.fieldType === 'text'" 
                v-model="dynamicForm[field.fieldName]" 
                :placeholder="`请输入${field.fieldName}`" 
                style="width: 300px;" 
              />
              
              <el-input 
                v-else-if="field.fieldType === 'textarea'" 
                v-model="dynamicForm[field.fieldName]" 
                type="textarea" 
                :placeholder="`请输入${field.fieldName}`" 
                style="width: 300px;"
              />
              
              <el-select
                v-else-if="field.fieldType === 'select'" 
                v-model="dynamicForm[field.fieldName]" 
                :placeholder="`请选择${field.fieldName}`" 
                style="width: 300px;"
              >
                <el-option 
                  v-for="option in field.options || []" 
                  :key="option.value" 
                  :label="option.label" 
                  :value="option.value"
                />
              </el-select>
              
              <el-radio-group
                v-else-if="field.fieldType === 'radio'" 
                v-model="dynamicForm[field.fieldName]"
              >
                <el-radio 
                  v-for="option in field.options || []" 
                  :key="option.value" 
                  :value="option.value"
                >{{ option.label }}</el-radio>
              </el-radio-group>
              
              <el-checkbox-group
                v-else-if="field.fieldType === 'checkbox'" 
                v-model="dynamicForm[field.fieldName]"
              >
                <el-checkbox 
                  v-for="option in field.options || []" 
                  :key="option.value" 
                  :value="option.value"
                >{{ option.label }}</el-checkbox>
              </el-checkbox-group>
              
              <el-date-picker
                v-else-if="field.fieldType === 'date'" 
                v-model="dynamicForm[field.fieldName]" 
                type="date" 
                :placeholder="`请选择${field.fieldName}`" 
                style="width: 300px;"
              />
              
              <el-date-picker
                v-else-if="field.fieldType === 'datetime'" 
                v-model="dynamicForm[field.fieldName]" 
                type="datetime" 
                :placeholder="`请选择${field.fieldName}`" 
                style="width: 300px;"
              />
              
              <span v-else>未知字段类型</span>
              
              <!-- 对于有效期字段，添加特殊说明 -->
              <div v-if="field.fieldName === '有效期'" class="field-description">
                <small style="color: #999;">不填表示长期有效</small>
              </div>
            </el-form-item>
          </div>
        </div>
        
        <!-- 上传材料 -->
        <div v-for="(material, index) in requiredMaterials" :key="`material_${index}`" style="margin-bottom: 15px;">
          <el-form-item :label="material.materialName" :prop="`materials.${material.materialName}`" :rules="[{ required: material.isRequired, message: '请上传' + material.materialName, trigger: 'change' }]">
            <el-upload
              class="upload-demo"
              :action="uploadUrl"
              :headers="{ Authorization: `Bearer ${getToken()}` }"
              :on-success="(response, file) => handleUploadSuccess(response, file, material.materialName)"
              :on-remove="handleRemoveWithMaterialName(material.materialName)"
              :before-upload="(file) => beforeUpload(file, material.materialName)"
              :file-list="getFileList(material.materialName)"
              :accept="'.jpg,.png,.pdf'"
              :limit="1"
              :disabled="uploadButtonStates[material.materialName]"
              :auto-upload="true"
              list-type="text"
            >
              <el-button size="small" type="primary" :disabled="uploadButtonStates[material.materialName]">上传</el-button>
              <template #tip>
                <div class="el-upload__tip">支持jpg/png/pdf格式，文件大小不超过10M</div>
              </template>
            </el-upload>
          </el-form-item>
        </div>
        
        <el-form-item label="补充说明" prop="remark">
          <el-input 
            v-model="applicationForm.remark" 
            type="textarea" 
            placeholder="选填，≤200字" 
            :rows="4" 
            maxlength="200" 
            show-word-limit
            style="width: 500px;"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleCloseDialog">取消</el-button>
          <el-button 
            type="primary" 
            @click="submitApplicationForm" 
            :disabled="!canSubmit"
          >提交申请</el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 申请成功提示 -->
    <el-dialog title="申请提交成功" v-model="showSuccessDialog" width="500px" :show-close="false">
      <div style="text-align: center; padding: 40px;">
        <el-result icon="success" title="申请提交成功!" :sub-title="`本次申请扣除${applyCertificatePoints}积分`">
          <template #extra>
            <el-button type="primary" @click="continueApplication">继续申请</el-button>
            <el-button @click="viewMyApplications">查看我的申请</el-button>
          </template>
        </el-result>
      </div>
    </el-dialog>
    
    <!-- 子路由视图 -->
    <router-view />
    
    <!-- 积分获取弹窗 -->
    <PointsGetDialog
      v-model:visible="showPointsGetDialog"
      :required-points="applyCertificatePoints"
      :operation-type="'addTemplate'"
      :application-id="selectedTemplate.templateId"
      @close="closePointsGetDialog"
      @points-changed="handlePointsChanged" />
  </div>
</template>

<script setup name="CertificateApplication">
import { listCertificateTemplate, getCertificateTemplate } from "@/api/business/certificate/certificateTemplate";
import { addCertificateApplication, getUserPoints, submitApplication } from "@/api/business/certificate/certificateApplication";
import { getPointsRuleByCode } from "@/api/system/points";
import { getToken } from "@/utils/auth";
import { ElMessage, ElMessageBox } from "element-plus";
import { nextTick, ref, reactive, computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import PointsGetDialog from '@/components/PointsGetDialog/index.vue';
import Pagination from '@/components/Pagination';

const router = useRouter();
const route = useRoute();

// 添加申请证书所需积分
const applyCertificatePoints = ref(0);

// 添加计算属性来判断是否显示主内容
const showMainContent = computed(() => {
  // 检查当前路由是否匹配详情页面
  return !route.path.includes('/certificate/application/detail');
});

// 控制对话框显示
const showApplicationDialog = ref(false);
const showSuccessDialog = ref(false);

// 页面状态
const step = ref(1); // 1: 选择模板, 2: 填写信息, 3: 成功
const userPoints = ref(0); // 当前用户积分
const insufficientPoints = ref(false);

// 分页相关
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
});

// 积分获取弹窗相关
const showPointsGetDialog = ref(false);

// 模板选择相关
const templateList = ref([]);
const filteredTemplates = computed(() => templateList.value.filter(t => t.status === '0'));  // 计算属性：只显示已上架的证书模板
const templateLoading = ref(false);
const selectForm = ref({
  templateId: '',
  templateName: '',
  certificateType: ''
});
const selectRules = ref({
  templateId: [{ required: true, message: '请选择证书模板', trigger: 'change' }]
});

// 申请信息相关
const selectedTemplate = ref({});
const requiredMaterials = ref([]); // 根据选择的模板动态获取所需材料
const dynamicFields = ref([]); // 动态表单字段配置
const dynamicForm = ref({
  materials: {} // 存储上传的材料文件，用于Element Plus表单验证
}); // 动态表单数据
const dynamicFormRules = ref({}); // 动态表单验证规则
const applicationForm = ref({
  remark: '',
  materials: {} // 存储上传的材料文件
});
const applicationRules = ref({});

// 上传相关 - 使用项目通用上传服务
const uploadUrl = ref(import.meta.env.VITE_APP_BASE_API + '/common/upload'); // 根据文件上传服务说明，使用通用上传接口

// 添加状态码映射常量
const STATUS_CODES = {
  DRAFT: '0',           // 草稿
  PENDING_REVIEW: '1',  // 待审核 (Pending Review)
  APPROVED: '2',        // 已批准 (Approved)
  REJECTED: '3',        // 已驳回 (Rejected)
  ISSUED: '4'           // 已发放
};

// 状态码转中文描述的映射
const STATUS_DESC_MAP = {
  '0': '草稿',
  '1': '待审核',
  '2': '已批准',
  '3': '已驳回',
  '4': '已发放'
};

// 获取状态描述函数
function getStatusDescription(statusCode) {
  return STATUS_DESC_MAP[statusCode] || '未知状态';
}

// 计算属性：判断是否可以提交申请
const canSubmit = computed(() => {
  // 检查动态表单字段是否都已填写
  const dynamicFieldsFilled = dynamicFields.value.every(field => {
    if (field.required && !dynamicForm.value[field.fieldName]) {
      return false;
    }
    return true;
  });
  
  // 检查所有必传材料是否都已上传（处理 isRequired 和 required 两种字段名）
  const materialsUploaded = requiredMaterials.value.every(material => {
    const isRequired = material.isRequired || material.required || false;
    if (!isRequired) {
      return true; // 不是必传材料，直接返回true
    }
    // 检查材料是否已上传
    const uploaded = applicationForm.value.materials[material.materialName];
    return uploaded !== undefined && uploaded !== null && uploaded !== '';
  });
  
  return dynamicFieldsFilled && materialsUploaded;
});

// 计算属性：管理每个材料的上传按钮状态
const uploadButtonStates = computed(() => {
  const states = {};
  requiredMaterials.value.forEach(material => {
    // 如果材料已上传，则禁用上传按钮
    const uploaded = applicationForm.value.materials[material.materialName];
    states[material.materialName] = uploaded !== undefined && uploaded !== null && uploaded !== '';
  });
  return states;
});

/** 初始化方法 */
onMounted(() => {
  loadUserData();
  
  // 检查是否是重新申请，如果是则自动选择模板并打开申请对话框
  if (route.query.reapply && route.query.templateId) {
    // 延迟执行以确保数据已加载
    nextTick(() => {
      // 等待模板列表加载完成
      const checkAndSelectTemplate = () => {
        if (templateList.value.length > 0) {
          // 根据模板ID自动选择模板
          selectForm.value.templateId = parseInt(route.query.templateId);
          // 触发模板选择事件
          onTemplateSelect(parseInt(route.query.templateId));
          
          // 如果有之前申请的数据，尝试预填充表单
          if (route.state && route.state.applicationData) {
            // 将之前申请的数据存储起来，等模板加载完成后使用
            window.previousApplicationData = route.state.applicationData;
          }
          
          // 自动开始申请
          startApplication();
        } else {
          // 如果模板列表还没加载完，稍后再试
          setTimeout(checkAndSelectTemplate, 100);
        }
      };
      checkAndSelectTemplate();
    });
  }
});

/** 加载用户数据 */
function loadUserData() {
  // 先加载用户积分
  getUserPoints().then(response => {
    userPoints.value = response.data || 0; // 从AjaxResult的data字段获取积分值
    // 再加载申请所需积分
    loadApplyCertificatePoints().then(() => {
      // 最后加载模板列表
      loadTemplateList();
    });
  }).catch(error => {
    console.error('获取用户积分失败:', error);
    // 即使积分获取失败，也要加载申请所需积分
    loadApplyCertificatePoints().then(() => {
      // 最后加载模板列表
      loadTemplateList();
    }).catch(pointsError => {
      console.error('获取申请证书积分规则失败:', pointsError);
      // 即使获取积分规则失败，也要加载模板列表
      loadTemplateList();
    });
  });
}

/** 加载申请证书所需积分 */
function loadApplyCertificatePoints() {
  // 获取APPLY_CERTIFICATE规则的积分值
  return getPointsRuleByCode('APPLY_CERTIFICATE').then(response => {
    if(response && response.data && response.data.pointsValue !== undefined) {
      // 积分规则中的值通常为负数表示扣减，取绝对值得到所需积分
      applyCertificatePoints.value = Math.abs(response.data.pointsValue) || 0;
    } else {
      // 如果获取不到积分规则，则使用默认值
      applyCertificatePoints.value = 0;
    }
  }).catch(error => {
    console.error('获取申请证书积分规则失败:', error);
    // 如果获取失败，则使用默认值
    applyCertificatePoints.value = 0;
    return Promise.resolve(); // 确保返回Promise
  });
}

/** 加载模板列表 */
function loadTemplateList() {
  templateLoading.value = true;
  const query = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  };
  listCertificateTemplate(query).then(response => {
    templateList.value = response.rows;
    pagination.total = response.total;
    templateLoading.value = false;
  }).catch(error => {
    templateLoading.value = false;
    console.error('加载模板列表失败:', error);
  });
}

/** 模板选择改变事件 */
function onTemplateSelect(templateId) {
  const template = templateList.value.find(t => t.templateId === templateId && t.status === '0');  // 只允许选择已上架的模板
  if (template) {
    selectForm.value.templateName = template.templateName;
    selectForm.value.certificateType = template.certificateType;
  }
}

/** 开始申请 */
function startApplication() {
  if (!selectForm.value.templateId) {
    ElMessage.warning('请先选择证书模板');
    return;
  }
  
  // 获取选中的模板详情，确保是已上架的模板
  const selected = templateList.value.find(t => t.templateId === selectForm.value.templateId && t.status === '0');
  if (!selected) {
    ElMessage.warning('所选证书模板不存在或未上架');
    return;
  }
  
  getCertificateTemplate(selectForm.value.templateId).then(response => {
    selectedTemplate.value = response.data;
    
    // 确保选中的模板是已上架状态
    if (selectedTemplate.value.status !== '0') {
      ElMessage.warning('所选证书模板已下架，无法申请');
      return;
    }
    
    // 解析模板中的必需材料
    try {
      let rawMaterials = JSON.parse(selectedTemplate.value.applyRequiredFields || '[]');
      // 标准化材料配置，处理 isRequired 和 required 字段
      requiredMaterials.value = rawMaterials.map(material => ({
        ...material,
        isRequired: material.isRequired || material.required || false,  // 兼容两种字段名
        materialName: material.materialName || material.name || material.label || '未知材料' // 兼容多种名称字段
      }));
    } catch (e) {
      console.error('解析申请必需字段失败:', e);
      requiredMaterials.value = [];
    }
    
    // 解析模板中的表单字段配置
    try {
      const templateFieldsConfig = JSON.parse(selectedTemplate.value.templateFields || 'null');
      if (templateFieldsConfig && templateFieldsConfig.formFields) {
        dynamicFields.value = templateFieldsConfig.formFields;
        
        // 初始化动态表单数据和验证规则
        dynamicForm.value = {};
        dynamicFormRules.value = {};
        
        dynamicFields.value.forEach(field => {
          // 初始化字段值
          dynamicForm.value[field.fieldName] = field.defaultValue || '';
          
          // 如果字段是必填的，添加验证规则
          if (field.required) {
            dynamicFormRules.value[field.fieldName] = [
              { required: true, message: `请输入${field.fieldName}`, trigger: 'blur' }
            ];
            
            // 根据字段类型添加额外验证
            if (field.fieldType === 'input' && field.validationPattern) {
              dynamicFormRules.value[field.fieldName].push({
                pattern: new RegExp(field.validationPattern),
                message: field.validationMessage || `请输入有效的${field.fieldName}`,
                trigger: 'blur'
              });
            }
          }
        });
      } else if (Array.isArray(templateFieldsConfig)) {
        // 如果直接是数组格式（兼容现有数据）
        dynamicFields.value = templateFieldsConfig.map(field => ({
          ...field,
          required: field.isRequired // 将 isRequired 转换为 required
        }));
        
        // 初始化动态表单数据和验证规则
        dynamicForm.value = {};
        dynamicFormRules.value = {};
        
        dynamicFields.value.forEach(field => {
          // 初始化字段值
          dynamicForm.value[field.fieldName] = field.defaultValue || '';
          
          // 如果字段是必填的，添加验证规则
          if (field.required) {
            dynamicFormRules.value[field.fieldName] = [
              { required: true, message: `请输入${field.fieldName}`, trigger: 'blur' }
            ];
            
            // 根据字段类型添加额外验证
            if (field.fieldType === 'input' && field.validationPattern) {
              dynamicFormRules.value[field.fieldName].push({
                pattern: new RegExp(field.validationPattern),
                message: field.validationMessage || `请输入有效的${field.fieldName}`,
                trigger: 'blur'
              });
            }
          }
        });
      } else {
        // 如果模板没有配置字段，则使用默认字段（兼容旧模板）
        dynamicFields.value = [
          { fieldName: '真实姓名', fieldType: 'input', required: true },
          { fieldName: '身份证号', fieldType: 'input', required: true },
          { fieldName: '联系电话', fieldType: 'input', required: false },
          { fieldName: '所属单位', fieldType: 'input', required: false }
        ];
        
        // 初始化动态表单数据和验证规则
        dynamicForm.value = {};
        dynamicFormRules.value = {};
        
        dynamicFields.value.forEach(field => {
          // 初始化字段值
          dynamicForm.value[field.fieldName] = '';
          if (field.required) {
            dynamicFormRules.value[field.fieldName] = [
              { required: true, message: `请输入${field.fieldName}`, trigger: 'blur' }
            ];
          }
        });
      }
    } catch (e) {
      console.error('解析模板字段配置失败:', e);
      // 如果解析失败，使用默认字段
      dynamicFields.value = [
        { fieldName: '真实姓名', fieldType: 'input', required: true },
        { fieldName: '身份证号', fieldType: 'input', required: true },
        { fieldName: '联系电话', fieldType: 'input', required: false },
        { fieldName: '所属单位', fieldType: 'input', required: false }
      ];
    }
    
    // 如果是重新申请且有之前的数据，预填充表单
    if (route.query.reapply && window.previousApplicationData) {
      try {
        const previousData = window.previousApplicationData;
        
        // 解析之前申请的表单数据
        if (previousData.applicationData) {
          const previousFormData = JSON.parse(previousData.applicationData);
          
          // 遍历当前动态字段，用之前的数据填充
          for (const fieldName in previousFormData) {
            if (dynamicForm.value.hasOwnProperty(fieldName)) {
              // 只有当字段存在且之前有值时才填充
              if (previousFormData[fieldName] !== undefined && previousFormData[fieldName] !== null) {
                dynamicForm.value[fieldName] = previousFormData[fieldName];
              }
            }
          }
        }
        
        // 解析之前上传的材料信息
        if (previousData.applicationContent) {
          try {
            const previousMaterials = JSON.parse(previousData.applicationContent);
            
            // 遍历当前需要的材料，用之前的数据填充
            requiredMaterials.value.forEach(material => {
              if (previousMaterials[material.materialName]) {
                // 更新申请表单中的材料信息
                applicationForm.value.materials = { 
                  ...applicationForm.value.materials, 
                  [material.materialName]: previousMaterials[material.materialName]
                };
                
                // 更新动态表单中的材料信息
                dynamicForm.value.materials = { 
                  ...dynamicForm.value.materials, 
                  [material.materialName]: previousMaterials[material.materialName]
                };
              }
            });
          } catch (parseError) {
            console.error('解析之前申请的材料信息失败:', parseError);
          }
        }
        
        // 清除临时存储的数据
        delete window.previousApplicationData;
      } catch (parseError) {
        console.error('解析之前申请的数据失败:', parseError);
      }
    }
    
    // 检查积分是否充足
    const insufficient = userPoints.value < applyCertificatePoints.value;
    insufficientPoints.value = insufficient;
    
    showApplicationDialog.value = true; // 显示申请对话框
  }).catch(error => {
    console.error('获取模板详情失败:', error);
    ElMessage.error('获取模板详情失败');
  });
}

/** 选择模板 */
function selectTemplate(template) {
  // 确保只有已上架的模板可以被选择
  if (template.status !== '0') {
    ElMessage.warning('该证书模板未上架，无法申请');
    return;
  }
  selectForm.value.templateId = template.templateId;
  selectForm.value.templateName = template.templateName;
  selectForm.value.certificateType = template.certificateType;
  startApplication();
}

/** 文件上传成功回调 */
async function handleUploadSuccess(response, file, materialName) {
  if (response.code === 200) {
    // 处理不同的响应结构
    let fileName;
    if (response.data && response.data.fileName) {
      fileName = response.data.fileName;
    } else if (response.fileName) {
      fileName = response.fileName;
    } else if (response.data && typeof response.data === 'string') {
      // 如果响应的data是文件名字符串
      fileName = response.data;
    } else {
      // 尝试从response中找寻可能的文件名字段
      fileName = file.name;
    }
    
    // 使用Vue的响应式更新方法来确保状态更新
    applicationForm.value.materials = { ...applicationForm.value.materials, [materialName]: fileName };
    // 也需要更新dynamicForm，以便Element Plus表单验证能够正确工作
    dynamicForm.value.materials = { ...dynamicForm.value.materials, [materialName]: fileName };
    
    // 确保DOM更新
    await nextTick();
    ElMessage.success(`${materialName}上传成功`);
  } else {
    ElMessage.error(`${materialName}上传失败: ${response.msg || '未知错误'}`);
  }
}

/** 文件移除回调 */
async function handleRemove(materialName) {
  // 使用解构方式创建新对象以触发响应式更新
  const { [materialName]: removed1, ...remaining1 } = applicationForm.value.materials;
  applicationForm.value.materials = remaining1;
  
  const { [materialName]: removed2, ...remaining2 } = dynamicForm.value.materials;
  dynamicForm.value.materials = remaining2;
  
  // 确保DOM更新
  await nextTick();
}

/** 获取特定材料的文件列表 */
function getFileList(materialName) {
  // 尝试获取材料名称对应的文件
  const fileName = applicationForm.value.materials[materialName];
  if (fileName) {
    // 构建完整的文件URL
    let fileUrl;
    if (fileName.startsWith('http') || fileName.startsWith('/')) {
      fileUrl = fileName;
    } else {
      fileUrl = import.meta.env.VITE_APP_BASE_API + '/profile/' + fileName;
    }
    // 返回Element Plus Upload组件期望的文件对象格式
    return [{
      name: fileName,
      url: fileUrl,
      status: 'success',
      uid: `uploaded-${materialName}-${Date.now()}` // 使用材料名称和时间戳确保唯一性
    }];
  }
  return [];
}

/** 判断材料是否已上传 */
function isMaterialUploaded(materialName) {
  return !!applicationForm.value.materials[materialName];
}

/** 上传前检查 */
function beforeUpload(file, materialName) {
  // 检查是否已有该材料的文件，如果有则阻止上传
  if (applicationForm.value.materials[materialName]) {
    ElMessage.warning(`${materialName} 已有文件，如需更换请先删除原文件`);
    return false;
  }
  return true;
}

/** 创建移除回调函数 */
function handleRemoveWithMaterialName(materialName) {
  return (file, fileList) => {
    handleRemove(materialName);
  };
}

/** 关闭申请对话框 */
function handleCloseDialog() {
  showApplicationDialog.value = false;
  // 重置动态表单数据
  dynamicForm.value = {
    materials: {} // 保持materials对象
  };
  dynamicFields.value = [];
  dynamicFormRules.value = {};
  requiredMaterials.value = [];
  applicationForm.value = {
    remark: '',
    materials: {}
  };
}

/** 打开获取积分弹窗 */
function openPointsGetDialog() {
  showPointsGetDialog.value = true;
}

/** 关闭获取积分弹窗 */
function closePointsGetDialog() {
  showPointsGetDialog.value = false;
}

/** 积分发生变化时的回调 */
function handlePointsChanged(newPoints) {
  userPoints.value = newPoints;
  
  // 如果积分已足够，询问是否继续执行原操作
  if (newPoints >= applyCertificatePoints.value) {
    ElMessageBox.confirm(
      `您已获取积分，当前积分为 ${newPoints} 分，是否继续申请操作？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(() => {
      // 关闭积分获取弹窗
      closePointsGetDialog();
      // 检查是否可以提交
      if (canSubmit.value) {
        submitApplicationForm();
      }
    }).catch(() => {
      // 用户取消操作，但不关闭积分获取弹窗
      console.log('用户取消继续操作');
    });
  }
}

/** 提交申请 */
function submitApplicationForm() {
  if (!canSubmit.value) {
    ElMessage.warning('请完善申请信息后再提交');
    return;
  }
  
  // 检查用户积分
  if (userPoints.value < applyCertificatePoints.value) {
    ElMessage.warning(`提交申请需要消耗 ${applyCertificatePoints.value} 积分，您的当前积分为 ${userPoints.value} 分，积分不足！`);
    return;
  }
  
  // 检查所选模板是否仍然上架
  const selected = templateList.value.find(t => t.templateId === selectedTemplate.value.templateId && t.status === '0');
  if (!selected) {
    ElMessage.warning('所选证书模板已下架，无法提交申请');
    return;
  }
  
  // 积分充足，显示确认对话框
  ElMessageBox.confirm(
    `确认要提交申请吗？此操作需要消耗 ${applyCertificatePoints.value} 积分，您的当前积分为 ${userPoints.value} 分。`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const applicationData = {
      templateId: selectedTemplate.value.templateId,
      templateName: selectedTemplate.value.templateName,
      applicationData: JSON.stringify(dynamicForm.value), // 保存动态表单数据到正确的字段
      applicationContent: JSON.stringify(applicationForm.value.materials), // 上传的材料（已使用通用上传服务）
      remark: applicationForm.value.remark,
      applicationPoints: applyCertificatePoints.value, // 使用动态获取的积分值
      applicationStatus: STATUS_CODES.PENDING_REVIEW // 使用常量，1 - 待审核
    };

    submitApplication(applicationData).then(response => {
      ElMessage.success('申请提交成功！');
      showApplicationDialog.value = false; // 关闭申请对话框
      showSuccessDialog.value = true; // 显示成功对话框
    }).catch(error => {
      ElMessage.error('申请提交失败: ' + error.message);
    });
  }).catch(() => {
    // 用户取消操作
    console.log('用户取消提交申请');
  });
}

/** 查看我的申请 */
function viewMyApplications() {
  router.push('/certificate/my-applications');
}

/** 继续申请 */
function continueApplication() {
  showSuccessDialog.value = false; // 关闭成功对话框
  selectForm.value.templateId = ''; // 重置选择的模板
  loadTemplateList(); // 重新加载模板列表
}

// 分页大小改变事件
function handleSizeChange(val) {
  pagination.pageSize = val;
  loadTemplateList();
}

// 当前页改变事件
function handleCurrentChange(val) {
  pagination.pageNum = val;
  loadTemplateList();
}
</script>

<style scoped>
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

.points-warning {
  margin-bottom: 20px;
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
}

.field-item.full-width {
  grid-column: 1 / -1; /* 跨越所有列，独占一行 */
  justify-content: flex-start; /* 左对齐 */
  text-align: left; /* 文本左对齐 */
}

.field-form-item {
  margin-bottom: 10px;
}

.field-description {
  margin-top: 5px;
}

/* 若依标准表格样式 */
.el-table {
  margin-bottom: 20px;
}
</style>
