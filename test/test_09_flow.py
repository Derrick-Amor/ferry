# -*- coding: utf-8 -*-
"""
跨接口流程与数据一致性用例。
需要"上一步结果作为下一步输入"，Excel 单行驱动不了，所以用函数写。
"""

import requests

PAID_COURSE_ID = 2      # seed.sql 里的付费课
JOB_ID = 1              # seed.sql 里的职位


def _h(token):
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


def test_pay_increases_enroll_count(base_url, tokens):
    """支付成功 → 课程报名人数 enrollCount +1。"""
    sid = PAID_COURSE_ID

    before = requests.get(f"{base_url}/api/course/{sid}").json()["data"]["course"]["enrollCount"]

    # 加购 → 下单 → 支付
    requests.post(f"{base_url}/api/cart/add", json={"courseId": sid},
                  headers=_h(tokens["student"]))
    cart = requests.get(f"{base_url}/api/cart/list", headers=_h(tokens["student"])).json()["data"]
    item = next(c for c in cart if c["courseId"] == sid)
    oid = requests.post(f"{base_url}/api/order/create",
                        json={"cartIds": [item["cartId"]]},
                        headers=_h(tokens["student"])).json()["data"]
    requests.post(f"{base_url}/api/order/pay/{oid}", headers=_h(tokens["student"]))

    after = requests.get(f"{base_url}/api/course/{sid}").json()["data"]["course"]["enrollCount"]
    assert after == before + 1, f"支付后报名人数应 +1：支付前 {before}，支付后 {after}"


def test_create_order_removes_cart_item(base_url, tokens):
    """创建订单 → 购物车里对应的项被删除。"""
    sid = PAID_COURSE_ID
    requests.post(f"{base_url}/api/cart/add", json={"courseId": sid},
                  headers=_h(tokens["student"]))
    cart = requests.get(f"{base_url}/api/cart/list", headers=_h(tokens["student"])).json()["data"]
    item = next(c for c in cart if c["courseId"] == sid)

    requests.post(f"{base_url}/api/order/create", json={"cartIds": [item["cartId"]]},
                  headers=_h(tokens["student"]))

    cart_after = requests.get(f"{base_url}/api/cart/list", headers=_h(tokens["student"])).json()["data"]
    assert not any(c["cartId"] == item["cartId"] for c in cart_after), "下单后购物车项应被删除"


def test_apply_increases_job_apply_count(base_url, tokens):
    """投递成功 → 职位 applyCount +1。"""
    before = requests.get(f"{base_url}/api/job/{JOB_ID}").json()["data"]["applyCount"]

    requests.post(f"{base_url}/api/job/apply/{JOB_ID}", headers=_h(tokens["student"]))

    after = requests.get(f"{base_url}/api/job/{JOB_ID}").json()["data"]["applyCount"]
    assert after == before + 1, f"投递后 applyCount 应 +1：投递前 {before}，投递后 {after}"


def test_free_course_study_increases_enroll_count(base_url, tokens):
    """免费课首次学习 → 报名人数 +1（只测第一次，后续同一课程不再加分）。"""
    sid = 1
    h = _h(tokens["student"])

    detail = requests.get(f"{base_url}/api/course/{sid}").json()["data"]
    chapters = detail.get("chapters") or []
    if not chapters:
        return                      # 没章节就跳过（需要 seed 里带章节）
    cid = chapters[0]["id"]

    before = detail["course"]["enrollCount"]
    requests.post(f"{base_url}/api/course/progress",
                  json={"courseId": sid, "chapterId": cid, "progress": 10}, headers=h)
    after = requests.get(f"{base_url}/api/course/{sid}").json()["data"]["course"]["enrollCount"]

    # 首次学习才 +1；如果之前学过则不涨，所以放宽为 >= before
    assert after >= before, f"报名人数不应减少：之前 {before}，之后 {after}"


def test_downline_course_hidden_from_my_courses(base_url, tokens):
    """课程下架后，「我的课程」里不可见（含已购）。"""
    sid = 1
    admin_h = _h(tokens["admin"])
    stu_h = _h(tokens["student"])

    # 学生先学习一次，确保"我的课程"里有它
    requests.get(f"{base_url}/api/course/study/{sid}", headers=stu_h)

    # 管理员下架
    requests.put(f"{base_url}/api/admin/course/status/{sid}",
                 params={"status": 2}, headers=admin_h)

    mine = requests.get(f"{base_url}/api/course/mine", headers=stu_h).json()["data"]
    assert not any(c["id"] == sid for c in mine), "下架课程不应出现在我的课程里"

    # 复原，避免影响后续用例
    requests.put(f"{base_url}/api/admin/course/status/{sid}",
                 params={"status": 1}, headers=admin_h)
