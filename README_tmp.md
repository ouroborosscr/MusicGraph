# 使用：
```shell
neo4j console
& 'D:\code-Drivers\java\jdk-17.0.14\bin\java.exe' '@C:\Users\OUROBO~1\AppData\Local\Temp\cp_179ypy74ixg2h4d1n163xuv3n.argfile' 'com.songmap.songmap.SongmapApplication' 
```


# 访问：
```
http://localhost:7474/browser/
```

存内容：
```
http://localhost:8080/api/music/listen?name=稻香
```


已开发：
```log
POST /api/music/listen?name=稻香   如果不存在节点则创建节点。如果同一天内访问过节点，则让前一节点指向当前节点（不会出现自环）。记录当前节点。
POST /api/music/delete?firstname=1&nextname=2    删除firstname节点到lastname节点的关系，但不删除节点。
POST /api/music/newlisten    如果不存在节点则创建节点。记录当前节点。（不和之前访问节点产生关系）
2026.2.4:
将历史歌曲存储从查询图改为redis，使用LRU记录最近听的10首歌。
2026.2.4 16:58:
查询redis中的听歌历史,在以下两个方案中选择了方案1：
因为歌曲分为id和歌曲名，从图中查询使用id更方便，但是存在需要歌曲名的场景，如何存储id和歌曲名出了问题，两个解决方案：
方案1. redis的list内存储内容是“id::name”，java读取后进行分离。
方案2. redis存两个list，分别是id和name，同时读取两个list，用下标分别访问。
gemini给的建议是方案1，因为方案2需要同时读取两个list，分开存储是给自己挖坑，特别是分布式场景（缺乏原子性）。而且JavaString.split("::") 在内存中处理，耗时是纳秒级（ns）的。对于 10 条数据，这种开销几乎可以忽略不计。Redis取两次就要考虑网络延迟（ms级）。
GET /api/music/listenhistory    查询最近听的歌。
2026.2.4 18:00:
批量添加和删除点和边的属性功能：1.添加点的属性：给所有点添加一个属性，POST传参有三个，第三个可选，第一个传参是属性名，第二个传参是数据类型，第三个传参是这次添加的赋值，如果没有就为空；2.删除点的属性：只有一个传参是属性名；3.添加边的属性；4.删除边的属性。
POST /api/music/property/node/add?key=emotion&type=string&value=Happy 效果：现在数据库里所有的歌曲节点都有了一个 emotion: "Happy" 的属性。
POST /api/music/property/node/delete?key=emotion 删除所有边的emotion属性
POST /api/music/property/edge/add?key=count&type=int&value=1。 查看：去 Neo4j 浏览器运行 MATCH ()-[r:NEXT]->() RETURN r。
POST /api/music/property/node/delete?key=count 删除所有点的count属性
2026.2.4 18:46:
处理歌曲同名的问题：（暂时）添加作者名。以后尝试接入各播放器api后使用它们的api。
点添加：1.作者名 2.听歌次数 3.完播次数 4.快速跳转次数 5.主动选择次数 6.被随机选中次数
边添加：1.跳转次数 2.主动选择次数 3.被随机选中次数
因为版本迭代，以前的点和边没有这些属性，需要一次迭代初始化：作者名为空，每一条线的跳转次数都为1，都是主动选择次数，在这个基础上计算点的听歌次数和主动选择次数（入度），每次都完播。
POST /api/music/init-data   （一次性）数据清洗
POST /listen?name=稻香&artist=周杰伦&isFullPlay=true    听歌接口，记录用户听歌行为。只有name必须填，其余都可缺省
POST /newlisten?name=稻香&artist=周杰伦&isFullPlay=true    添加新的歌接口，记录用户听歌行为。只有name必须填，其余都可缺省
2026.2.4 18:55:
处理笛卡尔积问题，先查id，防止比对每一个键
```

开发中：
```
添加查询功能：
1.使用id查询点
2.使用name查询点
3.使用id查询边
4.使用frontname和nextname查询边
查询点返回的信息粒度分为两种：
1.点本身的属性
2.分别查点本身的属性/点的出边的属性集/点的入边的属性集/点的出边的目标点的属性集/点的入边的源点的属性集。
查询边分为两种：
1.边本身的属性
2.边本身属性/源点属性/目标点属性
反查功能：点可以查询指向自己的点（因为图上的单项指针在数据结构上是双向指针，所以正向查询和反向查询的时间复杂度相同，不用强制建立反向边）
查询点 - 粒度 1 (Basic) 请求: GET /api/music/query/node?name=七里香
                            GET /api/music/query/edge?id=55
查询点 - 粒度 2 (Detailed) 请求: GET /api/music/query/node?name=七里香&detail=true
                            GET /api/music/query/edge?id=55&detail=true
查询边 - 粒度 1 (Basic) 请求: GET /api/music/query/edge?from=七里香&to=江南
查询边 - 粒度 2 (Detailed) 请求: GET /api/music/query/edge?from=七里香&to=江南&detail=true
```

待开发：
```



打分功能：
加分项：点的主动选中率，边的主动选中率，边的正向，完播率

设计思路是：
权重升高：主动选择（主动选中次数）（说明近期很喜欢），上次播放时间较长（保证新鲜感）。
权重降低：随机选中（被随机选中次数）（听多了就不喜欢了）
优先正向，其次反向，避免返回同一个（权重最低，比随机跳到别的歌还低）
并不是严格的阶梯关系，比如正向的随即次数过多会导致权重不如反向。
接入api后考虑“完播率”和“快速跳转率（不喜欢）”，（现在暂时不考虑）主要的区别是是否听完副歌。听完基本能确定喜欢，不喜欢则会快速跳转。也要避免有些副歌是戛然而止的，这会导致权重过高。
进一步的，还可以统计每条边的跳转次数（综合边权点权）
最后实现打分

随机播放系统
1.分池
2.历史上歌的打分也入池
3.部分随机歌曲也会入池

调用api
```


自己的曲库：
已完成：
周杰伦、林俊杰

未完成：
许嵩、陈奕迅、罗大佑、李宗盛、庾澄庆、张信哲、小虎队、beyond、张学友、张宇、王菲、王力宏、梁静茹、孙燕姿、徐良、汪苏泷