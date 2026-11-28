# 渡口 Ferry

> 课程是岸，岗位是岸。中间那一段水，得有船。

一个把「学」和「做」接起来的学习与求职平台：学员在这里买课、学习、写简历、投岗位；讲师与企业在这里上架内容、发布职位；平台管理员负责上下架审核。

项目最初是一个课程作业，现在正在被重构成一个可以公开阅读、可以一键跑起来的工程项目。重构过程记录在 [路线图](#路线图) 里，每一步都有可验证的产出。

---

## 技术栈

| 层次 | 技术选型 |
|---|---|
| 后端 | Java 21、Spring Boot 3.3.4、Spring MVC 三层架构、MyBatis-Plus 3.5.8 |
| 前端 | Vue 3.4（`<script setup>`）、Vite 5、Pinia、Vue Router 4、Axios |
| 数据库 | MySQL 8.0，17 张表 / 24 个外键 / 26 个 CHECK 约束 / 43 个索引 |
| 鉴权 | JWT + 后端拦截器 + 前端路由守卫，三角色 RBAC（学员 / 讲师·企业 / 管理员） |
| 构建 | Maven（含 wrapper）、npm |
| 测试 | JUnit 5（补齐中）、pytest 196 条接口用例、Selenium UI 自动化、JMeter 压测 |
| 部署 | Docker Compose（4 容器：mysql / redis / backend / frontend） |
| AI | Spring AI 1.0 + 硅基流动：bge-m3 向量、bge-reranker-v2-m3 重排、Qwen2.5-7B 生成 |

---

## 快速开始

### 前置条件

- JDK 17 或以上（推荐 21）
- Maven 3.9+
- Node.js 18+
- MySQL 8.0+

### 1. 建库并导入数据

```bash
mysql -uroot -p -e "CREATE DATABASE ferry CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

mysql -uroot -p --default-character-set=utf8mb4 ferry < backend/src/main/resources/db/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 ferry < backend/src/main/resources/db/data.sql
```

> 两个脚本都可重复执行，不会因重复跑而报错或产生重复数据。
> `--default-character-set=utf8mb4` 不能省，否则中文会乱码。

### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，至少填入 `DB_PASSWORD`。数据库口令、JWT 密钥都不写在代码里。

**Linux / macOS / Git Bash**

```bash
set -a && source .env && set +a
```

**Windows PowerShell**

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
  }
}
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

服务监听 `http://localhost:8080`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`，`/api` 与 `/uploads` 已由 Vite 代理到后端。

---

### 5. 方式二：Docker Compose（一条命令）

上面的 1~4 步是「方式一：本地手动启动」。如果你只是想把项目跑起来看效果，
不想在本机装 JDK 21 / Maven / Node / MySQL，用方式二即可：

```bash
# 1) 准备环境变量：Docker 模式需要填 MYSQL_PASSWORD 与 MYSQL_ROOT_PASSWORD
#    （DB_PASSWORD 是「方式一」连宿主机 MySQL 用的，Docker 模式不读）
#    JWT_SECRET 可先用示例值
cp .env.example .env

# 2) 一条命令启动（首次要构建镜像，耗时较长）
docker compose up -d --build

