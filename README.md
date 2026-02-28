# 🎵 MusicGraph

> **基于知识图谱的个性化音乐轨迹记录与推荐系统**
> *A Knowledge Graph-based Personalized Music Trajectory System*

## 📖 项目简介

**MusicGraph** 是一个探索性的音乐数据系统。不同于传统的基于标签（Tag-based）或协同过滤的推荐系统，MusicGraph 关注用户的**实际听歌轨迹**。

它通过记录用户在歌曲之间的跳转行为（A -> B），在 Neo4j 中构建一个动态生长的**有向加权图谱**。系统不仅记录“你听过什么”，更记录“你在听完 A 之后，潜意识里更倾向于听 B 还是 C”。

结合 **Redis** 的 LRU 缓存机制，本项目实现了一个混合存储架构，既能处理复杂的图谱拓扑查询，又能高效处理高频的历史记录访问。

**Demo**: https://login-security-haven.nocode.host/#/login

## 📂 项目结构

```
MusicGraph                          // 项目工作空间根目录
├── .vscode                         // VS Code 配置文件
├── songmap                         // 核心后端工程 (Spring Boot Module)
│   ├── .mvn/wrapper                // Maven Wrapper 环境
│   ├── src
│   │   ├── main
│   │   │   ├── java/com/songmap/songmap
│   │   │   │   ├── controller      // 控制层：API 接口定义 (MusicController)
│   │   │   │   ├── dto             // 数据传输对象：规范复杂查询返回 (NodeDetailDTO等)
│   │   │   │   ├── entity          // 实体类：映射 Neo4j 节点与关系 (Song, PlayRecord)
│   │   │   │   ├── repository      // 仓库层：Neo4j 数据访问接口与自定义 Cypher
│   │   │   │   ├── service         // 业务层：图谱逻辑、Redis 历史记录管理
│   │   │   │   └── SongmapApplication.java // Spring Boot 启动入口
│   │   │   └── resources
│   │   │       ├── static          // 静态资源文件
│   │   │       ├── templates       // 前端模板文件
│   │   │       └── application.properties // 系统配置文件 (数据库/Redis连接)
│   │   └── test                    // 单元测试目录
│   ├── target                      // 编译输出目录
│   ├── .gitignore                  // Git 忽略规则
│   ├── mvnw / mvnw.cmd             // Maven 跨平台执行脚本
│   └── pom.xml                     // Maven 依赖与构建配置
└── README.md                       // 项目说明文档

```

## 🛠️ 技术栈

* **核心框架**: Java 17, Spring Boot 3+
* **图数据库**: Neo4j (存储节点、关系、动态属性)
* **缓存中间件**: Redis (存储 LRU 听歌历史)
* **构建工具**: Maven
* **数据交互**: Spring Data Neo4j (SDN), Spring Data Redis, Neo4jClient

## 🚀 快速开始

### 1. 环境准备

你需要安装以下服务（推荐使用 Docker）：

```bash
# 1. 启动 Neo4j
docker run -d --name neo4j-music \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password \
  neo4j:latest

#自配环境需要安装APOC插件
#第一步：下载对应版本的 APOC jar 包
#注意： APOC 的版本必须与你的 Neo4j 版本严格一致！
#如果你的 Neo4j 是 5.x：去 APOC GitHub Releases (5.x) 下载。
#如果你的 Neo4j 是 4.x：去 APOC GitHub Releases (4.x) 下载。
#下载名为 apoc-xxx-core.jar 的文件。
#第二步：放入 plugins 目录
#找到你的 Neo4j 安装目录（也就是你解压的那个文件夹），找到 plugins 文件夹。 把刚才下载的 .jar 文件扔进去。
#路径示例：C:\neo4j-community-5.12.0\plugins\apoc-5.12.0-core.jar

# 2. 启动 Redis
docker run -d --name redis-music \
  -p 6379:6379 \
  redis:latest

或者windows本地启动：
redis-server

# 3.启动QQ音乐API
cd QQMusicAPI
yarn
yarn start

# 4.安装前端依赖
cd frontend
npm install
npm install axios pinia vue-router
npm install naive-ui vfonts # UI库
npm install echarts # 图表库

```

### 2. 配置

修改 `songmap/src/main/resources/application.properties`：

```properties
spring.neo4j.uri=bolt://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=password

spring.data.redis.host=localhost
spring.data.redis.port=6379

# 自定义历史记录长度
songmap.history.limit=100

```

### 3. 运行

在 `songmap` 目录下执行：

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Mac/Linux
./mvnw spring-boot:run

#运行前端：
cd frontend
npm run dev

