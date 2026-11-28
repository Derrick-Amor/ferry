# 渡口 Ferry · 项目交接文档

> 写给接手的新对话。读完这份，你应该能在不看历史聊天记录的情况下继续推进。
> 最后更新：2026-11-28 01:25

---

## 一、这是什么

**渡口 Ferry**，一个学习与求职平台。学员买课、学习、写简历、投岗位；讲师/企业上架课程与职位；管理员做审核。

它原本是一个课程作业（原名「智慧教育平台」），正在被重构成可以公开阅读、一条命令跑起来的工程项目，**用于求职作品集**。

- 仓库：https://github.com/Derrick-Amor/ferry
- 本地：`C:\Users\D.Amor\Desktop\智慧教育平台`
- 作者：Amor（Derrick-Amor），大四软件工程，2027 年毕业

**核心差异化**：不是"又一个 CRUD 系统"，而是要在其中做出**带引用溯源的 RAG 智能助教**。这是简历上真正值钱的部分。

---

## 二、当前进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| S0 | monorepo 重组、可重建 DDL、密钥外置、隐私清洗 | ✅ 已推送 |
| S1-A | Java 21、密码脱敏、5 处事务、删死代码 | ✅ 已推送 |
| S1-B | 8 个 Service 接口化（51 个方法） | ✅ 已推送 |
| S1-C | Docker 三服务编排 + 品牌统一 | ✅ 已推送并实地跑通 |
| S2-A | Redis Stack + Spring AI + 知识库两张表 | ✅ 已推送 |
| S2-B | 37 章正文生成 + 分块向量化入库（**165 块**） | ✅ 已推送 |
| S2-C | 智能助教：学习答疑（生成式）+ 岗位咨询（RAG，含引用）+ 前端 | ✅ 已实现，待提交 |
| S3 | 模拟面试改造（接 LLM 动态出题） | ⏳ 待做 |
| S4 | JUnit 单测、慢 SQL、Redis 缓存、压测对比 | ⏳ 待做 |
| S5 | README 精修、演示 GIF、GitHub Actions | ⏳ 待做 |

**提交记录**（S2-C 未提交；下面是截至 S2-B 的已推送记录）：

```
38fea27 docs: 交接文档补充 S2-B 成果与 Spring AI metadata 坑
b375fbd feat(rag): 生成 37 章课程正文，完成分块向量化入库
78df439 feat(rag): 接入 Redis 向量库与 Spring AI，建立知识库表结构
25d7533 chore(ai): 接入硅基流动，声明 RAG 所需的三类模型配置
b3b9b36 fix(sql): 修正 data.sql 密码哈希与注释不符的问题
160474c fix(docker): 修复容器内中文乱码与构建期网络阻断，端到端跑通
ec2c53b refactor: 品牌统一为「渡口 Ferry」
b6ef591 chore(docker): 三服务编排，一条命令拉起全栈
b3be3df refactor: 8 个 Service 接口化，补齐 MVC 三层规范
8f1833a feat: 升级 Java 21，封堵密码泄漏，补齐核心事务
b9b10f7 chore: 重组为 monorepo 并完成 S0 止血
```

---

## 三、技术栈（已定，不要改）

| 层 | 选型 |
|---|---|
| 后端 | Java **21**、Spring Boot 3.3.4、MyBatis-Plus 3.5.8、Maven |
| 前端 | Vue 3.4（script setup）、Vite 5、Pinia、Vue Router 4 |
| 数据库 | MySQL 8.0，**17 张表** / 24 外键 / 26 CHECK / 43+ 索引 |
| 向量库 | Redis Stack（`redis/redis-stack-server`），索引 `ferry-knowledge`，dim=1024 HNSW COSINE |
| AI | Spring AI **1.0.0** + 硅基流动 |
| 部署 | Docker Compose（4 容器：mysql / redis / backend / frontend） |
| 测试 | pytest 196 条接口用例、Selenium、JMeter |

**模型选型（全部免费额度内，已实测可用）**：

