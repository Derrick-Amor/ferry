#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""智慧教育平台 —— 性能测试执行脚本

做三件事：
  1. 生成登录压测用的账号 CSV（与 01_seed_perf_data.sql 造出的 200 个学生一一对应）
  2. 按场景逐个调用 JMeter 非 GUI 模式
  3. 解析每个场景的 JTL，算出平均/P90/P95/P99/错误率/TPS，汇总成表格

用法：
    python run_perf.py                 # 跑全部场景（含登录专测）
    python run_perf.py --only read     # 只跑只读接口
    python run_perf.py --only sat      # 只跑饱和测试
    python run_perf.py --smoke         # 冒烟：3 线程 1 循环，验证脚本本身能跑通
    python run_perf.py --list          # 只列出将要执行的场景，不真跑

前置条件：
    - 后端已启动（localhost:8080）
    - 已执行 sql/01_seed_perf_data.sql 造好压测数据（脚本会自检数据量）
    - 已设置 JMETER_HOME 或 JMeter 装在默认路径 D:\\JMeter\\apache-jmeter-5.6.3

--------------------------------------------------------------------------------
三个容易做错、这里专门处理了的地方
--------------------------------------------------------------------------------
1. 必须预热，且基准测试不能带思考时间
   后端 JVM 的 JIT 编译、HikariCP 的连接池、MySQL 的 buffer pool 在刚启动时
   都是冷的，不预热的话「基准测试」测到的其实是冷启动开销。

   但预热只解决后端那一半。JMeter 自己也是个刚启动的 JVM，它前半段采样同样
   偏慢，而基准测试样本量最小，这些慢样本对平均值的影响最大。
   实测：基准测试若带 100~500ms 思考时间，1 个线程 30 个循环只能采到 30 个
   样本，均值 9.0ms、P90 19ms，明显右偏，甚至比 200 并发时的 4.6ms 还慢 ——
   这个「基准」是失真的。改成关掉思考时间、跑 300 个循环（1800 个样本）之后
   降到 4.20ms，才和饱和测试低负载段的数据对得上。

   结论：基准测试要测的是「没有资源竞争时的纯服务耗时」，思考时间是模拟
   用户节奏用的，与这个目的无关，反而会限制样本量。

2. TPS 必须扣掉爬坡期
   ramp-up 是让线程分批启动的过程，这段时间并发数还没到满。
   拿整个时间窗口算 TPS，会把爬坡期的低并发摊进去。
   极端例子：登录接口 10 秒爬坡 + 0.87 秒实际工作，算出来 47.7 TPS，
   而稳态其实有 570 TPS —— 差一个数量级，结论完全相反。
   所以这里同时给出「TPS（含爬坡）」和「稳态 TPS（爬坡结束后）」。

3. 思考时间会掩盖容量上限
   带 100~500ms 思考时间时，300 并发也只能打出 516 TPS，
   因为限流的是用户的思考时间，不是后端。这组数据回答的是
   「300 个真实用户同时在线会不会卡」，而不是「后端能扛多少」。
   想知道容量上限，必须去掉思考时间单独做饱和测试（--only sat）。
