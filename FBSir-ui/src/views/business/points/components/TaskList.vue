<template>
  <el-dialog
    title="获取更多积分"
    v-model="visible"
    width="800px"
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="task-list-container">
      <!-- 任务分类标签 -->
      <div class="category-tabs">
        <el-tag
          v-for="category in categories"
          :key="category"
          :type="activeCategory === category ? 'primary' : 'info'"
          @click="activeCategory = category"
          class="category-tag"
          :class="{ 'active': activeCategory === category }"
        >
          {{ category }}
        </el-tag>
      </div>

      <!-- 任务列表 -->
      <div class="task-list" v-loading="loading">
        <div
          v-for="task in filteredTasks"
          :key="task.ruleCode"
          class="task-item"
        >
          <div class="task-icon">
            <i :class="task.icon || 'el-icon-trophy'"></i>
          </div>
          <div class="task-content">
            <div class="task-header">
              <h4 class="task-name">{{ task.ruleName }}</h4>
              <span class="task-points">+{{ task.taskPoint }} 积分</span>
            </div>
            <div class="task-info">
              <el-tag v-if="task.limitDesc" size="small" type="warning" class="limit-tag">
                {{ task.limitDesc }}
              </el-tag>
              <span v-if="task.progress && task.progress.remaining >= 0" class="progress-text">
                剩余 {{ task.progress.remaining }} 次
              </span>
              <span v-if="task.remark && !task.limitDesc" class="task-desc">
                {{ task.remark }}
              </span>
            </div>
          </div>
          <div class="task-status">
            <el-icon v-if="task.isCompleted" class="task-check-icon completed">
              <Check />
            </el-icon>
            <el-icon v-else class="task-check-icon pending">
              <Clock />
            </el-icon>
          </div>
        </div>
        
        <el-empty v-if="!loading && filteredTasks.length === 0" description="暂无任务" />
      </div>
    </div>
    
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup name="PointTaskList">
import { ref, computed, watch, getCurrentInstance } from 'vue'
import { Check, Clock } from '@element-plus/icons-vue'
import { getPointTaskList } from '@/api/business/points'

const { proxy } = getCurrentInstance()

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const taskList = ref([])
const activeCategory = ref('全部')
const categories = ref(['全部', '每日任务', '创作任务', '互动任务', '消费任务', '使用任务', '其他任务'])

const visible = computed({
  get() {
    return props.modelValue
  },
  set(val) {
    emit('update:modelValue', val)
  }
})

const filteredTasks = computed(() => {
  if (activeCategory.value === '全部') {
    return taskList.value
  }
  return taskList.value.filter(task => task.category === activeCategory.value)
})

watch(visible, (newVal) => {
  if (newVal) {
    loadTaskList()
  }
})

function loadTaskList() {
  loading.value = true
  getPointTaskList().then(response => {
    if (response.code === 200) {
      taskList.value = response.data || []
    } else {
      proxy.$modal.msgError(response.msg || "获取任务列表失败")
    }
  }).catch(error => {
    proxy.$modal.msgError("获取任务列表失败")
    console.error(error)
  }).finally(() => {
    loading.value = false
  })
}
</script>

<style scoped>
.task-list-container {
  min-height: 400px;
  max-height: 600px;
  overflow-y: auto;
}

.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #ebeef5;
}

.category-tag {
  cursor: pointer;
  transition: all 0.3s;
  padding: 8px 16px;
  font-size: 14px;
}

.category-tag:hover {
  transform: scale(1.05);
}

.category-tag.active {
  font-weight: bold;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.task-item {
  display: flex;
  align-items: center;
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.task-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, #409eff 0%, #67c23a 100%);
  opacity: 0;
  transition: opacity 0.3s;
}

.task-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: #409eff;
}

.task-item:hover::before {
  opacity: 1;
}

.task-icon {
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  margin-right: 15px;
  flex-shrink: 0;
}

.task-icon i {
  font-size: 24px;
  color: #ffffff;
}

.task-content {
  flex: 1;
  min-width: 0;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.task-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}

.task-points {
  font-size: 18px;
  font-weight: bold;
  color: #f56c6c;
  margin-left: 10px;
  white-space: nowrap;
}

.task-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.limit-tag {
  font-size: 12px;
}

.task-desc {
  font-size: 13px;
  color: #909399;
}

.task-status {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 15px;
  flex-shrink: 0;
}

.task-check-icon {
  font-size: 20px;
}

.task-check-icon.completed {
  color: #67c23a;
}

.task-check-icon.pending {
  color: #909399;
}

.progress-text {
  font-size: 12px;
  color: #409eff;
  margin-left: 8px;
  font-weight: 500;
}

/* 滚动条样式 */
.task-list-container::-webkit-scrollbar {
  width: 6px;
}

.task-list-container::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.task-list-container::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.task-list-container::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>