# 3) 访问前端
#    http://localhost:8088
```

几点说明：

- **首次启动 MySQL 会自动执行 `schema.sql` + `data.sql`** 完成建库与灌数，
  约需 **20-30 秒**。等 `docker compose ps` 里 `ferry-mysql` 变成 `healthy`、
  `ferry-backend` 也变成 `healthy` 之后再访问前端，否则可能出现空列表。
- 前端映射到宿主机 **8088**（不是 8080，避开花式冲突）；
  后端 8080 与 MySQL 3306 **都不对外暴露**，API 由 Nginx 把 `/api`、`/uploads`
  反代到容器内的 `backend:8080`。
- 容器与网络/数据卷都带 `ferry-` 前缀（`ferry-mysql`、`ferry-backend`、
  `ferry-frontend`、`ferry-net`、`ferry-mysql-data`），不会和你机器上
  其他项目（cpcc、tlias、vote_db 等）冲突。
- 改了 Java / Vue 代码后执行 `docker compose up -d --build` 重新构建；
  依赖层有缓存，只改业务代码时通常十几秒完成。
- 停止：`docker compose down`；**连数据一起清空**（下次启动会重新灌种子数据）：
  `docker compose down -v`。

### 国内网络环境的两处配置

如果构建卡住，通常是这两个网络问题，与代码无关：

**① 拉镜像报 `auth.docker.io ... Bad Gateway`**

在 Docker Desktop → Settings → Docker Engine 的 JSON 里加入：

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

点 **Apply & restart**。

**② Maven 依赖下载报 `403 Forbidden`**

仓库已内置处理：`backend/settings.xml` 把 Maven Central 指向阿里云公共仓库，
构建时由 Dockerfile 复制为 `/root/.m2/settings.xml`。
如果你的网络访问阿里云反而更慢，改该文件里的 `<url>` 即可。

## 测试账号

所有账号口令统一为 `123456`（仅用于本地演示，正式环境请通过接口重置）。

| 用户名 | 角色 | 说明 |
|---|---|---|
| `admin` | 管理员 | 上下架审核、用户/课程/职位/订单管理 |
| `teacher01` / `teacher02` | 讲师 | 上架课程、查看学员 |
| `company01` | 企业 | 发布职位、查看投递 |
| `student01` ~ `student04` | 学员 | 购课、学习、投递、模拟面试 |

---

## 目录结构

```
ferry/
├── backend/                      Spring Boot 后端
│   ├── src/main/java/seb/
│   │   ├── controller/           接口层（8 个）
│   │   ├── service/              业务层（8 个）
│   │   ├── mapper/               数据访问层（MyBatis-Plus）
│   │   ├── entity/ dto/          实体与传输对象
│   │   ├── auth/                 JWT 工具与拦截器
│   │   ├── common/               统一响应、异常、分页
│   │   └── config/               配置类
│   └── src/main/resources/db/    schema.sql、data.sql
├── frontend/                     Vue 3 前端（36 个页面/组件）
├── test/                         接口测试、UI 自动化、性能测试
├── docs/                         需求、设计、数据库、测试报告
└── .env.example                  环境变量清单
```

---

## 功能概览

| 模块 | 已实现 | 说明 |
|---|---|---|
| 用户与鉴权 | ✅ | 注册、登录、找回密码、三角色隔离 |
| 课程 | ✅ | 浏览、筛选、详情、章节、学习进度 |
| 交易 | ✅ | 购物车、下单、订单查询 |
| 简历与投递 | ✅ | 简历维护、职位投递、投递状态跟踪 |
| 上下架审核 | ✅ | 讲师/企业提交申请，管理员审核 |
| 后台管理 | ✅ | 用户、课程、职位、订单管理 |
| 模拟面试 | ⚠️ | 当前为固定题库 + 答题记录，**不是 AI 评分** |
| 学习答疑（生成式） | ✅ | 不检索、不拒答，讲清楚 + 给例子；可以追问 |
| 岗位咨询（RAG） | ✅ | 只依据系统里在架的职位记录回答，带引用可跳岗位详情；没有就说没有 |

> 关于「模拟面试」：早期版本曾把它称作「AI 面试」，但实际只是前端固定 5 道题、后端按已答题数计分，没有任何模型参与。这个命名已纠正，真正的 LLM 驱动版本排在 S3。

---

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| S0 止血 | monorepo 重组、可重建的 DDL 与种子数据、密钥外置、隐私清洗 | ✅ 完成 |
| S1 工程地基 | 升级 Java 21、Service 接口化、补事务、Docker Compose、密码脱敏 | ✅ 完成 |
| S2 RAG 主链路 | 知识库建模、文档解析分块、向量检索、引用溯源、Python 评测 | ✅ 完成 |
| S3 模拟面试 | LLM 动态出题、多轮追问、结构化评分 | ⏳ 待开始 |
| S4 质量与性能 | JUnit 单测、慢 SQL 优化、Redis 缓存、虚拟线程压测对比 | ⏳ 待开始 |
| S5 展示 | README 精修、演示 GIF、GitHub Actions CI | ⏳ 待开始 |

### 同一个入口，两种问答范式

助手不是「什么问题都去检索」。**问什么就该用什么方式回答**：

| | 学习答疑 | 岗位咨询 |
|---|---|---|
| 范式 | 生成式对话 | RAG（检索 + 重排） |
| 依据 | 模型自身的知识，课程只作背景提示 | 仅系统里的在架职位记录 |
| 没有依据时 | 讲不了就说不确定，**不拒答** | 明说「系统里暂时没有符合条件的岗位」 |
| 引用 | 无（生成式回答硬配引用只会变成假溯源） | 有，`[1]` 可点开原文、可跳岗位详情 |

这不是两种口味，是两种事实约束：讲清楚「多态是什么」需要的是解释力，
而回答「杭州有哪些后端岗」需要的是准确——薪资少写 5k、城市写错就是误导。

**岗位咨询的链路**（`scope=job`）：

```
提问
 ├─ 解析硬条件：城市 / 岗位方向 / 经验 / 是否实习（规则解析，不是模型猜）
 ├─ 向量召回（bge-m3）+ 回 MySQL 补标题与归属
 ├─ 结构化过滤：不符合硬条件的候选直接剔除
 │    └─ 过滤后为空 → 拒答「系统里暂时没有符合条件的岗位。」
 ├─ 重排到 4 条（bge-reranker-v2-m3），同一岗位只保留分最高的那块
 └─ 带着编号资料流式生成，先推引用再推正文
