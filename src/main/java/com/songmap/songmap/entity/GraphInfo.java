package com.songmap.songmap.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

/**
 * 图谱元数据节点
 * 记录用户创建的每一个“独立宇宙”的信息
 */
@Node("GraphInfo")
@Data
@NoArgsConstructor
public class GraphInfo {
    @Id @GeneratedValue
    private Long id;

    // 图谱显示的名称（如 "周杰伦的音乐宇宙"）
    private String name;

    // 【核心】该图谱专用的动态 Label (如 "G_u10_t170888")
    // 后续在这个图里加歌时，都要带上这个 Label
    private String nodeLabel;

    // 图谱类型：EMPTY(空), TEMPLATE(模板)
    private String type;

    // 封面颜色（为了前端好看，存一个 CSS 渐变色字符串）
    private String coverColor;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GraphInfo(String name, String nodeLabel, String type, String coverColor) {
        this.name = name;
        this.nodeLabel = nodeLabel;
        this.type = type;
        this.coverColor = coverColor;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}