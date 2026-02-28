package com.songmap.songmap.controller;

import com.songmap.songmap.dto.ScoredSongDTO;
import com.songmap.songmap.entity.Song;
import com.songmap.songmap.service.MusicGraphService;
import com.songmap.songmap.service.MusicHistoryService;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.songmap.songmap.entity.User;
import com.songmap.songmap.repository.UserRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 音乐控制器类，处理与音乐相关的 HTTP 请求
 * <p>
 * 该控制器提供了听歌记录和数据填充的 API 接口，
 * 负责接收和处理客户端的音乐相关请求，并调用音乐图服务进行业务处理。
 * </p>
 * 
 * @author SongMap Team
 * @since 1.0.0
 */
@Slf4j // 【新增】自动生成 log 对象
@RestController // 声明这是一个处理 HTTP 请求的控制器，自动返回 JSON 格式数据
@RequestMapping("/api/music") // 所有接口前缀，定义基础路径
public class MusicController {
    /**
     * 音乐图服务实例，用于处理音乐相关的业务逻辑
     */
    private final MusicGraphService musicService;
    private final MusicHistoryService historyService;
    private final UserRepository userRepository; // 【新增】
    // 【新增】定义 QQMusicApi 地址
    private static final String QQ_API_BASE = "http://localhost:3300";

    /**
     * 构造函数，通过依赖注入获取音乐图服务实例
     * 
     * @param musicService 音乐图服务实例
     */
    public MusicController(MusicGraphService musicService, 
                           MusicHistoryService historyService,
                           UserRepository userRepository) { // 【修改】构造函数
        this.musicService = musicService;
        this.historyService = historyService;
        this.userRepository = userRepository;
    }

    // 2. 升级版听歌接口
    // POST /api/music/listen?name=夜曲&artist=周杰伦&isFullPlay=true
    @PostMapping("/listen")
    public Song listen(@RequestAttribute("currentUserId") Long userId,
                       @RequestParam Long graphId, // 【新增】必传
                       @RequestParam String name,
                       @RequestParam(required = false, defaultValue = "Unknown") String artist,
                       @RequestParam(defaultValue = "false") boolean isRandom,
                       @RequestParam(defaultValue = "true") boolean isFullPlay,
                       @RequestParam(defaultValue = "false") boolean isSkip) {
        log.info("用户 {} 在图谱 {} 听歌: {}", userId, graphId, name);
        // forceNew 默认为 false
        return musicService.addSong(userId, graphId, name, artist, false, isRandom, isFullPlay, isSkip);
    }

    @GetMapping("/recommend")
    public List<ScoredSongDTO> recommend(@RequestParam Long currentId) {
        // 从 Redis 获取上一首 ID，用于判断“回头路”
        Long lastId = historyService.getLastListenedSongId();
        // 注意：这里 historyService 取到的是 *当前* 这首（因为刚 listen 完存进去了）
        // 实际上如果你想避开的是“前一首”，可能需要取 history list 的第 2 个元素
        // 简单起见，我们暂且认为 lastId 就是要避开的
        
        // 更好的逻辑：前端传 currentId，我们去 history 查 "latest" 其实是 current，
        // 这里的 lastId 应该是 history.get(1)
        List<String> history = historyService.getHistory();
        Long previousId = null;
        if (history.size() >= 2) {
             // 解析 history[1] 获取 ID
             String[] parts = history.get(1).split("::");
             previousId = Long.valueOf(parts[0]);
        }

        return musicService.recommendNextSongs(currentId, previousId);
    }

    // ================= 查询接口 =================

    /**
     * 查询点
     * GET /api/music/query/node?id=10
     * GET /api/music/query/node?name=夜曲&artist=周杰伦&detail=true
     */
    @GetMapping("/query/node")
    public Object queryNode(@RequestParam(required = false) Long id,
                            @RequestParam(required = false) String name,
                            @RequestParam(required = false) String artist,
                            @RequestParam(defaultValue = "false") boolean detail) {
        return musicService.queryNode(id, name, artist, detail);
    }

