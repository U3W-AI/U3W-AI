<template>
  <el-dialog
    :model-value="modelValue"
    :title="isRollback ? '发起套餐策略补偿回滚' : '修订套餐策略'"
    width="min(680px, calc(100vw - 32px))"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :before-close="beforeClose"
    @update:model-value="handleVisibilityChange"
    @closed="$emit('closed')"
  >
    <el-alert
      v-if="isRollback"
      title="补偿回滚会创建新版本"
      :description="`将历史回执 ${form.rollbackOfReceiptId} 的五项策略原样复制为 N+1；回滚值已冻结，不会修改历史或让 head 倒退。`"
      type="warning"
      show-icon
      :closable="false"
      class="dialog-alert"
    />
    <el-alert
      v-if="conflict"
      title="409：当前策略或幂等意图已冲突"
      description="原表单、预期版本和幂等键均已保留。请手动刷新当前策略进行对比；页面不会自动套用新版本，也不会自动改写后重试。"
      type="error"
      show-icon
      :closable="false"
      aria-live="assertive"
      class="dialog-alert"
    />
    <el-alert
      v-else-if="ambiguous"
      title="上次请求结果不明确"
      description="仅可使用当前锁定的同一负载与同一幂等键精确重放；所有可变字段已冻结。"
      type="error"
      show-icon
      :closable="false"
      aria-live="assertive"
      class="dialog-alert"
    />
    <el-alert
      v-if="externalError || formError"
      :title="externalError || formError"
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
      <div class="identity-grid">
        <el-form-item label="套餐">
          <el-input
            :model-value="planLabel"
            readonly
            aria-label="套餐策略目标套餐"
          />
        </el-form-item>
        <el-form-item label="预期当前版本">
          <el-input
            :model-value="form.expectedVersion"
            readonly
            aria-label="套餐策略预期当前版本"
          />
        </el-form-item>
      </div>

      <el-form-item label="套餐名称" prop="planName">
        <el-input
          ref="planNameInput"
          v-model="form.planName"
          minlength="1"
          maxlength="128"
          show-word-limit
          :disabled="submitting || ambiguous || conflict || isRollback"
          aria-label="套餐策略套餐名称"
        />
      </el-form-item>

      <div class="quota-grid">
        <el-form-item label="每日会议额度" prop="dailyMeetingLimit">
          <el-input-number
            v-model="form.dailyMeetingLimit"
            :min="1"
            :max="10000"
            :precision="0"
            controls-position="right"
            :disabled="submitting || ambiguous || conflict || isRollback"
            aria-label="套餐策略每日会议额度"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="单次议题额度" prop="agendaLimit">
          <el-input-number
            v-model="form.agendaLimit"
            :min="1"
            :max="30"
            :precision="0"
            controls-position="right"
            :disabled="submitting || ambiguous || conflict || isRollback"
            aria-label="套餐策略单次议题额度"
            class="full-width"
          />
        </el-form-item>
      </div>

      <el-form-item label="席位额度" prop="seatLimit">
        <div class="seat-editor">
          <el-input-number
            v-model="form.seatLimit"
            :min="1"
            :max="100"
            :precision="0"
            controls-position="right"
            :disabled="submitting || ambiguous || conflict || isRollback || unlimitedSeats"
            aria-label="套餐策略席位额度"
            class="seat-input"
          />
          <el-checkbox
            v-if="form.planCode === 'BOARD_VIP'"
            v-model="unlimitedSeats"
            :disabled="submitting || ambiguous || conflict || isRollback"
            aria-label="VIP 套餐不设席位上限"
          >不设套餐席位上限</el-checkbox>
        </div>
      </el-form-item>

      <el-form-item label="秘书能力" prop="secretaryEnabled">
        <el-switch
          v-model="form.secretaryEnabled"
          :disabled="submitting || ambiguous || conflict || isRollback"
          inline-prompt
          active-text="开启"
          inactive-text="关闭"
          aria-label="套餐策略秘书能力"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button :disabled="submitting" @click="handleVisibilityChange(false)">关闭</el-button>
        <el-button
          v-if="conflict"
          type="warning"
          :loading="refreshing"
          :disabled="submitting || refreshing"
          @click="$emit('manual-refresh')"
        >手动刷新当前策略</el-button>
        <el-button
          v-if="conflict"
          type="danger"
          plain
          :disabled="submitting || refreshing"
          @click="$emit('abandon')"
        >放弃旧意图</el-button>
        <el-button
          v-else
          type="primary"
          :loading="submitting"
          :disabled="submitting || !writeAvailable"
          @click="submit"
        >{{ ambiguous ? '使用原请求精确重放' : (isRollback ? '确认补偿回滚' : '提交完整修订') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import {
  buildPlanPolicyRevisionPayload,
  buildPlanPolicyRollbackPayload,
  createPlanPolicyIdempotencyKey,
  parsePlanPolicyRevisionPayload
} from './planPolicyModel.js'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  catalog: { type: Array, required: true },
  submitting: { type: Boolean, default: false },
  ambiguous: { type: Boolean, default: false },
  conflict: { type: Boolean, default: false },
  refreshing: { type: Boolean, default: false },
  writeAvailable: { type: Boolean, default: true },
  externalError: { type: String, default: '' }
})
const emit = defineEmits([
  'update:modelValue', 'submit', 'manual-refresh', 'abandon', 'closed'
])

const formRef = ref(null)
const planNameInput = ref(null)
const formError = ref('')
const unlimitedSeats = ref(false)
const lockedRollbackPayload = ref(null)
const form = reactive(emptyForm())

