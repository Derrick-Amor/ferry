# 渡口 Ferry · 项目交接文档

> 写给接手的新对话。读完这份，你应该能在不看历史聊天记录的情况下继续推进。
> 最后更新：2026-11-27 22:15

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
| S2-C | 问答链路（检索→重排→生成→引用）+ 前端 | ⏳ 待做 |
| S3 | 模拟面试改造（接 LLM 动态出题） | ⏳ 待做 |
| S4 | JUnit 单测、慢 SQL、Redis 缓存、压测对比 | ⏳ 待做 |
| S5 | README 精修、演示 GIF、GitHub Actions | ⏳ 待做 |

**提交记录**（截至 S2-A）：

```
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
| 对话 | `XingChenAGI/Xing4.0-29B` | 质量最佳（62s/章）；**推理模型，max_tokens 须 ≥3000** |
| 对话备选 | `Qwen/Qwen2.5-7B-Instruct` | 快（17s/章），质量略平 |
| 重排 | `BAAI/bge-reranker-v2-m3` | 「如何学习Java」→ 0.5382(Java) / 0.0001(Vue) / 0.0000(Python) |

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

### 8. Spring AI 的 RedisVectorStore 检索时不回传业务 metadata

写入时 metadata 里有 `docId` / `chunkId` / `title` / `courseId`，Redis 里也确实存着，
但 `similaritySearch()` 返回的 `Document.getMetadata()` 只有 `distance` 和 `vector_score`，
业务字段全部读成 null。这是 Spring AI 1.0.0 的实现行为，不是写法问题。

**S2-C 的正确做法**：`Document.getId()` 返回的就是 `chunkId`，
拿它批量 `IN` 查询 `knowledge_chunk` join `knowledge_doc` 回补标题与文档归属，
**不要试图从 metadata 里取**。

## 六、目录结构

```
智慧教育平台/
├── backend/                      Spring Boot
│   ├── src/main/java/seb/
│   │   ├── controller/  service/(8 接口)  service/impl/(8 实现)
│   │   ├── entity/  dto/  vo/  mapper/  common/  config/  auth/
│   │   └── ai/                   ← S2 新增（分块、索引、问答）
│   ├── src/main/resources/db/    schema.sql(17 表) data.sql knowledge_seed.sql
│   ├── settings.xml              阿里云 Maven 镜像
│   └── Dockerfile
├── frontend/                     Vue 3
│   ├── src/views/                Home/Login/Course/Job/Cart/Order/Profile/admin/...
│   ├── nginx.conf                动态 DNS 反代 + SPA 回退
│   └── Dockerfile
├── test/                         pytest 196 条 + selenium + perf_tests
├── scripts/                      一次性脚本（如 gen_knowledge_seed.py）
├── docs/                         课程作业阶段的历史文档（旧名「智慧教育平台」，保留原样）
├── docker-compose.yml            4 服务 + 命名卷 + healthcheck
├── .env / .env.example           环境变量（.env 不入库）
└── HANDOFF.md                    本文件
```

---

## 七、下一步该做什么

### 立刻要做（S2-B 收尾 → S2-C）

1. **S2-B 的验收**（工程师正在做）：37 章正文生成、分块、向量化入库、检索质量抽查
2. **S2-C：问答链路**（数据已就绪：37 篇文档 / 165 块已入库）
   - 检索：`similaritySearch(query, 8)` → 拿 `Document.getId()`（= chunkId）
     → 批量 `IN` 回补 title/docId → rerank top4（`BAAI/bge-reranker-v2-m3`）
   - 生成：`POST /api/assistant/chat`，返回 `{answer, citations[]}`，
     citations 每条含 `title / chunkId / docId / score / 摘录`
   - System prompt 必须约束：**只根据资料回答，资料里没有就说"课程中没有相关内容"**
   - 新建管理接口触发重建（原临时 Runner 已删），供后续内容更新用
   - 前端：课程详情页悬浮入口（限定本课程）+ 独立 `/assistant` 页面（全库）

### 之后

- **S3 模拟面试**：当前是假的（前端 5 道固定题、后端按已答题数计分，README 已声明不是 AI）。改成 LLM 基于简历 + 岗位动态出题、多轮追问、结构化评分
- **S4 质量**：JUnit 单测（目前后端零单测，196 条都在 pytest 侧）、慢 SQL 优化、Redis 缓存、虚拟线程压测对比
- **S5 展示**：README 精修、演示 GIF、GitHub Actions CI

---

## 八、已知问题（未修，别以为不存在）

| 问题 | 严重度 | 位置 |
|---|---|---|
| `forgotPassword` 只凭手机号就能重置密码，无验证码 | **高（越权）** | `UserService.forgotPassword` |
| `Service` 接口仍直接返回实体（Job/AiInterview/Resume），未走 VO | 中 | 建议 S2-C 后统一处理 |
| `@Resource` 按类型回退注入（字段名 `userService` vs bean `userServiceImpl`） | 低 | 若将来一个接口多实现会歧义 |
| 演示口令 `123456` 与本机 MySQL root 口令相同 | 中 | 用户已决定暂不处理；容器内 3306 不对外，风险可控 |
| 后端零单元测试 | 中 | S4 补 |
| `docs/` 里是旧项目名文档，未改写 | 低 | README 已说明来源 |

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
| 容器编排 | `docker-compose.yml`（4 服务，注意 redis 用 REDIS_ARGS） |
| 前端反代 | `frontend/nginx.conf`（动态 DNS，别改回静态） |
| 接口约定 | `seb/controller/*.java` + 前端 `src/api/*.js` |