    /**
     * 查询边
     * GET /api/music/query/edge?id=55
     * GET /api/music/query/edge?from=夜曲&to=七里香&detail=true
     */
    @GetMapping("/query/edge")
    public Object queryEdge(@RequestParam(required = false) Long id,
                            @RequestParam(required = false, name = "from") String fromName,
                            @RequestParam(required = false, name = "to") String toName,
                            @RequestParam(defaultValue = "false") boolean detail) {
        return musicService.queryEdge(id, fromName, toName, detail);
    }

    // /**
    //  * 处理听歌请求，记录用户听歌行为
    //  * <p>
    //  * 当用户听歌时，调用音乐图服务添加歌曲并记录播放记录
    //  * </p>
    //  * 
    //  * @param name 歌曲名称
    //  * @param forceNew 是否强制创建新的歌曲节点，默认为 false（查找现有歌曲）
    //  * @return 创建或找到的歌曲对象
    //  * 
    //  * @apiNote 请求示例：POST /api/music/listen?name=七里香&forceNew=false
    //  */
    // @PostMapping("/listen") // 处理 POST 请求，路径为 /api/music/listen
    // public Song listen(@RequestParam String name, // 歌曲名称参数
    //                    @RequestParam(defaultValue = "false") boolean forceNew) { // 是否强制创建新歌曲，默认为 false
    //     return musicService.addSong(name, forceNew);
    // }

    /**
     * 删除指定两首歌之间的关系
     * 请求示例：DELETE /api/music/delete?firstname=七里香&nextname=半岛铁盒
     */
    @RequestMapping("/delete") 
    public String deleteConnection(@RequestParam("firstname") String firstName, 
                                   @RequestParam("nextname") String nextName) {
        musicService.deleteConnection(firstName, nextName);
        return "尝试删除连接: " + firstName + " -> " + nextName;
    }
    /**
     * 删除指定歌曲（按名称）
     * DELETE /api/music/node/delete?graphId=10&name=夜曲
     */
    @DeleteMapping("/node/delete") // 建议明确使用 DeleteMapping
    public String deleteNode(@RequestAttribute("currentUserId") Long userId,
                             @RequestParam Long graphId,
                             @RequestParam String name) { // 参数改为 name
        musicService.deleteNode(userId, graphId, name);
        return "成功删除歌曲: " + name;
    }

    /**
     * 【升级版】独立听歌（不连接上一首）
     * 场景：强制开启新的一天，或者不想和上一首产生关联。
     * 逻辑：复用 addSongV2 的全套统计逻辑（加属性、作者、更新Redis），但强制 forceNewChain=true
     *
     * 请求示例：POST /api/music/newlisten?name=夜曲&artist=周杰伦&isFullPlay=true
     */
    @PostMapping("/newlisten")
    public Song newListen(@RequestAttribute("currentUserId") Long userId,
                          @RequestParam Long graphId, // 【新增】必传
                          @RequestParam String name,
                          @RequestParam(required = false, defaultValue = "Unknown") String artist,
                          @RequestParam(defaultValue = "false") boolean isRandom,
                          @RequestParam(defaultValue = "true") boolean isFullPlay,
                          @RequestParam(defaultValue = "false") boolean isSkip) {
        log.info("用户 {} 在图谱 {} 新开听歌: {}", userId, graphId, name);
        // forceNew = true
        return musicService.addSong(userId, graphId, name, artist, true, isRandom, isFullPlay, isSkip);
    }
    
