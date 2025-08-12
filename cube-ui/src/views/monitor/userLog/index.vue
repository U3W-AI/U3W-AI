<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="用户 ID" prop="userId">
        <el-input
          v-model="queryParams.userId"
          placeholder="请输入用户 ID"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="方法名称" prop="methodName">
        <el-input
          v-model="queryParams.methodName"
          placeholder="请输入方法名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="执行时间">
        <el-date-picker
          v-model="queryParams.params.beginExecutionTime"
          style="width: 180px"
          value-format="yyyy-MM-dd HH:mm:ss"
          type="datetime"
          placeholder="开始时间"
        ></el-date-picker>
        <span style="margin: 0 5px;">-</span>
        <el-date-picker
          v-model="queryParams.params.endExecutionTime"
          style="width: 180px"
          value-format="yyyy-MM-dd HH:mm:ss"
          type="datetime"
          placeholder="结束时间"
        ></el-date-picker>
      </el-form-item>
      <el-form-item label="执行时长" prop="executionTimeMillis">
        <el-input
          v-model="queryParams.executionTimeMillis"
          placeholder="请输入执行时长"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否成功" prop="isSuccess">
        <el-select v-model="queryParams.isSuccess" placeholder="请选择是否成功" clearable>
          <el-option label="成功" value="1" />
          <el-option label="失败" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['monitor:userLog:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          @click="handleClean"
          v-hasPermi="['monitor:userLog:remove']"
        >清空</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['monitor:userLog:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="userLogList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="日志ID" align="center" prop="id" />
      <el-table-column label="用户 ID" align="center" prop="userId" />
      <el-table-column label="方法名称" align="center" prop="methodName" />
      <el-table-column label="描述" align="center" prop="description" />
      <el-table-column label="方法参数" align="center" width="200">
        <template slot-scope="scope">
          <div class="copy-cell" @click="showContentDialog(scope.row.methodParams, '方法参数')">
            <div class="text-content" :title="scope.row.methodParams">
              {{ scope.row.methodParams && scope.row.methodParams.length > 30 ? scope.row.methodParams.substring(0, 30) + '...' : scope.row.methodParams }}
            </div>
            <i class="el-icon-view copy-icon"></i>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="执行时间" align="center" prop="executionTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.executionTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="执行结果" align="center" width="200">
        <template slot-scope="scope">
          <div class="copy-cell" @click="showContentDialog(scope.row.executionResult, '执行结果')">
            <div class="text-content" :title="scope.row.executionResult">
              {{ scope.row.executionResult && scope.row.executionResult.length > 30 ? scope.row.executionResult.substring(0, 30) + '...' : scope.row.executionResult }}
            </div>
            <i class="el-icon-view copy-icon"></i>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="执行时长" align="center" prop="executionTimeMillis" />
      <el-table-column label="是否成功" align="center" prop="isSuccess" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">

          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['monitor:userLog:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />



    <!-- 内容展示弹窗 -->
    <el-dialog :title="dialogTitle" :visible.sync="contentDialogVisible" width="60%" append-to-body>
      <pre class="content-display">{{ dialogContent }}</pre>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="copyDialogContent">复制内容</el-button>
        <el-button @click="contentDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listUserLog, delUserLog, cleanUserLog } from "@/api/monitor/userLog";

export default {
  name: "UserLog",
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
      // 日志信息（记录方法执行日志）表格数据
      userLogList: [],

      // 内容展示弹窗可见性
      contentDialogVisible: false,
      // 内容展示弹窗标题
      dialogTitle: "",
      // 内容展示弹窗内容
      dialogContent: "",
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userId: null,
        methodName: null,
        executionTime: null,
        executionTimeMillis: null,
        isSuccess: null,
        params: {
          beginExecutionTime: null,
          endExecutionTime: null
        }
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {}
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询日志信息（记录方法执行日志）列表 */
    getList() {
      this.loading = true;
      listUserLog(this.queryParams).then(response => {
        this.userLogList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },

    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.queryParams.params.beginExecutionTime = null;
      this.queryParams.params.endExecutionTime = null;
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },

    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal.confirm('是否确认删除日志信息（记录方法执行日志）编号为"' + ids + '"的数据项？').then(function() {
        return delUserLog(ids);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('monitor/userLog/export', {
        ...this.queryParams
      }, `userLog_${new Date().getTime()}.xlsx`)
    },
    /** 清空按钮操作 */
    handleClean() {
      this.$modal.confirm('是否确认清空所有用户日志数据项？').then(function() {
        // 使用专门的清空API，而不是批量删除
        return cleanUserLog();
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("清空成功");
      }).catch(() => {});
    },
    
    /** 显示内容弹窗 */
    showContentDialog(content, title) {
      if (!content || content.trim() === '') {
        this.$message({
          message: `${title}内容为空，无法显示`,
          type: 'warning',
          duration: 2000
        });
        return;
      }
      
      this.dialogTitle = title;
      this.dialogContent = content;
      this.contentDialogVisible = true;
    },
    
    /** 复制弹窗内容 */
    copyDialogContent() {
      this.copyToClipboard(this.dialogContent, this.dialogTitle);
    },
    
    /** 复制到剪贴板 - 重新设计版本 */
    async copyToClipboard(text, fieldName) {
      if (!text || text.trim() === '') {
        this.$message({
          message: `${fieldName}内容为空，无法复制`,
          type: 'warning',
          duration: 2000
        });
        return;
      }
      
      try {
        // 优先使用现代 Clipboard API
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(text);
          this.$message({
            message: `${fieldName}已成功复制到剪贴板`,
            type: 'success',
            duration: 2000,
            showClose: true
          });
        } else {
          // 降级到传统方法
          this.fallbackCopyTextToClipboard(text, fieldName);
        }
      } catch (err) {
        console.error('复制失败:', err);
        // 如果现代API失败，尝试降级方法
        this.fallbackCopyTextToClipboard(text, fieldName);
      }
    },
    
    /** 降级复制方法 */
    fallbackCopyTextToClipboard(text, fieldName) {
      const textArea = document.createElement('textarea');
      textArea.value = text;
      
      // 避免在iOS中出现缩放
      textArea.style.position = 'fixed';
      textArea.style.left = '-999999px';
      textArea.style.top = '-999999px';
      
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();
      
      try {
        const successful = document.execCommand('copy');
        if (successful) {
          this.$message({
            message: `${fieldName}已成功复制到剪贴板`,
            type: 'success',
            duration: 2000,
            showClose: true
          });
        } else {
          throw new Error('execCommand failed');
        }
      } catch (err) {
        console.error('降级复制也失败:', err);
        this.$message({
          message: '复制失败，请手动选择并复制文本',
          type: 'error',
          duration: 3000,
          showClose: true
        });
      } finally {
        document.body.removeChild(textArea);
      }
    }
  }
};
</script>

<style scoped>
/* 复制单元格样式 */
.copy-cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
  background-color: #fafafa;
  border: 1px solid transparent;
}

.copy-cell:hover {
  background-color: #f0f9ff;
  border-color: #409EFF;
  box-shadow: 0 2px 4px rgba(64, 158, 255, 0.1);
}

/* 文本内容样式 */
.text-content {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #606266;
  font-size: 13px;
  line-height: 1.4;
}

.copy-cell:hover .text-content {
  color: #409EFF;
}

/* 复制图标样式 */
.copy-icon {
  margin-left: 8px;
  font-size: 14px;
  color: #C0C4CC;
  opacity: 0;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.copy-cell:hover .copy-icon {
  opacity: 1;
  color: #409EFF;
}

/* 点击动画效果 */
.copy-cell:active {
  transform: scale(0.98);
  background-color: #e6f7ff;
}

/* 内容展示弹窗样式 */
.content-display {
  background-color: #f8f8f8;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  padding: 15px;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Consolas, Monaco, 'Andale Mono', monospace;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .copy-cell {
    padding: 6px 8px;
  }
  
  .text-content {
    font-size: 12px;
  }
  
  .copy-icon {
    font-size: 12px;
  }
  
  .content-display {
    font-size: 12px;
    padding: 10px;
    max-height: 300px;
  }
}
</style>
