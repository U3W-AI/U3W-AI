<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="证书名称" prop="templateName">
        <el-input
            v-model="queryParams.templateName"
            placeholder="请输入证书名称"
            clearable
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
            type="primary"
            plain
            icon="Plus"
            @click="handleAdd"
            v-hasPermi="['business:certificate:template:add']"
        >新增</el-button>
      </el-col>
      <!-- 添加一个刷新权限的按钮 -->
      <el-col :span="1.5">
        <el-button
            type="info"
            plain
            icon="Refresh"
            @click="refreshPermissions"
        >刷新权限</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="certificateTemplateList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="证书名称" align="center" prop="templateName" />
      <el-table-column label="证书类型" align="center" prop="certificateType" />
      <el-table-column label="上架状态" align="center" prop="status">
        <template #default="scope">
          <el-tag
              v-if="scope.row.status === '0'"
              type="success"
          >已上架</el-tag>
          <el-tag
              v-else
              type="info"
          >未上架</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
              size="small"
              type="text"
              icon="Edit"
              @click="handleUpdate(scope.row)"
              v-hasPermi="['business:certificate:template:edit']"
          >修改</el-button>
          <el-button
              size="small"
              type="text"
              icon="Delete"
              @click="handleDelete(scope.row)"
              v-hasPermi="['business:certificate:template:remove']"
          >删除</el-button>
          <el-button
              size="small"
              type="text"
              @click="toggleShelf(scope.row)"
              v-if="scope.row.status === '0'"
              v-hasPermi="['business:certificate:template:edit']"
          >下架</el-button>
          <el-button
              size="small"
              type="text"
              @click="toggleShelf(scope.row)"
              v-else
              v-hasPermi="['business:certificate:template:edit']"
          >上架</el-button>
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

    <!-- 添加或修改证书模板对话框 -->
    <el-dialog :title="title" v-model="open" width="800px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane label="基础信息" name="basic">
            <el-form-item label="证书名称" prop="templateName">
              <el-input v-model="form.templateName" placeholder="请输入2-50字的证书名称" maxlength="50" />
            </el-form-item>
            <el-form-item label="证书类型" prop="certificateType">
              <el-select v-model="form.certificateType" placeholder="请选择证书类型" @change="handleCertificateTypeChange">
                <el-option label="职业类" value="职业类" />
                <el-option label="学历类" value="学历类" />
                <el-option label="企业类" value="企业类" />
                <el-option label="其他" value="其他" />
              </el-select>
            </el-form-item>
            <el-form-item label="审核周期" prop="reviewCycle">
              <el-input v-model="form.reviewCycle" placeholder="示例：1-3个工作日" />
            </el-form-item>
            <el-form-item label="上架状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio label="0">上架</el-radio>
                <el-radio label="1">未上架</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="证书描述" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="选填，简要说明证书用途，≤200字" maxlength="200" :rows="4" />
            </el-form-item>
          </el-tab-pane>

          <el-tab-pane label="证书底版" name="background">
            <el-upload
                class="certificate-bg-uploader"
                :action="uploadUrl"
                :headers="uploadHeaders"
                :on-success="handleBgUploadSuccess"
                :on-error="handleBgUploadError"
                :before-upload="beforeBgUpload"
                :file-list="bgFileList"
                :limit="1"
                accept="image/*"
                list-type="picture-card"
            >
              <el-icon><Plus /></el-icon>
              <template #tip>
                <div class="el-upload__tip">
                  上传证书底版图片，支持jpg/png等格式，大小不超过5MB
                </div>
              </template>
            </el-upload>
            <div v-if="form.certificateBgImage" class="bg-preview-container">
              <h4>当前底版预览：</h4>
              <img :src="form.certificateBgImage" alt="证书底版" class="bg-preview-image" />
            </div>
          </el-tab-pane>

          <el-tab-pane label="所需材料配置" name="materials">
            <el-button type="primary" @click="addMaterialRow" size="small" style="margin-bottom: 10px;">添加材料行</el-button>
            <el-button @click="removeMaterialRows" size="small" style="margin-bottom: 10px; margin-left: 10px;">批量删除</el-button>

            <el-table :data="form.materials" style="width: 100%">
              <el-table-column prop="index" label="序号" width="60">
                <template #default="scope">{{ scope.$index + 1 }}</template>
              </el-table-column>
              <el-table-column prop="materialName" label="材料名称" width="200">
                <template #default="scope">
                  <el-input v-model="scope.row.materialName" placeholder="请输入材料名称" />
                </template>
              </el-table-column>
              <el-table-column prop="isRequired" label="必填" width="100">
                <template #default="scope">
                  <el-checkbox v-model="scope.row.isRequired">是</el-checkbox>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="scope">
                  <el-button size="small" type="danger" @click="removeMaterialRow(scope.$index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="表单字段配置" name="fields">
            <el-button type="primary" @click="addFieldRow" size="small" style="margin-bottom: 10px;">添加字段行</el-button>
            <el-button @click="removeFieldRows" size="small" style="margin-bottom: 10px; margin-left: 10px;">批量删除</el-button>

            <el-table :data="form.fields" style="width: 100%">
              <el-table-column prop="index" label="序号" width="60">
                <template #default="scope">{{ scope.$index + 1 }}</template>
              </el-table-column>
              <el-table-column prop="fieldName" label="字段名称" width="150">
                <template #default="scope">
                  <el-input v-model="scope.row.fieldName" :disabled="scope.row.fixed" placeholder="请输入字段名称" />
                </template>
              </el-table-column>
              <el-table-column prop="fieldType" label="字段类型" width="120">
                <template #default="scope">
                  <el-select v-model="scope.row.fieldType" :disabled="scope.row.fixed" placeholder="请选择类型">
                    <el-option label="文本" value="text" />
                    <el-option label="手机号" value="phone" />
                    <el-option label="邮箱" value="email" />
                    <el-option label="数字" value="number" />
                    <el-option label="日期" value="date" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column prop="isRequired" label="必填" width="100">
                <template #default="scope">
                  <el-checkbox v-model="scope.row.isRequired" :disabled="scope.row.fixed">是</el-checkbox>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180">
                <template #default="scope">
                  <el-button size="small" type="primary" @click="openFieldEditor">编辑字段位置</el-button>
                  <el-button size="small" type="danger" @click="removeFieldRow(scope.$index)" :disabled="scope.row.fixed">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button @click="previewCertificate" :disabled="!canPreview">预览证书样式</el-button>
          <el-button type="primary" @click="submitForm">保 存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 可视化字段位置编辑器 -->
    <el-dialog title="可视化字段位置编辑器" v-model="fieldEditorVisible" width="90%" top="5vh" fullscreen>
      <certificate-field-editor
          :certificateBgImage="form.certificateBgImage"
          :templateFields="form.fields"
          :initialFieldPositions="currentFieldPositions"
          @save="onSaveFieldPositions"
          @cancel="fieldEditorVisible = false"
      />
    </el-dialog>

    <!-- 预览证书对话框 -->
    <el-dialog title="预览证书" v-model="previewVisible" width="80%" top="5vh" fullscreen>
      <certificate-preview
          :certificateBgImage="form.certificateBgImage"
          :fieldPositions="parsedFieldPositions"
          :previewWidth="800"
          :previewHeight="600"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="previewVisible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 积分获取弹窗 -->
    <PointsGetDialog
        v-model:visible="showPointsGetDialog"
        :required-points="shelfTemplatePoints"
        :operation-type="operationType"
        :application-id="currentApplicationId"
        @close="closePointsGetDialog"
        @points-changed="handlePointsChanged" />
  </div>
