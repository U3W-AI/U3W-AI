<template>
  <el-dialog
    :model-value="modelValue"
    title="冲正积分发放"
    width="min(92vw, 560px)"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="!submitting"
    :before-close="beforeClose"
    @update:model-value="handleVisibilityChange"
  >
    <el-alert
      title="只冲正当前原发放"
      :description="operation
        ? `用户 ID：${userId}；原发放：${operation.operationId}；原金额：${operation.delta}。用户和金额由服务端从原操作派生。`
        : '未选择可冲正的原发放。'"
      type="warning"
      show-icon
      :closable="false"
      class="dialog-alert"
    />
    <el-alert
      v-if="ambiguous"
      title="上次请求结果不明确"
      description="本次不会自动重试；表单保留同一幂等键。请使用当前表单重试，或先关闭并刷新账本确认原发放状态。"
      type="error"
      show-icon
      :closable="false"
      aria-live="assertive"
      class="dialog-alert"
    />
    <el-alert
      v-if="formError"
      :title="formError"
      type="error"
      show-icon
      :closable="false"
      aria-live="assertive"
      class="dialog-alert"
    />
    <el-alert
      v-if="externalError"
      :title="externalError"
      type="error"
      show-icon
      :closable="false"
      aria-live="assertive"
      class="dialog-alert"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="submit"
    >
      <el-form-item label="冲正原因" prop="reasonCode">
        <el-select
          v-model="form.reasonCode"
          :disabled="submitting || ambiguous"
          aria-label="积分冲正原因"
          class="full-width"
        >
          <el-option
            v-for="reason in CREDIT_REVERSAL_REASONS"
            :key="reason.value"
            :label="reason.label"
            :value="reason.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="审计备注" prop="note">
        <el-input
          v-model="form.note"
          type="textarea"
          :rows="3"
          minlength="8"
          maxlength="128"
          show-word-limit
          resize="vertical"
          :disabled="submitting || ambiguous"
          aria-label="积分冲正审计备注"
          placeholder="请输入 8–128 个字符，说明冲正事实与依据"
        />
      </el-form-item>
      <el-form-item label="幂等请求标识">
        <el-input
          :model-value="form.idempotencyKey"
          readonly
          aria-label="积分冲正幂等请求标识"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="handleVisibilityChange(false)">取消</el-button>
      <el-button type="danger" :loading="submitting" :disabled="!operation" @click="submit">
        确认冲正原发放
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import {
  buildCreditReversalPayload,
  createCreditIdempotencyKey,
  CREDIT_REVERSAL_REASONS
} from './creditModel'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  userId: { type: Number, required: true },
  operation: { type: Object, default: null },
  accountVersion: { type: Number, required: true },
  submitting: { type: Boolean, default: false },
  ambiguous: { type: Boolean, default: false },
  externalError: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)
const formError = ref('')
const form = reactive({
  reasonCode: '',
  note: '',
  idempotencyKey: ''
})
const rules = Object.freeze({
  reasonCode: [{ required: true, message: '请选择冲正原因', trigger: 'change' }],
  note: [
    { required: true, message: '请输入审计备注', trigger: 'blur' },
    { min: 8, max: 128, message: '审计备注长度必须为 8–128 个字符', trigger: 'blur' }
  ]
})

watch(
  [
    () => props.modelValue,
    () => props.userId,
    () => props.operation?.operationId,
    () => props.accountVersion
  ],
  ([visible, userId, operationId, accountVersion], previous = []) => {
    if ((previous[1] !== undefined && previous[1] !== userId)
        || (previous[2] !== undefined && previous[2] !== operationId)
        || (previous[3] !== undefined && previous[3] !== accountVersion)) reset()
    if (visible) {
      ensureIdempotencyKey()
    } else if (!props.ambiguous) {
      reset()
    }
  }
)

function ensureIdempotencyKey() {
  if (form.idempotencyKey) return
  try {
    form.idempotencyKey = createCreditIdempotencyKey('reverse')
  } catch {
    formError.value = '当前浏览器无法生成安全幂等标识，已停止冲正。'
  }
}

async function submit() {
  formError.value = ''
  ensureIdempotencyKey()
  if (!props.operation || !form.idempotencyKey || props.submitting) return
  try {
    const valid = await formRef.value?.validate()
    if (valid === false) return
    emit('submit', buildCreditReversalPayload({
      operation: props.operation,
      expectedAccountVersion: props.accountVersion,
      reasonCode: form.reasonCode,
      note: form.note,
      idempotencyKey: form.idempotencyKey
    }))
  } catch (error) {
    if (error instanceof Error) formError.value = error.message
  }
}

function beforeClose(done) {
  if (!props.submitting) done()
}

function handleVisibilityChange(visible) {
  if (!props.submitting) emit('update:modelValue', visible)
}

function reset() {
  form.reasonCode = ''
  form.note = ''
  form.idempotencyKey = ''
  formError.value = ''
  formRef.value?.clearValidate()
}

function restore(payload) {
  try {
    if (!props.operation || payload?.originalOperationId !== props.operation.operationId) {
      throw new Error('未决冲正原操作与当前对话框不一致')
    }
    const safe = buildCreditReversalPayload({
      operation: props.operation,
      expectedAccountVersion: payload.expectedAccountVersion,
      reasonCode: payload.reasonCode,
      note: payload.note,
      idempotencyKey: payload.idempotencyKey
    })
    if (safe.expectedAccountVersion !== props.accountVersion) {
      throw new Error('未决冲正账户版本与当前对话框不一致')
    }
    form.reasonCode = safe.reasonCode
    form.note = safe.note
    form.idempotencyKey = safe.idempotencyKey
    formError.value = ''
    formRef.value?.clearValidate()
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '未决冲正恢复失败'
  }
}

defineExpose({ reset, restore })
</script>

<style lang="scss" scoped>
.dialog-alert { margin-bottom: 16px; }
.full-width { width: 100%; }
</style>
