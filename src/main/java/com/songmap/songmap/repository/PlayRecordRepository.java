package com.songmap.songmap.repository;

import com.songmap.songmap.entity.PlayRecord;
import com.songmap.songmap.entity.Song;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import java.util.Optional;

public interface PlayRecordRepository extends Neo4jRepository<PlayRecord, Long> {
    
    // 【知识点 5】自定义 Cypher 查询
    // 尽管 Repository 自带 findAll，但我们需要复杂的图查询时，直接写 Cypher 最快
    @Query("MATCH (r:PlayRecord) RETURN r ORDER BY r.timestamp DESC LIMIT 1")
    Optional<PlayRecord> findLastRecord();
}
