<template>
  <div class="app-container">
    <el-row >
      <!--部门数据-->
<!--      <el-col :span="4" :xs="24">-->
<!--        <div class="head-container">-->
<!--          <el-input-->
<!--            v-model="deptName"-->
<!--            placeholder="请输入部门名称"-->
<!--            clearable-->
<!--            size="small"-->
<!--            prefix-icon="el-icon-search"-->
<!--            style="margin-bottom: 20px"-->
<!--          />-->
<!--        </div>-->
<!--        <div class="head-container">-->
<!--          <el-tree-->
<!--            :data="deptOptions"-->
<!--            :props="defaultProps"-->
<!--            :expand-on-click-node="false"-->
<!--            :filter-node-method="filterNode"-->
<!--            ref="tree"-->
<!--            node-key="id"-->
<!--            default-expand-all-->
<!--            highlight-current-->
<!--            @node-click="handleNodeClick"-->
<!--          />-->
<!--        </div>-->
<!--      </el-col>-->
      <!--用户数据-->
      <el-col >
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
          <el-form-item label="粉丝名称" prop="userName">
            <el-input
              v-model="queryParams.userName"
              placeholder="请输入粉丝名称"
              clearable
              style="width: 240px"
              @keyup.enter.native="handleQuery"
            />
          </el-form-item>
          <el-form-item label="手机号码" prop="phonenumber">
            <el-input
              v-model="queryParams.phonenumber"
              placeholder="请输入手机号码"
              clearable
              style="width: 240px"
              @keyup.enter.native="handleQuery"
            />
          </el-form-item>
<!--          <el-form-item label="加入时间">-->
<!--            <el-date-picker-->
<!--              v-model="dateRange"-->
<!--              style="width: 240px"-->
<!--              value-format="yyyy-MM-dd"-->
<!--              type="daterange"-->
<!--              range-separator="-"-->
<!--              start-placeholder="开始日期"-->
<!--              end-placeholder="结束日期"-->
<!--            ></el-date-picker>-->
<!--          </el-form-item>-->
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">

          <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
        </el-row>

        <el-table  v-loading="loading" :data="userList">
          <el-table-column  label="粉丝编号" align="center" key="userId" prop="userId" v-if="columns[0].visible" />
