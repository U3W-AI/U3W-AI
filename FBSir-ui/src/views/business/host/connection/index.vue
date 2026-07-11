<template>
  <div class="app-container">
    <!-- 选项卡切换 -->
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
      <el-tab-pane label="连接记录" name="log">
        <!-- 连接记录查询表单 -->
        <el-form :model="logQueryParams" ref="logQueryRef" :inline="true" v-show="showSearch">
          <el-form-item label="主机ID" prop="hostId">
            <el-input
              v-model="logQueryParams.hostId"
              placeholder="请输入主机ID"
              clearable
              style="width: 200px"
              @keyup.enter="handleLogQuery"
            />
          </el-form-item>
          <el-form-item label="客户端IP" prop="remoteIp">
            <el-input
              v-model="logQueryParams.remoteIp"
              placeholder="请输入客户端IP"
              clearable
              style="width: 200px"
              @keyup.enter="handleLogQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="logQueryParams.status" placeholder="请选择状态" clearable style="width: 200px">
              <el-option label="连接中" :value="0" />
              <el-option label="已注册" :value="1" />
              <el-option label="正常断开" :value="2" />
              <el-option label="异常断开" :value="3" />
              <el-option label="被拒绝（白名单）" :value="4" />
              <el-option label="被拒绝（黑名单）" :value="5" />
              <el-option label="被拒绝（重复连接）" :value="6" />
              <el-option label="被管理员断开" :value="7" />
              <el-option label="主节点重启" :value="8" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleLogQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetLogQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="danger"
              plain
              icon="Delete"
              :disabled="logMultiple"
              @click="handleLogDelete"
              v-hasPermi="['business:host:connection:remove']"
            >删除</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getLogList"></right-toolbar>
        </el-row>

        <el-table v-loading="logLoading" :data="logList" @selection-change="handleLogSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="主机ID" align="center" prop="hostId" min-width="120" :show-overflow-tooltip="true" />
          <el-table-column label="客户端IP" align="center" prop="remoteIp" min-width="130" />
          <el-table-column label="Engine版本" align="center" prop="engineVersion" width="110" />
          <el-table-column label="时长(秒)" align="center" prop="durationSeconds" min-width="90" />
          <el-table-column label="状态" align="center" prop="status" min-width="120">
            <template #default="scope">
              <el-tag v-if="scope.row.status === 0" type="info" size="small">连接中</el-tag>
              <el-tag v-else-if="scope.row.status === 1" type="success" size="small">已注册</el-tag>
              <el-tag v-else-if="scope.row.status === 2" size="small">正常断开</el-tag>
              <el-tag v-else-if="scope.row.status === 3" type="warning" size="small">异常断开</el-tag>
              <el-tag v-else-if="scope.row.status === 4" type="danger" size="small">白名单拒绝</el-tag>
              <el-tag v-else-if="scope.row.status === 5" type="danger" size="small">黑名单拒绝</el-tag>
              <el-tag v-else-if="scope.row.status === 6" type="warning" size="small">重复连接</el-tag>
              <el-tag v-else-if="scope.row.status === 7" type="warning" size="small">管理员断开</el-tag>
              <el-tag v-else-if="scope.row.status === 8" type="info" size="small">服务重启</el-tag>
              <el-tag v-else type="info" size="small">未知</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="拒绝原因" align="center" prop="rejectReason" min-width="150" :show-overflow-tooltip="true">
            <template #default="scope">
              <span v-if="scope.row.rejectReason">{{ scope.row.rejectReason }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding" width="80" fixed="right">
            <template #default="scope">
              <el-button
                link
                type="primary"
                icon="View"
                @click="handleLogView(scope.row)"
                v-hasPermi="['business:host:connection:query']"
              >详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <pagination
          v-show="logTotal>0"
          :total="logTotal"
          v-model:page="logQueryParams.pageNum"
          v-model:limit="logQueryParams.pageSize"
          @pagination="getLogList"
        />
      </el-tab-pane>

      <el-tab-pane label="在线主机" name="online">
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="primary"
              plain
              icon="Refresh"
              @click="getOnlineList"
              v-hasPermi="['business:host:online:query']"
            >刷新</el-button>
          </el-col>
        </el-row>

        <el-table v-loading="onlineLoading" :data="onlineList">
          <el-table-column label="主机ID" align="center" prop="engineId" min-width="120" :show-overflow-tooltip="true" />
          <el-table-column label="公网IP" align="center" min-width="130">
            <template #default="scope">
              <span>{{ scope.row.deviceInfo?.publicIp || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Engine版本" align="center" prop="version" min-width="90" />
          <el-table-column label="操作系统" align="center" min-width="140" :show-overflow-tooltip="true">
            <template #default="scope">
              <span>{{ scope.row.deviceInfo?.osName }} {{ scope.row.deviceInfo?.osVersion }}</span>
            </template>
          </el-table-column>
          <el-table-column label="主机名" align="center" min-width="130" :show-overflow-tooltip="true">
            <template #default="scope">
              <span>{{ scope.row.deviceInfo?.hostname || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="连接时间" align="center" prop="connectedAt" min-width="150">
            <template #default="scope">
              <span>{{ parseTime(scope.row.connectedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="在线时长" align="center" min-width="100">
            <template #default="scope">
              <span>{{ formatDuration(scope.row.connectedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" min-width="80">
            <template #default="scope">
              <el-tag type="success" size="small">在线</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding" width="240" fixed="right">
            <template #default="scope">
              <el-button
                link
                type="primary"
                icon="View"
                @click="handleViewOnlineDetail(scope.row)"
                v-hasPermi="['business:host:online:query']"
              >详情</el-button>
              <el-button
                link
                type="success"
                icon="CircleCheck"
                @click="handleHealthCheck(scope.row)"
                v-hasPermi="['business:host:online:query']"
              >健康检查</el-button>
              <el-button
                link
                type="danger"
                icon="Close"
                @click="handleForceOffline(scope.row)"
                v-hasPermi="['business:host:online:offline']"
              >下线</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 连接记录详情对话框 -->
    <el-dialog title="连接记录详情" v-model="logDetailOpen" width="800px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="主机ID">{{ logDetail.hostId }}</el-descriptions-item>
        <el-descriptions-item label="会话ID">{{ logDetail.sessionId }}</el-descriptions-item>
        <el-descriptions-item label="客户端IP">{{ logDetail.remoteIp }}</el-descriptions-item>
        <el-descriptions-item label="客户端端口">{{ logDetail.remotePort }}</el-descriptions-item>
        <el-descriptions-item label="Engine版本">{{ logDetail.engineVersion }}</el-descriptions-item>
        <el-descriptions-item label="设备指纹">{{ logDetail.deviceId }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ logDetail.osName }} {{ logDetail.osVersion }}</el-descriptions-item>
        <el-descriptions-item label="Java版本">{{ logDetail.javaVersion }}</el-descriptions-item>
        <el-descriptions-item label="主机名">{{ logDetail.hostname }}</el-descriptions-item>
        <el-descriptions-item label="MAC地址">{{ logDetail.macAddress }}</el-descriptions-item>
        <el-descriptions-item label="连接时间">{{ parseTime(logDetail.connectTime) }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ parseTime(logDetail.registerTime) }}</el-descriptions-item>
        <el-descriptions-item label="断开时间">{{ parseTime(logDetail.disconnectTime) }}</el-descriptions-item>
        <el-descriptions-item label="持续时长">{{ logDetail.durationSeconds }} 秒</el-descriptions-item>
        <el-descriptions-item label="发送消息数">{{ logDetail.messageSent }}</el-descriptions-item>
        <el-descriptions-item label="接收消息数">{{ logDetail.messageReceived }}</el-descriptions-item>
        <el-descriptions-item label="心跳次数">{{ logDetail.heartbeatCount }}</el-descriptions-item>
        <el-descriptions-item label="错误次数">{{ logDetail.errorCount }}</el-descriptions-item>
        <el-descriptions-item label="拒绝原因" :span="2">{{ logDetail.rejectReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最后错误" :span="2">{{ logDetail.lastError || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="logDetailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 在线主机详情对话框 -->
    <el-dialog title="在线主机详情" v-model="onlineDetailOpen" width="900px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="主机ID">{{ onlineDetail.engineId }}</el-descriptions-item>
        <el-descriptions-item label="会话ID">{{ onlineDetail.sessionId }}</el-descriptions-item>
        <el-descriptions-item label="Engine版本">{{ onlineDetail.version }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="onlineDetail.status === 'REGISTERED'" type="success">在线</el-tag>
          <el-tag v-else type="info">{{ onlineDetail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="连接时间">{{ parseTime(onlineDetail.connectedAt) }}</el-descriptions-item>
        <el-descriptions-item label="在线时长">{{ formatDuration(onlineDetail.connectedAt) }}</el-descriptions-item>
        <el-descriptions-item label="最后活跃">{{ parseTime(onlineDetail.lastActiveAt) }}</el-descriptions-item>
        <el-descriptions-item label="最后心跳">{{ parseTime(onlineDetail.lastHeartbeatAt) }}</el-descriptions-item>
        <el-descriptions-item label="连接IP">{{ onlineDetail.deviceInfo?.connectionIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="本地IP">{{ onlineDetail.deviceInfo?.localIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="公网IP">{{ onlineDetail.deviceInfo?.publicIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="MAC地址">{{ onlineDetail.deviceInfo?.macAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ onlineDetail.deviceInfo?.osName }} {{ onlineDetail.deviceInfo?.osVersion }}</el-descriptions-item>
        <el-descriptions-item label="Java版本">{{ onlineDetail.deviceInfo?.javaVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主机名">{{ onlineDetail.deviceInfo?.hostname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备ID">{{ onlineDetail.deviceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发送消息数">{{ onlineDetail.stats?.messageSent || 0 }}</el-descriptions-item>
        <el-descriptions-item label="接收消息数">{{ onlineDetail.stats?.messageReceived || 0 }}</el-descriptions-item>
        <el-descriptions-item label="心跳次数">{{ onlineDetail.stats?.heartbeatCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="错误次数">{{ onlineDetail.stats?.errorCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="能力列表" :span="2">
          <el-tag v-for="(cap, index) in onlineDetail.capabilities" :key="index" style="margin-right: 5px; margin-bottom: 5px;">
            {{ cap.description || cap.type }}
          </el-tag>
          <span v-if="!onlineDetail.capabilities || onlineDetail.capabilities.length === 0">无</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="onlineDetailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="HostConnection">
import { listConnectionLog, getConnectionLog, delConnectionLog, listOnlineEngines, forceOfflineEngine } from "@/api/business/host/connection";
import request from '@/utils/request';

const { proxy } = getCurrentInstance();

const activeTab = ref("log");
const showSearch = ref(true);

const logList = ref([]);
const logLoading = ref(true);
const logIds = ref([]);
const logMultiple = ref(true);
const logTotal = ref(0);
const logDetailOpen = ref(false);
const logDetail = ref({});

const onlineList = ref([]);
const onlineLoading = ref(false);

const data = reactive({
  logQueryParams: {
    pageNum: 1,
    pageSize: 10,
    sessionId: null,
    hostId: null,
    remoteIp: null,
    status: null
  }
});

const { logQueryParams } = toRefs(data);

/** 查询连接记录列表 */
function getLogList() {
  logLoading.value = true;
  listConnectionLog(logQueryParams.value).then(response => {
    logList.value = response.rows;
    logTotal.value = response.total;
    logLoading.value = false;
  });
}

/** 查询在线主机列表 */
function getOnlineList() {
  onlineLoading.value = true;
  listOnlineEngines().then(response => {
    // 接口返回格式: { success: true, engines: [...], total: 1 }
    onlineList.value = response.engines || [];
    onlineLoading.value = false;
  }).catch(() => {
    onlineLoading.value = false;
  });
}

/** 选项卡切换 */
function handleTabClick(tab) {
  if (tab.props.name === 'online') {
    getOnlineList();
  }
}

/** 搜索按钮操作 */
function handleLogQuery() {
  logQueryParams.value.pageNum = 1;
  getLogList();
}

/** 重置按钮操作 */
function resetLogQuery() {
  proxy.resetForm("logQueryRef");
  handleLogQuery();
}

/** 多选框选中数据 */
function handleLogSelectionChange(selection) {
  logIds.value = selection.map(item => item.sessionId);
  logMultiple.value = !selection.length;
}

/** 查看详情 */
function handleLogView(row) {
  getConnectionLog(row.sessionId).then(response => {
    logDetail.value = response.data;
    logDetailOpen.value = true;
  });
}

/** 删除按钮操作 */
function handleLogDelete(row) {
  const sessionIdList = row.sessionId ? [row.sessionId] : logIds.value;
  const idsStr = sessionIdList.join(',');
  console.log('[连接记录删除] 准备删除SessionID:', idsStr);
  
  proxy.$modal.confirm('是否确认删除选中的连接记录数据项？').then(function() {
    console.log('[连接记录删除] 用户确认，开始调用API');
    return delConnectionLog(idsStr);
  }).then((response) => {
    console.log('[连接记录删除] API响应:', response);
    getLogList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch((error) => {
    console.error('[连接记录删除] 删除失败:', error);
    if (error && error !== 'cancel') {
      proxy.$modal.msgError("删除失败: " + (error.message || error));
    }
  });
}

/** 强制下线 */
function handleForceOffline(row) {
  console.log('[强制下线] 准备下线Engine:', row.engineId);
  
  proxy.$modal.confirm('是否确认强制下线主机"' + row.engineId + '"？').then(function() {
    console.log('[强制下线] 用户确认，开始调用API');
    return forceOfflineEngine(row.engineId);
  }).then((response) => {
    console.log('[强制下线] API响应:', response);
    
    // 检查响应格式
    if (response && response.data) {
      const result = response.data;
      if (result.success) {
        proxy.$modal.msgSuccess(result.message || "下线成功");
        // 延迟刷新，给后端时间处理
        setTimeout(() => {
          getOnlineList();
        }, 500);
      } else {
        proxy.$modal.msgError(result.message || "下线失败");
      }
    } else {
      proxy.$modal.msgSuccess("下线成功");
      setTimeout(() => {
        getOnlineList();
      }, 500);
    }
  }).catch((error) => {
    console.error('[强制下线] 下线失败:', error);
    if (error && error !== 'cancel') {
      proxy.$modal.msgError("下线失败: " + (error.message || error));
    }
  });
}

/** 查看在线主机详情 */
const onlineDetailOpen = ref(false);
const onlineDetail = ref({});

function handleViewOnlineDetail(row) {
  onlineDetail.value = row;
  onlineDetailOpen.value = true;
}

/** 健康检查 */
function handleHealthCheck(row) {
  proxy.$modal.loading("正在执行健康检查...");
  
  // 调用健康检查接口
  request.post('/ws/engine/request', {
    engineId: row.engineId,
    type: 'HEALTH_CHECK',
    userId: 'admin',
    timeout: 10
  }).then(response => {
    proxy.$modal.closeLoading();
    
    // 检查响应格式
    if (!response || typeof response !== 'object') {
      proxy.$modal.msgError("健康检查失败：响应格式错误");
      return;
    }
    
    // 处理错误响应
    if (response.error) {
      proxy.$modal.msgError("健康检查失败: " + (response.message || response.error));
      return;
    }
    
    // 处理成功响应（注意：后端返回的数据结构是嵌套的 data.data）
    if (response.success && response.data && response.data.data) {
      const data = response.data.data;
      const perf = data.performance || {};
      const hw = data.hardware || {};
      
      let message = `<div style="text-align: left;">`;
      message += `<table style="width: 100%; border-collapse: collapse; font-size: 14px;">`;
      message += `<tr style="border-bottom: 1px solid #eee;"><td style="padding: 8px 0; width: 120px; color: #606266; font-weight: 500;">硬件信息</td><td style="padding: 8px 0;"></td></tr>`;
      message += `<tr><td style="padding: 6px 0 6px 20px; color: #909399;">CPU</td><td style="padding: 6px 0;">${hw.cpuModel || '-'} (${hw.cpuCores || '-'}核)</td></tr>`;
      message += `<tr><td style="padding: 6px 0 6px 20px; color: #909399;">内存</td><td style="padding: 6px 0;">${hw.totalMemoryGB || '-'} GB</td></tr>`;
      message += `<tr><td style="padding: 6px 0 6px 20px; color: #909399;">磁盘</td><td style="padding: 6px 0;">${hw.totalDiskGB || '-'} GB</td></tr>`;
      message += `<tr style="border-bottom: 1px solid #eee;"><td style="padding: 12px 0 8px 0; color: #606266; font-weight: 500;">性能指标</td><td style="padding: 12px 0 8px 0;"></td></tr>`;
      message += `<tr><td style="padding: 6px 0 6px 20px; color: #909399;">CPU使用率</td><td style="padding: 6px 0;">${perf.cpuUsagePercent || '-'}%</td></tr>`;
      message += `<tr><td style="padding: 6px 0 6px 20px; color: #909399;">内存使用率</td><td style="padding: 6px 0;">${perf.memoryUsagePercent || '-'}%</td></tr>`;
      message += `<tr><td style="padding: 6px 0 6px 20px; color: #909399;">磁盘使用率</td><td style="padding: 6px 0;">${perf.diskUsagePercent || '-'}%</td></tr>`;
      message += `<tr><td style="padding: 6px 0 6px 20px; color: #909399;">JVM使用率</td><td style="padding: 6px 0;">${perf.jvmUsagePercent || '-'}%</td></tr>`;
      message += `<tr style="border-top: 1px solid #eee;"><td style="padding: 12px 0 0 0; color: #606266; font-weight: 500;">响应时间</td><td style="padding: 12px 0 0 0;">${response.executionTime || '-'} ms</td></tr>`;
      message += `</table>`;
      message += `</div>`;
      
      proxy.$alert(message, '健康检查结果', {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '确定'
      });
    } else {
      proxy.$modal.msgError("健康检查失败：未返回有效数据");
    }
  }).catch(error => {
    proxy.$modal.closeLoading();
    console.error('健康检查错误:', error);
    proxy.$modal.msgError("健康检查失败: " + (error.message || '未知错误'));
  });
}

/** 格式化在线时长 */
function formatDuration(connectTime) {
  if (!connectTime) return '-';
  const now = new Date().getTime();
  const connect = new Date(connectTime).getTime();
  const duration = Math.floor((now - connect) / 1000);
  
  const hours = Math.floor(duration / 3600);
  const minutes = Math.floor((duration % 3600) / 60);
  const seconds = duration % 60;
  
  return `${hours}时${minutes}分${seconds}秒`;
}

getLogList();
</script>