```

第 3 步是这套东西里最要紧的一步。实测「杭州 后端开发 岗位」这句问法，
重排模型会给杭州的运维岗打 **0.815** 的高分——它觉得「都在杭州、都算技术岗，挺相关」，
但用户问的是后端，运维岗就是不符合。城市、方向这类是**精确条件**，
必须先做结构化过滤，再让向量负责排序。

引用不是模型「说」出来的，而是检索结果本身：SSE 的第一个事件就是 `citations`，
模型再怎么编也改不了它。

评测脚本 `scripts/eval_assistant.py` 用 19 条黄金集跑岗位命中、岗位拒答与答疑可用性三项指标，
当前 **10/10、5/5、6/6** 全绿。

值得注意的是：命中样本最低分 0.014、应拒答样本最高分 0.020，两段区间完全重叠——
**光靠相关度阈值根本分不开该不该答**。让指标达标的是上面第 3 步的结构化过滤；
阈值只兜底那些解析不出任何条件的提问。

---

## 文档

`docs/` 下保留了项目最初的需求、设计、数据库设计与测试报告。这些文档由课程作业阶段产出，内容未经改写，部分命名仍是旧项目名「智慧教育平台」，可与当前代码对照阅读。

| 文档 | 内容 |
|---|---|
| `01-需求文档.pdf` | 功能需求与非功能需求 |
| `02-设计文档.pdf` | 模块划分与接口设计 |
| `03-数据库设计.pdf` | 表结构与关系说明 |
| `er-diagram/` | 6 张 E-R 图 |
| `04~08` | 测试需求、接口测试、缺陷报告、性能报告 |

---

## 说明

- 仓库内所有用户数据均为虚构，手机号统一为 `138xxxxxxxx` 占位段，邮箱统一为 `@ferry.local`。
- 演示用的封面图、视频与简历附件为占位路径，仓库中不含对应二进制文件，访问时会出现 404，属预期行为。
- 数据库口令与 JWT 密钥通过环境变量注入，`.env` 已被 `.gitignore` 排除。

## License

[MIT](LICENSE)