</template>

<script>
import { listCertificateTemplate, getCertificateTemplate, addCertificateTemplate, updateCertificateTemplate, delCertificateTemplate } from "@/api/business/certificate/certificateTemplate";
import { getUserPoints } from "@/api/business/certificate/certificateApplication";
import { getPointsRuleByCode } from "@/api/system/points";
import useUserStore from "@/store/modules/user";
import { getToken } from "@/utils/auth";
import { Plus } from "@element-plus/icons-vue";
import CertificateFieldEditor from "@/views/business/certificate/components/certificateFieldEditor.vue";
import CertificatePreview from "@/views/business/certificate/components/certificatePreview.vue";
import PointsGetDialog from '@/components/PointsGetDialog';

export default {
  name: "CertificateTemplate",
  components: {
    CertificateFieldEditor,
    CertificatePreview,
    PointsGetDialog
  },
  dicts: ['sys_normal_disable'],
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
      // 证书模板表格数据
      certificateTemplateList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 当前激活的tab
      activeTab: 'basic',
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        templateName: null,
        certificateType: null
      },
      // 表单参数
      form: {
        materials: [],
        fields: [],
        certificateBgImage: null,
        fieldPositions: []
      },
      // 证书底版上传相关
      uploadUrl: import.meta.env.VITE_APP_BASE_API + "/common/upload", // 上传地址
      uploadHeaders: {
        Authorization: "Bearer " + getToken()
      },
      bgFileList: [], // 底版文件列表
      // 字段位置编辑相关
      fieldPositionDialogVisible: false,
      currentField: null,
      currentFieldIndex: -1,
      // 可视化字段编辑器相关
      fieldEditorVisible: false,
      currentFieldPositions: [],
      // 预览证书相关
      previewVisible: false,
      // 表单校验
      rules: {
        templateName: [
          { required: true, message: "模板名称不能为空", trigger: "blur" },
          { min: 2, max: 50, message: "长度在 2 到 50 个字符", trigger: "blur" }
        ],
        certificateType: [
          { required: true, message: "证书类型不能为空", trigger: "change" }
        ]
      },
      // 用户积分
      userPoints: 0,
      // 上架证书模板所需积分
      shelfTemplatePoints: 50,
      // 积分获取弹窗相关
      showPointsGetDialog: false,
      operationType: '',
      currentApplicationId: null
    };
  },
  created() {
    this.getList();
    this.loadUserPoints();
    this.loadShelfTemplatePoints();
    // 检查是否从积分获取页面返回
    this.checkReturnFromPointsPage();
  },

  watch: {
    // 监听用户积分变化
    userPoints: {
      handler(newVal) {
        // 如果是从积分获取页面返回且积分已增加，询问是否继续操作
        if ((this.$route.query.from === 'addTemplate' ||
                this.$route.query.from === 'shelfTemplate') &&
            newVal >= this.shelfTemplatePoints) {
          this.continueOperation();
        }
      }
    }
  },

  computed: {
    canPreview() {
      // 检查必填字段是否已填写，并且有证书底版和字段位置配置
      if (!this.form.templateName ||
          !this.form.certificateType ||
          !this.form.certificateBgImage) {
        return false;
      }

      // 解析字段位置配置
      try {
        const positions = typeof this.form.fieldPositions === 'string'
            ? JSON.parse(this.form.fieldPositions)
            : this.form.fieldPositions;

        return Array.isArray(positions) && positions.length > 0;
      } catch (e) {
        return false;
      }
    },
    parsedFieldPositions() {
      // 解析字段位置配置
      try {
        const positions = typeof this.form.fieldPositions === 'string'
            ? JSON.parse(this.form.fieldPositions)
            : this.form.fieldPositions;

        if (Array.isArray(positions)) {
          return positions;
        }
        return [];
      } catch (e) {
        console.error('解析字段位置配置失败:', e);
        return [];
      }
    }
  },

  methods: {
    /** 查询证书模板列表 */
    getList() {
      this.loading = true;
      listCertificateTemplate(this.queryParams).then(response => {
        this.certificateTemplateList = response.rows;
        this.total = response.total;
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

    /** 加载上架证书模板所需积分 */
    loadShelfTemplatePoints() {
      // 获取SHELF_CERTIFICATE_TEMPLATE规则的积分值
      getPointsRuleByCode('SHELF_CERTIFICATE_TEMPLATE').then(response => {
        if(response && response.data && response.data.pointsValue !== undefined) {
          // 积分规则中的值通常为负数表示扣减，取绝对值得到所需积分
          this.shelfTemplatePoints = Math.abs(response.data.pointsValue) || 50;
        } else {
          this.shelfTemplatePoints = 50; // 默认值
        }
      }).catch(error => {
        console.error('获取上架证书模板积分规则失败:', error);
        this.shelfTemplatePoints = 50; // 默认值
      });
    },

    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      // 保存当前证书类型，以便在重置后检查是否需要添加固定字段
      const currentCertificateType = this.form.certificateType;

      this.form = {
        templateId: null,
        templateName: null,
        certificateType: null,
        reviewCycle: null,
        status: "0",
        remark: null,
        materials: [],
        fields: [],
        certificateBgImage: null,
        fieldPositions: []
      };
      this.bgFileList = []; // 清空底版文件列表
      this.activeTab = 'basic';
      this.resetForm("form");

      // 如果之前的证书类型已选择（非空），则添加对应固定字段
      if (currentCertificateType) {
        this.$nextTick(() => {
          if (currentCertificateType === '学历类') {
            this.addAcademicFixedFields();
            this.addCommonFixedFields();  // 添加有效期字段
          } else if (currentCertificateType === '企业类') {
            this.addEnterpriseFixedFields();
            this.addCommonFixedFields();  // 添加有效期字段
          } else if (currentCertificateType === '职业类') {  // 新增：处理职业类证书
            this.addProfessionalFixedFields();
            this.addCommonFixedFields();  // 添加有效期字段
          } else if (currentCertificateType === '其他') {
            this.addCommonFixedFields();  // 添加有效期字段
          }
        });
      }
    },
    /** 检查是否从积分获取页面返回 */
    checkReturnFromPointsPage() {
      // 如果是从积分获取页面返回，检查积分是否足够
      if (this.$route.query.from &&
          (this.$route.query.from === 'addTemplate' || this.$route.query.from === 'shelfTemplate')) {
        // 清除路由参数，避免重复提示
        const newQuery = { ...this.$route.query };
        delete newQuery.from;
        delete newQuery.templateId;

        if (JSON.stringify(newQuery) !== JSON.stringify({})) {
          this.$router.replace({ query: newQuery });
        }

        // 检查积分是否已足够执行操作
        if (this.userPoints >= this.shelfTemplatePoints) {
          this.continueOperation();
        }
      }
    },

    /** 继续执行原操作 */
    continueOperation() {
      // 询问用户是否继续执行原操作
      this.$confirm(`您已获取积分，当前积分为 ${this.userPoints} 分，是否继续执行原操作？`, "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        // 根据来源执行不同操作
        if (this.$route.query.from === 'addTemplate') {
          // 执行新增模板操作
          this.submitForm();
        } else if (this.$route.query.from === 'shelfTemplate' && this.$route.query.templateId) {
          // 执行上架模板操作
          this.performShelfTemplate(this.$route.query.templateId);
        }
      }).catch(() => {
        // 用户取消操作
        console.log('用户取消继续操作');
      });
    },

    /** 上传底版前的检查 */
    beforeBgUpload(file) {
      const isImage = file.type.startsWith('image/');
      const isLt5M = file.size / 1024 / 1024 < 5;

      if (!isImage) {
        this.$message.error('上传文件只能是图片格式!');
        return false;
      }
      if (!isLt5M) {
        this.$message.error('上传文件大小不能超过 5MB!');
        return false;
      }
      return true;
    },

    /** 底版上传成功回调 */
    handleBgUploadSuccess(response, file, fileList) {
      if (response.code === 200) {
        this.form.certificateBgImage = response.fileName; // 保存图片路径
        this.$message.success('底版上传成功!');
      } else {
        this.$message.error(response.msg || '底版上传失败!');
      }
    },

    /** 底版上传失败回调 */
    handleBgUploadError(err) {
      this.$message.error('底版上传失败!');
      console.error('上传错误:', err);
    },



    /** 打开可视化字段编辑器 */
    openFieldEditor() {
      // 将现有的字段位置配置转换为可视化编辑器需要的格式
      this.currentFieldPositions = [];

      // 如果已有字段位置配置，加载它们
      try {
        if (this.form.fieldPositions) {
          const parsedPositions = typeof this.form.fieldPositions === 'string'
              ? JSON.parse(this.form.fieldPositions)
              : this.form.fieldPositions;
          this.currentFieldPositions = Array.isArray(parsedPositions) ? parsedPositions : [];
        }
      } catch (e) {
        console.error('解析字段位置配置失败:', e);
        this.currentFieldPositions = [];
      }

      this.fieldEditorVisible = true;
    },

    /** 保存字段位置配置 */
    onSaveFieldPositions(positions) {
      this.form.fieldPositions = JSON.stringify(positions);
      this.fieldEditorVisible = false;
      this.$message.success('字段位置保存成功');
    },

    /** 预览证书 */
    previewCertificate() {
      // 验证是否可以预览
      if (!this.canPreview) {
        this.$message.warning('请先填写必填字段、上传证书底版并编辑字段位置');
        return;
      }

      // 打开预览对话框
      this.previewVisible = true;
    },

    /** 执行上架模板操作 */
    async performShelfTemplate(templateId) {
      try {
        // 找到对应的模板行
        const row = this.certificateTemplateList.find(item => item.templateId == templateId);
        if (row) {
          // 确认上架操作
          this.$confirm(`确认要将"${row.templateName}"上架吗？此操作需要消耗 ${this.shelfTemplatePoints} 积分，您的当前积分为 ${this.userPoints} 分。`, "提示", {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            type: "warning"
          }).then(() => {
            row.status = '0'; // 上架状态
            // 证书类型保持中文值，无需转换
            updateCertificateTemplate(row).then(response => {
              this.$modal.msgSuccess(`上架成功`);
              this.getList();
            });
          }).catch(() => {});
        }
      } catch (error) {
        this.$modal.msgError("上架模板失败: " + error.message);
      }
    },

    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.templateId)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加证书模板";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const templateId = row.templateId || this.ids
      getCertificateTemplate(templateId).then(response => {
        this.form = response.data;
        // 解析材料和字段配置
        try {
          this.form.materials = JSON.parse(this.form.applyRequiredFields || '[]');
        } catch (e) {
          this.form.materials = [];
        }
        try {
          this.form.fields = JSON.parse(this.form.templateFields || '[]');
        } catch (e) {
          this.form.fields = [];
        }
        // 解析字段位置配置
        let fieldPositionsParsed;
        try {
          fieldPositionsParsed = JSON.parse(this.form.fieldPositions || '[]');
        } catch (e) {
          fieldPositionsParsed = [];
        }
        // 确保fieldPositions始终是字符串格式用于表单提交
        this.form.fieldPositions = Array.isArray(fieldPositionsParsed) ?
            JSON.stringify(fieldPositionsParsed) : this.form.fieldPositions;
        // 根据证书类型添加对应的固定字段
        if (this.form.certificateType === '学历类') {
          this.addAcademicFixedFields();
          this.addCommonFixedFields();  // 添加有效期字段
        } else if (this.form.certificateType === '企业类') {
          this.addEnterpriseFixedFields();
          this.addCommonFixedFields();  // 添加有效期字段
        } else if (this.form.certificateType === '职业类') {  // 新增：处理职业类证书
          this.addProfessionalFixedFields();
          this.addCommonFixedFields();  // 添加有效期字段
        } else if (this.form.certificateType === '其他') {
          this.addCommonFixedFields();  // 添加有效期字段
        }
        // 设置底版文件列表（如果存在底版图片）
        if (this.form.certificateBgImage) {
          this.bgFileList = [{
            name: 'certificate_bg',
            url: this.form.certificateBgImage
          }];
        }
        this.open = true;
        this.title = "修改证书模板";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          // 合并材料和字段配置到JSON字符串
          this.form.applyRequiredFields = JSON.stringify(this.form.materials);
          this.form.templateFields = JSON.stringify(this.form.fields);
          // 确保fieldPositions已经是字符串格式，避免双重序列化
          if (typeof this.form.fieldPositions !== 'string') {
            this.form.fieldPositions = JSON.stringify(this.form.fieldPositions);
          }

          if (this.form.templateId != null) {
            updateCertificateTemplate(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            // 在新增前检查积分
            this.checkPointsAndSubmit();
          }
        }
      });
    },

    /** 检查积分并提交新增 */
    checkPointsAndSubmit() {
      // 检查用户积分
      if (this.userPoints < this.shelfTemplatePoints) {
        // 积分不足，打开积分获取弹窗
        this.operationType = 'addTemplate';
        this.currentApplicationId = null;
        this.showPointsGetDialog = true;
        return;
      }

      // 积分充足，显示确认对话框
      this.$confirm(`此操作需要消耗 ${this.shelfTemplatePoints} 积分，您的当前积分为 ${this.userPoints} 分，确认继续吗？`, "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        // 执行新增操作
        addCertificateTemplate(this.form).then(response => {
          this.$modal.msgSuccess("新增成功");
          this.open = false;
          this.getList();
        }).catch(error => {
          this.$modal.msgError("新增失败: " + error.message);
        });
      }).catch(() => {
        // 用户取消操作
      });
    },

    /** 关闭获取积分弹窗 */
    closePointsGetDialog() {
      this.showPointsGetDialog = false;
      this.operationType = '';
      this.currentApplicationId = null;
    },

    /** 积分发生变化时的回调 */
    handlePointsChanged(newPoints) {
      this.userPoints = newPoints;

      // 如果积分已足够，询问是否继续执行原操作
      if (newPoints >= this.shelfTemplatePoints) {
        this.$confirm(`您已获取积分，当前积分为 ${newPoints} 分，是否继续执行原操作？`, "提示", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          // 关闭积分获取弹窗
          this.closePointsGetDialog();
          // 根据操作类型执行对应操作
          if (this.operationType === 'addTemplate') {
            // 执行新增操作
            addCertificateTemplate(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            }).catch(error => {
              this.$modal.msgError("新增失败: " + error.message);
            });
          } else if (this.operationType === 'shelfTemplate' && this.currentApplicationId) {
            // 执行上架模板操作
            this.performShelfTemplate(this.currentApplicationId);
          }
        }).catch(() => {
          // 用户取消操作，但不关闭积分获取弹窗
          console.log('用户取消继续操作');
        });
      }
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const templateIds = row.templateId || this.ids;
      this.$confirm('是否确认删除证书模板编号为"' + templateIds + '"的数据项?', "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(function() {
        return delCertificateTemplate(templateIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 切换上下架状态 */
    toggleShelf(row) {
      const status = row.status === '0' ? '1' : '0';
      const statusText = row.status === '0' ? '下架' : '上架';

      // 检查是否为上架操作，如果是，检查积分
      if(status === '0') { // 即将上架
        if (this.userPoints < this.shelfTemplatePoints) {
          // 积分不足，打开积分获取弹窗
          this.operationType = 'shelfTemplate';
          this.currentApplicationId = row.templateId;
          this.showPointsGetDialog = true;
          return;
        }

        // 积分充足，显示确认对话框
        this.$confirm(`确认要将"${row.templateName}"上架吗？此操作需要消耗 ${this.shelfTemplatePoints} 积分，您的当前积分为 ${this.userPoints} 分。`, "提示", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          row.status = status;
          updateCertificateTemplate(row).then(response => {
            this.$modal.msgSuccess(`${statusText}成功`);
            this.getList();
          });
        }).catch(() => {});
      } else { // 下架操作，不需要积分
        this.$confirm(`确认要将"${row.templateName}"${statusText}吗？`, "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          row.status = status;
          updateCertificateTemplate(row).then(response => {
            this.$modal.msgSuccess(`${statusText}成功`);
            this.getList();
          });
        }).catch(() => {});
      }
    },
    /** 添加材料行 */
    addMaterialRow() {
      this.form.materials.push({
        materialName: '',
        isRequired: true
      });
    },
    /** 删除材料行 */
    removeMaterialRow(index) {
      this.form.materials.splice(index, 1);
    },
    /** 批量删除材料行 */
    removeMaterialRows() {
      if (this.form.materials.length > 0) {
        this.$confirm('是否确认删除所有材料配置？', "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          this.form.materials = [];
        }).catch(() => {});
      }
    },
    /** 添加字段行 */
    addFieldRow() {
      this.form.fields.push({
        fieldName: '',
        fieldType: 'text',
        isRequired: true
      });
    },
    /** 删除字段行 */
    removeFieldRow(index) {
      this.form.fields.splice(index, 1);
    },
    /** 批量删除字段行 */
    removeFieldRows() {
      if (this.form.fields.length > 0) {
        this.$confirm('是否确认删除所有字段配置？', "警告", {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning"
        }).then(() => {
          // 只删除非固定的字段
          this.form.fields = this.form.fields.filter(field => field.fixed === true);
        }).catch(() => {});
      }
    },
    /** 处理证书类型改变 */
    handleCertificateTypeChange(value) {
      // 先移除所有类型的固定字段
      this.removeAcademicFixedFields();
      this.removeEnterpriseFixedFields();
      this.removeProfessionalFixedFields();  // 新增：移除职业类固定字段
      this.removeCommonFixedFields();  // 移除通用固定字段

      // 根据选择的类型添加对应的固定字段
      if (value === '学历类') {
        this.addAcademicFixedFields();
        this.addCommonFixedFields();  // 添加有效期字段
      } else if (value === '企业类') {
        this.addEnterpriseFixedFields();
        this.addCommonFixedFields();  // 添加有效期字段
      } else if (value === '职业类') {  // 新增：处理职业类证书
        this.addProfessionalFixedFields();
        this.addCommonFixedFields();  // 添加有效期字段
      } else if (value === '其他') {
        this.addCommonFixedFields();  // 添加有效期字段
      }
    },
    /** 为学历类证书添加固定字段 */
    addAcademicFixedFields() {
      // 检查是否已存在这些固定字段
      const nameFieldExists = this.form.fields.some(field => field.fieldName === '姓名');
      const schoolFieldExists = this.form.fields.some(field => field.fieldName === '院校');

      // 如果不存在，则添加固定字段
      if (!nameFieldExists) {
        this.form.fields.unshift({
          fieldName: '姓名',
          fieldType: 'text',
          isRequired: true,
          fixed: false
        });
      }

      if (!schoolFieldExists) {
        this.form.fields.unshift({
          fieldName: '院校',
          fieldType: 'text',
          isRequired: true,
          fixed: false
        });
      }
    },
    /** 移除学历类证书的固定字段 */
    removeAcademicFixedFields() {
      // 过滤掉固定字段（姓名和院校）
      this.form.fields = this.form.fields.filter(field => field.fieldName !== '姓名' && field.fieldName !== '院校');
    },
    /** 为企业类证书添加固定字段 */
    addEnterpriseFixedFields() {
      // 检查是否已存在这些固定字段
      const nameFieldExists = this.form.fields.some(field => field.fieldName === '姓名');
      const enterpriseFieldExists = this.form.fields.some(field => field.fieldName === '企业');

      // 如果不存在，则添加固定字段
      if (!nameFieldExists) {
        this.form.fields.unshift({
          fieldName: '姓名',
          fieldType: 'text',
          isRequired: true,
          fixed: false
        });
      }

      if (!enterpriseFieldExists) {
        this.form.fields.unshift({
          fieldName: '企业',
          fieldType: 'text',
          isRequired: true,
          fixed: false
        });
      }
    },
    /** 移除企业类证书的固定字段 */
    removeEnterpriseFixedFields() {
      // 过滤掉固定字段（姓名和企业）
      this.form.fields = this.form.fields.filter(field => field.fieldName !== '姓名' && field.fieldName !== '企业');
    },

    /** 为职业类证书添加固定字段 */
    addProfessionalFixedFields() {
      // 检查是否已存在姓名固定字段
      const nameFieldExists = this.form.fields.some(field => field.fieldName === '姓名');

      // 如果不存在，则添加姓名固定字段
      if (!nameFieldExists) {
        this.form.fields.unshift({
          fieldName: '姓名',
          fieldType: 'text',
          isRequired: true,
          fixed: false
        });
      }
    },

    /** 移除职业类证书的固定字段 */
    removeProfessionalFixedFields() {
      // 过滤掉固定字段（姓名）
      this.form.fields = this.form.fields.filter(field => field.fieldName !== '姓名');
    },

    /** 添加通用固定字段（有效期） */
    addCommonFixedFields() {
      // 检查是否已存在有效期固定字段
      const expiryDateFieldExists = this.form.fields.some(field => field.fieldName === '有效期');

      // 如果不存在，则添加固定字段
      if (!expiryDateFieldExists) {
        this.form.fields.push({
          fieldName: '有效期',
          fieldType: 'date',
          isRequired: false,  // 不是必填
          fixed: false
        });
      }
    },
    /** 移除通用固定字段（有效期） */
    removeCommonFixedFields() {
      // 过滤掉固定字段（有效期）
      this.form.fields = this.form.fields.filter(field => field.fieldName !== '有效期');
    }
  }
};
</script>

<style lang="scss" scoped>
.certificate-preview-container {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 20px;
  background-color: #f5f5f5;
  min-height: 600px;
}

.certificate-preview {
  position: relative;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
  border: 1px solid #ddd;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.preview-field {
  border: 1px dashed rgba(0, 0, 0, 0.2);
  background-color: rgba(255, 255, 255, 0.7);
  overflow: hidden;
  word-break: break-all;
  white-space: normal;
}
.certificate-bg-uploader {
  :deep(.el-upload) {
    width: 100%;
    .el-upload-dragger {
      width: 100%;
      height: 200px;
    }
  }

  :deep(.el-upload-list__item) {
    width: 200px;
    height: 200px;
    margin: 0 8px 8px 0;
  }
}

.bg-preview-container {
  margin-top: 20px;

  h4 {
    margin-bottom: 10px;
  }

  .bg-preview-image {
    max-width: 100%;
    max-height: 300px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
  }
}

.dialog-footer {
  text-align: right;
}

// 为可视化字段编辑器添加样式
:deep(.el-dialog__body) {
  padding: 0 !important;
}
</style>