"""

import argparse
import csv
import json
import math
import os
import shutil
import statistics
import subprocess
import sys
import time
from pathlib import Path

# Windows 控制台默认是 GBK，JMeter 的输出里混有 UTF-8 字节，
# 直接 print 会抛 UnicodeEncodeError 把整个脚本打断。统一按 UTF-8 输出。
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

HERE = Path(__file__).resolve().parent
RESULTS_DIR = HERE / "results"

BACKEND = "http://localhost:8080"
JMETER_HOME = Path(os.getenv("JMETER_HOME", r"D:\JMeter\apache-jmeter-5.6.3"))

# 与 01_seed_perf_data.sql 保持一致
PERF_STUDENT_COUNT = 200
PERF_STUDENT_PASSWORD = "123456"

# 真实用户在两屏之间的停顿，JMeter 里按 [think, think+range] 均匀随机取值
THINK, THINK_RANGE = 100, 400


# ============================================================================
#  场景定义
# ============================================================================
#  基准测试：单线程、预热之后。拿到「没有任何资源竞争」的响应时间基线，
#            后面所有并发下的数字都要和它对比才有意义。
#  负载测试：50 并发 + 思考时间。模拟一个班的学生同时在线浏览课程。
#  阶梯测试：并发从 10 逐级升到 300。看 TPS 随并发的变化趋势，
#            以及响应时间在哪个并发数开始抬升。
#  饱和测试：去掉思考时间。让后端真正跑满，找容量上限与拐点。
#  登录专测：登录要跑 BCrypt（CPU 密集），性能特征与只读接口完全不同，
#            混在同一个脚本里会把列表接口的响应时间整体抬高、掩盖瓶颈，
#            所以单独一个脚本、单独一组场景。

STEP_LEVELS = [10, 25, 50, 75, 100, 150, 200, 300]
SAT_READ_LEVELS = [100, 200, 300, 400]
SAT_LOGIN_LEVELS = [50, 100, 200]
LOAD_THREADS = 50
BASE_LOOPS = 300       # 基准测试关思考时间，靠大循环数摊薄 JMeter 自身的预热开销
BASE_LOGIN_LOOPS = 100 # 登录单次约 73ms，循环数取小一些控制总时长
LOAD_LOOPS = 10

SAT_RAMPUP = 15          # 饱和测试的爬坡秒数
SAT_DURATION = 55        # 总时长 = 爬坡 + 40 秒稳态
SAT_THINK = (0, 0)       # 关掉思考时间，否则限流的是用户而不是后端
SAT_LOOPS = 10_000_000   # 饱和测试的循环次数取「等于无限」，实际由 duration 叫停
IDLE_DURATION = 9999     # 循环模式场景给调度器的时长：足够长，让 loops 先结束


def build_scenarios():
    scen = []

    def add(name, kind, threads, rampup, loops, think=THINK, thinkrange=THINK_RANGE,
            time_based=False):
        """time_based=True 表示压满 duration 秒（饱和测试用），忽略 loops。

        饱和测试必须按时长压：固定循环次数在并发数跨度很大时
        （100 并发 vs 400 并发）会产生差别极大的稳态窗口，
        100 并发那档甚至短到不足一秒，算出来的 TPS 没有意义。
        """
        scen.append(dict(
            name=name, kind=kind, threads=threads, rampup=rampup, loops=loops,
            think=think, thinkrange=thinkrange, time_based=time_based,
            duration=SAT_DURATION if time_based else 0,
            order=len(scen),
        ))

    # ---- 只读接口 ----
    # 基准测试关掉思考时间、并把循环次数拉到几百：
    #   1) 基准要测的是「没有资源竞争时的纯服务耗时」，思考时间是模拟用户节奏用的，
    #      跟这个目的无关；
    #   2) 只跑几十个样本的话，JMeter 自己那个新 JVM 的预热开销会显著抬高平均值
    #      （实测 30 个样本时均值 9.0ms、P90 19ms，明显右偏），
    #      循环数够大才能把它摊薄。
    add("基准_read_1并发", "read", 1, 1, BASE_LOOPS, think=0, thinkrange=0)
    add(f"负载_read_{LOAD_THREADS}并发", "read", LOAD_THREADS, 10, LOAD_LOOPS)
    for t in STEP_LEVELS:
        # ramp-up 不超过 15 秒：阶梯测试关心的是「稳定后」的表现，
        # 爬坡太慢会让整体平均被爬坡期拉低，掩盖高并发段的劣化。
        add(f"阶梯_read_{t:03d}并发", "read", t, min(t // 2, 15), LOAD_LOOPS)
    for t in SAT_READ_LEVELS:
        add(f"饱和_read_{t:03d}并发", "read", t, SAT_RAMPUP, 0,
            think=SAT_THINK[0], thinkrange=SAT_THINK[1], time_based=True)

    # ---- 登录接口 ----
    add("基准_login_1并发", "login", 1, 1, BASE_LOGIN_LOOPS)
    add(f"负载_login_{LOAD_THREADS}并发", "login", LOAD_THREADS, 5, 50)
    for t in SAT_LOGIN_LEVELS:
        add(f"饱和_login_{t:03d}并发", "login", t, SAT_RAMPUP, 0,
            think=SAT_THINK[0], thinkrange=SAT_THINK[1], time_based=True)

    return scen


JMX = {
    "read": HERE / "perf_read.jmx",
    "login": HERE / "perf_login.jmx",
}

# --only 的取值 → 场景名前缀
ONLY_MAP = {
    "base": ("基准_",),
    "read": ("基准_read", "负载_read", "阶梯_read"),
    "login": ("基准_login", "负载_login"),
    "sat": ("饱和_",),
}

# 预热参数。必须压得足够久 —— JIT 的 C2 编译要在持续负载下才会触发，
# 随便跑几百个请求是热不起来的：实测 20 线程 × 5 循环（600 请求）的预热之后，
# 1 并发的基准响应时间仍有 9.0ms，比 200 并发时的 4.6ms 还慢，
# 说明测到的还是「半热」状态。这里改成 50 线程 × 100 循环（约 3 万请求）。
WARM_THREADS = 50
WARM_LOOPS = 100


# ============================================================================
#  前置检查
# ============================================================================

def jmeter_bin():
    """定位 JMeter 启动器。Windows 用 jmeter.bat，其它平台用 bin/jmeter。"""
    if os.name == "nt":
        p = JMETER_HOME / "bin" / "jmeter.bat"
    else:
        p = JMETER_HOME / "bin" / "jmeter"
    if not p.exists():
        sys.exit(
            f"\n找不到 JMeter 启动器：{p}\n"
            f"请设置环境变量 JMETER_HOME 指向 JMeter 安装目录，例如：\n"
            f"    set JMETER_HOME=D:\\JMeter\\apache-jmeter-5.6.3\n"
        )
    return p


def check_backend_and_data():
    """后端在不在？压测数据造了没？

    数据量不达标必须当场拦下来：拿 2 门课的数据压分页接口，
    MySQL 全表扫 2 行，测出来的响应时间恒定在亚毫秒，
    「分页」「筛选」的性能特征完全体现不出来，白跑一趟。
    """
    import requests

    try:
        r = requests.get(f"{BACKEND}/api/course/list",
                         params={"page": 1, "size": 1}, timeout=10).json()
    except Exception as e:
        sys.exit(f"\n后端未就绪：{BACKEND}\n  原因：{e}\n"
                 f"  请先启动 Spring Boot 后端。\n")

    total = (r.get("data") or {}).get("total", 0)
    print(f"后端就绪，课程总数 = {total}")

    if total < 500:
        print()
        print("!" * 72)
        print(f"  压测数据量偏小（课程总数 {total} < 500）。")
        print("  请在 Navicat 中执行 perf_tests/sql/01_seed_perf_data.sql 造数据后重跑，")
        print("  否则分页/筛选/搜索的性能差异测不出来。")
        print("!" * 72)
        print()
        if total < 100:
            sys.exit("  数据量过小，已中止。")
    return total


def write_users_csv(path):
    """生成登录压测账号。

    每个线程必须用不同账号：所有线程抢同一个账号，后端可能命中
    用户级缓存或行锁，测出来的是锁竞争而不是登录接口本身的开销。
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["username", "password"])          # CSV Data Set 里设了 ignoreFirstLine
        for i in range(1, PERF_STUDENT_COUNT + 1):
            w.writerow([f"perf_stu{i:04d}", PERF_STUDENT_PASSWORD])
    print(f"账号文件已生成：{path}（{PERF_STUDENT_COUNT} 个账号）")


