# -*- coding: utf-8 -*-
"""职位模块：列表/详情/投递/面试/申请上下架/候选人。数据来自 data/job.xlsx。"""

import pytest
from helpers import load_cases, run_case

CASES = load_cases("job")


@pytest.mark.parametrize("case", CASES, ids=lambda c: f"{c.get('case_id')}-{c.get('title')}")
def test_job(case, base_url, tokens, resume_id):
    run_case(case, base_url, tokens, resume_id)
