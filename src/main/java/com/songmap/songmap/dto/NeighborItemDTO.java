package com.songmap.songmap.dto;

import com.songmap.songmap.entity.Song;
import lombok.Data;
import java.util.Map;

@Data
public class NeighborItemDTO {
    private String direction; // "OUT" or "IN"
    private Map<String, Object> edge; // 边属性
    private Song node; // 邻居节点
}