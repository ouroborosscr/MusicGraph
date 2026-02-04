package com.songmap.songmap.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;

/**
 * 播放记录实体类，用于在Neo4j图数据库中表示歌曲播放记录节点
 * <p>
 * 该类使用Spring Data Neo4j注解将Java对象映射到Neo4j图数据库中的节点，
 * 并通过关系定义实现播放记录之间的链表结构，用于记录听歌历史的先后顺序。
 * </p>
 * 
 * @author SongMap Team
 * @since 1.0.0
 */
@Node("PlayRecord") // 标记为Neo4j节点，标签为"PlayRecord"
@Data // Lombok注解，自动生成getter、setter、toString等方法
@NoArgsConstructor // Lombok注解，生成无参构造函数
public class PlayRecord {
    /**
     * 播放记录唯一标识符，由Neo4j自动生成
     */
    @Id @GeneratedValue // 标记为主键，并使用自动生成策略
    private Long id;

    /**
     * 播放记录的时间戳，记录歌曲播放的具体时间
     */
    private LocalDateTime timestamp;

    /**
     * 关联的歌曲节点，表示当前播放记录播放的歌曲
     * <p>
     * 方向设为 OUTGOING (发出)，type 是边上的标签
     * </p>
     */
    @Relationship(type = "PLAYS", direction = Relationship.Direction.OUTGOING) // 定义"PLAYS"关系，方向为从播放记录指向歌曲
    private Song song;
    

    /**
     * 指向下一个播放记录的关系，形成播放历史链表
     */
    @Relationship(type = "NEXT", direction = Relationship.Direction.OUTGOING) // 定义"NEXT"关系，方向为从当前播放记录指向下一个播放记录
    private PlayRecord nextRecord;

    /**
     * 带歌曲参数的构造函数
     * <p>
     * 创建播放记录实例时自动设置当前时间为播放时间戳
     * </p>
     * 
     * @param song 播放的歌曲对象
     */
    public PlayRecord(Song song) {
        this.timestamp = LocalDateTime.now(); // 自动设置为当前时间
        this.song = song;
    }

    /**
     * 设置下一个播放记录的方法
     * <p>
     * 用于构建播放记录之间的链表关系，建立播放历史的先后顺序
     * </p>
     * 
     * @param nextRecord 下一个播放记录对象
     */
    public void setNextRecord(PlayRecord nextRecord) {
        this.nextRecord = nextRecord;
    }
}
