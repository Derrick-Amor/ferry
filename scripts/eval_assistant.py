#!/usr/bin/env python3
"""助教评测：岗位咨询的命中/拒答 + 学习答疑的可用性。

为什么要单独写这个脚本：
「效果不错」在面试里毫无说服力，能说出来的是「16 条岗位题 hit@4 = 87.5%，
语料外问题拒答 5/5，答疑 6/6 有实质回答」。而且拒答阈值不该拍脑袋——
它必须落在「最低命中分」和「最高误答分」之间，这个脚本会把分布打出来。

两条链路的判定标准不同：
  - job：必须命中正确的岗位（标题匹配），语料外必须拒答
  - tutor：不要求引用，也不该拒答；只看有没有实质内容

用法：
    export FERRY_PASSWORD=<演示口令>
    python scripts/eval_assistant.py
    python scripts/eval_assistant.py --base-url http://localhost:8088

依赖：仅标准库。走的是和前端完全相同的 SSE 接口，没有后门。
"""
import argparse
import json
import os
import sys
import urllib.request

# kind:
#   job-hit    期望命中的岗位（expect 为标题关键字）
#   job-reject 岗位语料里没有，应当拒答
#   tutor      学习答疑，应当给出实质回答
CASES = [
    # ---- 岗位：应当命中 ----
    {"kind": "job-hit", "q": "北京有哪些 Java 后端岗位？", "expect": "Java"},
    {"kind": "job-hit", "q": "深圳有算法相关的工作吗？", "expect": "算法"},
    {"kind": "job-hit", "q": "上海有没有应届生能投的后端岗？", "expect": "应届"},
    {"kind": "job-hit", "q": "杭州的数据库岗位薪资大概多少？", "expect": "数据库"},
    {"kind": "job-hit", "q": "成都有产品经理的岗位吗？", "expect": "产品经理"},
    {"kind": "job-hit", "q": "苏州有没有前端开发的机会？", "expect": "前端"},
    {"kind": "job-hit", "q": "云启数据技术有限公司在招哪些岗位？", "expect": "云启"},
    {"kind": "job-hit", "q": "有没有实习或者校招的机会？", "expect": "实习"},
    # 这条走的是「解析不出结构化条件」的路径：iOS/移动开发不在关键词映射表里，
    # 只能靠语义与阈值。用来守阈值那条兜底逻辑不被改坏。
    {"kind": "job-hit", "q": "有没有 iOS 开发的岗位？", "expect": "iOS"},
    # 「设计」刻意没有被映射成关键词（会误伤「芯片设计」），
    # 所以这条要求用「设计师」这种更具体的说法来命中
    {"kind": "job-hit", "q": "有没有 UI 设计师的岗位？", "expect": "设计师"},

    # ---- 岗位：应当拒答 ----
    {"kind": "job-reject", "q": "有没有游戏策划的岗位？"},
    {"kind": "job-reject", "q": "西安有在招的岗位吗？"},
    {"kind": "job-reject", "q": "有没有芯片设计相关的职位？"},
    {"kind": "job-reject", "q": "这些公司提供期权和股票吗？"},
    {"kind": "job-reject", "q": "有没有可以远程办公的岗位？"},

    # ---- 学习答疑：应当给出实质回答（不拒答、不需要引用）----
    {"kind": "tutor", "q": "Java 的多态怎么理解？"},
    {"kind": "tutor", "q": "什么是闭包？能举个例子吗？"},
    {"kind": "tutor", "q": "面试的时候该怎么介绍自己的项目？"},
    {"kind": "tutor", "q": "MySQL 索引失效一般有哪些原因？"},
    {"kind": "tutor", "q": "Spring 的循环依赖是怎么解决的？"},
    {"kind": "tutor", "q": "零基础学编程，先学 Java 还是 Python？"},
]