| 用途 | 模型 | 实测 |
|---|---|---|
| 向量 | `BAAI/bge-m3` | 1024 维，11 tokens/短句 |
| 对话（当前） | `Qwen/Qwen2.5-7B-Instruct` | 非推理模型，首字 2~4s；答案质量主要取决于检索，生成侧只需忠实转述 |
| 对话（备选） | `XingChenAGI/Xing4.0-29B` | 质量更好但极慢（62s/章）；**推理模型，max_tokens 须 ≥3000** |
| 重排 | `BAAI/bge-reranker-v2-m3` | 相关 0.99 / 相邻话题 0.1~0.35 / 无关 0.002，兼作拒答阈值 |

平台：**硅基流动主站** `https://api.siliconflow.cn`（不是 TokenPlus）。Key 在 `.env` 的 `SILICONFLOW_API_KEY`，已实名认证。

---

## 四、怎么跑起来

```bash
cd "C:/Users/D.Amor/Desktop/智慧教育平台"

# Docker 方式（推荐）
cp .env.example .env     # 首次需要填 MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD / SILICONFLOW_API_KEY
docker compose up -d --build
docker compose ps        # 等 mysql / backend / redis 三个 healthy

# 访问
# http://localhost:8088
# 测试账号：admin / 123456（全部 8 个账号同口令）
```

**注意**：Docker CLI 不在 Git Bash 的 PATH 里，需要：

```bash
export PATH="/c/Users/D.Amor/AppData/Local/Programs/DockerDesktop/resources/bin:$PATH"
```

**本地开发方式**：`cd backend && mvn spring-boot:run`（需先 `set -a && source .env && set +a`）；`cd frontend && npm run dev`（5173 端口）。

---

## 五、踩过的坑（**接手必读，别再踩一遍**）

### 1. 控制台会把疑似口令脱敏替换

排查密码问题时，我用 curl 测试显示"123456 能登录"，但实际数据库里是 `123456`——**控制台把 `123456` 替换成了 `123456` 显示**，导致双方都被误导。

**规避方式**：用字符码构造口令（`''.join(map(chr, [55,48,48,50,49,52]))`），或用 Java 的 BCrypt 直接校验哈希。**不要相信输出里出现的明文口令**。

### 2. redis-stack 镜像不能覆盖 `command`

```yaml
command: ["redis-server", "--maxmemory", "512mb"]   # ❌ 会丢掉所有模块
environment:
  REDIS_ARGS: "--maxmemory 512mb --maxmemory-policy allkeys-lru"  # ✅
```

原因：模块（redisearch/rejson/bloom/gears）的 `--loadmodule` 写在 `/entrypoint.sh` 里，覆盖 `command` 等于绕过 entrypoint。**症状极隐蔽**：容器正常、healthcheck 正常，只有 `FT._LIST` 报 `unknown command`。

### 3. 容器 initdb 的客户端字符集是 latin1

MySQL 官方镜像在容器内执行 `schema.sql` / `data.sql` 时，因为没有 UTF-8 locale，`character_set_client` 是 latin1，中文会经历「latin1 解释 → utf8mb4 存储」的双重编码。

**修法**：两个 SQL 文件顶部都加了 `SET NAMES utf8mb4;`。别再删掉。

### 4. Maven Central 在国内会 403

`backend/settings.xml` 已配阿里云仓库，Dockerfile 会 COPY 到 `/root/.m2/settings.xml`。删掉会构建失败。

### 5. nginx 静态 upstream 会缓存 IP

原配置 `proxy_pass http://backend:8080` 只在启动时解析一次 DNS，backend 重建换 IP 后前端必 502。已改为 `resolver 127.0.0.11 + 变量形式 + 显式拼 $request_uri`，**不要改回静态写法**。

### 6. 主类包扫描曾用白名单

S2-A 时发现 `scanBasePackages` 是枚举式白名单，新建包不会被扫描。S2-B 已改为 `"seb"`。新增包时确认一下。

### 7. `data.sql` 里的密码哈希是手工生成的

改密码时**必须同时改**：`data.sql` 头部注释、8 条 INSERT 里的哈希、`.env` 相关项、宿主机本地库。改完必须 `docker compose down -v` 重建验证。

---

### 8. 向量索引没声明 metadata 字段，既读不到也过滤不了

