#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
渡口 Ferry · 知识库正文生成器（一次性脚本）

用途
----
`course_chapter` 表里只有章节标题、没有正文，RAG 无内容可分块。
本脚本对 37 个章节逐个调用大模型（硅基流动 Qwen3-8B）生成教学正文，
拼成 `backend/src/main/resources/db/knowledge_seed.sql`。

依赖
----
Python 3.9+，**只用标准库**（urllib / hashlib / subprocess / json）。
数据库访问通过 `mysql` 命令行客户端完成，因此本机需要能连到 ferry 库：
  - 默认 `MYSQL_CONTAINER=ferry-mysql`，走 `docker exec`（compose 未对外暴露 3306，
    这样既能读到应用真正在用的那个库，又不会和本机 3306 的 MySQL 打架）
  - 设 `MYSQL_CONTAINER=`（空）则退回本机 mysql 客户端 + MYSQL_HOST/MYSQL_PORT

用法
----
    python scripts/gen_knowledge_seed.py            # 全量 37 章
    python scripts/gen_knowledge_seed.py --limit 1  # 只跑第 1 章，用于连通性自检
    python scripts/gen_knowledge_seed.py --out /tmp/x.sql

密钥
----
从仓库根目录 `.env` 读取 `SILICONFLOW_API_KEY`；也支持同名环境变量覆盖。
**不要把 Key 写进本文件或提交到仓库。**
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_OUT = os.path.join(
    REPO_ROOT, "backend", "src", "main", "resources", "db", "knowledge_seed.sql"
)

API_BASE = os.environ.get("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn/v1")
API_MODEL = os.environ.get("SILICONFLOW_CHAT_MODEL", "XingChenAGI/Xing4.0-29B")

# 生成质量门槛：低于这个长度或不足 2 个小节，判定为无效内容
MIN_CONTENT_LEN = 500
MIN_SECTIONS = 2
MAX_RETRY = 2  # 首次 + 2 次重试

PROMPT_TEMPLATE = """你是一名资深技术讲师。请为课程《{course_name}》的章节「{chapter_name}」撰写教学正文，约 800-1000 字，面向零基础学员。要求：
1. 用 Markdown 分 3-5 个小节，每节用 ## 开头
2. 包含具体的代码示例（{lang_hint}）
3. 讲清楚"为什么"而不只是"怎么做"
4. 不要写"本章我们将..."这类空话，直接进入内容
5. 只输出正文 Markdown，不要输出任何前后说明或代码块标记之外的文字"""

# 按技术体系选择代码示例语言，让生成内容贴合课程主题
LANG_HINT = {
    "Java": "Java",
    "Vue": "Vue 3 / JavaScript",
    "MySQL": "SQL",
    "Python": "Python",
    "AI": "Python",
}


def load_dotenv(path: str) -> dict:
    """极简 .env 解析：KEY=VALUE，忽略注释与空行。"""
    data = {}
    if not os.path.exists(path):
        return data
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            data[k.strip()] = v.strip()
    return data


def mysql_query(sql: str) -> str:
    """
    执行一条 SELECT 并返回 TSV 文本。

    默认通过 docker exec 连容器内 MySQL；这样读到的是应用真正使用的库，
    而且不需要把 3306 暴露到宿主机（宿主机 3306 已被本机 MySQL 占用）。
    """
    container = os.environ.get("MYSQL_CONTAINER", "ferry-mysql")
    database = os.environ.get("DB_NAME") or "ferry"
    # 注意：.env 里的 DB_USER=root 是给「本地手动启动」连宿主机 MySQL 用的。
    # 容器内的业务账号固定是 ferry（mysql 镜像拒绝 MYSQL_USER=root），
    # 所以容器模式不要读 DB_USER，否则会用 root 去连、必然 1045 拒绝。
    if container:
        user = os.environ.get("KNOWLEDGE_DB_USER", "ferry")
        password = os.environ.get("MYSQL_PASSWORD") or ""
    else:
        user = os.environ.get("KNOWLEDGE_DB_USER") or os.environ.get("DB_USER") or "root"
        password = os.environ.get("MYSQL_PASSWORD") or os.environ.get("DB_PASSWORD") or ""

    if container:
        cmd = [
            "docker", "exec", "-i",
            "-e", "MYSQL_PWD=" + password,
            container,
            "mysql", "-u" + user, "-N", "-B",
            "--default-character-set=utf8mb4",
            database, "-e", sql,
        ]
    else:
        cmd = [
            "mysql", "-h", os.environ.get("MYSQL_HOST", "127.0.0.1"),
            "-P", os.environ.get("MYSQL_PORT", "3306"),
            "-u" + user, "-N", "-B",
            "--default-character-set=utf8mb4",
            database, "-e", sql,
        ]

    proc = subprocess.run(cmd, capture_output=True)
    if proc.returncode != 0:
        err = proc.stderr.decode("utf-8", "replace").strip()
        raise RuntimeError("查询失败：%s\nSQL: %s" % (err, sql))
    return proc.stdout.decode("utf-8")


