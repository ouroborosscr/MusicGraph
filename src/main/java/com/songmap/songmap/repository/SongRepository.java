package com.songmap.songmap.repository;

import com.songmap.songmap.dto.EdgeDetailDTO;
import com.songmap.songmap.dto.NodeDetailDTO;
import com.songmap.songmap.entity.Song;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.Map;
import java.util.Optional; // 别忘了导入这个

public interface SongRepository extends Neo4jRepository<Song, Long>{

    // DELETE THIS: 以后不再需要全表扫描找最新的歌了
    // // 自定义查询：找到最近一次听的那首歌
    // // Cypher 是 Neo4j 的查询语言，类似于 SQL
    // @Query("MATCH (s:Song) RETURN s ORDER BY s.listenedAt DESC LIMIT 1")
    // Song findLastListenedSong();

    // 【废弃】旧的只查 name
    // // 【新增】根据歌名查找（用于节点复用）
    // // 有现成函数+要传参容易被注入，所以不写sql语句
    // Optional<Song> findByName(String name);
    // ==================== 1. 查询点 ====================
    // 粒度1：查点本身 (复用 findById 或 findByNameAndArtist 即可)
    // 【新增】根据 歌名 和 作者 查找
    @Query("MATCH (s:Song) WHERE s.name = $name AND (s.artist = $artist OR ($artist IS NULL AND s.artist IS NULL)) RETURN s LIMIT 1")
    Optional<Song> findByNameAndArtist(@Param("name") String name, @Param("artist") String artist);

    // 粒度2：查点 + 出边 + 入边 (根据 ID)
    // 逻辑：找到点 n，收集它的出边(out)和目标点(t)，收集入边(in)和源点(s)
    @Query("MATCH (n:Song) WHERE id(n) = $id " +
           "OPTIONAL MATCH (n)-[out:NEXT]->(t:Song) " +
           "OPTIONAL MATCH (s:Song)-[in:NEXT]->(n) " +
           "RETURN n as self, " +
           "collect({edge: properties(out), target: t}) as outgoing, " +
           "collect({edge: properties(in), source: s}) as incoming")
    Optional<NodeDetailDTO> findNodeDetailById(@Param("id") Long id);

    // 粒度2：查点 + 出边 + 入边 (根据 Name + Artist)
    @Query("MATCH (n:Song) WHERE n.name = $name AND (n.artist = $artist OR ($artist IS NULL AND n.artist IS NULL)) " +
           "OPTIONAL MATCH (n)-[out:NEXT]->(t:Song) " +
           "OPTIONAL MATCH (s:Song)-[in:NEXT]->(n) " +
           "RETURN n as self, " +
           "collect({edge: properties(out), target: t}) as outgoing, " +
           "collect({edge: properties(in), source: s}) as incoming " +
           "LIMIT 1")
    Optional<NodeDetailDTO> findNodeDetailByName(@Param("name") String name, @Param("artist") String artist);

    // ==================== 2. 查询边 ====================

    // 粒度1：仅查边属性 (根据 Edge ID)
    @Query("MATCH ()-[r:NEXT]->() WHERE id(r) = $id RETURN properties(r)")
    Map<String, Object> findEdgePropertiesById(@Param("id") Long id);

    // 粒度2：查边 + 源点 + 目标点 (根据 Edge ID)
    @Query("MATCH (s:Song)-[r:NEXT]->(t:Song) WHERE id(r) = $id " +
           "RETURN r{.*} as edge, s as source, t as target") // <--- 修改这里
    Optional<EdgeDetailDTO> findEdgeDetailById(@Param("id") Long id);

    // 粒度1：仅查边属性 (根据 FromName, ToName)
    @Query("MATCH (s:Song {name: $fromName})-[r:NEXT]->(t:Song {name: $toName}) " +
           "RETURN properties(r) LIMIT 1")
    Map<String, Object> findEdgePropertiesByNames(@Param("fromName") String fromName, @Param("toName") String toName);

    // 粒度2：查边 + 源点 + 目标点 (根据 FromName, ToName)
    @Query("MATCH (s:Song {name: $fromName})-[r:NEXT]->(t:Song {name: $toName}) " +
           "RETURN r{.*} as edge, s as source, t as target LIMIT 1") // <--- 修改这里
    Optional<EdgeDetailDTO> findEdgeDetailByNames(@Param("fromName") String fromName, @Param("toName") String toName);

    // 【新增】删除两个歌曲之间的 NEXT 关系
    // 逻辑：找到名为 fromName 的节点 a，找到名为 toName 的节点 b，删除它们之间名为 NEXT 的关系 r
    @Query("MATCH (a:Song {name: $fromName})-[r:NEXT]->(b:Song {name: $toName}) DELETE r")
    void deleteRelationship(@Param("fromName") String fromName, @Param("toName") String toName);

    // 【核心大招】创建或更新边（Upsert）
    // 逻辑：找到两首歌，MERGE 它们的关系。如果是新建关系，设置初始值；如果是已有关系，计数器 +1
    // 参数说明：
    // jumpVal: 本次跳转权重（通常为1）
    // selectVal: 是否主动选择（1或0）
    // randomVal: 是否随机（1或0）
    @Query("MATCH (from:Song) WHERE id(from) = $fromId " +
           "MATCH (to:Song) WHERE id(to) = $toId " +   // <--- 关键修改：拆分成两个 MATCH
           "MERGE (from)-[r:NEXT]->(to) " +
           "ON CREATE SET r.jumpCount = $jumpVal, r.userSelectCount = $selectVal, r.randomSelectCount = $randomVal " +
           "ON MATCH SET r.jumpCount = coalesce(r.jumpCount, 0) + $jumpVal, " +
                        "r.userSelectCount = coalesce(r.userSelectCount, 0) + $selectVal, " +
                        "r.randomSelectCount = coalesce(r.randomSelectCount, 0) + $randomVal")
    void createOrUpdateEdge(@Param("fromId") Long fromId, 
                            @Param("toId") Long toId,
                            @Param("jumpVal") int jumpVal,
                            @Param("selectVal") int selectVal,
                            @Param("randomVal") int randomVal);
}

//基于方法名解析结果，框架会自动生成以下 Cypher 查询语句：
//MATCH (s:Song) WHERE s.name = $name RETURN s
//其中 $name 是方法参数的占位符，会被安全地绑定到实际传入的 name 值（防止注入攻击）。


//Spring Data Neo4j 会在运行时通过反射分析 findByName(String name) 方法的签名：

//- findBy ：表示“查询”操作（类似的前缀还有 findAllBy 、 countBy 、 deleteBy 等）。
//- Name ：表示查询条件的属性名，对应 Song 实体类中的 name 字段（框架会自动将首字母大写的 Name 转换为小写的 name ）。
//- String name ：表示查询参数，对应 name 属性的值。