S2-B 时的索引只有 `content` 与 `embedding` 两个属性。后果不只是
`similaritySearch()` 返回的 `Document.getMetadata()` 只有 `distance` / `vector_score`，
更关键的是 `filterExpression("courseId == 3")` **无法下推**——字段根本没进索引。

根因不是 Spring AI 的「行为」，而是自动配置建的 `RedisVectorStore` 没有 `metadataFields`。
S2-C 已改为自定义 Bean（`seb/ai/config/VectorStoreConfig`）显式声明
`docId / chunkId / courseId`（NUMERIC）与 `title`（TEXT）。

**迁移方式**（沿用旧数据卷时必须做一次）：
`FT.DROPINDEX ferry-knowledge`（**不带 DD**，JSON 数据保留）→ 重启后端，
`initializeSchema` 按新 schema 重建，已存在的 165 条 JSON 会自动重索引，
**不需要重新 embedding**，秒级完成。

即便如此，引用信息仍以 MySQL 为准：拿 `Document.getId()`（= chunkId）批量 `IN`
回查 `knowledge_chunk` + `knowledge_doc`，顺带过滤掉已禁用的文档。

### 9. `@ConditionalOnMissingBean` 按「Bean 方法的返回类型」匹配

自定义 `VectorStore` 时如果方法签名写 `public VectorStore vectorStore(...)`，
启动会直接 `BeanDefinitionOverrideException`。原因：
自动配置的方法返回 `RedisVectorStore`，条件按返回类型找 Bean，
只声明成 `VectorStore` 接口时它找不到，条件不成立，两边都要注册 `vectorStore` 这个名字。

**正确写法**：返回类型写 `RedisVectorStore`，方法名也别叫 `vectorStore`
（同名会先撞覆盖检查，报错信息还指向自动配置，很难看出是自己写的）。

### 10. 拒答阈值必须按实测分布标定，不能拍脑袋

最初按「相关 0.99 / 无关 0.002」把阈值设成 0.05，结果「今天天气怎么样」被
bge-reranker 打了 0.31，照样放行去生成。实测 19 条黄金集后：
命中最低 0.486、误答最高 0.317，最终把 `ferry.ai.reject-score-threshold` 定在 **0.38**。

**别把重排分和向量分混用**：0.38 对向量分等于全部放行，所以重排不可用时会切到
`ferry.ai.reject-vector-threshold=0.55` 兜底。改阈值请重跑 `scripts/eval_assistant.py`。

### 11. 全量重建不能同步做

37 篇逐篇调 embedding，实测 15~18 秒；但 nginx 的 `proxy_read_timeout` 是 60 秒，
一旦上游排队就会 504——事情做了，却没人知道做完了没有。
S2-C 做成**异步 + 内存态进度**（`KnowledgeRebuildJob`），接口立刻返回，
前端轮询 `/api/admin/knowledge/status`。重启会丢进度，对可重试操作无害。

### 12. 别把所有问答都套上 RAG

第一版助教对**任何**问题都先检索课程正文，检索不到就拒答。
于是「Java 的多态怎么理解」被判成「课程中没有相关内容」——而课程正文里
本来就没有「多态」这两个字（37 章是 LLM 生成的，覆盖有缺口）。

**教训**：RAG 的适用前提是「答案必须来自某个记录」，不是「模型不许有自己的知识」。
- 解释概念、举例、对比 → 生成式更合适，不该设资料门槛；
- 查薪资、查城市、查岗位要求 → 必须 RAG，因为这些字段编造出来就是误导。

所以现在按 `scope` 分成两条链路：学习答疑（`tutor`）不检索不拒答，
岗位咨询（`job`）检索 + 引用 + 拒答。**同一套前端组件，两个后端分支。**

### 13. 岗位检索的硬条件不能交给语义判断

实测：「杭州 后端开发 岗位」这句问法，bge-reranker 会给杭州的**运维岗**打 **0.815**
的高分——它觉得「都在杭州、都算技术岗，挺相关」，但用户问的是后端，运维岗就是不符合。

