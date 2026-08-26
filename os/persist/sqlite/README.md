# sqlite — persist 的 SQLite 插头

实现 `Persist`（`SqlitePersist.file(dir)`）。会话 `kernel.sqlite` 与审批 `approvals.sqlite` 分库，JDBC 走 `SqliteDb`。

```bash
mvn -f os/pom.xml -pl persist/sqlite -am test
```
