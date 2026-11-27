import pytest
from helpers import load_cases, run_case

CASES = load_cases("course")


@pytest.mark.parametrize("case", CASES, ids=lambda c: f"{c.get('case_id')}-{c.get('title')}")
def test_course(case, base_url, tokens):
    run_case(case, base_url, tokens)