def fetch_chapters() -> list:
    """读取全部章节，连带课程名与技术体系（用于选代码语言）。"""
    sql = (
        "SELECT ch.id, ch.chapter_name, ch.duration, c.id, c.course_name, "
        "IFNULL(c.tech_system,'') "
        "FROM course_chapter ch JOIN course c ON c.id = ch.course_id "
        "ORDER BY c.id, ch.sort_no"
    )
    rows = []
    for line in mysql_query(sql).splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        rows.append({
            "chapter_id": int(parts[0]),
            "chapter_name": parts[1],
            "duration": parts[2],
            "course_id": int(parts[3]),
            "course_name": parts[4],
            "tech_system": parts[5] if len(parts) > 5 else "",
        })
    return rows


def call_llm(api_key: str, prompt: str) -> str:
    """调用 OpenAI 兼容的 /chat/completions。"""
    payload = {
        "model": API_MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.6,
        "max_tokens": 4000,
        "stream": False,
    }
    req = urllib.request.Request(
        API_BASE.rstrip("/") + "/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=150) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    return (body["choices"][0]["message"].get("content") or "").strip()


def validate(text: str) -> tuple:
    """基本质量校验，返回 (是否通过, 失败原因)。"""
    if len(text) < MIN_CONTENT_LEN:
        return False, "正文过短(%d 字)" % len(text)
    sections = len(re.findall(r"^##\s+\S", text, re.M))
    if sections < MIN_SECTIONS:
        return False, "小节数不足(%d 个)" % sections
    return True, ""


def sql_escape(text: str) -> str:
    """MySQL 字符串字面量转义：反斜杠与单引号。"""
    return text.replace("\\", "\\\\").replace("'", "''")


def generate_one(api_key: str, chapter: dict) -> tuple:
    """生成单章正文；返回 (正文, 失败原因)。共尝试 1 + MAX_RETRY 次。"""
    lang = LANG_HINT.get(chapter["tech_system"], "Java")
    prompt = PROMPT_TEMPLATE.format(
        course_name=chapter["course_name"],
        chapter_name=chapter["chapter_name"],
        lang_hint=lang,
    )
    last_err = ""
    for attempt in range(MAX_RETRY + 1):
        try:
            text = call_llm(api_key, prompt)
            ok, reason = validate(text)
            if ok:
                return text, ""
            last_err = reason
        except urllib.error.HTTPError as e:
            last_err = "HTTP %s: %s" % (e.code, e.read().decode("utf-8", "replace")[:200])
        except Exception as e:  # 网络抖动、超时、JSON 解析失败
            last_err = "%s: %s" % (type(e).__name__, e)
        if attempt < MAX_RETRY:
            time.sleep(2 * (attempt + 1))  # 退避，避免打爆限流
    return "", last_err


def build_sql(chapters: list) -> str:
    lines = []
    lines.append("-- ============================================================================")
    lines.append("-- 渡口 Ferry · 知识库种子数据（knowledge_doc）")
    lines.append("-- ----------------------------------------------------------------------------")
    lines.append("-- ⚠ 本文件内容由大语言模型（硅基流动 " + API_MODEL + "）自动生成，")
    lines.append("--   仅用于演示 RAG 检索链路，技术细节未经专业审校，请勿直接作为教材使用。")
    lines.append("--")
    lines.append("-- 生成方式：python scripts/gen_knowledge_seed.py")
    lines.append("-- 覆盖范围：course_chapter 全部 %d 个章节，source_type=1" % len(chapters))
    lines.append("-- 幂等性：先删除 source_type=1 的旧文档（knowledge_chunk 由外键级联删除），再插入")
    lines.append("-- 执行方式：")
    lines.append("--   mysql --default-character-set=utf8mb4 -u<user> -p <db_name> < knowledge_seed.sql")
    lines.append("-- ============================================================================")
    lines.append("")
    lines.append("DELETE FROM `knowledge_doc` WHERE `source_type` = 1;")
    lines.append("")
    for idx, ch in enumerate(chapters, start=1):
        content = ch["content"]
        content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
        lines.append(
            "INSERT INTO `knowledge_doc` "
            "(`id`, `title`, `content`, `source_type`, `course_id`, `chapter_id`, "
            "`content_hash`, `chunk_count`, `status`) VALUES"
        )
        lines.append(
            "  (%d, '%s', '%s', 1, %d, %d, '%s', 0, 1);"
            % (
                idx,
                sql_escape(ch["chapter_name"]),
                sql_escape(content),
                ch["course_id"],
                ch["chapter_id"],
                content_hash,
            )
        )
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="生成课程章节正文并输出 knowledge_seed.sql")
    parser.add_argument("--limit", type=int, default=0, help="只处理前 N 章，用于连通性自检")
    parser.add_argument("--out", default=DEFAULT_OUT, help="输出 SQL 路径")
    args = parser.parse_args()

    env = load_dotenv(os.path.join(REPO_ROOT, ".env"))
    for k, v in env.items():
        os.environ.setdefault(k, v)

    api_key = os.environ.get("SILICONFLOW_API_KEY", "").strip()
    if not api_key:
        print("[ERROR] 未找到 SILICONFLOW_API_KEY（.env 或环境变量）", file=sys.stderr)
        return 2

    # 让 mysql_query 能用到 .env 里的口令
    os.environ.setdefault("MYSQL_PASSWORD", env.get("MYSQL_PASSWORD", ""))

    print("[1/3] 读取 course_chapter ...")
    chapters = fetch_chapters()
    print("      读到 %d 个章节" % len(chapters))
    if not chapters:
        print("[ERROR] course_chapter 为空，先执行 schema.sql + data.sql", file=sys.stderr)
        return 2
    if args.limit:
        chapters = chapters[: args.limit]
        print("      --limit 生效，只处理前 %d 章" % len(chapters))

    print("[2/3] 并发调用 %s（3 路并发）..." % API_MODEL, flush=True)
    from concurrent.futures import ThreadPoolExecutor, as_completed

    def _worker(item):
        idx, ch = item
        content, err = generate_one(api_key, ch)
        return idx, ch, content, err

    ok_count, failed = 0, []
    total = len(chapters)
    with ThreadPoolExecutor(max_workers=3) as pool:
        futures = [pool.submit(_worker, (i, ch)) for i, ch in enumerate(chapters, start=1)]
        for fut in as_completed(futures):
            i, ch, content, err = fut.result()
            tag = "%-2d/%d  %s | %s" % (i, total, ch["course_name"], ch["chapter_name"])
            if content:
                ch["content"] = content
                ok_count += 1
                print("      [OK]   %s  (%d 字, %d 小节)" % (
                    tag, len(content), len(re.findall(r"^##\s+\S", content, re.M))), flush=True)
            else:
                failed.append((ch["course_name"], ch["chapter_name"], err))
                print("      [FAIL] %s  <- %s" % (tag, err), flush=True)

    print("[3/3] 写出 SQL ...")
    if failed:
        print("      跳过 %d 个失败章节，仅写入成功部分" % len(failed))
    good = [c for c in chapters if c.get("content")]
    if not good:
        print("[ERROR] 没有任何章节生成成功，未写文件", file=sys.stderr)
        return 1

    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write(build_sql(good))

    print()
    print("=" * 60)
    print("成功 %d / 失败 %d    总章节 %d" % (ok_count, len(failed), len(chapters)))
    print("输出文件：%s  (%d 字节)" % (args.out, os.path.getsize(args.out)))
    if failed:
        print("失败清单：")
        for course, chapter, err in failed:
            print("  - %s | %s | %s" % (course, chapter, err))
    print("=" * 60)
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
