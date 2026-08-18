# Tepeu IDE 三栏布局设计

> 状态：已批准（2026-07-11）
> 范围：三栏壳 + 对话细节；不做 minimap / Export / System prompt / fork 树可视化

## 目标
主界面接近 pi-web：左会话/文件、中对话、右预览；对话含用量、过程折叠、输入工具条。

## 布局
- 顶栏：Tepeu · 工作区 · 左右折叠 · 会话用量 · 主题
- 左 ~240px：会话列表 + 文件树 + 底栏（工作区/记忆/终端/服务商）
- 中 flex:1：ChatView（820 居中）
- 右 ~40%：文件预览；空态 “No file open”
- 左右可折叠（宽度动画）

## 对话
- 助手消息下：↑↓ token · $cost + 复制
- 工具调用默认收进「过程详情」
- 输入底栏：服务商选择

## 不做
Chat Minimap、Export、System prompt、像素级皮肤复刻、会话 fork 树缩进
