---
name: qince-employee
display_name: 员工查询
description: Use the get_user_list MCP tool to query employee lists with permission-aware filtering by department, name, role, and gender.
description_zh: 通过 get_user_list MCP 工具查询权限范围内的员工列表，支持按部门（含子部门递归）、姓名、角色、性别等条件过滤。
description_en: Query employees visible to the current user via MCP, with recursive department, name, role, and gender filters.
category: org
version: 1.0.0
agent_created: true
---

# 员工查询技能

本技能配合 `qince` MCP 连接器使用。
调用时始终携带登录态，返回结果受当前用户数据权限控制：管理员或拥有员工菜单 viewAll 权限时可查全部员工，否则只能查看权限范围内的下级数据。

## 前置条件

1. 企业需在勤策注册开户（官方网站 https://www.qince.com ），并根据连接器引导绑定用户账号。
2. MCP 支持接口可查看接口定义信息。

## 可用工具

| 工具名 | 用途 |
|---|---|
| `get_user_list` | 查询当前登录用户权限内可见的员工列表，支持按部门、员工ID、姓名、角色、性别过滤。 |

## 参数规范

### get_user_list

请求参数：

```json
{
  "deptId": "10001",
  "empId": "20001",
  "empName": "张",
  "roleId": "30001",
  "gender": "M"
}
```

- `deptId`：可选，字符串。部门 ID，传入后递归查询该部门及其所有子部门的员工。
- `empId`：可选，字符串。员工 ID，精确匹配，与其他条件可叠加。
- `empName`：可选，字符串。员工姓名，模糊匹配（含关键字即命中）。
- `roleId`：可选，字符串。角色 ID，精确匹配；值必须为纯数字字符串。
- `gender`：可选，字符串。性别，`M` 为男，`F` 为女；大小写不敏感。

所有参数均为可选；不传任何参数时返回当前用户权限内的全量员工（状态为"正常"或"停用"，已删除员工不返回）。

响应结构：

```json
{
  "success": true,
  "userList": [
    {
      "id": "20001",
      "code": "EMP001",
      "name": "张三",
      "email": "zhangsan@example.com",
      "phone": "13800000000",
      "roleNames": ["销售主管", "区域经理"],
      "status": "1",
      "deptId": "10001",
      "deptName": "华东销售部",
      "createTime": "2024-01-01 09:00:00",
      "modifyTime": "2025-06-01 10:30:00"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 员工 ID |
| `code` | string | 员工工号 |
| `name` | string | 员工姓名 |
| `email` | string | 邮箱 |
| `phone` | string | 手机号 |
| `roleNames` | string[] | 角色名称列表，一人可拥有多个角色 |
| `status` | string | 状态：`1` 正常，`2` 停用 |
| `deptId` | string | 所属部门 ID |
| `deptName` | string | 所属部门名称 |
| `createTime` | string | 创建时间（`yyyy-MM-dd HH:mm:ss`） |
| `modifyTime` | string | 最后修改时间（`yyyy-MM-dd HH:mm:ss`） |

## 推荐流程

1. 收到用户问题后，明确用户意图：是查特定员工还是按组织维度批量查询。
2. 如果用户提到部门名称而非部门 ID，先通过其他工具（如部门查询）解析出 `deptId` 再传入。
3. 条件确认后调用 `get_user_list`，参数按需传入，不确定的字段不传（接口会按权限返回全量）。
4. 返回结果时，优先用 `name`、`deptName`、`roleNames` 向用户展示可读信息；`id`、`deptId` 等 ID 字段用于后续关联查询，不需要直接展示给用户。
5. 若用户追问某员工的详细信息，可用 `empId` 精确查询该员工。

## 注意事项

- **性别过滤在内存中执行**：`gender` 过滤不走数据库索引，数据量大时先用部门或角色缩小范围再加性别条件。
- **结果受权限控制**：非管理员用户即使传入顶级部门 ID，也只会返回其权限范围内的员工，不会返回越权数据。
- **roleId 值类型**：`roleId` 虽为字符串类型参数，内部会直接拼入 SQL，必须确保为纯数字，不能包含特殊字符。
- **已删除员工不返回**：接口固定过滤 `status IN ('1','2')`，无法通过参数查询已删除员工。

## 调试用例

### 查询指定部门员工

1. 用户问："帮我查一下华东销售部的所有员工"。
2. 先确认是否已知该部门的 `deptId`；若只有名称，先查部门获取 ID。
3. 调用 `get_user_list`，传 `deptId`，不传其他条件。
4. 返回该部门及所有子部门员工列表。

### 按姓名模糊搜索

1. 用户问："查一下姓张的员工"。
2. 直接调用 `get_user_list`，传 `empName: "张"`。
3. 返回所有姓名含"张"的员工。

### 多条件组合过滤

1. 用户问："华东区销售主管里有没有女员工"。
2. 先获取华东区部门 ID 和销售主管角色 ID。
3. 调用 `get_user_list`，同时传 `deptId`、`roleId`、`gender: "F"`。
4. 整合结果向用户展示。

## 错误处理

| 现象 | 处理 |
|---|---|
| `success: false` | 向用户转述 `errorMessage`；不要编造员工数据 |
| 返回空列表 | 提示用户当前权限内无符合条件的员工，建议调整查询条件或确认部门/角色 ID 是否正确 |
| `roleId` 包含非数字字符 | 调用前校验，确保传入纯数字字符串，否则提示用户提供正确的角色 ID |
| HTTP 4xx | 核对工具名称与 MCP 配置是否一致 |
| 超时 | 增加过滤条件（部门、角色）缩小范围后重试；不要无限制重试无条件的全量查询 |
