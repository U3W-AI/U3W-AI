<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="主机ID" prop="hostId">
        <el-input
          v-model="queryParams.hostId"
          placeholder="请输入主机ID"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否团队" prop="isTeam">
        <el-select v-model="queryParams.isTeam" placeholder="请选择" clearable style="width: 200px">
          <el-option label="个人" :value="0" />
          <el-option label="团队" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 200px">
          <el-option label="禁用" :value="0" />
          <el-option label="启用" :value="1" />
        </el-select>
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
          v-hasPermi="['business:host:whitelist:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['business:host:whitelist:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['business:host:whitelist:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="whitelistList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="主机ID" align="center" prop="hostId" width="150" />
      <el-table-column label="主机名称" align="center" prop="hostName" :show-overflow-tooltip="true" />
      <el-table-column label="负责人" align="center" prop="ownerName" width="100" />
      <el-table-column label="联系方式" align="center" prop="ownerContact" width="120" />
      <el-table-column label="类型" align="center" prop="isTeam" width="80">
        <template #default="scope">
          <el-tag v-if="scope.row.isTeam === 0" type="info">个人</el-tag>
          <el-tag v-else type="success">团队</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="主机类型" align="center" prop="hostType" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.hostType === 'openclaw'" type="primary">OpenClaw</el-tag>
          <el-tag v-else type="warning">Engine</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="在线状态" align="center" prop="onlineStatus" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.onlineStatus === 'online'" type="success">在线</el-tag>
          <el-tag v-else type="danger">离线</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="团队名称" align="center" prop="teamName" width="120" />
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <el-switch
            v-model="scope.row.status"
            :active-value="1"
            :inactive-value="0"
            @change="handleStatusChange(scope.row)"
            v-hasPermi="['business:host:whitelist:edit']"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="过期时间" align="center" prop="expireTime" width="160">
        <template #default="scope">
          <span v-if="scope.row.expireTime">{{ parseTime(scope.row.expireTime) }}</span>
          <span v-else style="color: #67C23A;">永不过期</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding" width="240" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['business:host:whitelist:edit']"
          >修改</el-button>
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleHealthCheck(scope.row)"
            v-if="scope.row.hostType === 'openclaw'"
            v-hasPermi="['business:host:whitelist:edit']"
          >健康检查</el-button>
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['business:host:whitelist:remove']"
          >删除</el-button>
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

    <!-- 添加或修改主机ID白名单对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="whitelistRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="主机ID" prop="hostId">
          <el-input v-model="form.hostId" placeholder="请输入主机ID" />
        </el-form-item>
        <el-form-item label="主机名称" prop="hostName">
          <el-input v-model="form.hostName" placeholder="请输入主机名称" />
        </el-form-item>
        <el-form-item label="负责人姓名" prop="ownerName">
          <el-input v-model="form.ownerName" placeholder="请输入负责人姓名" />
        </el-form-item>
        <el-form-item label="负责人联系方式" prop="ownerContact">
          <el-input v-model="form.ownerContact" placeholder="请输入联系方式（手机/邮箱）" />
        </el-form-item>
        <el-form-item label="是否团队主机" prop="isTeam">
          <el-radio-group v-model="form.isTeam">
            <el-radio :label="0">个人</el-radio>
            <el-radio :label="1">团队</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="团队名称" prop="teamName" v-if="form.isTeam === 1">
          <el-input v-model="form.teamName" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="允许的IP列表" prop="allowedIps">
          <el-input v-model="form.allowedIps" type="textarea" placeholder="多个IP用逗号分隔，为空表示不限制" />
        </el-form-item>
        <el-form-item label="主机类型" prop="hostType">
          <el-select v-model="form.hostType" placeholder="请选择主机类型">
            <el-option label="Engine" value="engine" />
            <el-option label="OpenClaw" value="openclaw" />
          </el-select>
        </el-form-item>
        <el-form-item label="健康检查URL" prop="healthCheckUrl">
          <el-input v-model="form.healthCheckUrl" placeholder="请输入健康检查URL（仅OpenClaw类型需要）" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="过期时间" prop="expireTime">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            placeholder="选择过期时间（不选则永不过期）"
            value-format="YYYY-MM-DDTHH:mm:ss"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="HostWhitelist">
