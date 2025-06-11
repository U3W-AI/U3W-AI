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
      handler(user) {
        if (user) {
          this.form = { nickName: user.nickName, phonenumber: user.phonenumber, email: user.email, corpId: user.corpId, sex: user.sex };
        }
      },
      immediate: true
    }
  },
  methods: {
    submit() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          updateUserProfile(this.form).then(response => {
            this.$modal.msgSuccess("修改成功");
            this.user.phonenumber = this.form.phonenumber;
            this.user.email = this.form.email;
          });
        }
      });
    },
    close() {
      this.$tab.closePage();
    }
  }
};
</script>