所以岗位链路是「**结构化过滤 → 语义排序**」：
`JobQueryParser` 用规则把问题拆成城市 / 方向 / 经验 / 实习（规则解析要的是确定性，
不是创造力），`JobCriteria` 拿这些条件过滤候选集，过滤后为空就拒答，
非空才交给向量与重排排顺序。

**代价**：只有映射过的关键词才是硬条件。「管理」「策划」这类没进映射表的词不会成为条件，
于是会出现「城市匹配、方向对不上」却仍然作答的情况（已记进已知问题）。
岗位规模上千条后，应把这些条件下推到向量索引（再给 `job` 加几个 metadata 字段）。

**关键词宁窄勿宽**：加「设计」想让设计岗能被条件命中，结果
「有没有芯片**设计**相关的职位」被它匹配上，答出了一个 UI/UX 岗位——
拒答率当场从 5/5 掉到 4/5。改成「设计师 / ui / ux / 交互设计」这类更具体的词才修好。
**每加一个关键词，都必须补一条「它可能误伤什么」的反例进评测集**，
否则宽词会把拒答兜底悄悄掏空。

### 14. bge-reranker 对疑问句式的分数系统性偏低

同一条实习岗位记录，重排分数随问法剧烈波动：

| 问法 | 分数 |
|---|---|
| `有没有实习岗位？` | 0.181 |
| `实习岗位` | 0.478 |
| `应届生 实习` | 0.572 |
| `有没有实习或者校招的机会？` | 0.014 |

带「有没有」「吗」这类疑问 scaffolding 的句子，分数会掉一个档。
两个应对：
- 岗位链路的拒答判定**以结构化过滤为准**（条件命中即答），分数阈值只兜底那些
  解析不出条件的提问；
- 别把阈值当唯一闸门——课程语料时代定的 0.38 换到岗位语料上就不合适了，
  阈值必须用当前语料的黄金集重新标定（`scripts/eval_assistant.py`）。

### 15. 知识库种子要挂进 initdb，并带 `SET NAMES`

`knowledge_seed.sql`（37 章正文）原先只在本地手动导入过，`docker compose down -v` 之后
知识库是空的——README 宣称的「一条命令跑起来」其实缺了一块。现已挂成
`03-knowledge-seed.sql`。

同时补了 `SET NAMES utf8mb4;`：容器 initdb 没有 UTF-8 locale，
客户端字符集默认 latin1，中文会「latin1 解释 → utf8mb4 存储」双重编码（同第 3 条坑）。
**这个文件现在也走 initdb，漏了这行会导致全新环境里中文全是乱码。**

已实测：用独立临时容器挂三个 SQL 走一遍 initdb，结果 17 张表 / 50 岗位 / 9 家公司 /
37 篇知识文档 / 8 个账号，中文全部正常——「一条命令跑起来」这条 README 承诺成立。

> 顺带记一个 Git Bash 的坑：手工 `docker run -v <host>:<container>` 时，
> MSYS 会把带冒号的参数拆坏（源路径变成 `schema.sql;D`），
> 要先 `export MSYS_NO_PATHCONV=1`。用 `docker compose` 不受影响（compose 自己解析相对路径）。

### 16. Docker Desktop 多实例会锁住 daemon.json

机器重启后 Docker Desktop 没起来，手动启动时报
`saving settings to file: rename ...daemon.json.tmp-xxx daemon.json: Access is denied`。

`Access is denied` 在这里**不是权限问题**（目录 ACL 是 FullControl），
而是目标文件被别的进程占着：当时同时跑着 4 个 `Docker Desktop.exe` 和 2 个 backend，
多实例互相抢占。处置：`taskkill //F //IM "Docker Desktop.exe" //T` 反复清几轮，
确认进程不再复活后，再单实例启动。
**排查顺序**：先 `tasklist` 看有几个实例 → 全杀 → 等十几秒确认不复活 → 再启动。

### 17. 无头浏览器验收会把自己锁死（QA 卡住 68 分钟的真因）

上一轮 QA 卡死不是模型慢，是脚本自己锁死了：

- 连续多次 `chrome --headless=new --remote-debugging-port=9333 --user-data-dir=<同一个目录>`，
  前一次没退干净，后一次要么连到旧实例、要么因为 profile 被锁直接退出；
