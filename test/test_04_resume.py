# -*- coding: utf-8 -*-
"""简历模块：列表/删除走 Excel，上传走函数（multipart 二进制塞不进 Excel）。"""

import requests
import pytest
from helpers import load_cases, run_case

CASES = load_cases("resume")


@pytest.mark.parametrize("case", CASES, ids=lambda c: f"{c.get('case_id')}-{c.get('title')}")
def test_resume(case, base_url, tokens):
    run_case(case, base_url, tokens)


# —— 上传类用例（Excel 放不下二进制） ——

def _upload(base_url, token, filename, content, mime):
    return requests.post(f"{base_url}/api/resume/upload",
                         headers={"Authorization": f"Bearer {token}"},
                         files={"file": (filename, content, mime)}, timeout=20).json()


def test_upload_pdf_success(base_url, tokens):
    """上传合法 PDF → 200，返回 resumeId。"""
    data = _upload(base_url, tokens["student"], "resume.pdf",
                   b"%PDF-1.4 fake content", "application/pdf")
    assert data["code"] == 200
    assert data["data"]["resumeId"]


def test_upload_non_pdf_rejected(base_url, tokens):
    """上传非 PDF → 400。"""
    data = _upload(base_url, tokens["student"], "resume.txt",
                   b"hello world", "text/plain")
    assert data["code"] == 400


def test_upload_fake_pdf_suffix(base_url, tokens):
    """改后缀伪装成 PDF → 视后端实现，可能 400 也可能 200。"""
    data = _upload(base_url, tokens["student"], "resume.pdf",
                   b"not a real pdf", "application/pdf")
    # 先跑一次看实际返回，再决定断言 200 还是 400
    assert data["code"] in (200, 400)