```

## 🔗 API 接口文档

### 🎧 1. 核心听歌交互

| 方法 | 路径 | 描述 | 参数示例 |
| --- | --- | --- | --- |
| **POST** | `/api/music/listen` | **听歌打卡**。自动连接上一首，更新听歌次数、完播率等权重。 | `name=稻香&artist=周杰伦&isFullPlay=true` |
| **POST** | `/api/music/newlisten` | **新的一天**。强制断开与上一首的连接，开启新的轨迹链。 | `name=夜曲&artist=周杰伦` |
| **GET** | `/api/music/listenhistory` | **最近播放**。从 Redis 获取最近播放列表 (LRU)。 | 无 |

### 🔍 2. 图谱查询 (多粒度)

| 方法 | 路径 | 描述 | 参数示例 |
| --- | --- | --- | --- |
| **GET** | `/api/music/query/node` | **查歌曲**。支持 Basic (仅属性) 和 Detailed (含出入边) 两种粒度。 | `name=七里香&detail=true` 或 `id=10` |
| **GET** | `/api/music/query/edge` | **查关系**。查看两首歌之间的跳转权重数据。 | `from=七里香&to=江南&detail=true` |

> **粒度说明：**
> * `detail=false`: 仅返回对象本身的属性 Map。
> * `detail=true`: 返回完整的 DTO 结构，包含邻接点信息。
> 
> 

### ⚙️ 3. 数据管理与运维

| 方法 | 路径 | 描述 | 参数示例 |
| --- | --- | --- | --- |
| **POST** | `/api/music/init-data` | **数据清洗**。初始化旧数据的统计指标（一次性版本迭代使用）。 | 无 |
| **POST** | `/api/music/property/node/add` | **批量加属性**。给所有歌曲加动态标签。 | `key=mood&type=string&value=Happy` |
| **POST** | `/api/music/property/node/delete` | **批量删属性**。 | `key=mood` |
| **POST** | `/api/music/property/edge/add` | **批量加边属性**。 | `key=weight&type=int&value=1` |
| **DELETE** | `/api/music/delete` | **剪断连线**。物理删除两首歌之间的关联边。 | `firstname=A&nextname=B` |

---

## 💡 核心技术决策

### 1. 混合存储架构 (Neo4j + Redis)

* **问题**：单纯使用 Neo4j 查询“最近播放历史”需要依赖时间戳排序 (`ORDER BY timestamp DESC`)，在数据量大时效率低，且逻辑复杂（需处理重复听歌导致的时间戳更新）。
* **方案**：引入 Redis `List` 结构，配合 Lua 脚本实现原子性的 **LRU (Least Recently Used)** 算法。
* **优势**：Neo4j 专注处理复杂的图拓扑计算，Redis 专注处理高频的线性历史记录，各司其职。

### 2. Redis 存储结构选型

* **场景**：需要同时存储歌曲 ID（用于图查询）和 歌曲名（用于前端展示）。
* **决策**：**方案 1 (单 List 存 `id::name`)** > 方案 2 (双 List 平行存储)。
* **理由**：
* **一致性**：双 List 在并发或故障场景下容易出现下标错位，导致 ID 和歌名不匹配，这是分布式系统的噩梦。
* **原子性**：单字符串存储保证了“ID和歌名”的强绑定，Lua 脚本只需操作一个 Key。
* **性能**：Java 内存中 `split("::")` 的耗时（纳秒级）远小于 Redis 两次网络 IO 的耗时（毫秒级）。



### 3. 动态属性模型 (Schema-less)

* 利用 Spring Data Neo4j 的 `@CompositeProperty` 和底层的 `Neo4jClient`，实现了在运行时给所有节点/边添加新属性的能力，无需修改 Java 实体类代码。
* **安全性**：所有动态 Key 均经过正则白名单校验 (`^[a-zA-Z0-9_]+$`)，杜绝 Cypher 注入风险。

---

## 📅 开发日志

详细的开发历史和思考过程在https://github.com/ouroborosscr/MusicGraph/blob/main/README_tmp.md

### ✅ 已完成
* **2026.02.04 20:25** - **全维度图谱查询体系**：
* 实现基于 `ID` 或 `Name` 的节点查询，以及基于 `ID` 或 `From/To` 的关系查询。
* 引入 **多粒度控制 (Granularity)**：支持 **Basic** (仅返回对象属性) 和 **Detailed** (返回包含出入边、源/目标点的完整拓扑结构 DTO) 两种模式。
* **反向查询优化**：利用 Neo4j 底层存储的双向链表特性，实现了高效的“入度查询”（），无需建立物理反向边。
* 新增通用查询接口 `/api/music/query/node` 与 `/api/music/query/edge`。
* **2026.02.04 18:55** - **性能优化**：解决 Neo4j 查询中的笛卡尔积警告，将单次 `MATCH (a),(b)` 拆分为双 `MATCH`。
* **2026.02.04 18:46** - **数据层升级**：
* 引入作者 (`artist`) 字段解决同名歌曲问题。
* 增加统计指标：`listenCount` (入度), `fullPlayCount`, `skipCount`, `userSelectCount`, `randomSelectCount`。
* 实现 `/init-data` 数据清洗接口，处理旧数据兼容性。


* **2026.02.04 18:00** - **动态属性**：实现全图点/边的属性批量增删。
* **2026.02.04 16:58** - **历史记录重构**：从图查询迁移至 Redis LRU。
* **2026.02.04** - **基础功能**：实现 `/listen`, `/newlisten`, `/delete` 等基础图谱构建功能。
* [ ] **打分与推荐引擎**：
* 设计权重公式：权重升高（主动选择、完播、新鲜感） vs 权重降低（随机命中、快速跳转）。打分算法是：https://github.com/ouroborosscr/MusicGraph/blob/main/Scoring_algorithm.md
* 实现 **随机漫步 (Random Walk)** 算法进行下一首推荐。

### 🚧 开发中

### 🔮 待办 (Roadmap)




* [ ] **随机播放池**：建立分级池，结合历史打分决定入池概率。
* [ ] **外部 API 接入**：接入网易云/QQ音乐 API 补全元数据（封面、流派）。
* [ ] **完善个人曲库**：
* 已录入：周杰伦、林俊杰
* 待录入：许嵩、陈奕迅、罗大佑、李宗盛、陶喆、王菲、孙燕姿等。



---

## 📄 License

MIT License