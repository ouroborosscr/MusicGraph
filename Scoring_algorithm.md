### 目标
歌曲打分系统，当我在听歌曲A时，对与A连接的所有歌（包括出边和入边）进行打分，其中加分项是：点的主动选中率，边的主动选中率，边的正向，完播率。
设计思路是：
权重升高：主动选择（主动选中次数）（说明近期很喜欢），上次播放时间较长（保证新鲜感）。
权重降低：随机选中（被随机选中次数）（听多了就不喜欢了）
优先正向，其次反向，避免返回同一个（权重最低，比随机跳到别的歌还低）
并不是严格的阶梯关系，比如正向的随即次数过多会导致权重不如反向。
接入api后考虑“完播率”和“快速跳转率（不喜欢）”，（现在暂时不考虑）主要的区别是是否听完副歌。听完基本能确定喜欢，不喜欢则会快速跳转。也要避免有些副歌是戛然而止的，这会导致权重过高。
进一步的，还可以统计每条边的跳转次数（综合边权点权）
最后实现打分

### 实现
这里借鉴 **Facebook 早期的 EdgeRank 算法**（用于信息流排序）和 **牛顿冷却定律**（Newton's Law of Cooling，常用于热度衰减/新鲜度控制），结合 **Markov Chain（马尔可夫链）** 的转移概率思想，为你设计一套专属的 **"SongRank"** 打分模型。

---

### 1. 核心模型设计：SongRank

我们的打分公式主要由三个核心因子组成：**亲密度 (Affinity)**、**边权重 (Weight)** 和 **时间衰减 (Time Decay)**。

#### 总公式

* ** (边分)**：当前歌 A 到 候选歌 B 的连接强度。
* ** (点分)**：候选歌 B 本身的质量（受欢迎程度）。
* ** (方向因子)**：正向边与反向边的权重修正。
* ** (新鲜度因子)**：距离上次播放时间的冷却系数。

---

### 2. 详细因子定义

#### A. 基础得分 ( & ) - 这里的核心是“主动 vs 随机”

业界通常不会简单相减，因为“被随机选中”也是一种曝光。但根据你的需求（随机多了说明被动灌输，不喜欢），我们引入**信噪比**的概念。

我们定义 **有效互动值 (Effective Interaction)**：


* ** (主动权重)**：高（例如 5.0）。代表用户强烈意愿。
* ** (跳转权重)**：中（例如 1.0）。代表自然连播。
* ** (随机惩罚)**：低（例如 0.5）。代表系统强推。

** (边得分)**：基于  这条边的 。
** (点得分)**：基于点 B 的全局  (归一化后作为辅助分)。

> **设计理由**：如果一首歌总是被“随机”播放（ 很高），但用户从未“主动点播”它（ 低）， 值会变低甚至为负，符合你“听多了就不喜欢”的直觉。

#### B. 方向因子 ()

这是图谱推荐的精髓。

* **正向 (Outgoing, )**：系数 **1.0**。顺势而为，最为自然。
* **反向 (Incoming, )**：系数 **0.6** (示例)。
* *逻辑*：虽然你是从 B 跳到 A 的，但 A 听完了想回味 B 也是一种可能，只是概率较低。
* *注意*：这里解决了“正向的随机次数过多导致权重不如反向”的问题。如果正向边的  因为随机过多被扣烂了，乘以 1.0 后依然可能小于 分数很高的反向边  0.6。



#### C. 新鲜度因子 () - 冷却机制

防止“单曲循环”或“来回鬼畜”。我们要奖励“许久未听”的歌。
使用 **S型曲线 (Sigmoid)** 或 **冷却曲线**：

* ：当前时间 - 目标歌曲上次播放时间 (单位：分钟或小时)。
* ：冷却速率。
* **效果**：
* 如果刚听过 ()，因子趋近 0  得分归零（极刑）。
* 如果很久没听 ()，因子趋近 1  全额得分。



---

### 3. Java 实现方案

为了保持代码的灵活性和可调试性，建议 **Cypher 只负责“抓取数据”**，**Java 负责“计算打分”**。这样方便你随时调整权重 ，而不用改数据库查询。

#### Step 1: 定义打分参数类 (Config)

在 `service` 中定义常量，方便后期调优。

```java
public class RankWeights {
    // 基础权重
    public static final double W_USER_SELECT = 5.0; // 主动点播权重高
    public static final double W_JUMP = 1.0;        // 自然跳转权重中
    public static final double W_RANDOM_PENALTY = 0.8; // 随机播放扣分

    // 方向系数
    public static final double DIR_FORWARD = 1.0;
    public static final double DIR_BACKWARD = 0.5;

    // 新鲜度参数 (假设单位是分钟)
    // 冷却时间常数：比如设置为 60，意味着 1小时内重复播放权重会大打折扣
    public static final double COOLING_LAMBDA = 0.05; 
}

```

#### Step 2: 定义 DTO 扩展

我们需要一个包含计算结果的 DTO。

```java
@Data
public class ScoredSongDTO implements Comparable<ScoredSongDTO> {
    private Song song;
    private double score;
    private String reason; // 调试用：比如 "Forward(High Active)"

    @Override
    public int compareTo(ScoredSongDTO o) {
        return Double.compare(o.score, this.score); // 降序排列
    }
}

```

#### Step 3: Repository 层 (获取所有邻居)

我们需要一次性把出边和入边都查出来。

