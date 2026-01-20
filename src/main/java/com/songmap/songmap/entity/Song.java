package com.songmap.songmap.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 歌曲实体类，用于在Neo4j图数据库中表示歌曲节点
 * <p>
 * 该类使用Spring Data Neo4j注解将Java对象映射到Neo4j图数据库中的节点，
 * 并通过关系定义实现歌曲之间的链表结构，用于记录听歌历史顺序。
 * </p>
 * 
 * @author SongMap Team
 * @since 1.0.0
 */
@Node("Song") // 标记为Neo4j节点，标签为"Song"
@Getter @Setter // Lombok注解，自动生成getter和setter方法
@NoArgsConstructor // Lombok注解，生成无参构造函数
public class Song {

    /**
     * 歌曲唯一标识符，由Neo4j自动生成
     */
    @Id @GeneratedValue // 标记为主键，并使用自动生成策略
    private Long id;

    /**
     * 歌曲名称
     */
    private String name;
    
    /**
     * 歌曲被播放的时间戳，用于判断是否为"新的一天"
     */
    private LocalDateTime listenedAt;

    /**
     * 歌曲来源平台，如"NETEASE"（网易云音乐）或"TENCENT"（QQ音乐）
     */
    private String sourcePlatform;
    
    /**
     * 歌曲在来源平台的外部ID，用于与外部音乐服务进行关联
     */
    private String externalId;

    /**
     * 指向后续歌曲的关系列表，形成播放历史链表
     * <p>
     * 使用@JsonIgnoreProperties避免JSON序列化时的循环引用问题
     * </p>
     */
    @JsonIgnoreProperties("nextSongs") // 忽略nextSongs属性，避免JSON序列化循环引用
    @Relationship(type = "NEXT", direction = Relationship.Direction.OUTGOING) // 定义"NEXT"关系，方向为从当前节点指向目标节点
    private List<Song> nextSongs = new ArrayList<>();

    /**
     * 带歌曲名称的构造函数
     * <p>
     * 创建歌曲实例时自动设置当前时间为播放时间
     * </p>
     * 
     * @param name 歌曲名称
     */
    public Song(String name) {
        this.name = name;
        this.listenedAt = LocalDateTime.now(); // 自动设置为当前时间
    }

    /**
     * 重写toString方法，返回歌曲的字符串表示
     * 
     * @return 包含歌曲ID、名称和播放时间的字符串
     */
    @Override
    public String toString() {
        return "Song{id=" + id + ", name='" + name + "', listenedAt=" + listenedAt + "}";
    }

    /**
     * 重写equals方法，基于ID判断两个歌曲对象是否相等
     * 
     * @param o 要比较的对象
     * @return 如果两个对象是同一实例或具有相同ID，则返回true；否则返回false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return id != null && Objects.equals(id, song.id);
    }

    /**
     * 重写hashCode方法，基于类的hashCode生成哈希值
     * <p>
     * 注意：此实现使用类的hashCode而不是基于实例字段，
     * 实际应用中可能需要根据具体需求调整为基于关键字段的哈希计算
     * </p>
     * 
     * @return 哈希码值
     */
    @Override
    public int hashCode() {
        return getClass().hashCode(); 
    }
}