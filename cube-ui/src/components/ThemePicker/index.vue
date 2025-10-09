<template>
  <el-color-picker
    v-model="theme"
    :predefine="predefineColors"
    class="theme-picker"
    popper-class="theme-picker-dropdown"
  />
</template>

<script setup>
import { ref, watch } from 'vue'
import { useStore } from 'vuex'

const store = useStore()
const predefineColors = [
  '#409EFF', '#1890ff', '#304156', '#212121', '#11a983', '#13c2c2', '#6959CD', '#f5222d'
]
const theme = ref(store.state.settings?.theme || '#409EFF')

watch(theme, (val) => {
  document.documentElement.style.setProperty('--el-color-primary', val)
  if (store._mutations['settings/SET_THEME']) {
    store.commit('settings/SET_THEME', val)
  }
})
</script>

<style>
.theme-message,
.theme-picker-dropdown {
  z-index: 99999 !important;
}
.theme-picker .el-color-picker__trigger {
  height: 26px !important;
  width: 26px !important;
  padding: 2px;
}
.theme-picker-dropdown .el-color-dropdown__link-btn {
  display: none;
}
</style>