```java
// SongRepository.java

@Query("MATCH (current:Song) WHERE id(current) = $songId " +
       // 1. 找正向 (Outgoing)
       "OPTIONAL MATCH (current)-[r_out:NEXT]->(target_out:Song) " +
       // 2. 找反向 (Incoming)
       "OPTIONAL MATCH (target_in:Song)-[r_in:NEXT]->(current) " +
       "RETURN " +
       "collect({direction: 'OUT', edge: properties(r_out), node: target_out}) as outgoing, " +
       "collect({direction: 'IN', edge: properties(r_in), node: target_in}) as incoming")
Optional<NeighborsDTO> findNeighbors(@Param("songId") Long songId);

```

*(注：你需要定义一个简单的 `NeighborsDTO` 来接收这个查询结果)*

#### Step 4: Service 层 (核心算法)

```java
// MusicGraphService.java

public List<ScoredSongDTO> recommendNextSongs(Long currentSongId) {
    // 1. 获取邻居数据
    NeighborsDTO neighbors = songRepository.findNeighbors(currentSongId).orElseThrow();
    List<ScoredSongDTO> candidates = new ArrayList<>();

    // 2. 遍历并打分
    // 处理正向边
    processCandidates(neighbors.getOutgoing(), RankWeights.DIR_FORWARD, candidates);
    // 处理反向边
    processCandidates(neighbors.getIncoming(), RankWeights.DIR_BACKWARD, candidates);

    // 3. 排序并返回
    Collections.sort(candidates);
    return candidates;
}

private void processCandidates(List<NeighborItem> items, double dirFactor, List<ScoredSongDTO> list) {
    if (items == null) return;
    
    LocalDateTime now = LocalDateTime.now();

    for (NeighborItem item : items) {
        Song node = item.getNode();
        Map<String, Object> edgeProps = item.getEdge();
        if (node == null) continue;

        // --- A. 计算基础互动分 (Effective Interaction) ---
        // 从 Edge 获取数据 (注意判空，这里用 helper method 获取 int)
        int edgeUser = getInt(edgeProps, "userSelectCount");
        int edgeJump = getInt(edgeProps, "jumpCount");
        int edgeRandom = getInt(edgeProps, "randomSelectCount");
        
        // 从 Node 获取数据
        int nodeUser = node.getUserSelectCount();
        int nodeRandom = node.getRandomSelectCount();

        // 核心公式：边分 + (点分 * 0.2作为辅助)
        double edgeScore = (edgeUser * RankWeights.W_USER_SELECT) 
                         + (edgeJump * RankWeights.W_JUMP) 
                         - (edgeRandom * RankWeights.W_RANDOM_PENALTY);
                         
        double nodeScore = (nodeUser * RankWeights.W_USER_SELECT) 
                         - (nodeRandom * RankWeights.W_RANDOM_PENALTY);

        double baseScore = edgeScore + (nodeScore * 0.2); // 点权作为辅助，权重较低

        // 防止分数为负 (可选，看你是否允许恶心歌曲沉底)
        if (baseScore < 0.1) baseScore = 0.1; 

        // --- B. 计算新鲜度因子 ---
        long minutesSinceLastPlay = ChronoUnit.MINUTES.between(node.getListenedAt(), now);
        // 1 - e^(-0.05 * t)
        // 例：10分钟 -> 1 - 0.6 = 0.4 (分打4折)
        // 例：60分钟 -> 1 - 0.05 = 0.95 (分打95折)
        double freshnessFactor = 1.0 - Math.exp(-RankWeights.COOLING_LAMBDA * minutesSinceLastPlay);

        // --- C. 最终得分 ---
        double finalScore = baseScore * dirFactor * freshnessFactor;

        ScoredSongDTO dto = new ScoredSongDTO();
        dto.setSong(node);
        dto.setScore(finalScore);
        dto.setReason(String.format("Base:%.1f * Dir:%.1f * Fresh:%.2f", baseScore, dirFactor, freshnessFactor));
        
        list.add(dto);
    }
}

private int getInt(Map<String, Object> map, String key) {
    Object val = map.get(key);
    return val instanceof Number ? ((Number) val).intValue() : 0;
}

```

---

### 4. 未来扩展性 (API 接入后)

你提到了“完播率”和“快速跳转”。这两个指标是衡量**满意度**的黄金指标。

接入后，只需修改 `RankWeights` 和公式：

1. **完播 (Full Play)**：极强的加分项。
* 
* 逻辑：如果一条边的  很高，说明这是一个“黄金转场”。


2. **快速跳转 (Skip)**：极强的减分项（甚至可以作为截断阈值）。
*  (惩罚)
* 公式修正：
* **截断逻辑**：如果  (80% 的时候跳过了)，直接将 Score 置为 0，不再推荐。



### 5. 总结

这套方案的优点：

1. **科学性**：引入了牛顿冷却定律处理“新鲜感”，这是推荐系统防疲劳的标准做法。
2. **灵活性**：所有权重在 Java 常量中定义，你可以通过观察实际体验，随时调整 `W_USER_SELECT` 或 `DIR_BACKWARD` 的值，甚至可以在后台做一个滑块来动态调整参数。
3. **可解释性**：每个推荐结果都有明确的来源（是点权高？还是边权高？还是因为方向正确？），方便调试。

下一步，你可以实现这个 `recommendNextSongs` 接口，并在前端展示推荐列表及其分数，观察一下是否符合你的预期。