    /**
     * 预留接口：填充 API 数据
     * <p>
     * 该接口用于通过第三方 API（如网易云音乐、腾讯音乐）获取歌曲的详细信息，
     * 目前仅返回 TODO 提示，后续需要实现具体功能。
     * </p>
     * 
     * @param songId 歌曲 ID
     * @return 提示信息，包含歌曲 ID
     * 
     * @apiNote 请求示例：POST /api/music/enrich/1
     */
    @PostMapping("/enrich/{songId}") // 处理 POST 请求，路径为 /api/music/enrich/{songId}
    public String enrichData(@PathVariable Long songId) { // 从路径中获取歌曲 ID 参数
        return "TODO: Call NetEase/Tencent API for song " + songId;
    }

    /**
     * 获取详细的听歌历史
     * 返回示例：[{"id": "10", "name": "七里香"}, {"id": "8", "name": "夜曲"}]
     */
    @GetMapping("/listenhistory")
    public List<Map<String, String>> getListenHistory(@RequestParam Long graphId) {
        // 历史记录也需要带 graphId
        return historyService.getStructuredHistory(graphId);
    }

    /**
     * 1. 添加点属性
     * POST /api/music/property/node/add?key=mood&type=string&value=happy
     */
    @PostMapping("/property/node/add")
    public String addNodeProperty(@RequestParam String key,
                                  @RequestParam String type,
                                  @RequestParam(required = false) String value) {
        musicService.addNodeProperty(key, type, value);
        return "成功给所有节点添加属性: " + key + " = " + value;
    }

    /**
     * 2. 删除点属性
     * POST /api/music/property/node/delete?key=mood
     */
    @PostMapping("/property/node/delete")
    public String removeNodeProperty(@RequestParam String key) {
        musicService.removeNodeProperty(key);
        return "成功删除所有节点的属性: " + key;
    }

    /**
     * 3. 添加边属性
     * POST /api/music/property/edge/add?key=weight&type=int&value=1
     */
    @PostMapping("/property/edge/add")
    public String addEdgeProperty(@RequestParam String key,
                                  @RequestParam String type,
                                  @RequestParam(required = false) String value) {
        musicService.addEdgeProperty(key, type, value);
        return "成功给所有边添加属性: " + key + " = " + value;
    }

    /**
     * 4. 删除边属性
     * POST /api/music/property/edge/delete?key=weight
     */
    @PostMapping("/property/edge/delete")
    public String removeEdgeProperty(@RequestParam String key) {
        musicService.removeEdgeProperty(key);
        return "成功删除所有边的属性: " + key;
    }

    /**
     * 4. 歌曲搜索 (透传 Cookie)
     * GET /api/music/search?key=周杰伦
     */
    @GetMapping("/search")
    public String searchSong(@RequestAttribute("currentUserId") Long userId, 
                             @RequestParam String key) {
        // 1. 获取用户 Cookie
        String cookie = getUserCookie(userId);
        
        // 2. 构造请求
        String url = QQ_API_BASE + "/search?key=" + key;
        return proxyGetRequest(url, cookie);
    }

    /**
     * 5. 获取播放链接 (透传 Cookie)
     * GET /api/music/song/urls?id=0039MnYb0qxYhV
     */
    @GetMapping("/song/urls")
    public String getSongUrls(@RequestAttribute("currentUserId") Long userId, 
                              @RequestParam String id) {
        // 1. 获取用户 Cookie
        String cookie = getUserCookie(userId);
        
        // 2. 构造请求
        String url = QQ_API_BASE + "/song/urls?id=" + id;
        return proxyGetRequest(url, cookie);
    }

    // --- 辅助方法 ---

    private String getUserCookie(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        return (user != null) ? user.getQqCookie() : null;
    }

    private String proxyGetRequest(String url, String cookie) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        
        // 如果有 Cookie，则添加到请求头
        if (cookie != null && !cookie.isEmpty()) {
            headers.add("Cookie", cookie);
        }
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("QQMusicApi call failed: {}", e.getMessage());
            return "{\"code\": 500, \"msg\": \"调用第三方接口失败\"}";
        }
    }

    // 1. 初始化数据的接口（跑一次就行）
    @PostMapping("/init-data")
    public String initData() {
        return musicService.initVersionUpdate();
    }
}