# ============================================================================
#  执行 JMeter
# ============================================================================

def run_jmeter(scen, users_csv, verbose):
    name = scen["name"]
    out_dir = RESULTS_DIR / name
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True)

    jtl = out_dir / "result.jtl"
    report = out_dir / "report"
    log = out_dir / "jmeter.log"

    cmd = [
        str(jmeter_bin()),
        "-n",                                   # 非 GUI
        "-t", str(JMX[scen["kind"]]),
        "-l", str(jtl),
        "-j", str(log),
        "-e", "-o", str(report),                # 跑完顺手生成 HTML 看板
        "-Jhost=localhost",
        "-Jport=8080",
        f"-Jthreads={scen['threads']}",
        f"-Jrampup={scen['rampup']}",
        f"-Jloops={SAT_LOOPS if scen['time_based'] else (scen['loops'] or 1)}",
        f"-Jthink={scen['think']}",
        f"-Jthinkrange={scen['thinkrange']}",
        f"-Jduration={scen['duration'] if scen['time_based'] else IDLE_DURATION}",
        f"-Jcsvfile={users_csv}",
        "-Jsampleresult.default.encoding=UTF-8",
    ]

    mode = (f"压满 {scen['duration']}s" if scen["time_based"]
            else f"每线程 {scen['loops']} 循环")
    print(f"  → {scen['threads']} 并发 / ramp-up {scen['rampup']}s / {mode} / "
          f"思考 {scen['think']}~{scen['think'] + scen['thinkrange']}ms ...",
          end="", flush=True)

    t0 = time.time()
    proc = subprocess.run(cmd, shell=(os.name == "nt"),
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    elapsed = time.time() - t0

    if proc.returncode != 0 or not jtl.exists():
        print(" 失败")
        tail = proc.stdout.decode("utf-8", "replace").strip().splitlines()[-15:]
        print("  JMeter 输出（末尾）：")
        for line in tail:
            print("    " + line)
        return None

    print(f" 完成（{elapsed:.0f}s）")
    if verbose:
        for line in proc.stdout.decode("utf-8", "replace").splitlines():
            if "summary" in line.lower() or "err" in line.lower():
                print("    " + line)
    return jtl


# ============================================================================
#  解析 JTL
# ============================================================================

def percentile(sorted_vals, p):
    """「最近秩」算法：取第 ceil(p% × N) 个值。

    小样本下比线性插值更保守，也更符合「95% 的请求都快于这个数」的直觉表述。
    """
    if not sorted_vals:
        return 0.0
    k = max(0, math.ceil(p / 100.0 * len(sorted_vals)) - 1)
    return sorted_vals[min(k, len(sorted_vals) - 1)]


def _block(rows):
    """一组采样 → 统计指标。"""
    elapsed = sorted(int(r["elapsed"]) for r in rows)
    ok = sum(1 for r in rows if r["success"].strip().lower() == "true")

    # 吞吐窗口：从最早一个请求开始，到最晚一个请求结束。
    # 不用「最后一个时间戳 - 第一个时间戳」，否则末尾那批请求的
    # 耗时会被算在分母之外，TPS 略偏高。
    start = min(int(r["timeStamp"]) for r in rows)
    end = max(int(r["timeStamp"]) + int(r["elapsed"]) for r in rows)
    window = max((end - start) / 1000.0, 0.001)

    return {
        "样本数": len(rows),
        "平均ms": statistics.mean(elapsed),
        "P90ms": percentile(elapsed, 90),
        "P95ms": percentile(elapsed, 95),
        "P99ms": percentile(elapsed, 99),
        "最大ms": elapsed[-1],
        "错误数": len(rows) - ok,
        "错误率": (len(rows) - ok) / len(rows) * 100.0,
        "TPS": len(rows) / window,
    }, start


def parse_jtl(jtl_path, rampup):
    """把 JTL 解析成 {label: 统计}。

    JTL 是 JMeter 的 CSV 采样日志，列名由 JMeter 自己写表头，
    这里按表头动态取值，不写死列序号，换 JMeter 版本也不会崩。

    每个 label 出两行 TPS：
      TPS     —— 整个窗口（含爬坡期）
      稳态TPS —— 只统计爬坡结束之后的采样。爬坡期并发还没到满，
                 混进去会把吞吐严重低估（登录接口就是典型）。
    """
    with open(jtl_path, "r", newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    if not rows:
        return {}, {}

    def steady(rows_subset, global_start):
        """爬坡结束后的子集；若为空（跑得太短）就退回全集。"""
        boundary = global_start + rampup * 1000
        sub = [r for r in rows_subset if int(r["timeStamp"]) >= boundary]
        return sub if sub else rows_subset

    buckets = {}
    for r in rows:
        buckets.setdefault(r["label"].strip(), []).append(r)

    global_start = min(int(r["timeStamp"]) for r in rows)

    stats = {}
    for label, rs in buckets.items():
        s, start = _block(rs)
        s["稳态TPS"] = _block(steady(rs, global_start))[0]["TPS"] if len(rs) > 1 else s["TPS"]
        stats[label] = s

    # 汇总行：所有请求合在一起看
    s, _ = _block(rows)
    s["稳态TPS"] = _block(steady(rows, global_start))[0]["TPS"]
    stats["【合计】"] = s

    total_end = max(int(r["timeStamp"]) + int(r["elapsed"]) for r in rows)
    return stats, {"起点": global_start, "终点": total_end,
                   "窗口s": (total_end - global_start) / 1000.0,
                   "总请求数": len(rows)}


# ============================================================================
#  输出
# ============================================================================

COLS = ["样本数", "平均ms", "P90ms", "P95ms", "P99ms", "最大ms",
        "错误数", "错误率", "TPS", "稳态TPS"]


def print_table(stats):
    if not stats:
        print("    （无数据）")
        return
    header = f"    {'接口':<20}" + "".join(f"{c:>11}" for c in COLS)
    print(header)
    print("    " + "-" * (len(header) - 4))
    for label, s in stats.items():
        line = f"    {label:<20}"
        for c in COLS:
            v = s[c]
            line += f"{v:>11.2f}" if isinstance(v, float) else f"{v:>11}"
        print(line)


def save_scenario(scen, stats, meta):
    """把场景配置和统计结果落盘。

    汇总时扫描目录而不是只用内存里的 records，这样
    「只重跑饱和测试，其余场景沿用上次结果」也能生成完整汇总表。
    """
    d = RESULTS_DIR / scen["name"]
    d.mkdir(parents=True, exist_ok=True)
    with open(d / "scenario.json", "w", encoding="utf-8") as f:
        json.dump({**scen, "stats": stats, "meta": meta}, f,
                  ensure_ascii=False, indent=2)


def load_records():
    """扫描 results/ 下所有场景目录，按定义顺序返回。

    「预热」目录不写 scenario.json，所以天然不会被收录。
    """
    records = []
    for d in sorted(RESULTS_DIR.glob("*/scenario.json")):
        with open(d, encoding="utf-8") as f:
            records.append(json.load(f))
    records.sort(key=lambda r: r.get("order", 999))
    return records


def write_summary(records):
    """汇总成 CSV + Markdown，方便直接往报告里贴。"""
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    csv_path = RESULTS_DIR / "summary.csv"
    with open(csv_path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["场景", "脚本", "并发", "接口"] + COLS)
        for rec in records:
            for label, s in rec["stats"].items():
                w.writerow([rec["name"], rec["kind"], rec["threads"], label]
                           + [round(s[c], 2) if isinstance(s[c], float) else s[c]
                              for c in COLS])

    md_path = RESULTS_DIR / "summary.md"
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("# 性能测试结果汇总\n\n")
        f.write(f"执行时间：{time.strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        f.write("| 场景 | 并发 | 接口 | 样本数 | 平均(ms) | P90 | P95 | P99 | "
                "最大(ms) | 错误率 | TPS | 稳态TPS |\n")
        f.write("|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n")
        for rec in records:
            for label, s in rec["stats"].items():
                f.write(f"| {rec['name']} | {rec['threads']} | {label} | {s['样本数']} | "
                        f"{s['平均ms']:.1f} | {s['P90ms']:.0f} | {s['P95ms']:.0f} | "
                        f"{s['P99ms']:.0f} | {s['最大ms']} | {s['错误率']:.2f}% | "
                        f"{s['TPS']:.1f} | {s['稳态TPS']:.1f} |\n")

    return csv_path, md_path


def print_curve(records, prefix, title):
    """并发 → TPS / P95 曲线，用来看拐点：TPS 不再增长、延迟开始抬升的位置。"""
    group = [r for r in records if r["name"].startswith(prefix)]
    if not group:
        return
    group.sort(key=lambda r: r["threads"])

    print()
    print(f"  {title}")
    print(f"    {'并发':>6}{'稳态TPS':>12}{'平均ms':>11}{'P95ms':>11}{'错误率':>11}   ")
    print("    " + "-" * 55)
    prev_tps = None
    for r in group:
        s = r["stats"].get("【合计】")
        if not s:
            continue
        note = ""
        if prev_tps is not None and prev_tps > 0:
            delta = (s["稳态TPS"] - prev_tps) / prev_tps * 100
            if delta < 10:
                note = f"  ← 吞吐仅增 {delta:.0f}%"
        print(f"    {r['threads']:>6}{s['稳态TPS']:>12.1f}{s['平均ms']:>11.1f}"
              f"{s['P95ms']:>11.0f}{s['错误率']:>10.2f}%{note}")
        prev_tps = s["稳态TPS"]


# ============================================================================
#  主流程
# ============================================================================

def main():
    ap = argparse.ArgumentParser(description="智慧教育平台性能测试执行脚本")
    ap.add_argument("--only", choices=sorted(ONLY_MAP),
                    help="只跑指定类别的场景")
    ap.add_argument("--smoke", action="store_true",
                    help="冒烟模式：3 并发 1 循环，验证脚本能跑通")
    ap.add_argument("--no-warmup", action="store_true", help="跳过预热（调试用）")
    ap.add_argument("--resummarize", action="store_true",
                    help="不压测，只按 results/ 下已有结果重新生成汇总表")
    ap.add_argument("--list", action="store_true", help="只列出场景，不执行")
    ap.add_argument("-v", "--verbose", action="store_true", help="打印 JMeter 摘要")
    args = ap.parse_args()

    def blank(name, kind):
        return dict(name=name, kind=kind, threads=1, rampup=1, loops=1,
                    think=0, thinkrange=0, time_based=False, duration=0, order=0)

    if args.resummarize:
        records = load_records()
        if not records:
            sys.exit(f"{RESULTS_DIR} 下没有找到任何 scenario.json，先跑一次压测。")
        csv_path, md_path = write_summary(records)
        print(f"已重新生成汇总（{len(records)} 个场景）：\n  {csv_path}\n  {md_path}")
        return

    if args.smoke:
        kinds = [args.only] if args.only else ["read", "login"]
        scenarios = [dict(blank(f"冒烟_{k}", k if k in JMX else "read"),
                          threads=3, loops=1)
                     for k in kinds]
    else:
        scenarios = build_scenarios()
        if args.only:
            prefixes = ONLY_MAP[args.only]
            scenarios = [s for s in scenarios if s["name"].startswith(prefixes)]

    if args.list:
        for s in scenarios:
            mode = (f"压满{s['duration']}s" if s["time_based"]
                    else f"{s['loops']}循环")
            print(f"  {s['name']:<28} {s['kind']:<6} 并发={s['threads']:<4} "
                  f"rampup={s['rampup']:<3} {mode:<10} "
                  f"think={s['think']}~{s['think'] + s['thinkrange']}ms")
        return

    print("=" * 76)
    print("  智慧教育平台 —— 性能测试")
    print("=" * 76)

    jmeter_bin()
    check_backend_and_data()

    users_csv = HERE / "users.csv"
    write_users_csv(users_csv)

    # ---- 预热 ----
    # 结果直接丢掉，目的是把 JVM JIT、连接池、MySQL buffer pool 捂热。
    # 不预热的话基准测试测的是冷启动开销，不是接口性能。
    if not args.no_warmup:
        print()
        print("[预热]（结果不计入报告，仅用于消除冷启动影响）")
        warm = dict(blank("预热", "read"), threads=WARM_THREADS, rampup=5,
                    loops=WARM_LOOPS)
        run_jmeter(warm, users_csv, args.verbose)

    print()
    print(f"共 {len(scenarios)} 个场景，结果输出到 {RESULTS_DIR}")
    print()

    failed = []
    for s in scenarios:
        print(f"[{s['name']}]")
        jtl = run_jmeter(s, users_csv, args.verbose)
        if jtl is None:
            failed.append(s["name"])
            continue
        stats, meta = parse_jtl(jtl, s["rampup"])
        print_table(stats)
        save_scenario(s, stats, meta)
        print()

    records = load_records()
    if not records:
        sys.exit("没有任何场景成功完成，请检查 jmeter.log。")

    print_curve(records, "阶梯_read", "阶梯加压曲线（只读接口，带思考时间）")
    print_curve(records, "饱和_read", "饱和曲线（只读接口，无思考时间）")
    print_curve(records, "饱和_login", "饱和曲线（登录接口）")

    csv_path, md_path = write_summary(records)

    print()
    print("=" * 76)
    print(f"  完成：{len(records)} 个场景")
    print(f"  汇总 CSV    ：{csv_path}")
    print(f"  汇总 Markdown：{md_path}")
    print(f"  HTML 看板   ：{RESULTS_DIR}\\<场景名>\\report\\index.html")
    if failed:
        print(f"  失败场景    ：{', '.join(failed)}")
    print("=" * 76)


if __name__ == "__main__":
    main()
