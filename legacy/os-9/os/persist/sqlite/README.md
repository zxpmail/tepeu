# sqlite — persist 的 SQLite DataSource

`SqliteEngine` 实现 `PersistEngine`。测试夹具 `SqliteDataSources.access` 返回 `Persist`。不知道 Session / Approval。发行：host 建连接后 `Persist.jdbc`。

```bash
mvn -f os/pom.xml -pl persist/sqlite -am test
```
