<template>
  <el-form ref="form" :model="form" :rules="rules" label-width="80px">
    <el-form-item label="用户昵称" prop="nickName">
      <el-input v-model="form.nickName" maxlength="30" />
    </el-form-item>
    <el-form-item label="手机号码" prop="phonenumber">
      <el-input v-model="form.phonenumber" maxlength="11" />
    </el-form-item>
    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" maxlength="50" />
    </el-form-item>

    <el-form-item label="主机ID" prop="corpId">
      <el-input v-model="form.corpId" maxlength="50" />
    </el-form-item>
    <el-form-item label="性别">
      <el-radio-group v-model="form.sex">
        <el-radio label="0">男</el-radio>
        <el-radio label="1">女</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" size="mini" @click="submit">保存</el-button>
      <el-button type="danger" size="mini" @click="close">关闭</el-button>
    </el-form-item>
  </el-form>
</template>

<script>
import { updateUserProfile } from "@/api/system/user";
import { setCorpId, ensureLatestCorpId } from "@/utils/corpId";

export default {
  props: {
    user: {
      type: Object
    }
  },
  data() {
    return {
      form: {},
      // 表单校验
      rules: {
        nickName: [
          { required: true, message: "用户昵称不能为空", trigger: "blur" }
        ],
      }
    };
  },
  watch: {
    user: {
      async handler(user) {
        console.log('个人中心watch触发，user:', user ? {corpId: user.corpId, nickName: user.nickName} : 'null');
        if (user) {
          // 先确保主机ID是最新的
          let latestCorpId = user.corpId;
          try {
            const currentCorpId = localStorage.getItem('corp_id');
            console.log('个人中心watch检查 - localStorage主机ID:', currentCorpId, ', 服务器主机ID:', user.corpId);
            if (currentCorpId && currentCorpId !== user.corpId) {
              console.log('表单数据绑定时检测到主机ID不一致，使用最新值:', currentCorpId);
              latestCorpId = currentCorpId;
            } else if (currentCorpId) {
              console.log('主机ID一致，使用localStorage值:', currentCorpId);
              latestCorpId = currentCorpId; // 始终使用localStorage中的值
            }
          } catch (error) {
            console.warn('获取最新主机ID失败:', error);
          }
          
          this.form = { 
            nickName: user.nickName, 
            phonenumber: user.phonenumber, 
            email: user.email, 
            corpId: latestCorpId, 
            sex: user.sex 
          };
          console.log('个人中心表单数据已绑定，主机ID:', latestCorpId);
        }
      },
      immediate: true,
      deep: true
    }
  },
  async mounted() {
    // 监听企业ID自动更新事件
    window.addEventListener('corpIdUpdated', this.handleCorpIdUpdated);
    
    // 确保表单中的主机ID是最新的（补充检查）
    this.$nextTick(async () => {
      try {
        const currentCorpId = localStorage.getItem('corp_id');
        console.log('个人中心mounted补充检查 - localStorage主机ID:', currentCorpId, ', 表单主机ID:', this.form ? this.form.corpId : 'null');
        if (currentCorpId && this.form && this.form.corpId !== currentCorpId) {
          console.log('个人中心页面mounted补充检查，更新表单主机ID:', currentCorpId);
          this.form.corpId = currentCorpId;
          this.$forceUpdate(); // 强制更新视图
        } else if (currentCorpId && this.form) {
          console.log('个人中心mounted检查 - 主机ID已是最新:', currentCorpId);
        }
      } catch (error) {
        console.error('个人中心页面mounted检查主机ID失败:', error);
      }
    });
  },
  beforeDestroy() {
    // 移除事件监听
    window.removeEventListener('corpIdUpdated', this.handleCorpIdUpdated);
  },
  methods: {
    submit() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          const oldCorpId = this.user.corpId;
          
          updateUserProfile(this.form).then(response => {
            this.$modal.msgSuccess("修改成功");
            // 更新父组件的用户数据
            this.user.nickName = this.form.nickName;
            this.user.phonenumber = this.form.phonenumber;
            this.user.email = this.form.email;
            this.user.corpId = this.form.corpId;
            this.user.sex = this.form.sex;
            
            // 如果主机ID发生变化，使用setCorpId函数更新
            if (this.form.corpId && this.form.corpId !== oldCorpId) {
              console.log('用户手动更新主机ID:', this.form.corpId);
              setCorpId(this.form.corpId);
            }
          });
        }
      });
    },
    close() {
      this.$tab.closePage();
    },
    handleCorpIdUpdated(event) {
      console.log('用户信息表单：检测到主机ID更新', event.detail.corpId);
      // 更新表单中的主机ID字段
      if (this.form && event.detail.corpId) {
        this.form.corpId = event.detail.corpId;
        this.$forceUpdate(); // 强制更新视图
      }
    }
  }
};
</script>