import { listWhitelist, getWhitelist, delWhitelist, addWhitelist, updateWhitelist, manualHealthCheck } from "@/api/business/host/whitelist";

const { proxy } = getCurrentInstance();

const whitelistList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    hostId: null,
    hostName: null,
    ownerName: null,
    isTeam: null,
    status: null
  },
  rules: {
    hostId: [
      { required: true, message: "主机ID不能为空", trigger: "blur" }
    ],
    hostName: [
      { required: true, message: "主机名称不能为空", trigger: "blur" }
    ],
    isTeam: [
      { required: true, message: "请选择是否团队主机", trigger: "change" }
    ],
    status: [
      { required: true, message: "请选择状态", trigger: "change" }
    ]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询主机ID白名单列表 */
function getList() {
  loading.value = true;
  listWhitelist(queryParams.value).then(response => {
    whitelistList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 取消按钮 */
function cancel() {
  open.value = false;
  reset();
}

/** 表单重置 */
function reset() {
  form.value = {
    id: null,
    hostId: null,
    hostName: null,
    ownerName: null,
    ownerContact: null,
    isTeam: 0,
    teamName: null,
    allowedIps: null,
    hostType: "engine",
    healthCheckUrl: null,
    onlineStatus: "offline",
    status: 1,
    expireTime: null,
    remark: null
  };
  proxy.resetForm("whitelistRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef");
  handleQuery();
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加主机ID白名单";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const id = row.id || ids.value
  getWhitelist(id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改主机ID白名单";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["whitelistRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateWhitelist(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addWhitelist(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const idList = row.id ? [row.id] : ids.value;
  const idsStr = idList.join(',');
  console.log('[白名单删除] 准备删除ID:', idsStr, '原始ID列表:', idList);
  
  proxy.$modal.confirm('是否确认删除主机ID为"' + (row.hostId || '') + '"的数据项？').then(function() {
    console.log('[白名单删除] 用户确认，开始调用API');
    return delWhitelist(idsStr);
  }).then((response) => {
    console.log('[白名单删除] API响应:', response);
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch((error) => {
    console.error('[白名单删除] 删除失败:', error);
    if (error && error !== 'cancel') {
      proxy.$modal.msgError("删除失败: " + (error.message || error));
    }
  });
}

/** 状态修改 */
function handleStatusChange(row) {
  let text = row.status === 1 ? "启用" : "停用";
  proxy.$modal.confirm('确认要"' + text + '""' + row.hostId + '"主机吗？').then(function() {
    return updateWhitelist(row);
  }).then(() => {
    proxy.$modal.msgSuccess(text + "成功");
  }).catch(function() {
    row.status = row.status === 0 ? 1 : 0;
  });
}

/** 手动健康检查 */
function handleHealthCheck(row) {
  if (!row.hostId) {
    proxy.$modal.msgError("未找到主机ID");
    return;
  }

  if (!row.healthCheckUrl) {
    proxy.$modal.msgWarning("该主机未配置健康检查URL");
    return;
  }

  proxy.$modal.confirm('确认要对"' + row.hostId + '"主机执行健康检查吗？').then(function() {
    proxy.$modal.loading("正在执行健康检查，请稍候...");
    return manualHealthCheck(row.id);
  }).then((response) => {
    proxy.$modal.closeLoading();
    if (response.code === 200) {
      proxy.$modal.msgSuccess("健康检查完成");
      // 刷新列表
      getList();
    } else {
      proxy.$modal.msgError("健康检查失败: " + (response.msg || "未知错误"));
    }
  }).catch(function(error) {
    proxy.$modal.closeLoading();
    if (error && error !== 'cancel') {
      proxy.$modal.msgError("健康检查失败: " + (error.message || error));
    }
  });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('business/host/whitelist/export', {
    ...queryParams.value
  }, `whitelist_${new Date().getTime()}.xlsx`)
}

getList();
</script>
