<template>
  <div class="app-container">
    <el-row >
      <!--用户数据-->
      <el-col >
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
          <el-form-item label="关键字" prop="keyWord">
            <el-input
              v-model="queryParams.keyWord"
              placeholder="请输入关键字"
              clearable
              style="width: 240px"
              @keyup.enter.native="handleQuery"
            />

          </el-form-item>
          <el-form-item label="状态" >
            <el-select
              v-model="queryParams.flowStatus"
              placeholder="积分类型"
              clearable
              style="width: 240px;margin-bottom: 10px"
              @change="getUserPointsRecord"
            >
              <el-option
                v-for="dict in flowStatusType"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select></el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">

          <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
        </el-row>

        <el-table  v-loading="loading" :data="commentList">
          <el-table-column  label="干货标题" align="center" key="title" prop="title" :show-overflow-tooltip="true" v-if="columns[0].visible" />
          <el-table-column label="评论人" align="center" key="nickName" prop="nickName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
          <el-table-column label="评论内容" align="center" key="comment" prop="comment" v-if="columns[2].visible" :show-overflow-tooltip="true" />
          <el-table-column  label="评论时间" align="center" prop="createTime" v-if="columns[3].visible" >
            <template slot-scope="scope">
              <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="点赞数" align="center" key="userlike" prop="userlike" v-if="columns[4].visible" :show-overflow-tooltip="true" />

          <el-table-column label="评论状态" align="center" key="flowStatus" prop="flowStatus" :show-overflow-tooltip="true" v-if="columns[5].visible">
            <template slot-scope="scope">
        <span :style="{ color: scope.row.flowStatus === 1 ? 'green' : scope.row.flowStatus === 2 ? 'red' : '#FFC125' }">
          {{ scope.row.flowStatus === 0 ? '待审核' : scope.row.flowStatus === 1 ? '审核通过' : '驳回' }}
        </span>
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
                @click="handleUpdate(scope.row.id,1,scope.row.userId)"
              >通过</el-button>
              <el-button style="color:red"
                size="mini"
                type="text"
                @click="handleUpdate(scope.row.id,2,scope.row.userId)"
              >驳回</el-button>
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


  </div>
</template>

<script>
import { getComment,updateComment } from "@/api/wechat/comment";
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
      flowStatusType:[
        {
          label:"全部",
          value:""
        },
        {
        label:"待审核",
        value:"0"
      },
        {
          label:"通过",
          value:"1"
        },
        {
          label:"驳回",
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
        page:0,
        type:'',
        userId:''
      },
      // 用户表格数据
      commentList: null,
      pointsRecordList: null,
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      openPointsRecord: false,
      // 表单参数
      form: {
      },

      editForm:{

      },
      // 查询参数
      queryParams: {
        page: 1,
        limit: 10,
        keyWord: '',
        flowStatus: ''
      },
      // 列信息
      columns: [
        { key: 0, label: `评论标题`, visible: true },
        { key: 1, label: `评论人`, visible: true },
        { key: 2, label: `评论内容`, visible: true },
        { key: 3, label: `评论时间`, visible: true },
        { key: 4, label: `点赞数`, visible: true },
        { key: 5, label: `评论状态`, visible: true }
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
    /** 查询用户列表 */
    getList() {
      this.loading = true;
      getComment(this.queryParams).then(response => {
          this.commentList = response.data.list;
          this.total = response.data.total;
          this.loading = false;
        }
      );
    },
    handleView(row) {
      this.openPointsRecord = true;
      this.queryPointForm.userId = row.userId
      this.getUserPointsRecord();
    },
    getUserPointsRecord(){
      getUserPointsRecord(this.queryPointForm).then(response => {
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
    // 用户状态修改
    handleStatusChange(row) {
      let text = row.status === "0" ? "启用" : "停用";
      this.$modal.confirm('确认要"' + text + '""' + row.userName + '"用户吗？').then(function() {
        return changeUserStatus(row.userId, row.status);
      }).then(() => {
        this.$modal.msgSuccess(text + "成功");
      }).catch(function() {
        row.status = row.status === "0" ? "1" : "0";
      });
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
    handleUpdate(id,flowStatus,userId) {
        this.editForm.id = id;
        this.editForm.userId = userId;
        this.editForm.flowStatus = flowStatus;
      updateComment(this.editForm).then(response => {
        this.$modal.msgSuccess("设置成功");
        this.getList()
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
