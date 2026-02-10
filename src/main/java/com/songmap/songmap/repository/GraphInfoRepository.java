package com.songmap.songmap.repository;

import com.songmap.songmap.entity.GraphInfo;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.util.List;

public interface GraphInfoRepository extends Neo4jRepository<GraphInfo, Long> {

    // 查询某个用户拥有的所有图谱
    @Query("MATCH (u:User)-[:OWNS]->(g:GraphInfo) WHERE id(u) = $userId RETURN g ORDER BY g.updatedAt DESC")
    List<GraphInfo> findAllByUserId(@Param("userId") Long userId);

    // 【新增】校验并查找（确保该图谱属于该用户）
    @Query("MATCH (u:User)-[:OWNS]->(g:GraphInfo) WHERE id(u) = $userId AND id(g) = $graphId RETURN g")
    Optional<GraphInfo> findByIdAndUserId(@Param("graphId") Long graphId, @Param("userId") Long userId);
}