# changjietu

共享项目入口（Claude / Grok / OpenCode / Codex / Hermes 通用）。

## Boot（进入项目后先读）

1. `AGENTS.md`（本文件）
2. `.memory/CURRENT_STATE.md`
3. `.memory/active_object.yaml`

## Rules

- 默认中文回复
- 不提交密钥 / API Key / token
- 不执行破坏性 git（reset --hard / clean -fd / 强推）除非用户明确要求
- 不擅自扩大范围；先跟 active object
- 改完要验证（构建/测试按项目类型）

## Paths

- 代码根：`/root/code/changjietu`
- 记忆：`.memory/`
- 收件箱：`.external_inbox/`（可与手机 inbox 同步）
- 发件箱：`.external_outbox/`
