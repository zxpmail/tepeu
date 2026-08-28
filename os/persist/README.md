# persist — 库引擎

`os/` 只列本目录。子模块：

| 目录 | artifact | 角色 |
|------|----------|------|
| `api/` | `tepeu-os-persist` | `Persist` 访问口；`PersistEngine` 连接定制 |
| `sqlite/` | `tepeu-os-persist-sqlite` | `SqliteEngine` + 测试夹具 |

领域组件只认 {@code Persist}（jdbc / tx / script），不建连接、不关库。host 用 {@code DataSourceBuilder} + {@code Persist.jdbc}。引擎口 {@code PersistEngine}（ServiceLoader）。换方言仍改适配器 SQL。

```bash
mvn -f os/persist/pom.xml test
```