- 脚本里 `await once('Page.loadEventFired')` 这类等待**没有超时**，
  一旦事件不来就永远挂着，前台 Bash 调用被拖到工具超时；
- 每次失败都留下几个 chrome 进程，最后累积到 **26 个**，形成死循环。

**验收脚本的三条硬规矩**：
1. 端口与 profile 每次唯一（`9333 + 随机数`、`chrome-prof-<时间戳>`），跑完 `rm -rf`；
2. 每个 CDP 等待都套 `Promise.race([wait, timeout(30s)])`，超时就 `process.exit(1)`；
3. `try/finally` 里必须 `proc.kill()`，不允许"跑起来就忘"。

**排查手法**：`taskkill //F //IM "chrome.exe" //T` 清干净；
用 `tasklist | grep -c chrome` 与 `netstat | grep 9333` 确认端口释放，
再重跑。另外 Git Bash 会把 `/assistant` 这样的参数当路径转换，
跑 node 脚本前先 `export MSYS_NO_PATHCONV=1`。

### 18. 三个限制叠在一起，追问功能等于摆设

用户反馈「追问不好用」。查下来不是模型的问题，是三处硬限制叠在一起：

1. `AssistantChatDTO.Turn.content` 有 `@Size(max = 1000)`——
   **只要上一轮回答超过 1000 字，下一句追问直接报「单条历史不能超过 1000 字」**。
   这是真 bug：助教正常回答 600~1700 字，等于问两轮必炸一次。
2. `question` 有 `@Size(max = 500)`，用户贴一段代码或报错就被拦。
3. `history` 只允许 8 条、前端 `.slice(-8)`，稍长一点的对话开头就被切掉。

现在：提问上限改由 `ferry.ai.max-question-chars`（默认 2 万）控制并在服务层校验；
历史单条放宽到 8000 字；总预算 `history-char-budget` 默认 12000 字，
**超出时从最旧的丢、保留最近的**；前端保留 10 轮，并把会话按 scope+context 存进
sessionStorage，切页面回来还能接着问。

**教训**：参数校验的上限要跟真实数据对齐。1000 字这个数字看起来「很宽松」，
但助教的正常输出就在 600~1700 字之间，等于给最常用的功能埋了一颗必炸的雷。
加限制前先问一句：这个字段的**正常值域**是多少？

## 六、目录结构

```
智慧教育平台/
├── backend/                      Spring Boot
│   ├── src/main/java/seb/
│   │   ├── controller/  service/(8 接口)  service/impl/(8 实现)
│   │   ├── entity/  dto/  vo/  mapper/  common/  config/  auth/
│   │   └── ai/                   ← S2 新增（config / controller / service / model / dto / vo）
│   ├── src/main/resources/db/    schema.sql(17 表) data.sql knowledge_seed.sql
│   ├── settings.xml              阿里云 Maven 镜像
│   └── Dockerfile
├── frontend/                     Vue 3
│   ├── src/views/                Home/Login/Course/Job/Cart/Order/Profile/admin/...
│   ├── nginx.conf                动态 DNS 反代 + SPA 回退
│   └── Dockerfile
├── test/                         pytest 196 条 + selenium + perf_tests
├── scripts/                      一次性脚本（gen_knowledge_seed.py、eval_assistant.py）
├── docs/                         课程作业阶段的历史文档（旧名「智慧教育平台」，保留原样）
├── docker-compose.yml            4 服务 + 命名卷 + healthcheck
├── .env / .env.example           环境变量（.env 不入库）
└── HANDOFF.md                    本文件
```

---

## 七、下一步该做什么

### S2-C 已实现（待你验收 + 提交）

**两种问答范式**，同一个入口，靠请求里的 `scope` 分流：

| scope | 链路 | 依据 | 没有依据时 | 引用 |
|---|---|---|---|---|
| `tutor` | 生成式 | 模型自身知识；课程名只作为 `context` 背景提示 | 讲不了就明说不确定，**不拒答** | 无（生成式硬配引用只会变成假溯源） |
| `job` | RAG | 仅系统里的在架职位记录 | 「系统里暂时没有符合条件的岗位。」 | 有，`[1]` 可点开原文、可跳 `/job/{jobId}` |

