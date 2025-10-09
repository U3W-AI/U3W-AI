<template>
  <el-card>
    <el-tabs v-model="activeName">
      <el-tab-pane label="基本信息" name="basic">
        <basic-info-form ref="basicInfoRef" :info="info" />
      </el-tab-pane>
      <el-tab-pane label="字段信息" name="columnInfo">
        <el-table ref="dragTableRef" :data="columns" row-key="columnId" :max-height="tableHeight">
          <el-table-column label="序号" type="index" min-width="5%" class-name="allowDrag" />
          <el-table-column
            label="字段列名"
            prop="columnName"
            min-width="10%"
            :show-overflow-tooltip="true"
          />
          <el-table-column label="字段描述" min-width="10%">
            <template #default="scope">
              <el-input v-model="scope.row.columnComment" @change="handleColumnChange(scope.row)"></el-input>
            </template>
          </el-table-column>
          <el-table-column
            label="物理类型"
            prop="columnType"
            min-width="10%"
            :show-overflow-tooltip="true"
          />
          <el-table-column label="Java类型" min-width="11%">
            <template #default="scope">
              <el-select v-model="scope.row.javaType" @change="handleColumnChange(scope.row)">
                <el-option label="Long" value="Long" />
                <el-option label="String" value="String" />
                <el-option label="Integer" value="Integer" />
                <el-option label="Double" value="Double" />
                <el-option label="BigDecimal" value="BigDecimal" />
                <el-option label="Date" value="Date" />
                <el-option label="Boolean" value="Boolean" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="java属性" min-width="10%">
            <template #default="scope">
              <el-input v-model="scope.row.javaField" @change="handleColumnChange(scope.row)"></el-input>
            </template>
          </el-table-column>

          <el-table-column label="插入" min-width="5%">
            <template #default="scope">
              <el-checkbox 
                true-label="1" 
                false-label="0" 
                v-model="scope.row.isInsert"
                @change="handleColumnChange(scope.row)"
              ></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="编辑" min-width="5%">
            <template #default="scope">
              <el-checkbox 
                true-label="1" 
                false-label="0" 
                v-model="scope.row.isEdit"
                @change="handleColumnChange(scope.row)"
              ></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="列表" min-width="5%">
            <template #default="scope">
              <el-checkbox 
                true-label="1" 
                false-label="0" 
                v-model="scope.row.isList"
                @change="handleColumnChange(scope.row)"
              ></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="查询" min-width="5%">
            <template #default="scope">
              <el-checkbox 
                true-label="1" 
                false-label="0" 
                v-model="scope.row.isQuery"
                @change="handleColumnChange(scope.row)"
              ></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="查询方式" min-width="10%">
            <template #default="scope">
              <el-select v-model="scope.row.queryType" @change="handleColumnChange(scope.row)">
                <el-option label="=" value="EQ" />
                <el-option label="!=" value="NE" />
                <el-option label=">" value="GT" />
                <el-option label=">=" value="GTE" />
                <el-option label="<" value="LT" />
                <el-option label="<=" value="LTE" />
                <el-option label="LIKE" value="LIKE" />
                <el-option label="BETWEEN" value="BETWEEN" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="必填" min-width="5%">
            <template #default="scope">
              <el-checkbox 
                true-label="1" 
                false-label="0" 
                v-model="scope.row.isRequired"
                @change="handleColumnChange(scope.row)"
              ></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column label="显示类型" min-width="12%">
            <template #default="scope">
              <el-select v-model="scope.row.htmlType" @change="handleColumnChange(scope.row)">
                <el-option label="文本框" value="input" />
                <el-option label="文本域" value="textarea" />
                <el-option label="下拉框" value="select" />
                <el-option label="单选框" value="radio" />
                <el-option label="复选框" value="checkbox" />
                <el-option label="日期控件" value="datetime" />
                <el-option label="图片上传" value="imageUpload" />
                <el-option label="文件上传" value="fileUpload" />
                <el-option label="富文本控件" value="editor" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="字典类型" min-width="12%">
            <template #default="scope">
              <el-select 
                v-model="scope.row.dictType" 
                clearable 
                filterable 
                placeholder="请选择"
                @change="handleColumnChange(scope.row)"
              >
                <el-option
                  v-for="dict in dictOptions"
                  :key="dict.dictType"
                  :label="dict.dictName"
                  :value="dict.dictType"
                >
                  <span style="float: left">{{ dict.dictName }}</span>
                  <span style="float: right; color: #8492a6; font-size: 13px">{{ dict.dictType }}</span>
                </el-option>
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="生成信息" name="genInfo">
        <gen-info-form ref="genInfoRef" :info="info" :tables="tables" :menus="menus"/>
      </el-tab-pane>
    </el-tabs>
    
    <el-form label-width="100px">
      <el-form-item style="text-align: center; margin-left: -100px; margin-top: 10px;">
        <el-button type="primary" @click="submitForm">提交</el-button>
        <el-button @click="close">返回</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script>
