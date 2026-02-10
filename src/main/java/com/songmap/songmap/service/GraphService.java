package com.songmap.songmap.service;

import cn.hutool.core.util.IdUtil;
import com.songmap.songmap.entity.GraphInfo;
import com.songmap.songmap.entity.User;
import com.songmap.songmap.repository.GraphInfoRepository;
import com.songmap.songmap.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import com.songmap.songmap.dto.GraphDataDTO; // 导入 DTO
import org.springframework.data.neo4j.core.schema.Node; // 如果有用到
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GraphService {

    private final GraphInfoRepository graphInfoRepository;
    private final UserRepository userRepository;
    private final Neo4jClient neo4jClient;

    // 预设一些好看的渐变色给前端用
    private static final String[] COVER_COLORS = {
        "linear-gradient(135deg, #FF9A9E 0%, #FECFEF 100%)",
        "linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)",
        "linear-gradient(135deg, #84fab0 0%, #8fd3f4 100%)",
        "linear-gradient(135deg, #cfd9df 0%, #e2ebf0 100%)",
        "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
    };

    public GraphService(GraphInfoRepository graphInfoRepository, UserRepository userRepository, Neo4jClient neo4jClient) {
        this.graphInfoRepository = graphInfoRepository;
        this.userRepository = userRepository;
        this.neo4jClient = neo4jClient;
    }

    /**
     * 获取指定图谱的全量数据 (用于可视化)
     */
    public GraphDataDTO getGraphData(Long userId, Long graphId) {
        // 1. 权限与存在性校验
        GraphInfo graph = graphInfoRepository.findByIdAndUserId(graphId, userId)
                .orElseThrow(() -> new IllegalArgumentException("图谱不存在或无权访问"));

        String label = graph.getNodeLabel();

        // 2. 执行动态 Cypher 查询
        // 查出该 label 下的所有点，以及它们之间的关系
        String cypher = String.format(
            "MATCH (n:`%s`) " +
            "OPTIONAL MATCH (n)-[r:NEXT]->(m:`%s`) " +
            "RETURN n, r, m",
            label, label
        );

        // 3. 获取结果并映射 (手动去重)
        // neo4jClient 返回的是扁平的行数据，我们需要聚合
        Map<String, GraphDataDTO.NodeData> nodeMap = new HashMap<>();
        List<GraphDataDTO.LinkData> links = new ArrayList<>();

        neo4jClient.query(cypher).fetch().all().forEach(row -> {
            // 处理源节点 n
            mapNode(row.get("n"), nodeMap);
            
            // 处理关系 r 和目标节点 m (可能为空，如果是孤立点)
            if (row.get("r") != null && row.get("m") != null) {
                mapNode(row.get("m"), nodeMap);
                
                // 处理边
                // 注意：Neo4j 的 InternalRelationship 获取 id 和 start/end 比较麻烦
                // 这里我们假设 fetch() 返回的是 Map 结构的属性或实体
                // 简单起见，我们重新映射一下 ID
                
                org.neo4j.driver.types.Node sourceNode = (org.neo4j.driver.types.Node) row.get("n");
                org.neo4j.driver.types.Node targetNode = (org.neo4j.driver.types.Node) row.get("m");
                org.neo4j.driver.types.Relationship rel = (org.neo4j.driver.types.Relationship) row.get("r");
                
                // 获取边权重 (jumpCount)
                int weight = rel.get("jumpCount").isNull() ? 1 : rel.get("jumpCount").asInt();
                
                links.add(new GraphDataDTO.LinkData(
                    String.valueOf(sourceNode.id()), 
                    String.valueOf(targetNode.id()), 
                    weight
                ));
            }
        });

        return new GraphDataDTO(new ArrayList<>(nodeMap.values()), links);
    }

    // 辅助方法：将 Neo4j Driver Node 映射为 DTO
    private void mapNode(Object rawNode, Map<String, GraphDataDTO.NodeData> nodeMap) {
        if (rawNode instanceof org.neo4j.driver.types.Node) {
            org.neo4j.driver.types.Node node = (org.neo4j.driver.types.Node) rawNode;
            String id = String.valueOf(node.id());
            
            if (!nodeMap.containsKey(id)) {
                String name = node.get("name").asString();
                String artist = node.get("artist").isNull() ? "Unknown" : node.get("artist").asString();
                
                // 计算节点大小：基础大小 20 + 听歌次数 * 2
                int listenCount = node.get("listenCount").isNull() ? 0 : node.get("listenCount").asInt();
                int symbolSize = Math.min(20 + listenCount * 2, 60); // 上限 60
                
                // 简单分类：听过超过 10 次算热门
                int category = listenCount > 10 ? 1 : 0;

                nodeMap.put(id, new GraphDataDTO.NodeData(id, name, artist, symbolSize, category));
            }
        }
    }

    /**
     * 初始化模板数据
     * 逻辑：查找所有带有 :base_Song 标签的节点和它们之间的关系，
     * 复制一份，并给新节点打上当前图谱的专属 Label (uniqueLabel)
     */
    private void initTemplateData(String uniqueLabel) {
        // 使用 APOC 插件会更简单，但为了兼容性，我们用纯 Cypher 实现
        // 逻辑分为两步：
        // 1. 复制节点：查出所有 base_Song，创建新节点，复制属性，打上 uniqueLabel
        // 2. 复制关系：查出 base_Song 之间的关系，在对应的新节点之间建立同样的关系

        // Step 1: 复制节点
        // 我们假设 base_Song 里的 name+artist 是唯一的，可以作为临时标识
        String copyNodesCypher = String.format(
            "MATCH (source:base_Song) " +
            "CREATE (target:Song:%s) " +
            "SET target = properties(source), " + // 复制所有属性
            "    target.isTemplateCopy = true, " + // 可选：标记一下来源
            // 重新初始化统计数据（可选，如果不希望继承热度）
            "    target.listenCount = 0, " +
            "    target.fullPlayCount = 0, " +
            "    target.skipCount = 0, " +
            "    target.userSelectCount = 0, " +
            "    target.randomSelectCount = 0 " +
            "RETURN count(target)",
            "`" + uniqueLabel + "`" // 动态 Label
        );
        
        neo4jClient.query(copyNodesCypher).run();

        // Step 2: 复制关系
        // 这一步比较 tricky，我们需要找到“新创建的节点A”和“新创建的节点B”，
        // 前提是它们对应的“原节点A”和“原节点B”之间有关系。
        // 我们可以利用 name 和 artist 来进行匹配（假设它们是主键）。
        
        String copyEdgesCypher = String.format(
            "MATCH (sourceA:base_Song)-[r:NEXT]->(sourceB:base_Song) " +
            "MATCH (targetA:Song:%1$s {name: sourceA.name, artist: sourceA.artist}) " +
            "MATCH (targetB:Song:%1$s {name: sourceB.name, artist: sourceB.artist}) " +
            "MERGE (targetA)-[newR:NEXT]->(targetB) " +
            "SET newR = properties(r), " + // 复制边属性
            "    newR.jumpCount = 1, " + // 重置权重（可选）
            "    newR.userSelectCount = 0 ",
            "`" + uniqueLabel + "`"
        );

        neo4jClient.query(copyEdgesCypher).run();
        
        log.info("Initialized template data for label: {}", uniqueLabel);
    }

    /**
     * 获取用户的图谱列表
     */
    public List<GraphInfo> getUserGraphs(Long userId) {
        return graphInfoRepository.findAllByUserId(userId);
    }

    /**
     * 创建新图谱
     */
    @Transactional
    public GraphInfo createGraph(Long userId, String type, String customName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 1. 生成基础信息
        // 【修改】如果有自定义名字就用自定义的，否则用默认逻辑
        String name;
        if (customName != null && !customName.isBlank()) {
            name = customName;
        } else {
            name = type.equals("template") ? "官方推荐图谱" : "我的新图谱";
        }
        // 【关键】生成唯一的 Label 名称，例如 "G_u10_abc123"
        // 加上 G_ 前缀是为了防止数字开头，加上 userId 是为了方便以后运维排查
        String uniqueLabel = "G_u" + userId + "_" + IdUtil.simpleUUID();
        
        // 随机颜色
        String color = COVER_COLORS[new Random().nextInt(COVER_COLORS.length)];

        GraphInfo graph = new GraphInfo(name, uniqueLabel, type, color);
        
        // 2. 建立 User -> GraphInfo 的关系
        user.getGraphs().add(graph);
        userRepository.save(user); // 级联保存 GraphInfo

        // 3. 如果是模板，需要初始化数据
        if ("template".equals(type)) {
            initTemplateData(uniqueLabel);
        }

        log.info("Created graph [{}] for user [{}], Label: {}", graph.getId(), userId, uniqueLabel);
        return graph;
    }

    /**
     * 删除图谱（仅删除元数据入口，暂不清洗数据节点）
     */
    @Transactional
    public void deleteGraph(Long userId, Long graphId) {
        // 1. 校验归属权（防止恶意删除别人的图）
        GraphInfo graph = graphInfoRepository.findByIdAndUserId(graphId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found or permission denied"));

        // 2. 删除节点 (SDN 会自动删除 User -> GraphInfo 的关系)
        graphInfoRepository.delete(graph);
        
        log.info("Deleted GraphInfo [{}] for user [{}]", graphId, userId);
    }

    /**
     * 初始化模板数据
     * 直接使用 Cypher 批量创建节点，并打上 uniqueLabel
     */
    private void initTemplateData(String label) {
        // 这里我们创建一个简单的 流行 -> 周杰伦 -> 夜曲 的结构
        // 注意：我们在 Cypher 中使用 apoc 或者字符串拼接来动态添加 Label
        // 由于 SDN 不支持动态 Label 参数，我们需要用 String.format (注意防注入，这里 label 是系统生成的，相对安全)
        
        String cypher = String.format(
            "CREATE (g:Genre {name: '流行', %1$s: true}) " +
            "CREATE (a:Artist {name: '周杰伦', %1$s: true}) " +
            "CREATE (s:Song {name: '夜曲', artist: '周杰伦', %1$s: true}) " +
            "MERGE (g)-[:Oi]->(a) " +
            "MERGE (a)-[:Oi]->(s)",
            "`" + label + "`" // 给 label 加上反引号，防止特殊字符报错
        );
        
        // 这里只是为了演示“专属Label”的概念。
        // 实际上，我们通常给节点打两个 Label：
        // 1. 通用 Label (如 Song) -> 用于类型查询
        // 2. 专用 Label (如 G_u1_xyz) -> 用于范围隔离
        
        String templateCypher = String.format(
            "CREATE (s1:Song:%1$s {name: '夜曲', artist: '周杰伦', listenCount: 0}) " +
            "CREATE (s2:Song:%1$s {name: '七里香', artist: '周杰伦', listenCount: 0}) " +
            "CREATE (s3:Song:%1$s {name: '晴天', artist: '周杰伦', listenCount: 0}) " +
            "CREATE (s1)-[:NEXT {jumpCount: 1}]->(s2) " +
            "CREATE (s2)-[:NEXT {jumpCount: 1}]->(s3) ",
            "`" + label + "`"
        );

        neo4jClient.query(templateCypher).run();
    }
}