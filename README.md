# 渡口 Ferry

> 课程是岸，岗位是岸。中间那一段水，得有船。

一个把「学」和「做」接起来的学习与求职平台：学员在这里买课、学习、写简历、投岗位；讲师与企业在这里上架内容、发布职位；平台管理员负责上下架审核。

项目最初是一个课程作业，现在正在被重构成一个可以公开阅读、可以一键跑起来的工程项目。重构过程记录在 [路线图](#路线图) 里，每一步都有可验证的产出。

---

## 技术栈

| 层次 | 技术选型 |
|---|---|
| 后端 | Java 17 → 21（迁移中）、Spring Boot 3.3.4、Spring MVC 三层架构、MyBatis-Plus 3.5.8 |
| 前端 | Vue 3.4（`<script setup>`）、Vite 5、Pinia、Vue Router 4、Axios |
| 数据库 | MySQL 8.0，15 张表 / 23 个外键 / 23 个 CHECK 约束 / 43 个索引 |
| 鉴权 | JWT + 后端拦截器 + 前端路由守卫，三角色 RBAC（学员 / 讲师·企业 / 管理员） |
| 构建 | Maven（含 wrapper）、npm |
| 测试 | JUnit 5（补齐中）、pytest 196 条接口用例、Selenium UI 自动化、JMeter 压测 |
| 部署 | Docker Compose（规划中） |
| AI | Spring AI + 硅基流动（规划中） |

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
| 智能助教（RAG） | 🚧 | 规划中，见路线图 S2 |

> 关于「模拟面试」：早期版本曾把它称作「AI 面试」，但实际只是前端固定 5 道题、后端按已答题数计分，没有任何模型参与。这个命名已纠正，真正的 LLM 驱动版本排在 S3。

---

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| S0 止血 | monorepo 重组、可重建的 DDL 与种子数据、密钥外置、隐私清洗 | ✅ 完成 |
| S1 工程地基 | 升级 Java 21、Service 接口化、补事务、Docker Compose、密码脱敏 | 🚧 进行中 |
| S2 RAG 主链路 | 知识库建模、文档解析分块、向量检索、引用溯源、Python 评测 | ⏳ 待开始 |
| S3 模拟面试 | LLM 动态出题、多轮追问、结构化评分 | ⏳ 待开始 |
| S4 质量与性能 | JUnit 单测、慢 SQL 优化、Redis 缓存、虚拟线程压测对比 | ⏳ 待开始 |
| S5 展示 | README 精修、演示 GIF、GitHub Actions CI | ⏳ 待开始 |

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
