---
name: zhihuishu-unified-skill
description: 智慧树教学中心，聚合课程、学期、班级、教学观测、学习分析、课堂报告、分组方案、OBE、备课、题目题库、资源检索、教学计划与团队、话题通知、能力阶梯、知识图谱、AI 翻转课堂与知识库统一检索
description_zh: 智慧树教学中心，聚合课程、学期、班级、教学观测、学习分析、课堂报告、分组方案、OBE、备课、题目题库、资源检索、教学计划与团队、话题通知、能力阶梯、知识图谱、AI 翻转课堂与知识库统一检索
description_en: Zhihuishu Teaching Center aggregating courses, terms, classes, teaching observation, learning analysis, classroom reports, group plans, OBE, lesson preparation, questions and question banks, resource search, teaching plans and teams, topics and notices, capability ladders, knowledge graphs, AI flipped classrooms, and knowledge-base retrieval.
version: "1.0.0"
author: "智慧树"
---

# 智慧树·教学中心

一个入口聚合智慧树教学的只读查询与检索能力，按用户意图路由到对应 Tool。

## 能力边界

- 全部 Tool 均只读：只提供查询、检索、分析与内容生成，不执行创建、编辑、发布、删除、上传、任务提交或消息发送等写操作。
- 用户提出写操作时，说明当前入口只支持查询；需要操作页面时，用对应的 `get_*_link` 工具返回管理入口链接，引导用户自行完成。
- 身份、学校、助手、Token 由服务端解析，任何 Tool 都不接收这些字段。
- 课程、班级、阶梯、题目等技术 ID 只用于 Tool 串联，不向用户展示，对用户展示名称。
- 只处理用户真实提出的意图，不主动扩展模块，不批量预取数据。

## 统一前置流程

1. 用 `search_courses` 定位课程。用户未指定学期时不传 `term`，查询全部学期；指定学期时先用 `query_terms` 换算为学期编号再传入。
2. 多个课程候选必须展示名称与学期并等待用户选择，不默认取第一项。
3. 课程确定后仍缺少学期时，用 `query_terms` 展示可选学期并等待选择；向用户只展示学期名称，不展示裸编号。
4. 模块需要班级时，用该模块的班级类 Tool 查询：教学观测用 `query_observation_classes`，能力阶梯用 `query_capability_ladder_classes`，通用班级与学生用 `search_classes`；多候选同样等待用户选择。
5. 同一轮内后续 Tool 复用已确认的课程、学期、班级，不重复前置；服务端会再次校验归属。
6. 前置查询失败或为空时立即停止并说明，不自动更换课程或学期重试。

## Tool 路由

### 学期、课程与班级

| 意图 | Tool |
|---|---|
| 查询或换算学期 | `query_terms` |
| 定位授课课程 | `search_courses` |
| 课程基本信息 | `get_course_basic_info` |
| 授课课程详情 | `get_course_teaching_detail` |
| 检索归档课程 | `list_archived_courses` |
| 检索共享课 | `list_shared_courses` |
| 查询课程班级 | `list_course_classes` |
| 查询班级与学生 | `search_classes` |
| 查询班级人员 | `query_students` |
| 导出班级学生名单（返回可下载文件信息） | `export_class_students` |

### 教学观测（学情 / 教学双视角）

前置：用 `query_observation_classes` 查询观测班级；用户未明确视角时先询问学情、教学或双视角，「教学概况」「全面观测」「整体数据」默认双视角。

学情数据：

| 意图 | Tool |
|---|---|
| 学习进度（完成率） | `query_learning_progress` |
| 学习进度详情 | `query_learning_progress_detail` |
| 学习排行榜 | `query_learning_rankings` |
| 教学活动完成情况 | `query_teaching_activity` |
| 总成绩与等级分布 | `query_total_scores` |
| 学习趋势 | `query_learning_trend` |
| 知识点掌握 | `query_knowledge_mastery` |
| 学习预警 | `query_learning_warnings` |
| 课堂互动明细 | `query_learning_lesson_interactions` |
| 话题讨论统计 | `query_learning_topic_discussion` |
| 作业统计与明细 | `query_learning_homework` |
| 考试统计与明细 | `query_learning_exams` |
| 能力阶梯达成 | `query_ability_ladder` |
| 能力训练情况 | `query_ability_training` |
| 智能体教学情况 | `query_agent_teaching` |
| 智能体问答概览 | `query_agent_qa_overview` |
| 智能体问题类型分布 | `query_agent_qa_question_types` |
| 智能体问答趋势 | `query_agent_qa_trend` |
| 学生最近提问 | `query_student_questions` |
| 学生不满意回答 | `query_dissatisfied_answers` |
| 学情综合评价 | `query_learning_evaluation` |

