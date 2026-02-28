package com.songmap.songmap.service;

import com.songmap.songmap.dto.NeighborItemDTO;
import com.songmap.songmap.dto.ScoredSongDTO;
import com.songmap.songmap.entity.GraphInfo;
import com.songmap.songmap.entity.Song;
import com.songmap.songmap.repository.GraphInfoRepository;
import com.songmap.songmap.repository.SongRepository;

import lombok.extern.slf4j.Slf4j; // 【生产级】引入日志框架
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert; // Spring自带的断言工具，用于参数校验

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;



/**
 * 音乐图谱核心服务
 * <p>
 * 负责处理歌曲节点的生命周期管理、关联关系构建以及动态属性维护。
 * 集成了 Neo4j 图数据库与 Redis 缓存。
 * </p>
 *
 * @author SongMap Team
 * @since 1.0.0
 */
@Slf4j // 1. 【可观测性】自动生成 log 对象
@Service
public class MusicGraphService {

    private final SongRepository songRepository;
    private final MusicHistoryService musicHistoryService;
    private final GraphInfoRepository graphInfoRepository;
    private final Neo4jClient neo4jClient;

    // 2. 【可配置性】从配置文件读取限制，而不是写死
    // 格式：在 application.properties 中配置 songmap.history.limit=100
    @Value("${songmap.history.limit:100}")
    private int historyLimit;

    // 3. 【安全性】预编译正则，只允许字母、数字、下划线作为属性名，防止 Cypher 注入
    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // 【修复】构造函数必须包含 graphInfoRepository，Spring 才能自动注入
    public MusicGraphService(SongRepository songRepository,
                             GraphInfoRepository graphInfoRepository,
                             MusicHistoryService musicHistoryService,
                             Neo4jClient neo4jClient) {
        this.songRepository = songRepository;
        this.graphInfoRepository = graphInfoRepository;
        this.musicHistoryService = musicHistoryService;
        this.neo4jClient = neo4jClient;
    }