<!--          <el-table-column label="用户名称" align="center" key="userName" prop="userName" v-if="columns[1].visible" :show-overflow-tooltip="true" />-->
          <el-table-column label="粉丝昵称" align="center" key="nickName" prop="nickName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
          <el-table-column label="AI标签" align="center" key="tags" prop="tags" v-if="columns[1].visible" :show-overflow-tooltip="true" />
          <el-table-column label="积分余额" align="center" key="points" prop="points" v-if="columns[2].visible" :show-overflow-tooltip="true" />
          <el-table-column label="手机号码" align="center" key="phonenumber" prop="phonenumber" v-if="columns[3].visible"  />
          <el-table-column  label="加入时间" align="center" prop="createTime" v-if="columns[4].visible" >
            <template slot-scope="scope">
              <span>{{ parseTime(scope.row.createTime) }}</span>
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
                v-hasPermi="['wechat:points:edit']"
              >设置积分</el-button>
              <el-button
                size="mini"
                type="text"
                @click="handleView(scope.row)"
              >积分明细</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total>0"
          :total="total"
          :page.sync="queryParams.pageNum"
          :limit.sync="queryParams.pageSize"
          @pagination="getList"
        />
      </el-col>
    </el-row>

    <!-- 添加或修改用户配置对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form"  label-width="80px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="用户ID" prop="userId">
              <el-input disabled v-model="form.userId"  maxlength="30" />
            </el-form-item>
            <el-form-item label="用户昵称" prop="nickName">
              <el-input disabled v-model="form.nickName" placeholder="请输入用户昵称" maxlength="30" />
            </el-form-item>
            <el-form-item label="积分数量" prop="changeAmount">
              <el-input type="number"   v-model="form.changeAmount" placeholder="仅支持整数" min="-Infinity" max="Infinity" />
            </el-form-item>
            <el-form-item label="修改后" prop="balanceAfter">
              <el-input type="number"   v-model="balanceAfter" disabled />
            </el-form-item>
          </el-col>

        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="积分详细" :visible.sync="openPointsRecord" width="1000px" append-to-body>
        <el-select
          v-model="queryPointForm.type"
          placeholder="积分类型"
          clearable
          style="width: 240px;margin-bottom: 10px"
          @change="getUserPointsRecord"
        >
          <el-option
            v-for="dict in changeType"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>

      <el-table  v-loading="loading" :data="pointsRecordList">
        <el-table-column label="用户昵称" align="center" key="nick_name" prop="nick_name"  :show-overflow-tooltip="true" />
        <el-table-column label="变更数量" align="center" key="change_amount" prop="change_amount" :show-overflow-tooltip="true">
          <template slot-scope="scope">
                <span :style="{ color: scope.row.change_amount >= 0 ? 'green' : 'red' }">
                  {{ scope.row.change_amount }}
                </span>
          </template>
        </el-table-column>
        <el-table-column label="积分余额" align="center" key="balance_after" prop="balance_after" :show-overflow-tooltip="true" />
        <el-table-column label="变更类型" align="center" key="change_type" prop="change_type"   />
        <el-table-column  width="200" label="变更时间" align="center" prop="create_time" >
          <template slot-scope="scope">
            <span>{{ parseTime(scope.row.create_time) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" align="center" key="create_name" prop="create_name"   />
        <el-table-column label="备注" align="center" key="remark" prop="remark"   />

      </el-table>
      <pagination
        v-show="pointtotal>0"
        :total="pointtotal"
        :page.sync="queryPointForm.page"
        :limit.sync="queryPointForm.limit"
        @pagination="getUserPointsRecord"
      />
    </el-dialog>
  </div>
</template>

<script>
import { listUser, getUser, updateUserPoints, getUserPointsRecord } from "@/api/wechat/company";
import { getToken } from "@/utils/auth";
import Treeselect from "@riophae/vue-treeselect";
import "@riophae/vue-treeselect/dist/vue-treeselect.css";

export default {
  name: "User",
  dicts: ['sys_normal_disable', 'sys_user_sex'],
  components: { Treeselect },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      changeType:[
        {
          label:"全部",
          value:"0"
        },
        {
        label:"增加",
        value:"1"
      },
        {
          label:"消耗",
          value:"2"
        },
      ],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      pointtotal: 0,
      queryPointForm:{
        limit:10,
        page:1,
        type:'',
        userId:''
      },
      // 用户表格数据
      userList: null,
      pointsRecordList: null,
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      openPointsRecord: false,
      // 表单参数
      form: {
      },


      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userName: undefined,
        phonenumber: undefined,
        type:1,
        status: undefined,
        deptId: undefined
      },
      // 列信息
      columns: [
        { key: 0, label: `用户编号`, visible: true },
        { key: 1, label: `用户昵称`, visible: true },
        { key: 2, label: `积分`, visible: true },
        { key: 3, label: `手机号码`, visible: true },
        { key: 4, label: `创建时间`, visible: true }
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
    // this.getDeptTree();
    this.getConfigKey("sys.user.initPassword").then(response => {
      this.initPassword = response.msg;
    });
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
    /** 查询用户列表 */
    getList() {
      this.loading = true;
      listUser(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
          this.userList = response.rows;
          this.total = response.total;
          this.loading = false;
        }
      );
    },
    handleView(row) {

      this.queryPointForm.userId = row.userId
      this.getUserPointsRecord();
    },
    getUserPointsRecord(){
      getUserPointsRecord(this.queryPointForm).then(response => {
        this.openPointsRecord = true;
        this.pointsRecordList = response.data.list;
        this.pointtotal = response.data.total
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
        userId: undefined,
        deptId: undefined,
        userName: undefined,
        nickName: undefined,
        password: undefined,
        phonenumber: undefined,
        email: undefined,
        sex: undefined,
        status: "0",
        remark: undefined,
        postIds: [],
        roleIds: []
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
      this.dateRange = [];
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
      getUser().then(response => {
        this.postOptions = response.posts;
        this.roleOptions = response.roles;
        this.open = true;
        this.title = "添加用户";
        this.form.password = this.initPassword;
      });
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const userId = row.userId || this.ids;
      getUser(userId).then(response => {
        this.form = response.data;
        this.postOptions = response.posts;
        this.roleOptions = response.roles;
        this.$set(this.form, "postIds", response.postIds);
        this.$set(this.form, "roleIds", response.roleIds);
        this.open = true;
        this.title = "设置积分";
        this.form.password = "";
      });
    },

    /** 提交按钮 */
    submitForm: function() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.userId != undefined) {
            this.form.balanceAfter = this.balanceAfter
            updateUserPoints(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
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
