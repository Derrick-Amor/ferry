# -*- coding: utf-8 -*-
import pytest
from helpers import load_cases, run_case

CASES = load_cases("user")


@pytest.mark.parametrize("case", CASES, ids=lambda c: f"{c.get('case_id')}-{c.get('title')}")
def test_user(case, base_url, tokens):
    run_case(case, base_url, tokens)
