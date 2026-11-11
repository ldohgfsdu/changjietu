# 酷安 → GitHub → OpenCode/Magpie 反馈闭环：上线操作

**范围**：长截图处理器。当前 PR 只搭建 GitHub 接收端与授权后可运行的编码 Agent；外部 Coolapk 只读采集、登录和公开回复尚未实现，绝不能宣称已在线自动监测。

## 已准备的仓库设施

- `docs/COOLAPK_FEEDBACK_POST.md`：酷安主帖、首条评论、回复话术、发布检查。
- `.github/ISSUE_TEMPLATE/bug_report.yml` 与 `feature_request.yml`：内部人工建单表单。
- `coolapk-feedback.yml`：唯一反馈帖 → 去重 Issue；可用 GitHub Actions 手动送入模拟数据验收。
- `feedback-agent.yml`：只有仓库 OWNER 评论内容**恰好为** `/opencode fix`，且显式启用后才运行 OpenCode，默认关闭。
- `tools/feedback/opencode.magpie.json`：第三方 OpenAI-compatible 网关的模型配置（不是默认开启）。

## 一次性准备

1. **人工在酷安发布** `docs/COOLAPK_FEEDBACK_POST.md` 的主帖，确认作者拥有该帖子；把动态的真实链接（一般为 `https://www.coolapk.com/feed/<数字>`）留存。
2. 将上述数字填入 GitHub 仓库 Settings → Secrets and variables → Actions → Variables：`COOLAPK_TARGET_FEED_ID`。没设置时接收器拒绝所有请求。
3. 合并此 PR 后，试运行 GitHub Actions → `Coolapk single-post feedback intake`，仅用自己制造的测试评论数据。第一次建 Issue，第二次同样的 `external_id` 不得重复建 Issue；不要把测试样本写成真实用户投诉。
4. 若决定启用编码 Agent，另设变量 `FEEDBACK_AGENT_ENABLED=true`、`MAGPIE_MODEL_ID=<实际网关模型 ID>`，并设置两个 Secret：`MAGPIE_API_KEY`（网关密钥）与 `MAGPIE_BASE_URL`（**HTTPS**，以 `/v1` 结尾）。严禁把密钥贴在 Issue、评论、日志或源码中。
5. GitHub Hosted Runner 无法访问你本机的 `127.0.0.1:3425`；Magpie 要通过经过认证的 HTTPS 可达端点供该 Runner 使用，或另行设计权限隔离的 self-hosted Runner。**不要无认证地暴露本地代理。**
6. 在一个**已审查**的 Issue 下，用仓库所有者账号单独评论 `/opencode fix`。Agent 只应提交独立 PR；代码合并、APP Release 和酷安公开回复均由验收门禁控制。

## 还未具备的能力

- **尚无 Coolapk 后台采集器**。现阶段需要外部*合法授权*的数据源产生标准化 JSON 并用 GitHub `repository_dispatch` 推送；无该组件就不会自动发现新评论。可调查 `Lniosy/coolapk-mcp` 的只读功能，但它是非官方协议，必须在实际账号与平台允许的前提下评估稳定性及风控，不得绕过验证码、强制抓取或使用他人的账号凭据。
- **酷安自动回复尚未接入**，只能手工复制经核实的回复草稿。
- **私有仓库的 GitHub Releases 不等于公开下载入口**。在酷安主帖提供任何下载链接前，要以未登录账户实际访问为准；否则通过酷安附件或另设公开分发页面。
- **GitHub PR CI 绿不等于真机功能已验收**：涉及裁剪、分享、权限和 UI 必须真机核验。

## 验收标准

| 关口 | 判据 |
| --- | --- |
| A | 只接收唯一指定帖子，其他帖子数据拒绝 |
| B | 同一外部评论重复提交，不重复创建 Issue |
| C | 外部文本仅作为数据，不可直接触发 Agent |
| D | 非仓库 OWNER 或未启用开关，不启动编码 Agent |
| E | 缺少网关凭据/模型/HTTPS URL 时，Agent 拒绝运行 |
| F | Agent 仅建 PR，不直接合并、签名或发版 |
| G | 公开回复前核对 Issue → PR → Release → 实际修复 |
| H | 所有酷安 URL、评论 ID、作者昵称按最小必要原则保留，附件需先脱敏 |

## 背景资料

- GitHub `repository_dispatch` 要求 workflow 已存在默认分支，只有完成 PR 合并才能使用外部事件。
- OpenCode 官方 `opencode.ai/docs/providers/`、`docs.dev.opencode.ai/docs/github/`；自定义模型网关的 baseURL/密钥/模型 ID 必须与部署相符。
