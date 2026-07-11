<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="规则名称" prop="ruleName">
        <el-input
          v-model="queryParams.ruleName"
          placeholder="请输入规则名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="数据状态" clearable style="width: 200px">
          <el-option
            v-for="dict in sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
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
          v-hasPermi="['points:rule:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['points:rule:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['points:rule:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="规则名称" align="center" prop="ruleName">
        <template #default="scope">
          <span>{{ scope.row.ruleName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="积分值" align="center" prop="pointsValue">
        <template #default="scope">
          <span :class="{ positive: scope.row.pointsValue > 0, negative: scope.row.pointsValue < 0 }">
            {{ scope.row.pointsValue > 0 ? '+' : '' }}{{ scope.row.pointsValue }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sortOrder" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status"/>
        </template>
      </el-table-column>
      <el-table-column label="限频配置" align="center" prop="remark" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ formatRuleConfig(scope.row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['points:rule:edit']"
          >修改</el-button>
          <el-button
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['points:rule:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改积分规则配置对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input 
            v-model="form.ruleCode" 
            placeholder="请输入规则编码，如：DAILY_LOGIN（唯一标识，创建后不可修改）"
            :disabled="form.ruleId !== undefined"
          />
          <div class="form-tip">规则编码作为唯一标识，创建后不可修改</div>
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="请输入规则名称，如：每日登录、使用AI功能等" />
        </el-form-item>
        <el-form-item label="积分值" prop="pointsValue">
          <el-input-number 
            v-model="form.pointsValue" 
            placeholder="请输入积分值，正数表示增加，负数表示扣除" 
            :precision="0"
            controls-position="right"
            style="width: 100%"
          />
          <div class="form-tip">正数表示增加积分，负数表示扣除积分</div>
        </el-form-item>
        <el-form-item label="显示排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="dict in sys_normal_disable"
              :key="dict.value"
              :value="dict.value"
            >{{dict.label}}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-divider content-position="left">限频配置</el-divider>
        <el-form-item label="限频类型">
          <el-select v-model="ruleConfig.limitType" placeholder="请选择限频类型" style="width: 100%">
            <el-option v-for="item in limitTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="form-tip">设置该积分规则的领取频率限制</div>
        </el-form-item>
        <el-form-item label="限频值" v-if="ruleConfig.limitType && ruleConfig.limitType !== 'NONE'">
          <el-input-number 
            v-model="ruleConfig.limitValue" 
            :min="1" 
            placeholder="例如：每日限制次数" 
            controls-position="right"
            style="width: 100%"
          />
          <div class="form-tip">在限频类型周期内，最多可领取的次数</div>
        </el-form-item>
        <el-form-item label="累计上限">
          <el-input-number 
            v-model="ruleConfig.maxAmount" 
            placeholder="不限制可留空" 
            controls-position="right" 
            :min="1"
            style="width: 100%"
          />
          <div class="form-tip">该规则累计可领取的积分上限，留空表示不限制</div>
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

<script setup name="PointRule">
import { ref, reactive, onMounted } from 'vue'
import { listPointsRule, getPointsRule, delPointsRule, addPointsRule, updatePointsRule } from "@/api/system/points"
import { parseTime } from '@/utils/FBSir'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = proxy.useDict("sys_normal_disable")

const dataList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    ruleName: undefined,
    status: undefined
  },
  rules: {
    ruleCode: [
      { required: true, message: "规则编码不能为空", trigger: "blur" }
    ],
    ruleName: [
      { required: true, message: "规则名称不能为空", trigger: "blur" }
    ],
    pointsValue: [
      { required: true, message: "积分值不能为空", trigger: "blur" }
    ],
    sortOrder: [
      { required: true, message: "排序不能为空", trigger: "blur" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

const ruleConfig = ref({
  limitType: 'NONE',
  limitValue: null,
  maxAmount: null
})

const limitTypeOptions = ref([
  { label: '不限', value: 'NONE' },
  { label: '每日', value: 'DAILY' },
  { label: '每周', value: 'WEEKLY' },
  { label: '每月', value: 'MONTHLY' },
  { label: '累计', value: 'TOTAL' }
])

/** 查询积分规则列表 */
function getList() {
  loading.value = true
  listPointsRule(queryParams.value).then(response => {
    dataList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    ruleId: undefined,
    ruleCode: undefined,
    ruleName: undefined,
    pointsValue: undefined,
    limitType: undefined,
    limitValue: undefined,
    maxAmount: undefined,
    status: "0",
    sortOrder: 0,
    remark: undefined
  }
  ruleConfig.value = getDefaultRuleConfig()
  proxy.resetForm("formRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加积分规则"
  ruleConfig.value = getDefaultRuleConfig()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.ruleId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const ruleId = row.ruleId || ids.value[0]
  getPointsRule(ruleId).then(response => {
    form.value = response.data
    ruleConfig.value = parseRuleConfig(form.value)
    open.value = true
    title.value = "修改积分规则"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["formRef"].validate(valid => {
    if (valid) {
      // 将限频配置序列化到实体类字段
      form.value.limitType = ruleConfig.value.limitType === 'NONE' ? null : ruleConfig.value.limitType
      form.value.limitValue = ruleConfig.value.limitType === 'NONE' ? null : ruleConfig.value.limitValue
      form.value.maxAmount = ruleConfig.value.maxAmount
      
      if (form.value.ruleId != undefined) {
        updatePointsRule(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addPointsRule(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const ruleIds = row.ruleId || ids.value
  proxy.$modal.confirm('是否确认删除规则编码为"' + ruleIds + '"的积分规则？').then(function() {
    return delPointsRule(ruleIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

function getDefaultRuleConfig() {
  return {
    limitType: 'NONE',
    limitValue: null,
    maxAmount: null
  }
}

function parseRuleConfig(rule) {
  let config = getDefaultRuleConfig()
  if (!rule) {
    return { ...config }
  }
  config.limitType = rule.limitType || 'NONE'
  config.limitValue = rule.limitValue
  config.maxAmount = rule.maxAmount
  return config
}

function formatRuleConfig(rule) {
  if (!rule) {
    return '未配置'
  }
  const parts = []
  
  // 限频类型和限频值
  if (rule.limitType && rule.limitType !== 'NONE') {
    const limitTypeMap = {
      'DAILY': '每日',
      'WEEKLY': '每周',
      'MONTHLY': '每月',
      'TOTAL': '累计'
    }
    const limitTypeText = limitTypeMap[rule.limitType] || rule.limitType
    if (rule.limitValue) {
      parts.push(`${limitTypeText}限${rule.limitValue}次`)
    } else {
      parts.push(`${limitTypeText}限频`)
    }
  } else {
    parts.push('不限频')
  }
  
  // 累计上限
  if (rule.maxAmount) {
    parts.push(`累计上限${rule.maxAmount}`)
  }
  
  return parts.length > 0 ? parts.join('，') : '未配置'
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.app-container {
  .form-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
  
  .positive {
    color: #67c23a;
    font-weight: 600;
  }
  
  .negative {
    color: #f56c6c;
    font-weight: 600;
  }
}
</style>

