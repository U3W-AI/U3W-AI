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
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
            <el-button type="success" size="mini" @click="pushOffice">收录公众号</el-button>
            <el-button icon="el-icon-delete" type="danger" size="mini" :disabled="ids.length==0" @click="delBatch">批量删除</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">

          <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
        </el-row>

        <el-table  v-loading="loading" :data="commentList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column  label="热词" align="center" key="prompt" prop="prompt" :show-overflow-tooltip="true" />
          <el-table-column  label="链接指令" align="center" key="userPrompt" prop="userPrompt" :show-overflow-tooltip="true" />
          <el-table-column label="文章作者" align="center" key="author" prop="author"  :show-overflow-tooltip="true" />
          <el-table-column label="文章标题" align="center" key="title" prop="title"  :show-overflow-tooltip="true" />
          <el-table-column label="文章链接" align="center" key="answer" prop="answer"  :show-overflow-tooltip="true" />
          <el-table-column  label="内容指令" align="center" key="userPromptTwo" prop="userPromptTwo" :show-overflow-tooltip="true" />
          <el-table-column label="标题摘要" align="center" key="summary" prop="summary"  :show-overflow-tooltip="true" />
          <el-table-column label="正文" align="center" key="text" prop="text"  :show-overflow-tooltip="true" />
          <el-table-column label="是否已收录" align="center" key="isPush" prop="isPush"  :show-overflow-tooltip="true" />
          <el-table-column label="AI" align="center" key="aiName" prop="aiName"  :show-overflow-tooltip="true" />
          <el-table-column label="使用人" align="center" key="nickName" prop="nickName"  :show-overflow-tooltip="true" />
          <el-table-column  label="抓取时间" align="center" prop="createTime" >
            <template slot-scope="scope">
              <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
            <template slot-scope="scope">
              <el-button
                size="mini"
                type="text"
                icon="el-icon-edit"
                @click="handleEdit(scope.row)"
              >编辑</el-button>
              <el-button
                size="mini"
                type="text"
                icon="el-icon-delete"
                @click="handleDelete(scope.row)"
              >删除</el-button>
              <el-button
                size="mini"
                type="text"
                icon="el-ic"
                @click="handleDelete(scope.row)"
              >添加到稿库</el-button>
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

    <!-- 编辑文章链接对话框 -->
    <el-dialog :title="title" :visible.sync="textOpen" width="65%" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="文章作者" prop="author">
          <el-input v-model="form.author" placeholder="请输文章作者名称" />
        </el-form-item>
        <el-form-item label="文章标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入文章标题" />
        </el-form-item>
        <el-form-item label="标题摘要" prop="summary">
          <el-input v-model="form.summary" placeholder="请输入标题摘要" />
        </el-form-item>
        <el-form-item label="正文" >
          <editor v-model="form.text" :min-height="192"/>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitTextForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

  </div>
</template>

<script>
import { getChromeKeyWord,delLink,pushOffice,updateArticleLink,delBatchLink} from "@/api/wechat/aigc";
import "@riophae/vue-treeselect/dist/vue-treeselect.css";

export default {
  name: "User",
  dicts: ['sys_normal_disable', 'sys_user_sex'],
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
        flowStatus: '',
        id: ''
      },
      // 列信息
      columns: [
      ],

      // ---------------------------------
      textOpen:false,
      // 表单参数
      form: {
      },
      // 表单校验
      rules: {
      },
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
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
      getChromeKeyWord(this.queryParams).then(response => {
          this.commentList = response.data.list;
          this.total = response.data.total;
          this.loading = false;
        }
      );
    },
    handleDelete(row) {
      this.editForm.id = row.id
      delLink(this.editForm).then(response => {
        this.$modal.msgSuccess("删除成功");
        this.getList()
      });
    },
    pushOffice(){
      this.loading = true;
      this.$confirm('确定'+(this.ids.length ===0 ? '全部':this.ids.length+'条') +'文章链接收录到公众号草稿箱？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        pushOffice(this.ids).then(response => {
          console.log(response.code);
          this.$modal.msgSuccess("收录成功，请前往绑定的公众号草稿箱查看。");
          this.loading = false;
          this.getList()
        }).catch(error => {
          console.error('收录失败:', error);
          this.$modal.msgError(error.messages || "系统错误，请稍后重试");
          this.loading = false;
        })
      }).catch(() => {
        this.loading = false;
        this.$modal.msgError("已取消全部收录");
      });
      // this.loading = false;
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
      this.ids = selection.map(item => item.id);
      console.log(this.ids);
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
    // 编辑操作按钮
    handleEdit(row) {
      this.reset();
      const id = row.id || this.ids
      this.queryParams.id = id;
      getChromeKeyWord(this.queryParams).then(response => {
        console.log(response.data.list[0]);
        this.form = response.data.list[0];
        this.textOpen = true;
        this.title = "编辑文章";
      });
    },
    // 提交文章
    submitTextForm(){
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateArticleLink(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.textOpen = false;
              this.queryParams.id = '';
              this.getList();
            });
          }
        }
      });
    },
    // 批量删除
    delBatch(){

      console.log(this.ids);

      this.$confirm('此操作将永久删除【' + this.ids.length
          + '】条文章链接, 是否继续?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        delBatchLink(this.ids).then(response => {
          this.$modal.msgSuccess("删除成功");
          this.getList();
        });
      }).catch(() => {
        this.$modal.msgError("已取消删除");
      });
    }
  }
};
</script>
