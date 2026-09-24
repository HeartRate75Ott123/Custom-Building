# Custom Building / 自定义建筑 — 多版本工作区

一个模组，多个 Minecraft 版本，每个版本一个独立子工程。仓库根目录只放工作区级的东西（说明、CI、忽略规则）。

## 目录结构

```
.
├── custom-building1211/     NeoForge 1.21.1   的完整工程
├── custom-building1201/     Forge    1.20.1   的完整工程
├── .github/workflows/       对每个子工程跑构建
├── .gitattributes
└── .gitignore               覆盖所有子工程（build/、.gradle/、run/、.idea/ 均不入库）
```

每个子工程都是自包含的：自带 `gradlew`、`build.gradle`、`settings.gradle`、`src/`，可以单独打开、单独构建、单独发布。

## 构建

```bash
cd custom-building1211 && ./gradlew build     # NeoForge 1.21.1，需要 JDK 21
cd custom-building1201 && ./gradlew build     # Forge 1.20.1，需要 JDK 17
```

开发环境：

```bash
./gradlew runClient     # 客户端
./gradlew runServer     # 服务端
```

## 版本对照

| 目录 | Minecraft | 加载器 | 映射 | Java |
|---|---|---|---|---|
| `custom-building1211` | 1.21.1 | NeoForge 21.1.238 | Parchment 2024.11.17 | 21 |
| `custom-building1201` | 1.20.1 | Forge 47.4.10 | official | 17 |

两个版本的玩法与数据包格式保持一致：数据包放在 `data/<命名空间>/custom_building/blueprint/<名称>.json`，
结构放在 `data/<命名空间>/structure/<名称>.nbt`，重载后自动出现在「自定义建筑」创造选项卡里。
各版本的实现细节（1.21.1 用数据组件、1.20.1 用 NBT 等）见对应子目录里的 README。

## 分支

| 分支 | 用途 |
|---|---|
| `main` | 工作区。目录结构、共享文档、CI，以及所有版本的当前代码 |
| `1.21.1` | 1.21.1 版本线，用于该版本的独立开发与发布 |
| `1.20.1` | 1.20.1 版本线，同上 |

发版打 tag：`v<mod版本>-mc<MC版本>`，例如 `v1.0.0-mc1.21.1`。

## 作者

Plume Jade
