# -*- coding: utf-8 -*-
import json
import os
import requests
import pandas as pd

_HERE = os.path.dirname(os.path.abspath(__file__))

# 数据文件：优先用 data/test_case.xlsx，没有就找同级目录下的 test_case.xlsx
EXCEL_PATH = next(
    (p for p in (os.path.join(_HERE, "data", "test_case.xlsx"),
                 os.path.join(_HERE, "test_case.xlsx"))
     if os.path.exists(p)),
    os.path.join(_HERE, "data", "test_case.xlsx"),
)


def _s(v):
    # 单元格转"去空格字符串"，空返回 None。
    v = str(v).strip() if v is not None else ""
    return v or None


# 表头容错：左边是可能被写错的形式，右边是代码里用的标准名。
# 中文输入法、复制粘贴很容易把 expect 打成 except，这里统一纠正，
# 免得因为一个字母让整个 sheet 报 KeyError。
_COLUMN_ALIAS = {
    "except_code": "expect_code",
    "expec_code": "expect_code",
    "expectcode": "expect_code",
    "exceptcode": "expect_code",
    "expect_msg": "expect_message",
    "except_message": "expect_message",
    "respose": "body",
    "request_body": "body",
    "param": "params",
    "query": "params",
    "caseid": "case_id",
    "id": "case_id",
    "actor": "role",
    "user": "role",
}


def _normalize_columns(df):
    """把表头去 BOM、去空格、小写，再套用别名表。

    为什么需要：Excel 另存为 CSV 或从网页复制时，
    第一个表头前面常会带一个看不见的 BOM 字符（﻿），
    导致 df.columns 变成 '﻿case_id'，代码里取 case_id 就是 None。
    """
    df.columns = [
        _COLUMN_ALIAS.get(
            str(c).strip().lstrip("﻿").lower().replace(" ", "_").replace("-", "_"),
            str(c).strip().lstrip("﻿"),
        )
        for c in df.columns
    ]
    return df


def load_cases(sheet):
    # 读 Excel 某个 sheet，返回用例列表。
    df = pd.read_excel(EXCEL_PATH, sheet_name=sheet, dtype=str, keep_default_na=False)
    df = _normalize_columns(df).fillna("")

    # 表头写错时给一句人话，而不是让 pandas 抛 KeyError
    required = {"case_id", "method", "path", "expect_code"}
    missing = required - set(df.columns)
    assert not missing, (
        f"sheet「{sheet}」缺少列 {sorted(missing)}，实际表头是 {list(df.columns)}"
    )

    # 整行空白是 Excel 里常见的"手滑多敲回车"，过滤掉
    df = df[df["case_id"].astype(str).str.strip() != ""]
    return [row.to_dict() for _, row in df.iterrows()]


def _resolve(text, resume_id):
    # 把 {{resume_id}} 换成真实 id。
    if not text or resume_id is None:
        return text
    return text.replace("{{resume_id}}", str(resume_id))


def _get(obj, path):
    # 点号路径取值，支持 list 下标：data.list.0.id
    cur = obj
    for part in path.split("."):
        if isinstance(cur, list) and part.isdigit():
            cur = cur[int(part)]
        elif isinstance(cur, dict):
            cur = cur.get(part)
        else:
            return None
    return cur


def _assert_field(result, expr):
    """data 字段断言： "路径 操作符 [参数]"，多组用 ; 分隔。"""
    path, op, *rest = expr.split(" ")
    val = _get(result, path)
    arg = rest[0] if rest else None
    if op == "not_null":
        assert val is not None, f"[{path}] 期望非空，实际 None"
    elif op == "not_empty":
        assert val is not None and len(val) > 0, f"[{path}] 期望非空集合"
    elif op == ">":
        assert val is not None and val > float(arg), f"[{path}] 期望 > {arg}，实际 {val}"
    elif op == ">=":
        assert val is not None and val >= float(arg), f"[{path}] 期望 >= {arg}，实际 {val}"
    elif op == "==":
        # 忽略大小写：Python 的 True/False 字符串化是大写开头，
        # 而 Excel 里手写的是 true/false，不忽略的话永远不相等。
        assert str(val).lower() == str(arg).lower(), \
            f"[{path}] 期望 == {arg}，实际 {val}"
    else:
        raise AssertionError(f"不支持的断言: {expr}")


def run_case(case, base_url, tokens, resume_id=None):
    """
    执行一条 Excel 用例：组装请求 → 发请求 → 断言 code / message / data。

    只有用到 {{resume_id}} 的测试文件需要传 resume_id，
    其余文件不传就行（默认 None，替换逻辑自动跳过）。
    """
    method = (_s(case.get("method")) or "GET").upper()
    path = _resolve(_s(case.get("path")) or "", resume_id)
    url = base_url + path

    headers = {"Content-Type": "application/json"}
    role = _s(case.get("role")) or "none"
    if role in tokens and tokens[role]:
        headers["Authorization"] = f"Bearer {tokens[role]}"
    extra = _s(case.get("headers"))
    if extra:
        headers.update(json.loads(extra))

    params_raw = _resolve(_s(case.get("params")), resume_id)
    body_raw = _resolve(_s(case.get("body")), resume_id)
    params = json.loads(params_raw) if params_raw else None
    body = json.loads(body_raw) if body_raw else None

    resp = requests.request(method, url, headers=headers,
                            params=params, json=body, timeout=20)
    result = resp.json()

    expect_code = int(float(case["expect_code"]))
    assert result.get("code") == expect_code, \
        f"code 断言失败：期望 {expect_code}，实际 {result}"

    msg = _s(case.get("expect_message"))
    if msg:
        assert msg in str(result.get("message", "")), \
            f"message 断言失败：期望包含「{msg}」，实际「{result.get('message')}」"

    field = _s(case.get("expect_field"))
    if field:
        for expr in field.split(";"):
            _assert_field(result, expr.strip())
