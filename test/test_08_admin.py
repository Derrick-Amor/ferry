# -*- coding: utf-8 -*-
"""后台管理模块：用户/订单/课程/职位列表与状态变更。数据来自 test_case.xlsx 的 admin sheet。

注意：这个文件必须最后跑（文件名前缀 08 保证），
因为真实的"上下架"会改变课程/职位状态 —— 虽然本 sheet 里只放
"id 不存在"和"禁用后恢复"这类可逆用例，
但顺序一旦打乱仍可能影响前面的模块。
"""

import pytest
from helpers import load_cases, run_case

CASES = load_cases("admin")


@pytest.mark.parametrize("case", CASES, ids=lambda c: f"{c.get('case_id')}-{c.get('title')}")
def test_admin(case, base_url, tokens):
    run_case(case, base_url, tokens)
