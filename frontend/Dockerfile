# ============================================================================
# 渡口 Ferry · 前端镜像（多阶段构建：Node 构建 -> Nginx 托管）
# ----------------------------------------------------------------------------
# 构建：docker build -t ferry-frontend ./frontend
# 或由 docker-compose.yml 统一编排
#
# 设计要点：
#   1. 先拷 package.json + package-lock.json 跑 npm ci，再拷源码。
#      依赖层与源码层分离，改页面代码不会重新装依赖。
#   2. 用 npm ci 而不是 npm install：严格按 lock 文件安装，可复现且更快。
#   3. 运行阶段只保留 Nginx + 静态文件，Node 不进入最终镜像。
# ============================================================================

# ---------------------------------------------------------------------------
# 阶段一：构建
# ---------------------------------------------------------------------------
FROM node:20-alpine AS build

WORKDIR /build

# 1) 先拷依赖清单，装依赖独立成层
COPY package.json package-lock.json ./
RUN npm ci

# 2) 再拷源码构建（node_modules / dist 已由 .dockerignore 排除）
COPY . .
RUN npm run build

# ---------------------------------------------------------------------------
# 阶段二：运行
# ---------------------------------------------------------------------------
FROM nginx:alpine AS runtime

# 用项目自己的配置替换默认站点（SPA history 回退 + /api 与 /uploads 反代）
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 只拷构建产物，不拷源码
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
