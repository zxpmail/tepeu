# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
本地后端运行中（30141）。已按审计结果完成一轮「能删就删 / 能改就改」。

## 上次停在哪
- ✅ 中文文件名 Content-Disposition（RFC 5987）
- ✅ 写文件自动建版本；记忆检索注入对话
- ✅ 去掉 E:/work/ReqForge 硬编码；任务失败写 partial/abandoned
- ✅ /clear-history（兼容 /compact）；技能+市场合并；侧栏去掉独立「市场」；多 Agent 降为高级
- ✅ 删除前端未用 API：getWorkspace/updateWorkspace/listMemories/getMemory/writeFile/createFileVersion

## 近期关键决定
- 市场入口并入技能「目录安装」页签，不删后端市场 API
- ReqForge 本机路径仅配置/环境变量/相对探测，无绝对路径默认值
- 文件三入口、双预览组件本次未大拆（风险高），仅改侧栏文案
