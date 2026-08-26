# persist — 库组件

领域组件（session / policy）只暴露端口。JDBC、schema、方言只在本模块。

本骨架默认插头：`sqlite` 包（`SqliteSessionStore` + `SqliteApprovalStore`）。换库 = 本组件另写插头（或另开配方），session/policy **不改**。

```bash
mvn -f os/pom.xml -pl persist -am test
```
