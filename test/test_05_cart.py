# -*- coding: utf-8 -*-
"""购物车模块：列表/加入/更新/删除。数据来自 test_case.xlsx 的 cart sheet。

说明：更新与删除的"成功路径"不放在 Excel 里 —— 那需要真实的 cartId，
而 cartId 是运行期产生的。这里只覆盖参数与权限类用例，
增删改查的真实闭环由 test_09_flow.py 的流程用例覆盖。
"""

import pytest
from helpers import load_cases, run_case

CASES = load_cases("cart")


@pytest.mark.parametrize("case", CASES, ids=lambda c: f"{c.get('case_id')}-{c.get('title')}")
def test_cart(case, base_url, tokens):
    run_case(case, base_url, tokens)
