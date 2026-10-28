# SQ Music Listening Room（维护分支）

基于 [SQMusic](https://github.com/59799517/simple_sq_music_plus) 与其 Web 客户端维护，面向自托管 NAS 的播放、音乐下载、歌单同步与曲库整理。

本分支新增：桌面与手机浏览器听歌界面、持续播放队列、收藏、同步歌词、浏览器媒体按键、本地音乐索引与支持 Range 的音频读取；修复 QQ 授权连接偶发失败时后台线程中断。

- 前端：[simple_sq_music_plus_web](https://github.com/liuwenxi163/simple_sq_music_plus_web)
- 服务端：[simple_sq_music_plus](https://github.com/liuwenxi163/simple_sq_music_plus)
- 手机端目前为响应式 Web/PWA，不是原生手机应用。后台播放与媒体按键取决于浏览器和系统限制；PWA 安装通常需要 HTTPS。
- 在线音源仍受平台账号权限、链接有效期、跨域及浏览器编解码支持影响。APE/WMA 不直接在浏览器播放。
- LyricFlow 独立服务基于 [laoning666/lyricFlow](https://github.com/laoning666/lyricFlow)，固定提交 ef2aef4a615f03e66d9942b6f6ec2d061405befa，遵循 **CC BY-NC 4.0，仅限非商业使用**。发布包保留原始许可；不能将整套集成声称为可任意商用。

## 持久化与配置

保留现有 MySQL 数据目录与音乐目录。后端 `SQ_DATA_DIR` 挂载独立可写目录，保存收藏与听歌状态；`JWT_SECRET` 至少 32 字节，存入私有环境文件。LyricFlow 挂载同一音乐目录到 `/music`，独立 `/state` 保存任务，后端与 worker 使用相同 `LYRICFLOW_KEY`（至少 32 字符），通过内部 `LYRICFLOW_URL` 通信，不公开 worker 端口。

默认只补全缺少的 .lrc 和 cover.jpg，不覆盖既有侧车文件；勾选写入文件时只补齐缺失标签。整理任务串行执行，等待加运行合计最多 256 个；重启后未完成任务标为已中断，可在界面重试。曲库扫描同时最多 1 次，最多索引 100,000 首，未请求的重复扫描合并。

## 账号歌单与下载

QQ 账号收藏同步在 NAS 服务内每 10 分钟检查一次，可在「我的歌单」启停、关闭自动下载或手动立即同步。同步账号的我喜欢、自建歌单和收藏歌单，只读取 QQ，不向 QQ 写入本地修改。本地歌单与远端快照独立保存。远端数据与设置位于 `SQ_DATA_DIR/qq-collection.json`；不要把该运行数据提交到 GitHub。

每个服务实例最多运行 1 次账号同步，重复触发合并，不创建额外等待队列。新增歌曲按下载设置加入原有 MySQL 下载队列，跨歌单按歌曲和音质去重；已有低音质记录可补充更高音质，失败记录由下载页重试。同步上限 200 个歌单、单歌单 5000 首、共 50000 条歌单歌曲引用，单轮读取时间上限 5 分钟；失败或超限保留上次成功快照并显示错误。账号歌单不会因取消收藏而删除 NAS 文件。

下载页读取实际字节回调显示进度、大小、平均速度和标签整理阶段，未知总大小时显示不定进度。已下载从音乐目录索引读取，完成历史保存在 MySQL。实时字节计数在重启后重建，历史不依赖这些内存计数。

接口（均需 `sqmusic` 登录头）：`GET /api/listening/downloads?tab=active&page=1` 支持 active/completed/failed/all，页大小 50，返回 items/total/page/pageSize，条目含 progress（bytes/total/percent/bytesPerSecond/stage）；总大小未知时 percent 为 null，只有 MySQL 确认完成才为 100。`GET /api/listening/collection` 返回歌单及歌曲快照；`GET /api/listening/collection/status` 返回不含歌曲的轻量状态；`PUT /api/listening/collection` 接受 enabled/autoDownload 布尔值；`POST /api/listening/collection/sync` 合并触发同步并立即返回状态。异步错误在 error 字段报告 QQ_LOGIN_REQUIRED、QQ_NETWORK_UNAVAILABLE、QQ_COLLECTION_UNAVAILABLE、QQ_COLLECTION_INCOMPLETE 或 COLLECTION_LIMIT_REACHED，lastSuccess 表示上次完整快照时间。

## 发布

本分支的 Actions 替代上游镜像仓库密钥。推送维护分支会打包、验证并创建以提交 SHA 命名的不可变 Release 包。镜像基于固定摘要的上游运行时，在 NAS 离线构建增量层（后端使用缓存的固定 JDK 与依赖编译，并执行 QQ 故障测试）；音乐、数据库和状态均位于外部挂载目录。

可选自动部署：配置仓库变量 `NAS_GITOPS_REPO` 与仅能写该配置仓库的部署密钥 `NAS_GITOPS_KEY`。Actions 将 Release URL、SHA256 与镜像标签写入已有 sqmusic Stack；NAS 拉取控制器校验摘要、预构建全部镜像，再替换服务并健康检查。初始化时必须先配置服务、挂载与私有环境文件。构建或检查失败不发布成功状态；健康失败恢复上一份 Compose。

## 原项目文档

以下保留上游说明和署名，部分原始发布地址仅适用于上游版本。

---


### 出现问题先升级到最新版本
### simple_sq_music_plus
是下载音乐工具，可以当普通的音乐下载工具使用，支持，flac，ape，mp3等格式的下载（根据码率不同）， 下载的歌曲目录结构支持emby 与 subsonic（后续开放） 类的服务，下载文件支持文件标签识别，歌词下载。
```js
\Music下载根路径
       \歌手名称
               \专辑名称
                       1- 歌曲1.flac
                       2- 歌曲2.flac

```
默认支持群辉等第三方音乐服务标识：
emby,jellyfin识别请参考如下配置 https://support.emby.media/support/solutions/articles/44001159113-music-naming

- 默认账号：admin
- 默认密码：admin  （登录后设置自行修改）




效果截图
![wechat_2025-09-28_160749_034.png](img/wechat_2025-09-28_160749_034.png)
![wechat_2025-09-28_160832_175.png](img/wechat_2025-09-28_160832_175.png)
![wechat_2025-09-28_160855_350.png](img/wechat_2025-09-28_160855_350.png)
![wechat_2025-09-28_160921_626.png](img/wechat_2025-09-28_160921_626.png)
![wechat_2025-09-28_160933_756.png](img/wechat_2025-09-28_160933_756.png)
![wechat_2025-09-28_161030_965.png](img/wechat_2025-09-28_161030_965.png)
![wechat_2025-09-28_161133_048.png](img/wechat_2025-09-28_161133_048.png)
![wechat_2025-09-28_161212_579.png](img/wechat_2025-09-28_161212_579.png)
![wechat_2025-09-28_161306_157.png](img/wechat_2025-09-28_161306_157.png)
![wechat_2025-09-28_161505_837.png](img/wechat_2025-09-28_161505_837.png)
![wechat_2025-09-28_161552_590.png](img/wechat_2025-09-28_161552_590.png)


#### 接口文档：
https://59799517.github.io/simple_sq_music_plus/#/README
#### MCP
```agsl
- 访问地址：
- http(s)://IP:端口/mcp
- 请求头：
- sqmusic=Token(设置下方的token值)
```




### 运行项目
#### 1. docker-compose（推荐--mysql启动慢导致报错可以多运行几次）
    运行docker-compose文件即可（本地编译使用docker-compose-local）
#### 2.docker启动请参考docker-compose配置手动启动
1. 启动mysql
```dockerfile
# 拉取 MySQL 5.7 镜像
docker pull mysql:5.7

# 创建自定义网络
docker network create sq-app-network

# 运行 MySQL 容器
docker run -d \
  --name sqmusic_mysql \
  --restart=always \
  -e MYSQL_ROOT_PASSWORD=sqmusicv3password \
  -e MYSQL_DATABASE=sqmusicv3 \
  -v ./mysql_data:/var/lib/mysql \
  -p 3306:3306 \
  --network simple_sq_music_plus_sq-app-network \
  mysql:5.8
```
2. 启动后端服务
```dockerfile
# 拉取后端服务镜像（使用最新版本号）
docker pull registry.cn-hangzhou.aliyuncs.com/sqdockler/simple_sq_music_plus:v3.0.8

# 运行后端容器
docker run -d \
  --name sqmusic_main \
  --restart=always \
  -e DB_IP=mysql \
  -e DB_PORT=3306 \
  -e DB_NAME=sqmusicv3 \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=sqmusicv3password \
  -v ./music:/music \
  --network simple_sq_music_plus_sq-app-network \
  registry.cn-hangzhou.aliyuncs.com/sqdockler/simple_sq_music_plus:latest
```
3. 启动前段服务
```dockerfile
# 拉取前端服务镜像（使用最新版本号）
docker pull registry.cn-hangzhou.aliyuncs.com/sqdockler/simple_sq_music_plus_web:v3.0.5

# 运行前端容器
docker run -d \
  --name sqmusic_web \
  --restart=always \
  -p 8996:80 \
  --network simple_sq_music_plus_sq-app-network \
  registry.cn-hangzhou.aliyuncs.com/sqdockler/simple_sq_music_plus_web:latest

```

Todo：
1. ~~页面增加播放页面播放删除~~
2. 支持苹果音乐（只能做到mp3 暂时不弄了  没无损无意义）
3. ~~增加网易云监听歌单~~
4. ~~增加指定格式下载~~
5. ~~增加当前下载速度展示~~
6. 增加下载暂停功能
7. ~~手机模式下专辑和歌手不展示问题~~
8. ~~增加阿里云盘同步~~
9. ~~增加QQ音乐超限制暂停~~
10. ~~增加咪咕音乐~~ 
11. ~~增加api文档~~
12. ~~下载失败使用其他插件进行下载~~
13. ~~综合查询下载不根据单一插件下载查询~~
14. ~~增加下载超时重试功能（设置增加重试间隔时间）~~
15. ~~根据条件自定义处理下载功能（删除重试等）~~
16. ~~增加内置网易云功能~~

下载模板支持（3.1.15版本后取消el表达式支持-有安全风险需要提出issues）
描述：目前支持
1. ${musicName}---歌曲名称
2. ${artists}---歌手名称（全部歌手）
3. ${album}---专辑名称
4. ${albumId}---专辑ID
5. ${artistsId}---歌手id
6. ${artist}------主要歌手（只有1个）
7. ${albumTime}---------专辑时间（2020-05-21）
8. ${albumYear}---------专辑年份（2020）
9. ${albumMonth}---------专辑月份（05）
10. ${albumDay}---------专辑日（21）
```
默认为${artists}/${album}/${musicName} - ${artists}---/歌手/专辑/歌曲名称 - 歌手名称
${album}/${musicName} - ${artists}  ------------专辑/歌曲名称 - 歌手名称```
示例：
-----------------------------
模板：${artists}/${album}/${musicName} - ${artists}
歌手目录: 周杰伦
专辑目录: 周杰伦\范特西
歌曲目录: 周杰伦\范特西\双截棍 - 周杰伦.flac
-----------------------------
模板：${album}/${musicName} - ${artists}
歌手目录: null
专辑目录: 范特西
歌曲目录: 范特西\双截棍 - 周杰伦.flac
-----------------------------
模板：/自定义文件1/${artists}/自定义文件夹2/${album}/${musicName}/自定义文件3/${musicName} - ${artists}
歌手目录: 自定义文件1\周杰伦
专辑目录: 自定义文件1\周杰伦\自定义文件夹2\范特西
歌曲目录: 自定义文件1\周杰伦\自定义文件夹2\范特西\双截棍\自定义文件3\双截棍 - 周杰伦.flac
```

[//]: # (高级支持SpEL表达式具体百度）)



感谢：
1. https://github.com/MakcRe/KuGouMusicApi  酷狗API
2. https://music.gdstudio.org  网易API下载支持



### 2.x迁移3.x版本
1. 导出已经同步过的歌单、专辑、歌手信息（文件是.json）
   ![img.png](img/img.png)
2. 3.0版本导入已经同步信息(时间较长耐心等待)
   ![wechat_2025-09-28_163226_700.png](img/wechat_2025-09-28_163226_700.png)


   # 免责声明

本音乐下载工具**仅用于个人技术学习、代码研究与技术测试目的**，仅供学习交流使用。

1. 工具使用者知悉：音乐作品受著作权法保护，未经版权方授权，不得私自下载、复制、传播、商用相关音频文件。
2. 若您使用本工具获取音频资源，请**仅限 24 小时内完成学习测试，并在 24 小时内彻底删除所下载的全部文件**。若超过时限继续留存，相关责任由使用者自行承担。
3. 使用者承诺，不会将本工具及下载得到的音乐资源用于商业用途、公开分享、二次分发等任何侵犯知识产权的行为。
4. 本工具作者不对使用者的任何使用行为承担法律责任，如使用者违反版权相关法律法规产生纠纷、赔偿、行政处罚等一切后果，均由使用者本人独立承担。
5. 如果您不同意本免责声明，请立即停止使用本工具。

