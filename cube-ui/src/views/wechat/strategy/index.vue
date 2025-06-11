<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="作者" prop="author">
        <el-input
          v-model="queryParams.author"
          placeholder="请输入作者"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
        >新增</el-button>
      </el-col>
      <!--        <el-col :span="1.5">-->
      <!--          <el-button-->
      <!--            type="success"-->
      <!--            plain-->
      <!--            icon="el-icon-edit"-->
      <!--            size="mini"-->
      <!--            :disabled="single"-->
      <!--            @click="handleUpdate"-->
      <!--          >修改</el-button>-->
      <!--        </el-col>-->
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="strategyList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <!--        <el-table-column label="攻略表id" align="center" prop="id" />-->
      <el-table-column label="攻略标题" align="center" prop="strategyTitle" />
      <!--        <el-table-column label="内容" align="center" prop="strategyContent" />-->
      <el-table-column label="作者" align="center" prop="author" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="攻略图" align="center" prop="picUrl" width="100">
        <template slot-scope="scope">
          <image-preview :src="scope.row.picUrl" :width="50" :height="50"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
          >删除</el-button>
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

    <!-- 添加或修改攻略记录对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="65%" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="攻略标题" prop="strategyTitle">
          <el-input v-model="form.strategyTitle" placeholder="请输入攻略标题" />
        </el-form-item>
        <el-form-item label="内容">
          <editor v-model="form.strategyContent" :min-height="192"/>
        </el-form-item>
        <el-form-item label="攻略概述" prop="desc">
          <el-input v-model="form.desc" placeholder="请输入攻略概述，建议100字以内" />
        </el-form-item>
        <el-form-item label="作者" prop="author">
          <el-input v-model="form.author" placeholder="请输入作者" />
        </el-form-item>
        <el-form-item v-if="false" label="浏览id" prop="viewId">
          <el-input v-model="form.viewId" placeholder="请输入浏览id" />
        </el-form-item>
        <el-form-item v-if="false" label="点赞id" prop="likeId">
          <el-input v-model="form.likeId" placeholder="请输入点赞id" />
        </el-form-item>
        <el-form-item v-if="false" label="收藏id" prop="collectionsId">
          <el-input v-model="form.collectionsId" placeholder="请输入收藏id" />
        </el-form-item>
        <el-form-item label="攻略图" prop="picUrl">
          <image-upload v-model="form.picUrl"/>
        </el-form-item>
        <el-form-item v-if="false" label="评论id" prop="commentId">
          <el-input v-model="form.commentId" placeholder="请输入评论id" />
        </el-form-item>
        <el-form-item label="标签" prop="tag">
          <el-input v-model="form.tag" placeholder="请输入标签,多个标签用英文逗号隔开" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listStrategy, getStrategy, delStrategy, addStrategy, updateStrategy } from "@/api/wechat/strategy";

export default {
  name: "Strategy",
  data() {
    return {
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
      // 攻略记录表格数据
      strategyList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        page: 1,
        limit: 10,
        author: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询攻略记录列表 */
    getList() {
      this.loading = true;
      listStrategy(this.queryParams).then(response => {
        this.strategyList = response.data.list;
        this.total = response.data.total;
        this.loading = false;
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
        id: null,
        strategyTitle: null,
        strategyContent: null,
        author: null,
        createTime: null,
        viewId: null,
        likeId: null,
        collectionsId: null,
        picUrl: null,
        commentId: null,
        tag: null
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
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加攻略";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids
      getStrategy(id).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改攻略";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateStrategy(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addStrategy(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal.confirm('是否确认删除攻略记录编号为"' + ids + '"的数据项？').then(function() {
        return delStrategy(ids);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('admin/strategy/export', {
        ...this.queryParams
      }, `strategy_${new Date().getTime()}.xlsx`)
    }
  }
};
</script>
