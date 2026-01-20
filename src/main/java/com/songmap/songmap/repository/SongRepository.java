package com.songmap.songmap.repository;

import com.songmap.songmap.entity.Song;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import java.util.Optional; // 别忘了导入这个

public interface SongRepository extends Neo4jRepository<Song, Long>{

    // 自定义查询：找到最近一次听的那首歌
    // Cypher 是 Neo4j 的查询语言，类似于 SQL
    @Query("MATCH (s:Song) RETURN s ORDER BY s.listenedAt DESC LIMIT 1")
    Song findLastListenedSong();

    // 【新增】根据歌名查找（用于节点复用）
    Optional<Song> findByName(String name);
}