<template>
  <div class="app-container">
    <el-row >

      <!--用户数据-->
      <el-col >
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
          <el-form-item  prop="userName">
            <el-input
              v-model="queryParams.keyWord"
              placeholder="请输入干货标题"
              clearable
              style="width: 240px"
              @keyup.enter.native="handleQuery"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">

          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button
                type="primary"
                plain
                icon="el-icon-plus"
                size="mini"
                @click="handleAdd"
                v-hasPermi="['wechat:research:add']"
              >干货上传</el-button>
            </el-col>
            <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
          </el-row>
        </el-row>

        <el-table  v-loading="loading" :data="resList">
          <el-table-column width="220"  label="干货标题" key="title" prop="title" v-if="columns[0].visible" :show-overflow-tooltip="true"/>
          <el-table-column width="80"  label="关键词"  key="keyword" prop="keyword" v-if="columns[1].visible" :show-overflow-tooltip="true" />
          <el-table-column width="150" label="干货链接"  key="resUrl" prop="resUrl" v-if="columns[2].visible" :show-overflow-tooltip="true" />
          <el-table-column width="100" label="所属行业"  key="industry" prop="industry" v-if="columns[3].visible"  />
          <el-table-column width="100" label="标签"  key="tag" prop="tag" v-if="columns[4].visible" :show-overflow-tooltip="true" />
          <el-table-column width="80" label="下载" align="center" key="downNum" prop="downNum" v-if="columns[5].visible">
            <template v-slot="{ row }">
              <span @click="handleResData(row,1)" :style="{ color: row.downNum ? 'blue' : '' }">{{ row.downNum }}</span>
            </template>
          </el-table-column>
          <el-table-column width="80" label="收藏" align="center" key="collectionNum" prop="collectionNum" v-if="columns[6].visible"  >
            <template v-slot="{ row }">
              <span @click="handleResData(row,2)" :style="{ color: row.collectionNum ? 'blue' : '' }">{{ row.collectionNum }}</span>
            </template>
          </el-table-column>
          <el-table-column width="80" label="浏览" align="center" key="browseNum" prop="browseNum" v-if="columns[7].visible" >
            <template v-slot="{ row }">
              <span @click="handleResData(row,3)" :style="{ color: row.browseNum ? 'blue' : '' }">{{ row.browseNum }}</span>
            </template>
          </el-table-column>
          <el-table-column width="100" label="干货作者"  key="resource" prop="resource" v-if="columns[8].visible"  :show-overflow-tooltip="true" />

          <el-table-column width="80" label="状态"  align="center" key="flowStatus" prop="flowStatus" :show-overflow-tooltip="true" v-if="columns[9].visible">
            <template slot-scope="scope">
        <span :style="{ color: scope.row.flowStatus === 1 ? 'green' : scope.row.flowStatus === 2 ? 'red' : '#FFC125' }">
          {{ scope.row.flowStatus === 0 ? '待审核' : scope.row.flowStatus === 1 ? '正常' : scope.row.flowStatus === 2?'驳回':'暂停下载' }}
        </span>
            </template>
          </el-table-column>
          <el-table-column width="100" label="驳回原因"  key="reason" prop="reason" v-if="columns[10].visible"  :show-overflow-tooltip="true"/>

          <el-table-column width="100" label="上传人"  key="userName" prop="userName" v-if="columns[11].visible"  />

          <el-table-column width="150"  label="上传时间"  prop="createTime" v-if="columns[12].visible" >
            <template slot-scope="scope">
              <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column width="100" label="最后更新人"  key="updUserName" prop="updUserName" v-if="columns[13].visible"  />

          <el-table-column width="140"  label="最后更新时间"  prop="updateTime" v-if="columns[14].visible" >
            <template slot-scope="scope">
              <span>{{ parseTime(scope.row.updateTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            align="center"
            class-name="small-padding fixed-width"
          >
            <template slot-scope="scope" v-if="scope.row.userId !== 1">
              <el-button
                size="mini"
                type="text"
                @click="handleUpdate(scope.row)"
                v-hasPermi="['wechat:research:edit']"
              >修改</el-button>
              <el-button
                size="mini"
                type="text"
                v-if="scope.row.flowStatus == 0"
                @click="handleView(scope.row)"
                v-hasPermi="['wechat:research:flow']"
              >通过</el-button>
              <el-button
                size="mini"
                type="text"
                v-if="scope.row.flowStatus == 0"
                @click="backFlow(scope.row)"
                style="color:red"
                v-hasPermi="['wechat:research:flow']"
              >驳回</el-button>
              <el-button
                size="mini"
                type="text"
                v-if="scope.row.flowStatus == 1"
                @click="stopReport(scope.row)"
                v-hasPermi="['wechat:research:flow']"
              >暂停</el-button>
              <el-button
                size="mini"
                type="text"
                v-if="scope.row.flowStatus == 3"
                @click="startReport(scope.row)"
                v-hasPermi="['wechat:research:flow']"
              >恢复</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total>0"
          :total="total"
          :page.sync="queryParams.page"
          :limit.sync="queryParams.limit"
          @pagination="getList"
        />
      </el-col>
    </el-row>
    <el-dialog :title="title" :visible.sync="flowopen" width="600px" append-to-body>
      <el-form ref="form" :model="flowForm"  label-width="80px">
        <el-form-item v-if="this.flowForm.flowStatus == 1" label="消耗积分" prop="changeAmount">
                  <el-input type="number"   v-model="flowForm.changeAmount" placeholder="下载需要扣除的积分数量" max="Infinity" />
        </el-form-item>

        <el-form-item v-if="this.flowForm.flowStatus == 2"  label="驳回原因">
          <el-input v-model="flowForm.reason" type="textarea" placeholder="请输入内容"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="changeFlowStatus">确 定</el-button>
      </div>
    </el-dialog>

    <!-- 添加或修改用户配置对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form"  label-width="80px">
        <el-row>
          <el-col :span="20">
            <el-form-item label="干货标题" prop="title">
              <el-input v-model="form.title" placeholder="请输入干货标题"/>
            </el-form-item>
            <el-form-item label="关键词" prop="keyWord">
              <el-input  v-model="form.keyWord" placeholder="请输入四位数关键词" maxlength="30" />
            </el-form-item>
            <el-form-item label="干货链接" prop="resUrl">
              <el-input  v-model="form.resUrl" placeholder="请输入干货微盘链接" />
            </el-form-item>
            <el-form-item label="所属行业" prop="industry">
              <el-select
                v-model="form.industry"
                placeholder="所属行业"
                clearable
              >
                <el-option
                  v-for="dict in dict.type.sys_industry"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="干货标签" prop="tag">
              <el-select
                v-model="form.tag"
                multiple
                placeholder="干货标签（最多选5个）"
              >
                <el-option
                  v-for="dict in dict.type.sys_user_tag"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="干货作者" prop="resource">
              <el-input  v-model="form.resource" placeholder="请输入干货作者" maxlength="30" />
            </el-form-item>

<!--            <el-form-item label="消耗积分" prop="changeAmount">-->
<!--              <el-input type="number"   v-model="form.changeAmount" placeholder="仅支持整数" min="-Infinity" max="Infinity" />-->
<!--            </el-form-item>-->
          </el-col>


        </el-row>

      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>


    <el-dialog title="干货数据" :visible.sync="openRecord" width="1000px" append-to-body>

      <el-table  v-loading="loading" :data="recordList">
        <el-table-column label="干货标题" align="center" key="title" prop="title"  :show-overflow-tooltip="true" />
        <el-table-column label="粉丝昵称" align="center" key="nickName" prop="nickName" :show-overflow-tooltip="true" />
        <el-table-column  width="200" label="时间" align="center" prop="createTime" >
          <template slot-scope="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="recordtotal>0"
        :total="recordtotal"
        :page.sync="queryResParams.page"
        :limit.sync="queryResParams.limit"
        @pagination="getResOpeData"
      />
    </el-dialog>


  </div>
</template>

<script>
import { getReportList,addReport,getReportDetail,changeResportFlowStatus,updateReport,getResOpeData} from "@/api/wechat/research";
import { getToken } from "@/utils/auth";
import "@riophae/vue-treeselect/dist/vue-treeselect.css";

export default {
  name: "User",
  dicts: ['sys_industry','sys_user_tag'],
  components: {  },
  data() {
    return {
      openRecord: false,
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      recordtotal: 0,
      // 用户表格数据
      resList: null,
      recordList: null,
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      flowopen: false,
      flowForm:{
        flowStatus: '',
        id:'',
        changeAmount:0,
        reason:''
      },
      openPointsRecord: false,
      // 表单参数
      form: {
      },
      // 查询参数
      queryParams: {
        page: 1,
        limit: 10,
        keyWord: undefined,
        flowStatus: undefined
      },
      queryResParams: {
        page: 1,
        limit: 10,
        id:'',
        dataType:1,
      },
      // 列信息
      columns: [
        { key: 0, label: `干货标题`, visible: true },
        { key: 1, label: `关键词`, visible: true },
        { key: 2, label: `干货链接`, visible: true },
        { key: 3, label: `所属行业`, visible: true },
        { key: 4, label: `总下载数`, visible: true },
        { key: 5, label: `总收藏数`, visible: true },
        { key: 6, label: `总下载数`, visible: true },
        { key: 7, label: `总浏览数`, visible: true },
        { key: 8, label: `干货作者`, visible: true },
        { key: 9, label: `审核状态`, visible: true },
        { key: 10, label: `驳回原因`, visible: false },
        { key: 11, label: `上传人`, visible: true },
        { key: 12, label: `上传时间`, visible: true },
        { key: 13, label: `最后更新人`, visible: false },
        { key: 14, label: `最后更新时间`, visible: false }
      ],

    };
  },
  watch: {
    // 根据名称筛选部门树
    deptName(val) {
      this.$refs.tree.filter(val);
    }
  },
  created() {
    this.getList();
  },
  computed: {
    balanceAfter() {
      // 将changeAmount转换为数字，如果转换失败则使用0
      const changeAmount = parseInt(this.form.changeAmount, 10) || 0;
      // 返回points与changeAmount的和
      return this.form.points + changeAmount;
    }
  },
  methods: {
    handleResData(row,num) {
      this.openRecord =true
      this.queryResParams.id = row.id;
      this.queryResParams.dataType = num;
      this.getResOpeData()
    },
    /** 查询用户列表 */
    getList() {
      this.loading = true;
      getReportList(this.queryParams).then(response => {
          this.resList = response.data.list;
          this.total = response.data.total;
          this.loading = false;
        }
      );
    },
    getResOpeData() {
      this.loading = true;
      getResOpeData(this.queryResParams).then(response => {
          this.recordList = response.data.list;
          this.recordtotal = response.data.total;
          this.loading = false;
        }
      );
    },
    handleView(row) {
      this.flowopen = true;
      this.flowForm.flowStatus =1;
      this.flowForm.id = row.id
    },
    backFlow(row) {
      this.flowopen = true;
      this.flowForm.flowStatus =2;
      this.flowForm.id = row.id
    },

    stopReport(row){
      this.flowForm.flowStatus = 3;
      this.flowForm.id = row.id
      changeResportFlowStatus(this.flowForm).then(response => {
        this.$modal.msgSuccess("暂停下载成功");
        this.getList();
      });
    },
    startReport(row){
      this.flowForm.flowStatus = 1;
      this.flowForm.id = row.id
      changeResportFlowStatus(this.flowForm).then(response => {
        this.$modal.msgSuccess("恢复下载成功");
        this.getList();
      });
    },

    // 筛选节点
    filterNode(value, data) {
      if (!value) return true;
      return data.label.indexOf(value) !== -1;
    },
    // 节点单击事件
    handleNodeClick(data) {
      this.queryParams.deptId = data.id;
      this.handleQuery();
    },

    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
      };
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.queryParams.deptId = undefined;
      this.$refs.tree.setCurrentKey(null);
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.userId);
      this.single = selection.length != 1;
      this.multiple = !selection.length;
    },
    // 更多操作触发
    handleCommand(command, row) {
      switch (command) {
        case "handleResetPwd":
          this.handleResetPwd(row);
          break;
        case "handleAuthRole":
          this.handleAuthRole(row);
          break;
        default:
          break;
      }
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const resId = row.id;
      getReportDetail(resId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "干货修改";
      });
    },

    changeFlowStatus(){
      changeResportFlowStatus(this.flowForm).then(response => {
        this.$modal.msgSuccess("设置成功");
        this.flowopen = false;
        this.getList();
      });
    },
    /** 提交按钮 */
    submitForm: function() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          this.form.tag = this.form.tag.join(','); // 将数组转换为逗号分隔的字符串

          if (this.form.id != undefined) {
            this.form.balanceAfter = this.balanceAfter
            updateReport(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          }else{
            //新增
            addReport(this.form).then(response => {
              this.$modal.msgSuccess("上传成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },

  }
};
</script>
