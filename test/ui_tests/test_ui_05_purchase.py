"""UI 自动化 —— 学生购买付费课端到端闭环

对应用例编号：UI_TC_045 ~ UI_TC_051

覆盖路径（全部通过真实页面点击完成，不调接口）：
    课程详情 -> 加入购物车 -> 购物车 -> 勾选结算 -> 订单详情 -> 立即支付
    -> 我的课程可见 -> 进入学习页

这些用例统一使用 fresh_student_ui：每次都注册一个全新的、没买过课的学生。
复用 student01 的话第二次执行就会因为「已购买」而失败，无法重复运行。
"""

import pytest

from ui_tests.flows import (
    FREE_COURSE_ID,
    FREE_COURSE_NAME,
    PAID_COURSE_ID,
    PAID_COURSE_NAME,
    add_paid_course_to_cart as _add_paid_course_to_cart,
    create_order as _create_order,
)
from ui_tests.pages import CourseStudyPage, MyCoursePage

pytestmark = pytest.mark.ui


# ------------------------------------------------------------------ 购物车

def test_UI_TC_045_加入购物车后跳转且列表含该课程(fresh_student_ui):
    """点击「加入购物车」应提示成功、跳转购物车，且购物车中出现该课程。"""
    cart = _add_paid_course_to_cart(fresh_student_ui)

    assert cart.path == "/cart"
    assert PAID_COURSE_NAME in cart.course_names(), \
        f"购物车中应包含 {PAID_COURSE_NAME}，实际：{cart.course_names()}"


def test_UI_TC_046_取消勾选后无法结算(fresh_student_ui):
    """取消勾选后「已选 N 件」应同步归零，点「去结算」应被拦截。

    【已知缺陷 BUG-002】本用例当前为红，是前端缺陷不是用例问题，详见桌面缺陷报告：
    Cart.vue:44 写的是 :checked="it.checked"（单向绑定），
    点击复选框只改 DOM 的 checked 属性，不会回写 it.checked，于是：
      1. 「已选 {{ checkedItems.length }} 件」恒等于服务端返回的数量，点复选框不动；
      2. checkedItems 恒定，checkout() 里「请先勾选要结算的课程」这条守卫
         在真实浏览器中永远走不到，用户看到的是直接生成订单并跳走；
      3. 用户无法把某门课排除在本次结算之外，只能整条「移除」。
    修复方式：:checked 改成 v-model。
    """
    cart = _add_paid_course_to_cart(fresh_student_ui)
    assert cart.checked_summary_text() == "已选 1 件"

    cart.uncheck_all()
    assert cart.checked_summary_text() == "已选 0 件", \
        "取消勾选后「已选」数量应同步归零（BUG-002：单向绑定导致状态不同步）"

    cart.checkout()
    cart.assert_toast("请先勾选要结算的课程", kind="info")
    assert cart.path == "/cart", "结算被拦截后应停留在购物车页"


def test_UI_TC_047_勾选结算生成订单并跳转详情(fresh_student_ui):
    """勾选课程后结算应生成订单，并跳转到订单详情页。"""
    order, order_id = _create_order(fresh_student_ui)

    assert order_id is not None, "订单 id 应从 URL 中解析出来"
    assert order.path == f"/order/{order_id}"
    assert order.status_text() == "待支付"
    assert order.text(order.TOTAL).startswith("¥")


# ------------------------------------------------------------------ 支付

def test_UI_TC_048_订单详情立即支付成功(fresh_student_ui):
    """订单详情页点击「立即支付」后状态应变为已支付。"""
    order, _ = _create_order(fresh_student_ui)

    order.pay()
    order.assert_toast("支付成功", kind="success")
    order.wait_status("已支付")


def test_UI_TC_049_支付后我的课程可见(fresh_student_ui):
    """支付成功后，「我的课程」中应能看到刚购买的课程。"""
    order, _ = _create_order(fresh_student_ui)
    order.pay()
    order.assert_toast("支付成功", kind="success")
    order.wait_status("已支付")

    my_course = MyCoursePage(fresh_student_ui).load().wait_loaded()
    assert PAID_COURSE_NAME in my_course.course_names(), \
        f"我的课程应包含 {PAID_COURSE_NAME}，实际：{my_course.course_names()}"


def test_UI_TC_050_支付后可进入学习页(fresh_student_ui):
    """支付成功后，付费课的学习页应能正常打开。"""
    order, _ = _create_order(fresh_student_ui)
    order.pay()
    order.assert_toast("支付成功", kind="success")
    order.wait_status("已支付")

    study = CourseStudyPage(fresh_student_ui).load(PAID_COURSE_ID).wait_ready()
    assert study.path == f"/course/study/{PAID_COURSE_ID}"
    assert study.text(study.SIDE_TITLE) == PAID_COURSE_NAME
    assert study.chapter_count() > 0, "学习页应展示章节列表"


# ------------------------------------------------------------------ 免费课

def test_UI_TC_051_免费课可直接学习并保存进度(fresh_student_ui):
    """免费课无需购买即可进入学习页，标记完成后提示进度已保存。"""
    study = CourseStudyPage(fresh_student_ui).load(FREE_COURSE_ID).wait_ready()
    assert study.text(study.SIDE_TITLE) == FREE_COURSE_NAME
    assert study.chapter_count() == 3

    study.mark_done()
    study.assert_toast("进度已保存", kind="success")