def login(base_url, user, password):
    """登录取 token。"""
    body = json.dumps({"username": user, "password": password}).encode()
    req = urllib.request.Request(base_url + "/api/user/login", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        payload = json.loads(resp.read())
    if payload.get("code") != 200:
        raise RuntimeError("登录失败：" + str(payload.get("message")))
    return payload["data"]["token"]


def ask(base_url, token, question, scope):
    """调一次问答，返回 (citations, top_score, rejected, answer)。"""
    body = json.dumps({"question": question, "scope": scope}).encode()
    req = urllib.request.Request(
        base_url + "/api/assistant/chat", data=body,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + token})

    citations, top_score, rejected, answer = [], 0.0, False, []
    with urllib.request.urlopen(req, timeout=180) as resp:
        event = None
        for raw in resp:
            line = raw.decode("utf-8").rstrip("\r\n")
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                try:
                    payload = json.loads(line[5:])
                except json.JSONDecodeError:
                    continue
                if event == "citations":
                    citations = payload.get("citations") or []
                    top_score = payload.get("topScore") or 0.0
                    rejected = bool(payload.get("rejected"))
                elif event == "delta":
                    answer.append(payload.get("text", ""))
            elif line == "":
                event = None
    return citations, top_score, rejected, "".join(answer)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8088")
    parser.add_argument("--user", default="student01")
    parser.add_argument("--password", default=os.environ.get("FERRY_PASSWORD", ""))
    args = parser.parse_args()

    if not args.password:
        print("缺少口令：请设置环境变量 FERRY_PASSWORD，或用 --password 传入", file=sys.stderr)
        return 2

    token = login(args.base_url, args.user, args.password)
    print(f"已登录 {args.user}，开始评测 {len(CASES)} 条\n")
    print(f"{'#':>2}  {'类型':<10} {'实际 top1':<30} {'分数':>6}  结果")
    print("-" * 76)

    job_hits = job_total = 0
    rejects = reject_total = 0
    tutors = tutor_total = 0
    pos_scores, neg_scores = [], []

    for i, case in enumerate(CASES, 1):
        scope = "tutor" if case["kind"] == "tutor" else "job"
        citations, score, rejected, answer = ask(args.base_url, token, case["q"], scope)
        top1 = citations[0]["title"] if citations else "(无引用)"

        if case["kind"] == "job-hit":
            job_total += 1
            ok = any(case["expect"] in c["title"] for c in citations)
            job_hits += ok
            if not rejected:
                pos_scores.append(score)
            mark = "命中" if ok else "未命中"
        elif case["kind"] == "job-reject":
            reject_total += 1
            rejects += rejected
            neg_scores.append(score)
            mark = "已拒答" if rejected else "未拒答"
        else:
            tutor_total += 1
            # 实质回答的标准：没被拒、且正文不短于 80 字
            ok = (not rejected) and len(answer.strip()) >= 80 and not citations
            tutors += ok
            mark = "有回答" if ok else ("被拒答" if rejected else "内容过短")

        print(f"{i:>2}  {case['kind']:<10} {top1[:28]:<30} {score:>6.3f}  {mark}")

    print("-" * 76)
    if job_total:
        print(f"岗位命中 hit@4 : {job_hits}/{job_total} = {job_hits / job_total:.1%}")
    if reject_total:
        print(f"岗位拒答准确率 : {rejects}/{reject_total} = {rejects / reject_total:.1%}")
    if tutor_total:
        print(f"答疑可用率     : {tutors}/{tutor_total} = {tutors / tutor_total:.1%}")

    if pos_scores:
        print(f"命中样本分数   : min={min(pos_scores):.3f}  max={max(pos_scores):.3f}")
    if neg_scores:
        print(f"应拒答样本分数 : min={min(neg_scores):.3f}  max={max(neg_scores):.3f}")
    if pos_scores and neg_scores and min(pos_scores) > max(neg_scores):
        mid = (min(pos_scores) + max(neg_scores)) / 2
        print(f"建议阈值       : {mid:.3f}（介于最差命中 {min(pos_scores):.3f} 与最高误答 {max(neg_scores):.3f} 之间）")
    elif pos_scores and neg_scores:
        print("注意：命中与误答的分数区间有重叠，单靠阈值无法完全分开，需要调整语料或加规则。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