接口：

| 接口 | 说明 |
|---|---|
| `POST /api/assistant/chat` | SSE，事件顺序固定 `citations → delta* → done`；请求体 `{question, scope, context?, history?}`，`history` 最多 8 条、前端持有后端不落库 |
| `GET /api/admin/knowledge/status` | 文档数 / 分块数 / 岗位文档数 / 重建进度 |
| `POST /api/admin/knowledge/rebuild` | 全量重建，异步，返回 `started` |
| `POST /api/admin/knowledge/{docId}/rebuild` | 单篇重建，同步返回统计 |
| `POST /api/admin/knowledge/sync-jobs` | 把在架职位同步成知识文档，并自动触发重建 |

自测（口令从环境变量取，别回显）：

```bash
export FERRY_PASSWORD=<演示口令>
python scripts/eval_assistant.py
```

实测基线：检索 + 重排 0.7~0.8s 出引用，首字 2~4s，一次完整回答 4~9s；
纯拒答 1.3s（不调生成模型）。

**可调参数**（都在 `application.properties`，带默认值）：

| 配置项 | 默认 | 作用 |
|---|---|---|
| `ferry.ai.max-question-chars` | 20000 | 单次提问上限，超了提示分段。是模型上下文决定的保护线，不是产品限制 |
| `ferry.ai.history-char-budget` | 12000 | 历史总字符预算，超出时**从最旧的丢** |
| `ferry.ai.job-keywords` | `file:./config/job-category-keywords.json` | 岗位方向词表，docker 里覆盖成 `/app/config/...` |
| `ferry.ai.job-sync-cron` | `0 0 3 * * ?` | 岗位同步定时任务 |
| `ferry.ai.job-sync-enabled` | `true` | 定时同步开关 |

数据现状：50 个在架岗位 / 9 家公司；知识库 87 篇文档（37 课程 + 50 岗位）/ 265 个分块。

评测结果（`scripts/eval_assistant.py`，19 条黄金集）：
**岗位命中 10/10、岗位拒答 5/5、答疑可用 6/6**。

其中最值得记的一条：命中样本最低分 0.014、应拒答样本最高分 0.020，**两个区间完全重叠**。
也就是说「靠相关度阈值区分该不该答」这条路在岗位场景下根本不成立——
真正让指标达标的是**结构化过滤**（把城市/方向/经验/实习当成硬条件）。
5 条拒答全部走的是「解析不出条件 → 落到阈值」这条路，最高分只有 0.020，
相对当前阈值 0.38 有 19 倍余量，所以阈值不必动。

### 之后

- **S3 模拟面试**：当前是假的（前端 5 道固定题、后端按已答题数计分，README 已声明不是 AI）。改成 LLM 基于简历 + 岗位动态出题、多轮追问、结构化评分
- **S4 质量**：JUnit 单测（目前后端零单测，196 条都在 pytest 侧）、慢 SQL 优化、Redis 缓存、虚拟线程压测对比
- **S5 展示**：README 精修、演示 GIF、GitHub Actions CI

---

## 八、已知问题（未修，别以为不存在）

| 问题 | 严重度 | 位置 |
|---|---|---|
| `forgotPassword` 只凭手机号就能重置密码，无验证码 | **高（越权）** | `UserService.forgotPassword` |
| 结构化过滤只认已映射的关键词 | 中 | `JobQueryParser.CATEGORY_KEYWORDS`；「管理」「策划」这类没映射的方向词不会被当条件 |
| 只匹配到城市、方向却对不上时仍会作答 | 中 | `AssistantServiceImpl.prepareJob`；命中城市即答，模型需自行说明最接近的是哪些 |
| 语料覆盖不全：37 章正文里没有「重载 / 多态」等基础概念 | 低 | LLM 生成时漏掉的；学习答疑已不依赖它，影响主要在课程原文检索 |
| SSE 客户端中途断开会在容器层打 ERROR（`Broken pipe`） | 低（仅日志噪声） | Tomcat 在客户端断开后写响应失败；功能无影响，压测式快速关页面时会被放大 |
| 全量重建会删旧分块重插，`knowledge_chunk.id` 整体换新 | 低 | 外部若缓存过 chunkId 会失效；引用只在单次回答内有效 |
| `Service` 接口仍直接返回实体（Job/AiInterview/Resume），未走 VO | 中 | 建议后续统一处理 |
| `@Resource` 按类型回退注入（字段名 `userService` vs bean `userServiceImpl`） | 低 | 若将来一个接口多实现会歧义 |
| 演示口令 `123456` 与本机 MySQL root 口令相同 | 中 | 用户已决定暂不处理；容器内 3306 不对外，风险可控 |
| 后端零单元测试 | 中 | S4 补 |
| `docs/` 里是旧项目名文档，未改写 | 低 | README 已说明来源 |

