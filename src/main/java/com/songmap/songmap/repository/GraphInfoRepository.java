package com.songmap.songmap.repository;

import com.songmap.songmap.entity.GraphInfo;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.util.List;

public interface GraphInfoRepository extends Neo4jRepository<GraphInfo, Long> {

    // 【修复】通过 User -> OWNS -> GraphInfo 关系来查找列表
    @Query("MATCH (u:User)-[:OWNS]->(g:GraphInfo) WHERE id(u) = $userId RETURN g")
    List<GraphInfo> findAllByUserId(Long userId);

    // 【修复】通过 User -> OWNS -> GraphInfo 关系来校验单个图谱归属权
    @Query("MATCH (u:User)-[:OWNS]->(g:GraphInfo) " +
           "WHERE id(u) = $userId AND id(g) = $id " +
           "RETURN g")
    Optional<GraphInfo> findByIdAndUserId(Long id, Long userId);
}