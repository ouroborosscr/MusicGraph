package com.songmap.songmap.controller;

import com.songmap.songmap.dto.GraphDataDTO;
import com.songmap.songmap.entity.GraphInfo;
import com.songmap.songmap.service.GraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    // 获取当前用户的所有图谱
    @GetMapping("/list")
    public List<GraphInfo> listGraphs(@RequestAttribute("currentUserId") Long userId) {
        return graphService.getUserGraphs(userId);
    }

    // 创建图谱
    // POST /api/graph/create?type=empty&name=我的新世界
    @PostMapping("/create")
    public GraphInfo createGraph(@RequestAttribute("currentUserId") Long userId,
                                 @RequestParam(defaultValue = "empty") String type,
                                 @RequestParam(required = false) String name) { // 【新增】name 参数
        return graphService.createGraph(userId, type, name);
    }

    // 删除图谱
    // DELETE /api/graph/delete/{id}
    @DeleteMapping("/delete/{id}")
    public String deleteGraph(@RequestAttribute("currentUserId") Long userId,
                              @PathVariable Long id) {
        graphService.deleteGraph(userId, id);
        return "Delete success";
    }

    // 获取图谱可视化数据
    // GET /api/graph/data/{id}
    @GetMapping("/data/{id}")
    public GraphDataDTO getGraphData(@RequestAttribute("currentUserId") Long userId,
                                     @PathVariable Long id) {
        return graphService.getGraphData(userId, id);
    }
}