import { defineComponent, ref, reactive, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getGenTable, updateGenTable } from "@/api/tool/gen"
import { optionselect as getDictOptionselect } from "@/api/system/dict/type"
import { listMenu as getMenuTreeselect } from "@/api/system/menu"
import basicInfoForm from "./basicInfoForm.vue"
import genInfoForm from "./genInfoForm.vue"
import Sortable from 'sortablejs'

export default defineComponent({
  name: "GenEdit",
  components: {
    basicInfoForm,
    genInfoForm
  },
  setup() {
    const route = useRoute()
    const router = useRouter()
    
    // 响应式数据
    const activeName = ref("columnInfo")
    const tableHeight = ref(`${document.documentElement.scrollHeight - 245}px`)
    const tables = ref([])
    const columns = ref([])
    const dictOptions = ref([])
    const menus = ref([])
    const info = reactive({
      tableId: undefined,
      tableName: '',
      tableComment: '',
      className: '',
      packageName: '',
      moduleName: '',
      businessName: '',
      functionName: '',
      functionAuthor: '',
      genType: '0',
      genPath: '/',
      options: '',
      remark: ''
    })
    
    // 模板引用
    const basicInfoRef = ref(null)
    const genInfoRef = ref(null)
    const dragTableRef = ref(null)
    
    // 监听窗口大小变化，更新表格高度
    const updateTableHeight = () => {
      tableHeight.value = `${document.documentElement.scrollHeight - 245}px`
    }
    
    // 处理列数据变化
    const handleColumnChange = (row) => {
      // 确保数据响应式更新
      console.log('列数据变化:', row)
    }
    
    // 获取表详细信息
    const fetchTableData = async () => {
      const tableId = route.params?.tableId
      if (tableId) {
        try {
          const res = await getGenTable(tableId)
          if (res.code === 200) {
            // 深度赋值确保响应式更新
            Object.assign(info, res.data.info || {})
            columns.value = res.data.rows || []
            tables.value = res.data.tables || []
            
            console.log('获取到的表数据:', {
              info: info,
              columns: columns.value,
              tables: tables.value
            })
          }
        } catch (error) {
          console.error('获取表数据失败:', error)
          ElMessage.error('获取表数据失败')
        }
      }
    }
    
    // 获取字典选项
    const fetchDictOptions = async () => {
      try {
        const response = await getDictOptionselect()
        dictOptions.value = response.data || []
      } catch (error) {
        console.error('获取字典选项失败:', error)
      }
    }
    
    // 获取菜单树
    const fetchMenuTree = async () => {
      try {
        const response = await getMenuTreeselect()
        menus.value = handleTree(response.data, "menuId") || []
      } catch (error) {
        console.error('获取菜单树失败:', error)
      }
    }
    
    // 树形数据处理
    const handleTree = (data, idKey, parentKey = 'parentId', childrenKey = 'children') => {
      if (!Array.isArray(data)) return []
      
      const map = {}
      const tree = []
      
      data.forEach(item => {
        map[item[idKey]] = { ...item, [childrenKey]: [] }
      })
      
      data.forEach(item => {
        const parent = map[item[parentKey]]
        if (parent) {
          parent[childrenKey].push(map[item[idKey]])
        } else {
          tree.push(map[item[idKey]])
        }
      })
      
      return tree
    }
    
    // 提交表单
    const submitForm = async () => {
      try {
        console.log('开始提交表单...')
        
        // 获取基本信息表单数据
        let basicFormData = {}
        if (basicInfoRef.value && basicInfoRef.value.getFormData) {
          basicFormData = basicInfoRef.value.getFormData()
        } else if (basicInfoRef.value && basicInfoRef.value.formData) {
          basicFormData = basicInfoRef.value.formData
        }
        
        // 获取生成信息表单数据
        let genFormData = {}
        if (genInfoRef.value && genInfoRef.value.getFormData) {
          genFormData = genInfoRef.value.getFormData()
        } else if (genInfoRef.value && genInfoRef.value.formData) {
          genFormData = genInfoRef.value.formData
        }
        
        console.log('基本信息数据:', basicFormData)
        console.log('生成信息数据:', genFormData)
        console.log('列数据:', columns.value)
        console.log('主信息:', info)
        
        // 合并所有数据
        const submitData = {
          ...info,
          ...basicFormData,
          ...genFormData,
          columns: columns.value.map((col, index) => ({
            ...col,
            sort: index + 1
          })),
          params: {
            treeCode: genFormData.treeCode || basicFormData.treeCode,
            treeName: genFormData.treeName || basicFormData.treeName,
            treeParentCode: genFormData.treeParentCode || basicFormData.treeParentCode,
            parentMenuId: genFormData.parentMenuId || basicFormData.parentMenuId
          }
        }
        
        console.log('最终提交数据:', submitData)
        
        // 验证必填字段
        const requiredFields = [
          'tableName', 'tableComment', 'className', 
          'packageName', 'moduleName', 'businessName'
        ]
        
        const missingFields = requiredFields.filter(field => !submitData[field])
        if (missingFields.length > 0) {
          ElMessage.error(`以下字段不能为空: ${missingFields.join(', ')}`)
          return
        }
        
        // 调用API
        const res = await updateGenTable(submitData)
        if (res.code === 200) {
          ElMessage.success(res.msg || '提交成功')
          close()
        } else {
          ElMessage.error(res.msg || '提交失败')
        }
      } catch (error) {
        console.error('提交失败:', error)
        ElMessage.error('提交失败: ' + (error.message || '未知错误'))
      }
    }
    
    // 关闭页面
    const close = () => {
      router.push({
        path: "/tool/gen",
        query: { 
          t: Date.now(), 
          pageNum: route.query.pageNum || 1 
        }
      })
    }
    
    // 初始化拖拽
    const initSortable = () => {
      nextTick(() => {
        if (!dragTableRef.value) {
          console.warn('dragTableRef 未找到')
          return
        }
        
        const table = dragTableRef.value.$el
        if (!table) return
        
        const tbody = table.querySelector('.el-table__body-wrapper tbody')
        if (!tbody) return
        
        Sortable.create(tbody, {
          handle: ".allowDrag",
          onEnd: (evt) => {
            const newIndex = evt.newIndex
            const oldIndex = evt.oldIndex
            
            if (newIndex === oldIndex) return
            
            const newArray = [...columns.value]
            const [removed] = newArray.splice(oldIndex, 1)
            newArray.splice(newIndex, 0, removed)
            
            // 更新排序
            columns.value = newArray.map((item, index) => ({
              ...item,
              sort: index + 1
            }))
          }
        })
      })
    }
    
    // 初始化数据
    onMounted(async () => {
      window.addEventListener('resize', updateTableHeight)
      
      await Promise.all([
        fetchTableData(),
        fetchDictOptions(),
        fetchMenuTree()
      ])
      
      initSortable()
    })
    
    return {
      activeName,
      tableHeight,
      tables,
      columns,
      dictOptions,
      menus,
      info,
      basicInfoRef,
      genInfoRef,
      dragTableRef,
      handleColumnChange,
      submitForm,
      close
    }
  }
})
</script>

<style scoped>
.allowDrag {
  cursor: move;
}
</style>