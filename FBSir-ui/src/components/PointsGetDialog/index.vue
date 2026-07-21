<template>
  <PublicDialog
    :visible="visible"
    :title="title"
    :width="dialogWidth"
    :show-footer="false"
    :destroy-on-close="true"
    @update:visible="handleClose"
    @close="handleClose">
    <div class="points-get-content">
      <!-- 当前积分提示 -->
      <div class="current-points">
        <el-alert
          type="info"
          :closable="false"
          show-icon>
          <span style="font-weight: bold;">📌 当前账户可用积分：{{ userPoints }}分</span>
          <span v-if="targetInfo.description">
            &nbsp;&nbsp;{{ targetInfo.description }}
          </span>
        </el-alert>
      </div>

      <h3>积分任务</h3>

      <!-- 简化的任务展示 -->
      <div class="simple-task">
        <div class="task-row">
          <div class="task-info">
            <span class="task-name">积分任务维护中</span>
            <span class="task-limit">，恢复后将展示经过服务端校验的任务</span>
          </div>
          <div class="task-status">
            <el-tag type="warning">已暂停</el-tag>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="actions">
        <el-button @click="handleClose">关闭</el-button>
      </div>
    </div>
  </PublicDialog>
</template>

<script>
import PublicDialog from '@/components/PublicDialog/index.vue';
import { getUserPoints } from "@/api/business/points";

export default {
  name: "PointsGetDialog",
  components: {
    PublicDialog
  },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    requiredPoints: {
      type: Number,
      default: 0
    },
    operationType: {
      type: String,
      default: ''
    },
    applicationId: {
      type: String,
      default: ''
    }
  },
  emits: ['update:visible', 'close', 'points-changed'],
  data() {
    return {
      userPoints: 0,
      originalRoute: null,
      checkInterval: null,
      dialogWidth: '500px'
    };
  },
  computed: {
    title() {
      return '获取积分';
    },
    targetInfo() {
      const requiredPoints = this.requiredPoints;
      const operationType = this.operationType;
      
      if (requiredPoints > 0) {
        const gap = Math.max(0, requiredPoints - this.userPoints);
        const currentPoints = this.userPoints;
        const isInsufficient = currentPoints < requiredPoints;
        
        let description = '';
        let operationDesc = '';
        
        // 根据操作类型显示不同的描述
        switch(operationType) {
          case 'approveApplication':
            operationDesc = '审批需';
            break;
          case 'addTemplate':
            operationDesc = '新增模板需';
            break;
          case 'receiveCertificate':
            operationDesc = '领取证书需';
            break;
          case 'shelfTemplate':
            operationDesc = '上架模板需';
            break;
          default:
            operationDesc = '操作需';
            break;
        }
        
        if (isInsufficient) {
          description = `${operationDesc}${requiredPoints}分（还差${gap}分）`;
        } else {
          description = `${operationDesc}${requiredPoints}分（积分已足够）`;
        }
        
        return {
          description: description,
          requiredPoints: requiredPoints,
          isInsufficient: isInsufficient,
          operationType: operationType
        };
      }
      
      return { description: '', requiredPoints: 0, isInsufficient: false, operationType: '' };
    }
  },
  watch: {
    visible: {
      handler(newVal) {
        if (newVal) {
          this.initData();
        }
      },
      immediate: true
    },
    userPoints: {
      handler(newVal, oldVal) {
        if (newVal !== oldVal) {
          // 积分发生变化，通知父组件
          this.$emit('points-changed', newVal);
        }
      }
    }
  },
  methods: {
    // 初始化数据
    async initData() {
      if (this.visible) {
        await this.updateUserPoints();
        
        // 设置定时器定期检查积分变化
        this.startPolling();
      }
    },

    // 启动轮询检查积分变化
    startPolling() {
      if (this.checkInterval) {
        clearInterval(this.checkInterval);
      }
      
      this.checkInterval = setInterval(async () => {
        await this.updateUserPoints();
      }, 5000);
    },

    // 更新用户积分
    async updateUserPoints() {
      try {
        const response = await getUserPoints();
        const newPoints = response.data || 0;
        
        if (newPoints > this.userPoints) {
          this.$message.success(`积分已更新！当前积分为 ${newPoints} 分`);
        }
        
        this.userPoints = newPoints;
      } catch (error) {
        console.error('获取用户积分失败:', error);
      }
    },

    // 关闭弹窗
    handleClose() {
      // 清理定时器
      if (this.checkInterval) {
        clearInterval(this.checkInterval);
        this.checkInterval = null;
      }
      
      this.$emit('update:visible', false);
      this.$emit('close');
    }
  },
  beforeUnmount() {
    // 清理资源
    if (this.checkInterval) {
      clearInterval(this.checkInterval);
      this.checkInterval = null;
    }
  }
};
</script>

<style scoped>
.points-get-content {
  min-height: 200px;
}

.current-points {
  margin-bottom: 20px;
}

.simple-task {
  background-color: #f8f9fa;
  border-radius: 4px;
  padding: 15px;
  margin-bottom: 20px;
  border: 1px solid #e9ecef;
}

.task-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.task-info {
  display: flex;
  align-items: center;
  font-size: 14px;
  flex: 1;
}

.task-name {
  font-weight: bold;
  color: #333;
}

.task-reward {
  color: #67C23A;
  font-weight: bold;
}

.task-limit {
  color: #909399;
}

.task-status {
  font-size: 13px;
  color: #606266;
  margin: 0 20px;
  white-space: nowrap;
}

.task-action {
  flex-shrink: 0;
}

.actions {
  text-align: center;
  margin-top: 20px;
}
</style>
