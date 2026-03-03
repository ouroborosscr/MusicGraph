package com.songmap.songmap.service;

import com.songmap.songmap.dto.NeighborItemDTO;
import com.songmap.songmap.dto.ScoredSongDTO;
import com.songmap.songmap.entity.GraphInfo;
import com.songmap.songmap.entity.Song;
import com.songmap.songmap.repository.GraphInfoRepository;
import com.songmap.songmap.repository.SongRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 音乐图谱核心服务
 */
@Slf4j
@Service
public class MusicGraphService {

    private final SongRepository songRepository;
    private final GraphInfoRepository graphInfoRepository;
    private final MusicHistoryService musicHistoryService;
    private final Neo4jClient neo4jClient;

    @Value("${songmap.history.limit:100}")
    private int historyLimit;

    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

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
     * 【智能听歌接口】包含节点合并与去重逻辑
     */
    @Transactional
    public Song addSong(Long userId, Long graphId, String name, String artist, boolean forceNewChain, 
                          boolean isRandom, boolean isFullPlay, boolean isSkip) {
        // 1. 校验并获取图谱专属 Label
        if (artist == null || artist.isEmpty()) artist = "Unknown";
        
        GraphInfo graph = graphInfoRepository.findByIdAndUserId(graphId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found or access denied"));
        
        String label = graph.getNodeLabel();

        // 2. 获取该图谱的上一首
        Long lastSongId = musicHistoryService.getLastListenedSongId(graphId);

        // 3. 【核心修改】智能查找或创建节点
        // 逻辑：
        // A. 优先找 name + artist 完全匹配的
        // B. 其次找 name 匹配且 artist='Unknown' 的 (说明之前存的时候不知道歌手，现在知道了，就复用它)
        // C. 如果都找不到，才 CREATE
        
        String findCypher = String.format(
            "MATCH (n:`%s`) " +
            "WHERE n.name = $name AND (n.artist = $artist OR n.artist = 'Unknown') " +
            "RETURN n " +
            "ORDER BY CASE WHEN n.artist = $artist THEN 1 ELSE 2 END " + // 优先匹配确切歌手
            "LIMIT 1",
            label
        );

        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("artist", artist);
        
        // 尝试查找现有节点
        Song existingNode = neo4jClient.query(findCypher)
                .bindAll(params)
                .fetchAs(Song.class)
                .mappedBy((typeSystem, record) -> {
                    Song s = new Song();
                    s.setId(record.get(0).asNode().id());
                    s.setName(record.get(0).asNode().get("name").asString());
                    if (!record.get(0).asNode().get("artist").isNull()) {
                        s.setArtist(record.get(0).asNode().get("artist").asString());
                    }
                    return s;
                })
                .one()
                .orElse(null);

        Song currentSong;
        
        if (existingNode != null) {
            // --- 情况 A/B: 节点已存在 (可能是完全匹配，也可能是 Unknown) ---
            log.info("合并至现有节点: id={}, name={}", existingNode.getId(), existingNode.getName());
            
            String updateCypher = String.format(
                "MATCH (n) WHERE id(n) = $id " +
                "SET n.listenCount = coalesce(n.listenCount, 0) + 1, " +
                "    n.listenedAt = localdatetime(), " + // 【修复】使用 localdatetime() 避免 500 错误
                "    n.fullPlayCount = coalesce(n.fullPlayCount, 0) + $fullPlayInc, " +
                "    n.skipCount = coalesce(n.skipCount, 0) + $skipInc, " +
                "    n.userSelectCount = coalesce(n.userSelectCount, 0) + $userSelectInc, " +
                "    n.randomSelectCount = coalesce(n.randomSelectCount, 0) + $randomSelectInc, " +
                "    n.artist = $artist " + // 【关键】强制更新作者（如果是 Unknown 会被覆盖为真名）
                "RETURN n",
                label
            );
            
            params.put("id", existingNode.getId());
            params.put("fullPlayInc", isFullPlay ? 1 : 0);
            params.put("skipInc", isSkip ? 1 : 0);
            params.put("userSelectInc", isRandom ? 0 : 1);
            params.put("randomSelectInc", isRandom ? 1 : 0);
            
            neo4jClient.query(updateCypher).bindAll(params).run();
            currentSong = existingNode; 
            
        } else {
            // --- 情况 C: 节点不存在，创建新节点 ---
            log.info("创建新节点: name={}, artist={}", name, artist);
            
            String createCypher = String.format(
                "CREATE (n:Song:`%s` {name: $name, artist: $artist}) " +
                "SET n.listenCount = 1, " +
                "    n.listenedAt = localdatetime(), " + // 【修复】使用 localdatetime()
                "    n.fullPlayCount = $fullPlayInc, " +
                "    n.skipCount = $skipInc, " +
                "    n.userSelectCount = $userSelectInc, " +
                "    n.randomSelectCount = $randomSelectInc " +
                "RETURN n", 
                label
            );
            
            params.put("fullPlayInc", isFullPlay ? 1 : 0);
            params.put("skipInc", isSkip ? 1 : 0);
            params.put("userSelectInc", isRandom ? 0 : 1);
            params.put("randomSelectInc", isRandom ? 1 : 0);

            currentSong = neo4jClient.query(createCypher)
                .bindAll(params)
                .fetchAs(Song.class)
                .mappedBy((typeSystem, record) -> {
                    Song s = new Song();
                    s.setId(record.get(0).asNode().id());
                    s.setName(record.get(0).asNode().get("name").asString());
                    return s;
                })
                .one()
                .orElseThrow(() -> new RuntimeException("Failed to create node"));
        }

        // 4. 处理连线 (仅当上一首存在且不强制断连，且不是自环时)
        if (lastSongId != null && !forceNewChain && !currentSong.getId().equals(lastSongId)) {
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

    @Transactional
    public void deleteConnection(String fromName, String toName) {
        Assert.hasText(fromName, "From-name must not be empty");
        Assert.hasText(toName, "To-name must not be empty");
        
        songRepository.deleteRelationship(fromName, toName);
        log.info("Deleted relationship between [{}] and [{}]", fromName, toName);
    }

    @Transactional
    public void deleteNode(Long userId, Long graphId, String songName) {
        Assert.hasText(songName, "Song name must not be empty");

        GraphInfo graph = graphInfoRepository.findByIdAndUserId(graphId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found or access denied"));
        
        String label = graph.getNodeLabel();

        String cypher = String.format("MATCH (n:`%s`) WHERE n.name = $name DETACH DELETE n", label);
        
        neo4jClient.query(cypher)
                .bind(songName).to("name")
                .run();
                
        log.info("用户 {} 从图谱 {} 中删除了歌曲: {}", userId, graphId, songName);
    }

    // ================= 动态属性管理 =================

    @Transactional
    public void addNodeProperty(String key, String type, String valueStr) {
        validatePropertyKey(key);
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
        log.warn("Batch removed node property: key={}", key);
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

    private void validatePropertyKey(String key) {
        Assert.hasText(key, "Property key must not be empty");
        if (!SAFE_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid property key: " + key);
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
            throw new IllegalArgumentException("Cannot convert value [" + valueStr + "] to type [" + type + "]");
        }
    }

    // ================= 查询功能 =================

    public Object queryNode(Long id, String name, String artist, boolean detail) {
        if (id != null) {
            if (detail) return songRepository.findNodeDetailById(id).orElseThrow();
            else return songRepository.findById(id).orElseThrow();
        } else {
            Assert.hasText(name, "Query name cannot be empty");
            if (artist == null) artist = "Unknown";
            if (detail) return songRepository.findNodeDetailByName(name, artist).orElseThrow();
            else return songRepository.findByNameAndArtist(name, artist).orElseThrow();
        }
    }

    public Object queryEdge(Long id, String fromName, String toName, boolean detail) {
        if (id != null) {
            if (detail) return songRepository.findEdgeDetailById(id).orElseThrow();
            else return neo4jClient.query("MATCH ()-[r:NEXT]->() WHERE id(r) = $id RETURN properties(r)").bind(id).to("id").fetchAs(Map.class).one().orElseThrow();
        } else {
            Assert.hasText(fromName, "From-name empty");
            Assert.hasText(toName, "To-name empty");
            if (detail) return songRepository.findEdgeDetailByNames(fromName, toName).orElseThrow();
            else return neo4jClient.query("MATCH (s:Song {name: $from})-[r:NEXT]->(t:Song {name: $to}) RETURN properties(r)")
                    .bind(fromName).to("from").bind(toName).to("to").fetchAs(Map.class).one().orElseThrow();
        }
    }

    @Transactional
    public String initVersionUpdate() {
        return "数据版本迭代完成";
    }

    public List<ScoredSongDTO> recommendNextSongs(Long currentSongId, Long lastSongId) {
        List<NeighborItemDTO> neighbors = songRepository.findAllNeighbors(currentSongId);
        if (neighbors == null || neighbors.isEmpty()) return new ArrayList<>();

        List<ScoredSongDTO> candidates = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (NeighborItemDTO item : neighbors) {
            Song candidateNode = item.getNode();
            if (candidateNode == null) continue;
            Map<String, Object> edgeProps = item.getEdge();

            double edgeScore = calculateInteraction(
                getInt(edgeProps, "userSelectCount"),
                getInt(edgeProps, "jumpCount"),
                getInt(edgeProps, "randomSelectCount")
            );
            double nodeScore = calculateInteraction(
                candidateNode.getUserSelectCount(), 0, candidateNode.getRandomSelectCount()
            );

            double baseScore = edgeScore + (nodeScore * 0.2);
            if (baseScore < 0.1) baseScore = 0.1;

            double dirFactor = RankWeights.DIR_FORWARD;
            if ("IN".equals(item.getDirection())) dirFactor = RankWeights.DIR_BACKWARD;
            if (lastSongId != null && candidateNode.getId().equals(lastSongId)) dirFactor = RankWeights.DIR_REPEAT;

            double freshnessFactor = 1.0;
            if (candidateNode.getListenedAt() != null) {
                // 使用 LocalDateTime 计算时间差，避免时区报错
                long minutesDiff = java.time.temporal.ChronoUnit.MINUTES.between(candidateNode.getListenedAt(), now);
                freshnessFactor = 1.0 - Math.exp(-RankWeights.COOLING_LAMBDA * minutesDiff);
            }

            double finalScore = baseScore * dirFactor * freshnessFactor;
            ScoredSongDTO dto = new ScoredSongDTO();
            dto.setSong(candidateNode);
            dto.setScore(finalScore);
            dto.setReason(String.format("Base:%.1f * Dir:%.1f * Fresh:%.2f", baseScore, dirFactor, freshnessFactor));
            candidates.add(dto);
        }
        Collections.sort(candidates);
        return candidates;
    }

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