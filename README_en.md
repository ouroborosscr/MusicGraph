# 🎵 MusicGraph

> **A Knowledge Graph-based Personalized Music Trajectory Recording & Recommendation System**

## 📖 Introduction

**MusicGraph** is an exploratory music data system. Unlike traditional tag-based recommendation systems or collaborative filtering, MusicGraph focuses on the user's **actual listening trajectory**.

By recording the transition behavior between songs (A -> B), it builds a dynamically growing **Directed Weighted Graph** in Neo4j. The system captures not just "what you listened to," but "what you subconsciously preferred to listen to after Song A."

Combined with **Redis** for LRU (Least Recently Used) caching, this project implements a hybrid storage architecture that efficiently handles both complex graph topology queries and high-frequency history access.

## 📂 Project Structure

```
MusicGraph                          // Project Root Workspace
├── .vscode                         // VS Code Configuration
├── songmap                         // Core Backend Module (Spring Boot)
│   ├── .mvn/wrapper                // Maven Wrapper
│   ├── src
│   │   ├── main
│   │   │   ├── java/com/songmap/songmap
│   │   │   │   ├── controller      // Controller Layer: API definitions (MusicController)
│   │   │   │   ├── dto             // DTOs: Standardizing complex query returns (NodeDetailDTO, etc.)
│   │   │   │   ├── entity          // Entity Layer: Neo4j Node & Relationship mappings (Song, PlayRecord)
│   │   │   │   ├── repository      // Repository Layer: Neo4j access & Custom Cypher queries
│   │   │   │   ├── service         // Service Layer: Graph logic, Redis history management, Dynamic props
│   │   │   │   └── SongmapApplication.java // Spring Boot Entry Point
│   │   │   └── resources
│   │   │       ├── static          // Static resources
│   │   │       ├── templates       // Frontend templates
│   │   │       └── application.properties // Configuration (DB/Redis connections)
│   │   └── test                    // Unit Tests
│   ├── target                      // Build Output
│   ├── .gitignore                  // Git Ignore Rules
│   ├── mvnw / mvnw.cmd             // Maven Wrapper Scripts
│   └── pom.xml                     // Maven Dependencies & Build Config
└── README.md                       // Project Documentation

```

## 🛠️ Tech Stack

* **Core Framework**: Java 17, Spring Boot 3+
* **Graph Database**: Neo4j (Nodes, Relationships, Dynamic Properties)
* **Cache**: Redis (LRU Listening History)
* **Build Tool**: Maven
* **Data Access**: Spring Data Neo4j (SDN), Spring Data Redis, Neo4jClient

## 🚀 Quick Start

### 1. Prerequisites

You need the following services installed (Docker recommended):

```bash
# 1. Start Neo4j
docker run -d --name neo4j-music \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password \
  neo4j:latest

# 2. Start Redis
docker run -d --name redis-music \
  -p 6379:6379 \
  redis:latest

```

### 2. Configuration

Modify `songmap/src/main/resources/application.properties`:

```properties
spring.neo4j.uri=bolt://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=password

spring.data.redis.host=localhost
spring.data.redis.port=6379

# Custom history length
songmap.history.limit=100

```

### 3. Run

Execute the following in the `songmap` directory:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Mac/Linux
./mvnw spring-boot:run

