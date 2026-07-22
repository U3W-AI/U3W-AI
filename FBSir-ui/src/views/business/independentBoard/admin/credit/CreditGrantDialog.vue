<template>
  <el-dialog
    :model-value="modelValue"
    title="受控发放积分"
    width="min(92vw, 560px)"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="!submitting"
    :before-close="beforeClose"
    @update:model-value="handleVisibilityChange"
  >
    <el-alert
      title="高风险运营调整"
      :description="`目标用户 ID：${userId}。金额、原因和审计备注将写入不可变账本；操作人身份仅取服务端登录会话。`"
      type="warning"
      show-icon
      :closable="false"
      class="dialog-alert"
    />
    <el-alert
      v-if="ambiguous"
      title="上次请求结果不明确"
      description="本次不会自动重试；表单保留同一幂等键。请使用当前表单重试，或先关闭并刷新该用户账本核对最新操作。"
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
      <el-form-item label="发放数量" prop="amount">
        <el-input-number
          v-model="form.amount"
          :min="1"
          :max="100000"
          :precision="0"
          controls-position="right"
          :disabled="submitting || ambiguous"
          aria-label="发放积分数量"
          class="full-width"
        />
      </el-form-item>
      <el-form-item label="发放原因" prop="reasonCode">
        <el-select
          v-model="form.reasonCode"
          :disabled="submitting || ambiguous"
          aria-label="积分发放原因"
          class="full-width"
        >
          <el-option
            v-for="reason in CREDIT_GRANT_REASONS"
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
          aria-label="积分发放审计备注"
          placeholder="请输入 8–128 个字符，说明事实与处理依据"
        />
      </el-form-item>
      <el-form-item label="幂等请求标识">
        <el-input
          :model-value="form.idempotencyKey"
          readonly
          aria-label="积分发放幂等请求标识"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="handleVisibilityChange(false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        确认发放并写入账本
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import {
  buildCreditGrantPayload,
  createCreditIdempotencyKey,
  CREDIT_GRANT_REASONS
} from './creditModel'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  userId: { type: Number, required: true },
  accountVersion: { type: Number, required: true },
  submitting: { type: Boolean, default: false },
  ambiguous: { type: Boolean, default: false },
  externalError: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)
const formError = ref('')
const form = reactive({
  amount: null,
  reasonCode: '',
  note: '',
  idempotencyKey: ''
})
const rules = Object.freeze({
  amount: [{ required: true, message: '请输入发放数量', trigger: 'change' }],
  reasonCode: [{ required: true, message: '请选择发放原因', trigger: 'change' }],
  note: [
    { required: true, message: '请输入审计备注', trigger: 'blur' },
    { min: 8, max: 128, message: '审计备注长度必须为 8–128 个字符', trigger: 'blur' }
  ]
})

watch(
  [() => props.modelValue, () => props.userId, () => props.accountVersion],
  ([visible, userId, accountVersion], previous = []) => {
  if (previous[1] !== undefined && previous[1] !== userId) reset()
  if (previous[2] !== undefined && previous[2] !== accountVersion) reset()
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
    form.idempotencyKey = createCreditIdempotencyKey('grant')
  } catch {
    formError.value = '当前浏览器无法生成安全幂等标识，已停止发放。'
  }
}

async function submit() {
  formError.value = ''
  ensureIdempotencyKey()
  if (!form.idempotencyKey || props.submitting) return
  try {
    const valid = await formRef.value?.validate()
    if (valid === false) return
    emit('submit', buildCreditGrantPayload({
      userId: props.userId,
      expectedAccountVersion: props.accountVersion,
      amount: form.amount,
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
  form.amount = null
  form.reasonCode = ''
  form.note = ''
  form.idempotencyKey = ''
  formError.value = ''
  formRef.value?.clearValidate()
}

function restore(payload) {
  try {
    const safe = buildCreditGrantPayload(payload)
    if (safe.userId !== props.userId
        || safe.expectedAccountVersion !== props.accountVersion) {
      throw new Error('未决发放用户或账户版本与当前对话框不一致')
    }
    form.amount = safe.amount
    form.reasonCode = safe.reasonCode
    form.note = safe.note
    form.idempotencyKey = safe.idempotencyKey
    formError.value = ''
    formRef.value?.clearValidate()
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '未决发放恢复失败'
  }
}

defineExpose({ reset, restore })
</script>

<style lang="scss" scoped>
.dialog-alert { margin-bottom: 16px; }
.full-width { width: 100%; }
</style>