教学数据：

| 意图 | Tool |
|---|---|
| 教学统计概览 | `query_teaching_stats_overview` |
| 作业考试发布与完成率 | `query_teaching_homework_exam_stats` |
| 课堂互动发布情况 | `query_teaching_lesson_interactions` |
| 话题讨论发布情况 | `query_teaching_topic_discussion` |
| 课程图谱统计 | `query_course_graph` |
| AI 应用与智能体统计 | `query_teaching_agent_ai_stats` |

路由规则：

- 泛问「智能体问答情况」时组合调用 `query_agent_qa_overview`、`query_agent_qa_question_types`、`query_agent_qa_trend`；只问热词、类型或趋势时只调用对应 Tool。
- 「学生最近都在问什么」只用 `query_student_questions`；「哪些回答被点踩」只用 `query_dissatisfied_answers`，不与其他模块组合。
- 学情视角的课堂互动用 `query_learning_lesson_interactions`，教学视角用 `query_teaching_lesson_interactions`；话题讨论同理按视角区分。

### 学习分析

| 意图 | Tool |
|---|---|
| 作业深入分析 | `analyze_homework` |
| 考试学情分析 | `analyze_exam_learning` |
| 查询作业 | `search_homeworks` |
| 查询考试 | `search_exams` |
| 训练题库学情 | `query_training_question_bank_learning` |

学习分析中的观测类 Tool 与「教学观测」共用，见上表；同一轮内不要重复调用。

### 课堂报告

| 意图 | Tool |
|---|---|
| 查询课堂课次 | `search_classroom_lessons` |
| 课堂核心数据 | `query_classroom_core_report` |
| 课堂核心回顾 | `query_classroom_review` |
| 随堂互动 | `query_classroom_interactions` |
| 随堂测验详情 | `query_classroom_quiz_report` |
| 课堂人员概况 | `query_classroom_people_report` |

### 能力阶梯

| 意图 | Tool |
|---|---|
| 检索能力阶梯 | `search_capability_ladders` |
| 阶梯详情 | `query_capability_ladder_detail` |
| 阶梯班级 | `query_capability_ladder_classes` |
| 培养全景 | `query_capability_ladder_panorama` |
| 能力达成矩阵 | `query_capability_ladder_matrix` |
| 学生完成情况 | `query_capability_ladder_students` |
| 管理入口链接 | `query_capability_ladder_manage_link` |

### 知识图谱

| 意图 | Tool |
|---|---|
| 课程三谱概览 | `query_course_knowledge_graph` |
| 知识图谱详情 | `query_knowledge_graph` |
| 知识图谱管理版本 | `query_knowledge_graph_version` |
| 能力图谱详情 | `query_ability_graph` |
| 问题图谱题目 | `query_question_graph` |
| 管理入口链接 | `get_knowledge_graph_manage_link` |
| 预览入口链接 | `get_knowledge_graph_preview_link` |

### AI 翻转课堂

| 意图 | Tool |
|---|---|
| 查询翻转计划 | `query_flipped_plan` |
| 翻转计划统计 | `query_flipped_plan_summary` |
| 翻转路径列表 / 详情 | `list_flipped_paths` / `query_flipped_path_detail` |
| 翻转讲次列表 / 详情 / 任务 | `list_flipped_nodes` / `query_flipped_node_detail` / `list_flipped_node_tasks` |
| 翻转完成统计 | `query_flipped_progress_counts` |
| 学生明细 / 进度 / 学情 / 建议 | `list_flipped_students` / `query_flipped_student_progress` / `query_flipped_student_analysis` / `query_flipped_student_advice` |
| 创建入口链接 | `get_flipped_course_create_link` |
| 计划 / 讲次编辑入口链接 | `get_flipped_plan_edit_link` / `get_flipped_node_edit_link` |
| 讲次 / 路径预览入口链接 | `get_flipped_node_preview_link` / `get_flipped_path_preview_link` |

### 分组方案