```

## 🔗 API Documentation

### 🎧 1. Core Listening Interaction

| Method | Path | Description | Parameter Example |
| --- | --- | --- | --- |
| **POST** | `/api/music/listen` | **Listen Check-in**. Connects to the previous song, updates weights (play count, full play rate). | `name=Starboy&artist=TheWeeknd&isFullPlay=true` |
| **POST** | `/api/music/newlisten` | **New Session**. Forces a break from the previous song, starting a new trajectory chain. | `name=Halo&artist=Beyonce` |
| **GET** | `/api/music/listenhistory` | **Recent History**. Retrieves the recent playback list from Redis (LRU). | None |

### 🔍 2. Graph Query (Multi-Granularity)

| Method | Path | Description | Parameter Example |
| --- | --- | --- | --- |
| **GET** | `/api/music/query/node` | **Query Node**. Supports Basic (props only) and Detailed (topology) granularity. | `name=Starboy&detail=true` or `id=10` |
| **GET** | `/api/music/query/edge` | **Query Edge**. View transition weights between two songs. | `from=A&to=B&detail=true` |

> **Granularity Note:**
> * `detail=false`: Returns the object's property Map only.
> * `detail=true`: Returns the full DTO structure, including adjacent edges/nodes.
> 
> 

### ⚙️ 3. Data Management & Ops

| Method | Path | Description | Parameter Example |
| --- | --- | --- | --- |
| **POST** | `/api/music/init-data` | **Data Cleaning**. Initializes statistical metrics for old data (One-time migration). | None |
| **POST** | `/api/music/property/node/add` | **Batch Add Node Prop**. Adds dynamic tags to all songs. | `key=mood&type=string&value=Happy` |
| **POST** | `/api/music/property/node/delete` | **Batch Delete Node Prop**. | `key=mood` |
| **POST** | `/api/music/property/edge/add` | **Batch Add Edge Prop**. | `key=weight&type=int&value=1` |
| **DELETE** | `/api/music/delete` | **Cut Connection**. Physically removes the edge between two songs. | `firstname=A&nextname=B` |

---

## 💡 Key Technical Decisions

### 1. Hybrid Storage Architecture (Neo4j + Redis)

* **Problem**: Querying "recent history" using Neo4j relies on time-based sorting (`ORDER BY timestamp DESC`), which is inefficient for large datasets and complex to handle (e.g., duplicate listens updating timestamps).
* **Solution**: Introduced Redis `List` with Lua scripts to implement an atomic **LRU (Least Recently Used)** algorithm.
* **Benefit**: Neo4j focuses on complex graph topology calculations, while Redis handles high-frequency linear history access.

### 2. Redis Data Structure Selection

* **Scenario**: Need to store both Song ID (for graph queries) and Song Name (for frontend display).
* **Decision**: **Option 1 (Single List: `id::name`)** > Option 2 (Parallel Dual Lists).
* **Reasoning**:
* **Consistency**: Dual lists risk index misalignment during concurrency or failures, leading to data corruption (ID mismatching Name).
* **Atomicity**: Storing as a single string ensures strong binding; Lua scripts only need to manipulate one Key.
* **Performance**: The overhead of `split("::")` in Java memory (nanoseconds) is negligible compared to the network IO overhead of fetching two lists from Redis (milliseconds).



### 3. Dynamic Property Model (Schema-less)

* Leveraged Spring Data Neo4j's `@CompositeProperty` and the underlying `Neo4jClient` to enable adding new attributes to all nodes/edges at runtime without modifying Java entity code.
* **Security**: All dynamic keys are validated against a Regex whitelist (`^[a-zA-Z0-9_]+$`) to prevent Cypher injection attacks.

---

## 📅 Development Log

### ✅ Completed

* **2026.02.04 18:55** - **Performance Optimization**: Fixed Cartesian Product warnings in Neo4j queries by splitting single `MATCH (a),(b)` into dual `MATCH` statements.
* **2026.02.04 18:46** - **Data Layer Upgrade**:
* Introduced `artist` field to handle duplicate song names.
* Added statistical metrics: `listenCount` (In-degree), `fullPlayCount`, `skipCount`, `userSelectCount`, `randomSelectCount`.
* Implemented `/init-data` for legacy data migration.


* **2026.02.04 18:00** - **Dynamic Properties**: Implemented batch add/remove for Node/Edge properties.
* **2026.02.04 16:58** - **History Refactoring**: Migrated history storage from Graph to Redis LRU.
* **2026.02.04** - **Core Features**: Implemented basic graph construction (`/listen`, `/newlisten`, `/delete`).

### 🚧 In Progress

* **Reverse Query Optimization**: Verified the bidirectional linked-list nature of Neo4j storage. Forward query (A->B) and reverse query (B<-A) share the same  time complexity, eliminating the need for physical reverse edges.

### 🔮 Roadmap

* [ ] **Scoring & Recommendation Engine**:
* Design weight formula: Weight Up (User Select, Full Play, Freshness) vs. Weight Down (Random Hit, Skip).
* Implement **Random Walk** algorithm for next-song recommendation.


* [ ] **Random Play Pool**: Hierarchical pool based on historical scores.
* [ ] **External API Integration**: Fetch metadata (Cover Art, Genre) via NetEase/Tencent Music APIs.
* [ ] **Personal Library Expansion**: Continue importing artist discographies (Jay Chou, JJ Lin completed).

---

## 📄 License

MIT License