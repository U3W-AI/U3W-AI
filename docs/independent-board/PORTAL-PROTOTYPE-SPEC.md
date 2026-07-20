# me / admin 双后台原型说明

状态：`PROTOTYPE_ONLY`。本原型不连接 API、不改变生产路由，不代表页面已经实施。只有获得明确确认后，才允许修改 `FBSir-ui/src`。

## 设计基准

不新建前端框架。生产实现必须复用 U3W-AI 当前若依底座：

- `FBSir-ui/src/layout` 的侧栏、顶栏、面包屑、标签页和响应式结构；
- Spring Security、角色、菜单、按钮权限和数据权限；
- Vue 3、Vue Router、Pinia、Element Plus 和现有 `request.js`；
- 当前企业、成员、积分、Webhook、主机、Run/Step/Outbox/Receipt 页面模式；
- 同一后端按权限提供数据，域名仅决定默认门户体验，绝不代替服务端授权。

## 信息架构

### me.u3w.com

1. `我的首页`：VIP 生效状态、Connector 状态、今日会议额度、积分、最近会议和唯一下一步；
2. `独董会`：会议记录、议题与席位、秘书状态、产出物和回执；
3. `能力与积分`：免费/VIP 对照、已解锁能力、积分余额与不可变流水；
4. `连接与授权`：WorkBuddy OAuth、Scope、最近使用、撤销和重新授权；
5. `推送订阅`：用户 Webhook、会议进展、独董会内参、投递状态和失败处置；
6. `设备与云资产`：Apple Watch、FBSir Hub、绑定、最后在线、撤销；
7. `安全与回执`：登录设备、授权记录、ACTION/DELIVERY 回执和导出。

首页首值顺序固定为：

```text
看清当前权益
  -> 看清今天还能开几次会
  -> 若 VIP 待激活则连接 WorkBuddy
  -> 否则发起或继续一次独董会
```

### admin.u3w.com

1. `运营总览`：VIP、活跃租户、会议完成、失败率、待处理异常；
2. `用户与企业`：用户、企业、成员、角色、状态和数据范围；
3. `产品与权益`：免费/VIP 计划、授予、失效、版本和 Connector 生效条件；
4. `积分与账本`：Grant/Reserve/Settle/Release/Refund，只允许受控操作；
5. `OAuth 与连接器`：客户端、Scope、Token family、撤销和异常登录；
6. `Webhook 与投递`：Endpoint、订阅、Outbox、重试、UNKNOWN 和回读；
7. `设备与主机`：Watch、ChannelBinding、FBSir Hub、隔离和吊销；
8. `会议与回执`：Run、Step、Approval、Receipt、失败分类和恢复；
9. `域名与发布`：Git 提交、构建摘要、迁移集合、子域名回读和回滚点。

## 关键页面决策

| 决策 | 原型选择 | 理由 |
|---|---|---|
| 两个域名是否两套系统 | 否，同一若依代码库的两种门户模式 | 复用身份、菜单、权限和组件，降低漂移 |
| me 是否显示完整后台菜单 | 否，只显示用户任务和自助管理 | 降低认知负担和误操作 |
| VIP 授予是否等于生效 | 否 | VIP 必须完成可信 Connector 绑定 |
| 积分是否显示为现金 | 否 | 只显示能力解锁与不可变流水 |
| Watch 是否提供自由遥控 | 否 | 只提供状态、批准/拒绝/取消和短摘要 |
| admin 能否替用户批准 | 否 | 管理员只能处置系统状态，不能伪造用户同意 |

## 复用映射

| 原型模块 | 生产实现优先复用 |
|---|---|
| 用户/企业筛选 | 现有系统用户、企业成员、数据权限 |
| 积分卡与流水 | 现有积分页面的展示组件；交易内核需升级后接入 |
| Webhook 列表 | 现有企微 Webhook 页面表格、掩码和回执交互 |
| Run/Receipt 审计 | SmartBot Run/Step/Receipt 页面与后端对象 |
| Host 状态 | 现有主机纳管页面 |
| 动态菜单 | `getRouters`、permission store、`v-hasPermi` |
| 表单/表格/状态 | Element Plus 现有用法和仓库校验脚本 |

## 确认点

进入生产 UI 实施前，需要确认：

1. me 首页是否采用“权益—额度—会议”三段首值；
2. me 是否需要把“积分”和“能力”合并在同一菜单；
3. admin 是否保留现有若依全部系统菜单，独董会作为一级业务模块；
4. VIP 待连接状态是否采用显著黄色提示，而不是直接显示“VIP 已开通”；
5. 首期是否只实现首页、权益、会议、连接器四个页面，把 Watch/Webhook 深页延后。

可点击原型入口：[双后台原型](./prototypes/portals/index.html)。
