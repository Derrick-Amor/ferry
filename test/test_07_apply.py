# -*- coding: utf-8 -*-
"""申请审核模块：我的申请/待审/已审/审核。数据来自 test_case.xlsx 的 apply sheet。

说明：真正"审核通过/驳回并改变课程职位状态"的用例放在 test_09_flow.py，
因为那需要真实的待审申请 id，而且会把申请处理掉、影响后续用例。
这里只覆盖查询、权限与"申请不存在"类分支。
"""

import pytest
from helpers import load_cases, run_case

CASES = load_cases("apply")


@pytest.mark.parametrize("case", CASES, ids=lambda c: f"{c.get('case_id')}-{c.get('title')}")
def test_apply(case, base_url, tokens):
    run_case(case, base_url, tokens)
