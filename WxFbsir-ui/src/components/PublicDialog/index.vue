<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    :width="width"
    :top="top"
    :fullscreen="fullscreen"
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :show-close="showClose"
    :destroy-on-close="destroyOnClose"
    :append-to-body="appendToBody"
    @close="handleClose"
    @opened="handleOpened"
    @closed="handleClosed">
    <div :style="contentStyle">
      <slot></slot>
    </div>
    <template #footer v-if="showFooter">
      <div :class="footerClass">
        <slot name="footer"></slot>
      </div>
    </template>
  </el-dialog>
</template>

<script>
export default {
  name: "PublicDialog",
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    title: {
      type: String,
      default: ""
    },
    width: {
      type: String,
      default: "50%"
    },
    top: {
      type: String,
      default: "15vh"
    },
    fullscreen: {
      type: Boolean,
      default: false
    },
    closeOnClickModal: {
      type: Boolean,
      default: true
    },
    closeOnPressEscape: {
      type: Boolean,
      default: true
    },
    showClose: {
      type: Boolean,
      default: true
    },
    destroyOnClose: {
      type: Boolean,
      default: false
    },
    appendToBody: {
      type: Boolean,
      default: false
    },
    showFooter: {
      type: Boolean,
      default: true
    },
    footerClass: {
      type: String,
      default: "dialog-footer"
    },
    contentStyle: {
      type: Object,
      default: () => ({})
    }
  },
  emits: ['update:visible', 'close', 'opened', 'closed'],
  methods: {
    handleClose() {
      this.$emit('update:visible', false);
      this.$emit('close');
    },
    handleOpened() {
      this.$emit('opened');
    },
    handleClosed() {
      this.$emit('closed');
    }
  }
};
</script>

<style scoped>
.dialog-footer {
  text-align: right;
}
</style>