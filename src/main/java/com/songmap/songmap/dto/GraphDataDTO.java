package com.songmap.songmap.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphDataDTO {
    private List<NodeData> nodes;
    private List<LinkData> links;

    @Data
    @AllArgsConstructor
    public static class NodeData {
        private String id;       // ECharts 最好用 String ID
        private String name;     // 显示名称
        private String artist;   // 作者
        private int symbolSize;  // 节点大小 (根据热度)
        private int category;    // 分类 (0: 普通, 1: 热门)
        // 可以加更多属性给 tooltip 展示
    }

    @Data
    @AllArgsConstructor
    public static class LinkData {
        private String source;   // 起点 ID
        private String target;   // 终点 ID
        private int value;       // 边权重 (跳转次数)
    }
}