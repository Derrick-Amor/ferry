# -*- coding: utf-8 -*-
"""订单模块：创建/列表/详情/支付/取消。数据来自 test_case.xlsx 的 order sheet。

说明：下单→支付→取消 的完整链路需要上一步返回的 orderId，
Excel 单行驱动不了，放在 test_09_flow.py 里。
这里只覆盖参数校验、权限与"订单不存在"类分支。
"""

import pytest
from helpers import load_cases, run_case

CASES = load_cases("order")


@pytest.mark.parametrize("case", CASES, ids=lambda c: f"{c.get('case_id')}-{c.get('title')}")
def test_order(case, base_url, tokens):
    run_case(case, base_url, tokens)