| 意图 | Tool |
|---|---|
| 分组方案列表 / 详情 | `query_group_plans` / `query_group_plan_detail` |
| 小组成员 | `query_group_members` |
| 小组作业 | `query_group_homeworks` |
| 未进组学生 | `query_ungrouped_students` |

### OBE

| 意图 | Tool |
|---|---|
| 课程目标 / 毕业要求 | `query_obe_course_objectives` / `query_obe_graduation_requirements` |
| 课程目标达成度 | `query_obe_objective_achievement` |
| 学生 OBE 达成度 | `query_obe_student_achievement` |

### 备课与教学计划

| 意图 | Tool |
|---|---|
| 备课课次 | `query_preparation_lessons` |
| 课程课表 | `query_preparation_schedules` |
| 教学计划结构 | `query_teaching_plan_structure` |
| 教学计划关联活动 | `query_teaching_plan_activities` |

### 题目与题库

| 意图 | Tool |
|---|---|
| 定位课程题库文件夹 | `locate_question_folder` |
| 题库列表 | `list_question_banks` |
| 题库知识点 / 标签 | `list_question_knowledge_points` / `list_question_labels` |
| 筛选查询题目 | `search_questions` |
| 训练题库班级 / 详情 / 题目 / 概览 | `query_training_question_bank_classes` / `query_training_question_bank_detail` / `query_training_question_bank_questions` / `query_training_question_bank_statistics` |

### 资源检索

| 意图 | Tool |
|---|---|
| 定位资源库文件 | `locate_resource_file` |
| 搜索资源库文件 | `search_resource_files` |
| 跨资源库查找文件 | `search_file_across_libraries` |
| 搜索个人知识库 | `search_knowledge_resources` |
| 搜索学习资源 | `search_study_resources` |
| 学习资源章节树 / 配置 / 下载链接 | `query_study_resource_tree` / `query_study_resource_detail` / `get_study_resource_download` |

### 教学团队

| 意图 | Tool |
|---|---|
| 教学团队成员 | `query_teaching_team_members` |
| 团队管理入口链接 | `get_teaching_team_manage_link` |

### 话题与通知

| 意图 | Tool |
|---|---|
| 检索课程话题 | `search_topics` |
| 分析课程讨论区 | `analyze_topics` |
| 话题词云 | `query_topic_word_cloud` |
| 话题管理入口链接 | `get_topic_manage_link` |
| 查询通知 | `search_notices` |
| 分析通知 | `analyze_notices` |
| 通知阅读详情 | `query_notice_read_detail` |
| 通知管理入口链接 | `get_notice_manage_link` |

### 统一检索

| 意图 | Tool |
|---|---|
| 查询可检索的知识库 | `list_searchable_knowledge_bases` |
| 检索知识库与互联网资料 | `search_knowledge_sources` |

检索规则：

1. 每个用户轮次第一次检索前先调用 `list_searchable_knowledge_bases`，不复用上一轮结果；`total` 为 0 表示当前没有可用知识库。
2. 策略选择：`SERIAL` 知识库优先（默认）；`PARALLEL` 知识库与互联网对比；`EXTERNAL_ONLY` 纯联网实时信息；`DEEP_ONLY` 知识库深度研究。用户明确要求「只查知识库」时不得选择 `EXTERNAL_ONLY`。
3. 检索结果按来源分组展示，参考资料输出为可点击的完整 Markdown 链接，不展示占位符。

### 材料出题

| 意图 | Tool |
|---|---|
| 依据材料生成试题与解析 | `generate_material_questions` |

使用要求：必须同时具备用户确认的学科、用户明确指定的题型和真实资料；资料来自统一检索结果或用户提供的正文，不能凭模型知识补造；不生成编程题、不入库、不写文件。

### 内部工具

| 意图 | Tool |
|---|---|
| 连接智慧树教学服务 | `get_zhihuishu_script_context` |

`get_zhihuishu_script_context` 为本地脚本流程准备一次性授权上下文，无参数、只读；常规对话不需要主动调用。

## 通用规则

- 管理入口链接类 Tool 只返回链接，不执行页面内操作；用户要做写操作时，说明本入口只读，并给出对应管理链接。
- 模块间相互独立：某个模块无数据或失败时说明该模块不可用，继续其他已选择模块。
- 空结果如实说明「暂无数据」，不编造内容；检索类空结果提示可调整关键词或策略。
- 比例字段按百分数展示，超过两位小数四舍五入保留两位。
- 不向用户展示 Token、nid、route、内部 ID 或原始错误响应。
