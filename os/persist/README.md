# persist — 库组件

`os/` 只列本目录。子模块：

| 目录 | artifact | 角色 |
|------|----------|------|
| `api/` | `tepeu-os-persist` | 契约 `Persist`，无 JDBC |
| `sqlite/` | `tepeu-os-persist-sqlite` | SQLite 插头 |

compose 选 `SqlitePersist.file(dir)`。PG 升版在本目录另开子模块，不把 JDBC 写进 `api/`。

```bash
mvn -f os/persist/pom.xml test
```
