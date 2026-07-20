# 独董会控制面唯一真源

本仓库是福帮手“独董会”配套平台开发、构建、迁移和部署的唯一源码真源。

## 不变量

- 不从任何其它本地目录加载源码、脚本、迁移、前端资源或运行时配置；
- 外部仓库、旧工作树、原型、审核包和线上实例只作为只读证据或迁移输入；
- 需要复用的实现必须经过审阅后完整落入本仓库并由本仓库测试覆盖；
- GitHub、`me.u3w.com`、`admin.u3w.com`、API、追踪和后续登记的 U3W 子域名必须绑定本仓库提交；
- 每次部署必须生成提交、构建摘要、迁移集合、目标域名、回读结果和回滚点组成的回执；
- 独董会 26.7.20 审核包处于冻结状态，不是本仓库的构建依赖，也不接受本开发线修改。

## 对齐方向

```text
standalone repository commit
  -> deterministic build
  -> ordered migration manifest
  -> target-specific deployment
  -> version/readiness readback
  -> deployment receipt
  -> GitHub and domain alignment report
```

不存在提交绑定和回读回执时，只能称为“待对齐”，不得称为已经同步或已经上线。