const isRollback = computed(() => form.rollbackOfReceiptId !== null)
const planLabel = computed(() => props.catalog
  .find(plan => plan.planCode === form.planCode)?.planName || form.planCode || '')

const rules = Object.freeze({
  planName: [
    { required: true, message: '请输入套餐名称', trigger: 'blur' },
    { min: 1, max: 128, message: '套餐名称长度必须为 1–128 个字符', trigger: 'blur' }
  ],
  dailyMeetingLimit: [
    { required: true, message: '请输入每日会议额度', trigger: 'change' }
  ],
  agendaLimit: [
    { required: true, message: '请输入单次议题额度', trigger: 'change' }
  ],
  seatLimit: [{ validator: validateSeatLimit, trigger: 'change' }]
})

watch(unlimitedSeats, value => {
  if (form.planCode !== 'BOARD_VIP') return
  if (value) form.seatLimit = null
  else if (form.seatLimit === null) form.seatLimit = 1
})

watch(() => props.modelValue, visible => {
  if (visible) nextTick(() => planNameInput.value?.focus?.())
})

function emptyForm() {
  return {
    planCode: '',
    expectedVersion: null,
    planName: '',
    dailyMeetingLimit: null,
    agendaLimit: null,
    seatLimit: null,
    secretaryEnabled: false,
    rollbackOfReceiptId: null,
    idempotencyKey: ''
  }
}

function assignPayload(payload) {
  const safe = parsePlanPolicyRevisionPayload(payload)
  Object.assign(form, safe)
  lockedRollbackPayload.value = safe.rollbackOfReceiptId === null ? null : safe
  unlimitedSeats.value = safe.planCode === 'BOARD_VIP' && safe.seatLimit === null
  formError.value = ''
  formRef.value?.clearValidate()
}

function openRevision(plan) {
  reset()
  Object.assign(form, {
    planCode: plan.planCode,
    expectedVersion: plan.version,
    planName: plan.planName,
    dailyMeetingLimit: plan.dailyMeetingLimit,
    agendaLimit: plan.agendaLimit,
    seatLimit: plan.seatLimit,
    secretaryEnabled: plan.secretaryEnabled,
    rollbackOfReceiptId: null,
    idempotencyKey: createPlanPolicyIdempotencyKey('revise')
  })
  unlimitedSeats.value = plan.planCode === 'BOARD_VIP' && plan.seatLimit === null
  emit('update:modelValue', true)
}

function openRollback(receipt) {
  reset()
  assignPayload(buildPlanPolicyRollbackPayload({
    catalog: props.catalog,
    receipt,
    idempotencyKey: createPlanPolicyIdempotencyKey('rollback')
  }))
  emit('update:modelValue', true)
}

function restore(payload) {
  reset()
  assignPayload(payload)
  emit('update:modelValue', true)
}

async function submit() {
  if (props.submitting || props.conflict || !props.writeAvailable) return
  formError.value = ''
  try {
    const valid = await formRef.value?.validate()
    if (valid === false) return
    const raw = {
      planCode: form.planCode,
      expectedVersion: form.expectedVersion,
      planName: form.planName,
      dailyMeetingLimit: form.dailyMeetingLimit,
      agendaLimit: form.agendaLimit,
      seatLimit: unlimitedSeats.value ? null : form.seatLimit,
      secretaryEnabled: form.secretaryEnabled,
      rollbackOfReceiptId: form.rollbackOfReceiptId,
      idempotencyKey: form.idempotencyKey
    }
    const payload = isRollback.value
      ? parsePlanPolicyRevisionPayload(lockedRollbackPayload.value)
      : (props.ambiguous
        ? parsePlanPolicyRevisionPayload(raw)
        : buildPlanPolicyRevisionPayload({
        catalog: props.catalog,
        draft: {
          planCode: raw.planCode,
          expectedVersion: raw.expectedVersion,
          planName: raw.planName,
          dailyMeetingLimit: raw.dailyMeetingLimit,
          agendaLimit: raw.agendaLimit,
          seatLimit: raw.seatLimit,
          secretaryEnabled: raw.secretaryEnabled
        },
        rollbackOfReceiptId: raw.rollbackOfReceiptId,
        idempotencyKey: raw.idempotencyKey
      }))
    emit('submit', payload)
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '套餐策略表单校验失败'
  }
}

function validateSeatLimit(rule, value, callback) {
  if (form.planCode === 'BOARD_VIP' && unlimitedSeats.value && value === null) {
    callback()
    return
  }
  if (!Number.isSafeInteger(value) || value < 1 || value > 100) {
    callback(new Error('有限席位额度必须是 1–100 的整数'))
    return
  }
  callback()
}

function beforeClose(done) {
  if (!props.submitting) done()
}

function handleVisibilityChange(visible) {
  if (!props.submitting) emit('update:modelValue', visible)
}

function reset() {
  Object.assign(form, emptyForm())
  unlimitedSeats.value = false
  lockedRollbackPayload.value = null
  formError.value = ''
  formRef.value?.clearValidate()
}

defineExpose({ openRevision, openRollback, restore, reset })
</script>

<style lang="scss" scoped>
.dialog-alert {
  margin-bottom: 16px;
}

.identity-grid,
.quota-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.seat-editor {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.seat-input,
.full-width {
  width: 100%;
}

.seat-input {
  max-width: 260px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 768px) {
  .identity-grid,
  .quota-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .seat-editor,
  .dialog-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .seat-input,
  .dialog-footer .el-button {
    width: 100%;
    max-width: none;
    margin-left: 0;
  }
}
</style>
