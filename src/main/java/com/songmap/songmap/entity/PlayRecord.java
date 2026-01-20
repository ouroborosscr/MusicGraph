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

@Node("PlayRecord")
@Data
@NoArgsConstructor
public class PlayRecord {
    @Id @GeneratedValue // 自动生成 Long 类型的 ID
    private Long id;

    private LocalDateTime timestamp;

    // 【知识点 4】关系定义
    // 方向设为 OUTGOING (发出)，type 是边上的标签
    @Relationship(type = "PLAYS", direction = Relationship.Direction.OUTGOING)
    private Song song;
    

    @Relationship(type = "NEXT", direction = Relationship.Direction.OUTGOING)
    private PlayRecord nextRecord;

    public PlayRecord(Song song) {
        this.timestamp = LocalDateTime.now();
        this.song = song;
    }

    // Setter for nextRecord
    public void setNextRecord(PlayRecord nextRecord) {
        this.nextRecord = nextRecord;
    }
}