**关于 admin 登不上（已解决）**：早期发现库里的 admin 哈希与 `data.sql` 那份不一致，
是某个旧数据卷被单独改过口令、没回写种子文件（正是第 7 条坑）。
`docker compose down -v` 重建后一切正常，8 个账号口令统一。
**教训：种子文件与数据卷不一致时，先重建数据卷确认，别急着改种子。**

**关于结构化过滤的边界**：目前只有「城市 / 岗位方向 / 经验 / 实习」四类条件是硬过滤，
其余交给语义。带来的后果是：用户问「杭州的算法岗」时，只要杭州有岗位（哪怕都是测试岗），
系统就会作答而不是拒答——因为方向词「算法」映射得到，城市也匹配得到，
但库里杭州确实没有算法岗时，过滤后为空，会正确拒答；
只有「城市有、方向映射不到」的组合才会出现不理想的结果。

---

## 九、协作约定（写给接手的 AI）

1. **改文件前先读**，不要凭记忆改
2. **改完必须验证**，给出真实命令输出，不要写"应该可以"
3. **不要声称未验证的事已验证**——S1-C 阶段明确要求过：本机没 Docker 就只写静态校验，不许写"已启动成功"
4. **提交前先跑构建**，提交信息用 Conventional Commits + 中文正文
5. **不要替用户做需要登录态的操作**（GitHub 网页操作、校内 VPN）
6. **遇到口令/密钥，不要回显**；用 `MYSQL_PWD` 环境变量传递
7. **验证事务/回滚这类东西，要真的制造失败再查库**，不能只看代码有没有注解
8. 用户的审美：**排斥政务公文腔**（"智慧/一站式/综合服务平台"），偏好有温度的表达

---

## 十、关键文件速查

| 想改什么 | 看哪里 |
|---|---|
| 建表/改表 | `backend/src/main/resources/db/schema.sql`（17 表，DROP 顺序是子→父逆序） |
| 种子数据 | `backend/src/main/resources/db/data.sql`（8 账号，口令 123456） |
| 知识库内容 | `backend/src/main/resources/db/knowledge_seed.sql`（37 章，LLM 生成） |
| AI 配置 | `application.properties` 的 `spring.ai.*` 与 `ferry.ai.*` 段 |
| 问答提示词与拒答 | `seb/ai/service/impl/AssistantServiceImpl` |
| 岗位方向词表（可改配置） | `config/job-category-keywords.json` + `seb/ai/service/JobCategoryDictionary` |
| 岗位定时同步 | `seb/ai/service/JobKnowledgeSyncJob`（`@Scheduled`，需 `@EnableScheduling`） |
| 检索与重排 | `seb/ai/service/impl/KnowledgeRetrievalServiceImpl`、`RerankServiceImpl` |
| 向量库 schema | `seb/ai/config/VectorStoreConfig`（改 metadata 字段要重建索引） |
| 重建索引 | `seb/ai/service/KnowledgeRebuildJob` |
| 检索评测 | `scripts/eval_assistant.py`（改阈值后必须重跑） |
| 容器编排 | `docker-compose.yml`（4 服务，注意 redis 用 REDIS_ARGS） |
| 前端反代 | `frontend/nginx.conf`（动态 DNS，别改回静态） |
| 接口约定 | `seb/controller/*.java` + 前端 `src/api/*.js` |
