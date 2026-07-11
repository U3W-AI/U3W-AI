<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="IP地址" prop="ipAddress">
        <el-input
          v-model="queryParams.ipAddress"
          placeholder="请输入IP地址"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="封禁类型" prop="blockType">
        <el-select v-model="queryParams.blockType" placeholder="请选择封禁类型" clearable style="width: 200px">
          <el-option label="临时封禁" :value="1" />
          <el-option label="永久封禁" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 200px">
          <el-option label="已解除" :value="0" />
          <el-option label="生效中" :value="1" />
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
          v-hasPermi="['business:host:blacklist:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['business:host:blacklist:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['business:host:blacklist:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="blacklistList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="IP地址" align="center" prop="ipAddress" width="150" />
      <el-table-column label="封禁原因" align="center" prop="blockReason" :show-overflow-tooltip="true" />
      <el-table-column label="封禁类型" align="center" prop="blockType" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.blockType === 1" type="warning">临时封禁</el-tag>
          <el-tag v-else type="danger">永久封禁</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="解封时间" align="center" prop="expireTime" width="160">
        <template #default="scope">
          <span v-if="scope.row.blockType === 1 && scope.row.expireTime">{{ parseTime(scope.row.expireTime) }}</span>
          <span v-else style="color: #909399;">-</span>
        </template>
      </el-table-column>
      <el-table-column label="命中次数" align="center" prop="hitCount" width="100" />
      <el-table-column label="最后命中时间" align="center" prop="lastHitTime" width="160">
        <template #default="scope">
          <span v-if="scope.row.lastHitTime">{{ parseTime(scope.row.lastHitTime) }}</span>
          <span v-else style="color: #909399;">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <el-switch
            v-model="scope.row.status"
            :active-value="1"
            :inactive-value="0"
            @change="handleStatusChange(scope.row)"
            v-hasPermi="['business:host:blacklist:edit']"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding" width="160" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['business:host:blacklist:edit']"
          >修改</el-button>
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['business:host:blacklist:remove']"
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

    <!-- 添加或修改IP黑名单对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="blacklistRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="IP地址" prop="ipAddress">
          <el-input v-model="form.ipAddress" placeholder="请输入IP地址" />
        </el-form-item>
        <el-form-item label="封禁原因" prop="blockReason">
          <el-input v-model="form.blockReason" type="textarea" placeholder="请输入封禁原因" />
        </el-form-item>
        <el-form-item label="封禁类型" prop="blockType">
          <el-radio-group v-model="form.blockType">
            <el-radio :label="1">临时封禁</el-radio>
            <el-radio :label="2">永久封禁</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="解封时间" prop="expireTime" v-if="form.blockType === 1">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            placeholder="选择解封时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">生效</el-radio>
            <el-radio :label="0">已解除</el-radio>
          </el-radio-group>
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

<script setup name="HostBlacklist">
import { listBlacklist, getBlacklist, delBlacklist, addBlacklist, updateBlacklist } from "@/api/business/host/blacklist";

const { proxy } = getCurrentInstance();

const blacklistList = ref([]);
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
    ipAddress: null,
    blockType: null,
    status: null
  },
  rules: {
    ipAddress: [
      { required: true, message: "IP地址不能为空", trigger: "blur" },
      { pattern: /^(\d{1,3}\.){3}\d{1,3}$/, message: "请输入正确的IP地址格式", trigger: "blur" }
    ],
    blockReason: [
      { required: true, message: "封禁原因不能为空", trigger: "blur" }
    ],
    blockType: [
      { required: true, message: "请选择封禁类型", trigger: "change" }
    ],
    status: [
      { required: true, message: "请选择状态", trigger: "change" }
    ]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询IP黑名单列表 */
function getList() {
  loading.value = true;
  listBlacklist(queryParams.value).then(response => {
    blacklistList.value = response.rows;
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
    ipAddress: null,
    blockReason: null,
    blockType: 1,
    expireTime: null,
    hitCount: 0,
    lastHitTime: null,
    status: 1,
    remark: null
  };
  proxy.resetForm("blacklistRef");
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
  title.value = "添加IP黑名单";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const id = row.id || ids.value
  getBlacklist(id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改IP黑名单";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["blacklistRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateBlacklist(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addBlacklist(form.value).then(response => {
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
  console.log('[IP黑名单删除] 准备删除ID:', idsStr, '原始ID列表:', idList);
  
  proxy.$modal.confirm('是否确认删除IP地址为"' + (row.ipAddress || '') + '"的数据项？').then(function() {
    console.log('[IP黑名单删除] 用户确认，开始调用API');
    return delBlacklist(idsStr);
  }).then((response) => {
    console.log('[IP黑名单删除] API响应:', response);
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch((error) => {
    console.error('[IP黑名单删除] 删除失败:', error);
    if (error && error !== 'cancel') {
      proxy.$modal.msgError("删除失败: " + (error.message || error));
    }
  });
}

/** 状态修改 */
function handleStatusChange(row) {
  let text = row.status === 1 ? "生效" : "解除";
  proxy.$modal.confirm('确认要"' + text + '""' + row.ipAddress + '"的封禁吗？').then(function() {
    return updateBlacklist(row);
  }).then(() => {
    proxy.$modal.msgSuccess(text + "成功");
  }).catch(function() {
    row.status = row.status === 0 ? 1 : 0;
  });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('business/host/blacklist/export', {
    ...queryParams.value
  }, `blacklist_${new Date().getTime()}.xlsx`)
}

getList();
</script>