    /**
     * 升级版听歌接口
     * @param name 歌名
     * @param artist 作者 (可为空)
     * @param forceNewChain 是否强制断连
     * @param isRandom 是否是随机推荐的 (用于统计)
     * @param isFullPlay 是否完播
     * @param isSkip 是否跳过
     */
    /**
     * 【核心升级】支持指定图谱的听歌逻辑
     */
    @Transactional
    public Song addSong(Long userId, Long graphId, String name, String artist, boolean forceNewChain, 
                          boolean isRandom, boolean isFullPlay, boolean isSkip) {
        // 1. 校验并获取图谱专属 Label
        if (artist == null || artist.isEmpty()) artist = "Unknown";
        
        GraphInfo graph = graphInfoRepository.findByIdAndUserId(graphId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found or access denied"));
        
        String label = graph.getNodeLabel(); // 例如 "G_u10_xxx"

        // 2. 获取该图谱的上一首
        Long lastSongId = musicHistoryService.getLastListenedSongId(graphId);

        // 3. 使用动态 Cypher 查找或创建节点
        // 我们同时打上 :Song 和 动态Label
        String mergeNodeCypher = String.format(
            "MERGE (n:Song:`%s` {name: $name, artist: $artist}) " +
            "ON CREATE SET " +
            "   n.listenCount = 1, " +
            "   n.listenedAt = datetime(), " +
            "   n.fullPlayCount = $fullPlayInc, " +
            "   n.skipCount = $skipInc, " +
            "   n.userSelectCount = $userSelectInc, " +
            "   n.randomSelectCount = $randomSelectInc " +
            "ON MATCH SET " +
            "   n.listenCount = coalesce(n.listenCount, 0) + 1, " +
            "   n.listenedAt = datetime(), " +
            "   n.fullPlayCount = coalesce(n.fullPlayCount, 0) + $fullPlayInc, " +
            "   n.skipCount = coalesce(n.skipCount, 0) + $skipInc, " +
            "   n.userSelectCount = coalesce(n.userSelectCount, 0) + $userSelectInc, " +
            "   n.randomSelectCount = coalesce(n.randomSelectCount, 0) + $randomSelectInc " +
            "RETURN n", 
            label
        );

        // 准备参数
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("artist", artist);
        params.put("fullPlayInc", isFullPlay ? 1 : 0);
        params.put("skipInc", isSkip ? 1 : 0);
        params.put("userSelectInc", isRandom ? 0 : 1);
        params.put("randomSelectInc", isRandom ? 1 : 0);

        Song currentSong = neo4jClient.query(mergeNodeCypher)
                .bindAll(params)
                .fetchAs(Song.class)
                .mappedBy((typeSystem, record) -> {
                    // 手动映射一下 ID 和 Name，或者直接让 SDN 映射
                    // 这里为了保险，只要 ID 和关键属性
                    Song s = new Song();
                    s.setId(record.get(0).asNode().id());
                    s.setName(record.get(0).asNode().get("name").asString());
                    return s;
                })
                .one()
                .orElseThrow(() -> new RuntimeException("Failed to create/update node"));

        // 4. 处理连线 (仅当上一首存在且不强制断连，且不是自环时)
        if (lastSongId != null && !forceNewChain && !currentSong.getId().equals(lastSongId)) {
            // 注意：这里我们不需要再查一次 lastSong，直接用 ID 连线即可
            // 但为了安全，最好确保 lastSong 也在这个图谱里（通过 Label 约束）
            String edgeCypher = String.format(
                "MATCH (prev:`%s`), (curr:`%s`) " +
                "WHERE id(prev) = $lastId AND id(curr) = $currId " +
                "MERGE (prev)-[r:NEXT]->(curr) " +
                "ON CREATE SET " +
                "   r.jumpCount = 1, " +
                "   r.userSelectCount = $userSelectInc, " +
                "   r.randomSelectCount = $randomSelectInc " +
                "ON MATCH SET " +
                "   r.jumpCount = coalesce(r.jumpCount, 0) + 1, " +
                "   r.userSelectCount = coalesce(r.userSelectCount, 0) + $userSelectInc, " +
                "   r.randomSelectCount = coalesce(r.randomSelectCount, 0) + $randomSelectInc",
                label, label
            );
            
            neo4jClient.query(edgeCypher)
                .bind(lastSongId).to("lastId")
                .bind(currentSong.getId()).to("currId")
                .bind(isRandom ? 0 : 1).to("userSelectInc")
                .bind(isRandom ? 1 : 0).to("randomSelectInc")
                .run();
        }

        // 5. 更新 Redis 历史 (带 graphId)
        musicHistoryService.updateHistory(graphId, currentSong.getId(), currentSong.getName(), historyLimit);

        return currentSong;
    }

    // /**
    //  * 添加歌曲并处理播放逻辑
    //  */
    // @Transactional
    // public Song addSong(String name, boolean forceNewChain) {
    //     // 4. 【健壮性】入参校验，防止空指针传给数据库
    //     Assert.hasText(name, "Song name must not be empty");

    //     log.debug("Processing addSong request: name={}, forceNew={}", name, forceNewChain);

    //     try {
    //         // 1. 获取 Redis 历史记录
    //         Long lastSongId = musicHistoryService.getLastListenedSongId();
    //         Song lastSong = null;
    //         if (lastSongId != null) {
    //             lastSong = songRepository.findById(lastSongId).orElse(null);
    //         }

    //         // 提取状态
    //         String lastSongName = (lastSong != null) ? lastSong.getName() : "";
    //         LocalDate lastDate = (lastSong != null) ? lastSong.getListenedAt().toLocalDate() : null;

    //         // 2. 准备当前歌曲
    //         Song currentSong = songRepository.findByName(name).orElse(new Song(name));
    //         currentSong.setListenedAt(LocalDateTime.now());
            
    //         // 必须先保存以生成/更新 ID
    //         currentSong = songRepository.save(currentSong);

    //         // 3. 防自环逻辑
    //         if (lastSongName.equals(name)) {
    //             log.debug("Detected self-loop for song: {}", name);
    //             musicHistoryService.updateHistory(currentSong.getId(), currentSong.getName(), historyLimit);
    //             return currentSong;
    //         }

    //         // 4. 建立连接逻辑
    //         if (lastSong != null && !forceNewChain && lastDate != null) {
    //             LocalDate today = currentSong.getListenedAt().toLocalDate();

    //             if (lastDate.equals(today)) {
    //                 if (lastSong.getNextSongs() == null) {
    //                     lastSong.setNextSongs(new ArrayList<>());
    //                 }
    //                 lastSong.getNextSongs().add(currentSong);
    //                 songRepository.save(lastSong);
    //                 log.info("Created relationship: [{}] -> [{}]", lastSongName, name);
    //             }
    //         }

    //         // 5. 更新 Redis 历史
    //         musicHistoryService.updateHistory(currentSong.getId(), currentSong.getName(), historyLimit);
            
    //         return currentSong;

    //     } catch (Exception e) {
    //         // 5. 【可观测性】生产环境必须记录异常堆栈
    //         log.error("Failed to add song: {}", name, e);
    //         throw e; // 抛出异常以便事务回滚
    //     }
    // }

    @Transactional
    public void deleteConnection(String fromName, String toName) {
        Assert.hasText(fromName, "From-name must not be empty");
        Assert.hasText(toName, "To-name must not be empty");
        
        songRepository.deleteRelationship(fromName, toName);
        log.info("Deleted relationship between [{}] and [{}]", fromName, toName);
    }

    /**
     * 【修改】删除指定名称的歌曲节点及其关联关系
     * @param userId 当前用户ID
     * @param graphId 图谱ID
     * @param songName 歌曲名称
     */
    @Transactional
    public void deleteNode(Long userId, Long graphId, String songName) {
        Assert.hasText(songName, "Song name must not be empty"); // 参数校验

        // 1. 校验图谱权限
        GraphInfo graph = graphInfoRepository.findByIdAndUserId(graphId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found or access denied"));
        
        String label = graph.getNodeLabel();

        // 2. 执行删除 (匹配 name 属性)
        // 注意：如果有同名歌曲（比如不同歌手），这里会把它们都删掉。
        // 如果想更精确，可以加 artist 参数，但目前为了简便只用 name。
        String cypher = String.format("MATCH (n:`%s`) WHERE n.name = $name DETACH DELETE n", label);
        
        neo4jClient.query(cypher)
                .bind(songName).to("name")
                .run();
                
        log.info("用户 {} 从图谱 {} 中删除了歌曲: {}", userId, graphId, songName);
    }

    // ================= 动态属性管理 (高危区域) =================

    @Transactional
    public void addNodeProperty(String key, String type, String valueStr) {
        validatePropertyKey(key); // 【安全性】关键校验
        
        Object typedValue = parseValue(type, valueStr);
        String cypher = String.format("MATCH (n:Song) SET n.`%s` = $val", key);
        
        neo4jClient.query(cypher).bind(typedValue).to("val").run();
        log.info("Batch added node property: key={}, type={}", key, type);
    }

    @Transactional
    public void removeNodeProperty(String key) {
        validatePropertyKey(key);
        
        String cypher = String.format("MATCH (n:Song) REMOVE n.`%s`", key);
        neo4jClient.query(cypher).run();
        log.warn("Batch removed node property: key={}", key); // 删除操作使用 WARN 级别
    }

    @Transactional
    public void addEdgeProperty(String key, String type, String valueStr) {
        validatePropertyKey(key);

        Object typedValue = parseValue(type, valueStr);
        String cypher = String.format("MATCH ()-[r:NEXT]->() SET r.`%s` = $val", key);
        
        neo4jClient.query(cypher).bind(typedValue).to("val").run();
        log.info("Batch added edge property: key={}, type={}", key, type);
    }

    @Transactional
    public void removeEdgeProperty(String key) {
        validatePropertyKey(key);

        String cypher = String.format("MATCH ()-[r:NEXT]->() REMOVE r.`%s`", key);
        neo4jClient.query(cypher).run();
        log.warn("Batch removed edge property: key={}", key);
    }

    // ================= 辅助方法 =================

    /**
     * 【安全性】校验属性名是否合法
     * 防止 Cypher 注入攻击：比如 key 传 "name` = 1 DELETE n //"
     */
    private void validatePropertyKey(String key) {
        Assert.hasText(key, "Property key must not be empty");
        if (!SAFE_KEY_PATTERN.matcher(key).matches()) {
            log.warn("Potential injection attack detected with key: {}", key);
            throw new IllegalArgumentException("Invalid property key: " + key + ". Only alphanumeric and underscore allowed.");
        }
    }

    private Object parseValue(String type, String valueStr) {
        if (valueStr == null) return "";
        try {
            return switch (type.toLowerCase()) {
                case "int", "integer" -> Integer.parseInt(valueStr);
                case "long" -> Long.parseLong(valueStr);
                case "double", "float" -> Double.parseDouble(valueStr);
                case "boolean", "bool" -> Boolean.parseBoolean(valueStr);
                default -> valueStr;
            };
        } catch (NumberFormatException e) {
            log.error("Data type conversion error: value={}, type={}", valueStr, type);
            throw new IllegalArgumentException("Cannot convert value [" + valueStr + "] to type [" + type + "]");
        }
    }

    // ================= 查询功能 =================

    /**
     * 查询点信息
     * @param id 点ID (优先)
     * @param name 点名称 (如果ID为空)
     * @param artist 作者 (配合name使用)
     * @param detail 是否返回详细信息 (粒度2)
     */
    public Object queryNode(Long id, String name, String artist, boolean detail) {
        if (id != null) {
            // 策略：按 ID 查
            if (detail) {
                return songRepository.findNodeDetailById(id)
                        .orElseThrow(() -> new RuntimeException("Node not found with ID: " + id));
            } else {
                return songRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Node not found with ID: " + id));
            }
        } else {
            // 策略：按 Name 查
            Assert.hasText(name, "Query name cannot be empty when ID is null");
            if (artist == null) artist = "Unknown"; // 保持和之前默认值一致
            
            if (detail) {
                return songRepository.findNodeDetailByName(name, artist)
                        .orElseThrow(() -> new RuntimeException("Node not found: " + name));
            } else {
                return songRepository.findByNameAndArtist(name, artist)
                        .orElseThrow(() -> new RuntimeException("Node not found: " + name));
            }
        }
    }

    /**
     * 查询边信息
     * @param id 边ID (优先)
     * @param fromName 起点名
     * @param toName 终点名
     * @param detail 是否返回详细信息 (粒度2)
     */
    public Object queryEdge(Long id, String fromName, String toName, boolean detail) {
        if (id != null) {
            // 策略：按 ID 查
            if (detail) {
                return songRepository.findEdgeDetailById(id)
                        .orElseThrow(() -> new RuntimeException("Edge not found with ID: " + id));
            } else {
                // 【修复】使用 neo4jClient 直接获取 Map，避开 Repository 映射错误
                return neo4jClient.query("MATCH ()-[r:NEXT]->() WHERE id(r) = $id RETURN properties(r)")
                        .bind(id).to("id")
                        .fetchAs(Map.class) // 明确告诉它我要 Map
                        .one()
                        .orElseThrow(() -> new RuntimeException("Edge not found with ID: " + id));
            }
        } else {
            // 策略：按 起止点名 查
            Assert.hasText(fromName, "From-name cannot be empty");
            Assert.hasText(toName, "To-name cannot be empty");
            
            if (detail) {
                // 如果 DTO 映射也报错，请把 Repository 里的 properties(r) 改为 r{.*}
                return songRepository.findEdgeDetailByNames(fromName, toName)
                        .orElseThrow(() -> new RuntimeException("Edge not found between " + fromName + " and " + toName));
            } else {
                // 【修复】使用 neo4jClient 直接获取 Map
                String cypher = "MATCH (s:Song {name: $from})-[r:NEXT]->(t:Song {name: $to}) RETURN properties(r)";
                return neo4jClient.query(cypher)
                        .bind(fromName).to("from")
                        .bind(toName).to("to")
                        .fetchAs(Map.class)
                        .one()
                        .orElseThrow(() -> new RuntimeException("Edge not found between " + fromName + " and " + toName));
            }
        }
    }

    /**
     * 【版本迭代】一次性数据初始化
     * 规则：
     * 1. 作者名默认为 "Unknown" (或者空字符串)
     * 2. 线：跳转次数=1, 主动选择=1, 随机=0
     * 3. 点：基于线的统计重新计算听歌次数
     */
    @Transactional
    public String initVersionUpdate() {
        // 1. 初始化所有点的基础属性 (如果为空才设置)
        // 注意：这里先给一个默认值，防止后面计算出 null
        String initNodes = "MATCH (n:Song) " +
                "SET n.artist = COALESCE(n.artist, 'Unknown'), " +
                "n.listenCount = 1, " +
                "n.fullPlayCount = 1, " +
                "n.skipCount = 0, " +
                "n.userSelectCount = 1, " +
                "n.randomSelectCount = 0";
        neo4jClient.query(initNodes).run();

        // 2. 初始化所有边的属性
        String initEdges = "MATCH ()-[r:NEXT]->() " +
                "SET r.jumpCount = 1, " +
                "r.userSelectCount = 1, " +
                "r.randomSelectCount = 0";
        neo4jClient.query(initEdges).run();

        // 3. 【关键修正】根据边的关系，重新计算点的统计数据
        // 逻辑：
        // - 如果 totalJumps > 0，则 listenCount = totalJumps (只算入度)
        // - 如果 totalJumps == 0，则 listenCount = 1 (兜底，作为链头被听的那一次)
        String recalcNodes = "MATCH (n:Song) " +
                "OPTIONAL MATCH ()-[r:NEXT]->(n) " + 
                "WITH n, sum(r.jumpCount) as totalJumps, sum(r.userSelectCount) as totalSelects " +
                "SET n.listenCount = CASE WHEN totalJumps = 0 THEN 1 ELSE totalJumps END, " + 
                "n.userSelectCount = CASE WHEN totalSelects = 0 THEN 1 ELSE totalSelects END, " +
                "n.fullPlayCount = CASE WHEN totalJumps = 0 THEN 1 ELSE totalJumps END"; 
                // 假设历史数据每次跳转进来都完播了，所以完播次数 = 听歌次数
        
        neo4jClient.query(recalcNodes).run();

        return "数据版本迭代完成：属性初始化完毕，统计已根据入度重算（孤立点默认为1）。";
    }
    /**
     * 【核心算法】智能推荐下一首
     * @param currentSongId 当前播放的歌曲 ID
     * @param lastSongId 上一首播放的歌曲 ID (用于降权回头路)
     */
    public List<ScoredSongDTO> recommendNextSongs(Long currentSongId, Long lastSongId) {
        // 1. 获取所有邻居
        List<NeighborItemDTO> neighbors = songRepository.findAllNeighbors(currentSongId);
        
        if (neighbors == null || neighbors.isEmpty()) {
            return new ArrayList<>();
        }

        List<ScoredSongDTO> candidates = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (NeighborItemDTO item : neighbors) {
            Song candidateNode = item.getNode();
            if (candidateNode == null) continue;
            
            Map<String, Object> edgeProps = item.getEdge();

            // --- A. 计算互动分 (Interaction Score) ---
            double edgeScore = calculateInteraction(
                getInt(edgeProps, "userSelectCount"),
                getInt(edgeProps, "jumpCount"),
                getInt(edgeProps, "randomSelectCount")
            );

            double nodeScore = calculateInteraction(
                candidateNode.getUserSelectCount(),
                // 点没有 jumpCount，可以用 listenCount 代替或忽略
                0, 
                candidateNode.getRandomSelectCount()
            );

            // 基础分 = 边分 + (点分 * 0.2 辅助)
            double baseScore = edgeScore + (nodeScore * 0.2);
            if (baseScore < 0.1) baseScore = 0.1; // 保底分

            // --- B. 计算方向因子 (Direction Factor) ---
            double dirFactor = RankWeights.DIR_FORWARD; // 默认正向
            
            if ("IN".equals(item.getDirection())) {
                dirFactor = RankWeights.DIR_BACKWARD; // 反向降权
            }
            
            // 特殊逻辑：如果是刚听过的上一首 (回头路)，给予极刑
            if (lastSongId != null && candidateNode.getId().equals(lastSongId)) {
                dirFactor = RankWeights.DIR_REPEAT;
            }

            // --- C. 计算新鲜度因子 (Freshness Factor) ---
            double freshnessFactor = 1.0;
            if (candidateNode.getListenedAt() != null) {
                long minutesDiff = java.time.temporal.ChronoUnit.MINUTES.between(candidateNode.getListenedAt(), now);
                // 牛顿冷却公式: 1 - e^(-λt)
                freshnessFactor = 1.0 - Math.exp(-RankWeights.COOLING_LAMBDA * minutesDiff);
            }

            // --- D. 最终得分 ---
            double finalScore = baseScore * dirFactor * freshnessFactor;

            // 封装结果
            ScoredSongDTO dto = new ScoredSongDTO();
            dto.setSong(candidateNode);
            dto.setScore(finalScore);
            dto.setReason(String.format("Base:%.1f(Edge:%.1f, Node:%.1f) * Dir:%.1f * Fresh:%.2f", 
                baseScore, edgeScore, nodeScore, dirFactor, freshnessFactor));
            
            candidates.add(dto);
        }

        // 排序
        Collections.sort(candidates);
        return candidates;
    }

    // 辅助计算互动值：主动加分，随机减分
    private double calculateInteraction(int userSelect, int jump, int randomSelect) {
        return (userSelect * RankWeights.W_USER_SELECT) 
             + (jump * RankWeights.W_JUMP) 
             - (randomSelect * RankWeights.W_RANDOM);
    }

    private int getInt(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return 0;
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : 0;
    }
}