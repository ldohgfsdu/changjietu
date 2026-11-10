# 酷安反馈试点（GitHub 接收器）

**状态：仅 GitHub 接收端已实现，不代表自动监听酷安或自动回复酷安。** 本变更不修改 Android App、不改变既有构建、签名和发布规则。

## 数据流

已获授权的外部采集器（尚待接入） → GitHub repository_dispatch: coolapk-feedback → JSON 校验 → 按外部 ID 去重 → GitHub Issue → 人工分拣 → 独立 Draft PR → 完整测试和审核 → 正式发布 → 生成回复草稿 → 人工确认后在酷安回复。

当前仓库没有酷安 API 凭据、后台采集器或自动发帖能力，也不需要 Coolapk Cookie、设备标识等凭据。

## 本地测试

运行以下命令（项目根目录）：

    python -m unittest discover -s tools/feedback -p 'test_coolapk_bridge.py' -v

本地模拟接收：

    python tools/feedback/coolapk_bridge.py intake --event event.json --event-name workflow_dispatch --dry-run

其中 event.json 内容示例：

    {
      "inputs": {
        "feedback_json": "{\"external_id\":\"reply:201:303\",\"source_url\":\"https://www.coolapk.com/feed/201?replyid=303\",\"text\":\"测试反馈\",\"version\":\"0.2.8\"}"
      }
    }

上述酷安链接是格式示例，不是真实用户反馈。

## GitHub Actions 接入

合并后在 GitHub Actions 中找到 Coolapk feedback intake (no Coolapk write access)，选择 Run workflow 并填写 feedback_json；或者由具备 repo 访问权限的已授权监听程序向 REST API 发出 POST /repos/ldohgfsdu/changjietu/dispatches 请求，其 JSON body 形如：

    {
      "event_type": "coolapk-feedback",
      "client_payload": {
        "external_id": "reply:123456:456789",
        "source_url": "https://www.coolapk.com/feed/123456",
        "text": "反馈内容",
        "author": "用户昵称",
        "version": "0.2.8"
      }
    }

外部调用者应独立安全管理 GitHub Token，不得把令牌、会话 Cookie 或敏感字段写入仓库、Issue 或日志。external_id 必须对同一原始评论稳定且唯一，重试时不会重复建单。现有工作流只授予 issues:write、contents:read，不执行用户评论中的指令，不合并 PR，不自动发布。

## 回复草稿（不会自动发布到酷安）

当正式 Release 已存在时，可在具备 GitHub Token 的环境执行：

    GITHUB_REPOSITORY=ldohgfsdu/changjietu GITHUB_TOKEN=<token> python tools/feedback/coolapk_bridge.py draft-reply --tag v0.2.8

仅验证 Release 为已发布的正式版本。注意：**Release 存在不等于该 Issue 的修复已经包含在该版本中。** 还需要确认 Issue→PR→Release 的对应关系，再人工审核和发布回复。

## 酷安接入门禁

尚未查到酷安官方公开授权的开发者评论读取/回复 API。社区逆向接口不是官方授权或稳定性承诺。接入前要确认账号可管理目标内容、访问方式符合平台规则，再进行只读小样本验证，验证重复数据、验证码和网络失败处理。禁止绕过验证、对抗平台限制或批量刷屏。在授权与稳定性确认前，不启用酷安自动写入。
