package com.songmap.songmap.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("User")
@Data
@NoArgsConstructor
public class User {
    @Id @GeneratedValue
    private Long id;

    // 建立唯一索引（需要在 Neo4j 数据库手动建，或者让 SDN 自动扫描）
    private String username;
    
    // 存加密后的哈希值，不要存明文！
    private String password; 
    
    private String avatar; // 头像

    // 后面可以加：
    // @Relationship(type = "OWNS", direction = Relationship.Direction.OUTGOING)
    // private List<GraphScope> graphs;

    // 【新增】用户拥有的图谱列表
    @Relationship(type = "OWNS", direction = Relationship.Direction.OUTGOING)
    private List<GraphInfo> graphs = new ArrayList<>();
    // 记得要有 Getter/Setter，或者用 Lombok 的 @Data
    public List<GraphInfo> getGraphs() {
        return graphs;
    }
}