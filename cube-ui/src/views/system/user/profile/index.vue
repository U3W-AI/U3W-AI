<template>
  <div class="app-container">
    <el-row :gutter="20">
      <el-col :span="6" :xs="24">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>个人信息</span>
          </div>
          <div>
            <div class="text-center">
              <userAvatar />
            </div>
            <ul class="list-group list-group-striped">
              <li class="list-group-item">
                <svg-icon icon-class="user" />用户名称
                <div class="pull-right" id="userName">{{ user.userName }}</div>
              </li>
              <li class="list-group-item">
                <svg-icon icon-class="phone" />手机号码
                <div class="pull-right">{{ user.phonenumber }}</div>
              </li>
              <li class="list-group-item">
                <svg-icon icon-class="email" />用户邮箱
                <div class="pull-right">{{ user.email }}</div>
              </li>
              <li class="list-group-item">
                <svg-icon icon-class="tree" />所属部门
                <div class="pull-right" v-if="user.dept">{{ user.dept.deptName }} / {{ postGroup }}</div>
              </li>
              <li class="list-group-item">
                <svg-icon icon-class="peoples" />所属角色
                <div class="pull-right">{{ roleGroup }}</div>
              </li>
              <li class="list-group-item">
                <svg-icon icon-class="date" />创建日期
                <div class="pull-right">{{ user.createTime }}</div>
              </li>
            </ul>
          </div>
        </el-card>
      </el-col>
      <el-col :span="18" :xs="24">
        <el-card>
          <div slot="header" class="clearfix">
            <span>基本资料</span>
          </div>
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本资料" name="userinfo">
              <userInfo :user="user" />
            </el-tab-pane>
<!--            <el-tab-pane label="修改密码" name="resetPwd">-->
<!--              <resetPwd />-->
<!--            </el-tab-pane>-->
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import userAvatar from "./userAvatar";
import userInfo from "./userInfo";
import resetPwd from "./resetPwd";
import { getUserProfile } from "@/api/system/user";
import { ensureLatestCorpId } from "@/utils/corpId";

export default {
  name: "Profile",
  components: { userAvatar, userInfo, resetPwd },
  data() {
    return {
      user: {},
      roleGroup: {},
      postGroup: {},
      activeTab: "userinfo"
    };
  },
  async created() {
    // 确保主机ID是最新的，然后获取用户信息
    try {
      const result = await ensureLatestCorpId();
      console.log('个人中心父页面进入时主机ID已更新:', result.corpId);
    } catch (error) {
      console.warn('刷新主机ID失败，继续加载用户信息:', error);
    }
    this.getUser();
  },
  mounted() {
    // 监听企业ID自动更新事件
    window.addEventListener('corpIdUpdated', this.handleCorpIdUpdated);
  },
  beforeDestroy() {
    // 移除事件监听
    window.removeEventListener('corpIdUpdated', this.handleCorpIdUpdated);
  },
  methods: {
    async getUser() {
      try {
        const response = await getUserProfile();
        this.user = response.data;
        this.roleGroup = response.roleGroup;
        this.postGroup = response.postGroup;
        
        // 获取用户信息后，确保主机ID字段显示最新值
        const currentCorpId = localStorage.getItem('corp_id');
        if (currentCorpId) {
          if (this.user.corpId !== currentCorpId) {
            console.log('个人中心用户信息获取完成，同步最新主机ID:', currentCorpId);
            this.user.corpId = currentCorpId;
          } else {
            console.log('个人中心用户信息主机ID已是最新:', currentCorpId);
          }
          // 强制更新以确保子组件能收到最新数据
          this.$forceUpdate();
        }
      } catch (error) {
        console.error('获取用户信息失败:', error);
      }
    },
    handleCorpIdUpdated(event) {
      console.log('个人中心页面：检测到主机ID更新', event.detail.corpId);
      this.$message.success('主机ID已自动更新');
      // 重新获取用户信息，确保显示最新的主机ID
      this.getUser();
    }
  }
};
</script>
