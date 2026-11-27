"""UI 自动化 —— 订单列表与状态流转（views/order/OrderList.vue、OrderDetail.vue）

对应用例编号：UI_TC_052 ~ UI_TC_057

注意列表页与详情页的按钮文案不同，别搞混：
    列表页：支付 / 取消 / 详情
    详情页：立即支付 / 取消订单 / 去学习
"""

import pytest

from ui_tests.flows import PAID_COURSE_NAME, create_order
from ui_tests.pages import OrderDetailPage, OrderListPage

pytestmark = pytest.mark.ui


def test_UI_TC_052_订单列表页正常渲染(fresh_student_ui):
    """订单列表页应有标题与五列表头。

    必须先造一条订单：OrderList.vue 在订单为空时整张表都不渲染，
    只显示空态，表头断言会拿到 []。
    """
    create_order(fresh_student_ui)

    page = OrderListPage(fresh_student_ui).load()
    page.wait_loaded()

    assert page.path == "/order"
    assert page.text(page.TITLE) == "我的订单"
    assert page.header_texts() == ["订单号", "金额", "状态", "创建时间", "操作"]


def test_UI_TC_053_新订单在列表展示为待支付(fresh_student_ui):
    """刚创建的订单应出现在列表中，状态为待支付并带支付/取消按钮。"""
    create_order(fresh_student_ui)

    page = OrderListPage(fresh_student_ui).load()
    page.wait_loaded()

    assert page.row_count() >= 1, "订单列表应有至少一条记录"
    assert "待支付" in page.row_statuses()
    assert page.visible(page.BTN_PAY_ROW), "待支付订单应显示支付按钮"
    assert page.visible(page.BTN_CANCEL_ROW), "待支付订单应显示取消按钮"
    assert page.visible(page.BTN_DETAIL_ROW), "任何订单都应显示详情按钮"


def test_UI_TC_054_列表页取消订单(fresh_student_ui):
    """在列表页取消待支付订单后，状态应变为已取消且操作按钮消失。"""
    create_order(fresh_student_ui)

    page = OrderListPage(fresh_student_ui).load()
    page.wait_loaded()
    page.cancel_first()
    page.assert_toast("已取消", kind="success")

    page.wait_row_status("已取消")
    assert "待支付" not in page.row_statuses()
    assert page.absent(page.BTN_PAY_ROW), "取消后不应再显示支付按钮"


def test_UI_TC_055_按状态筛选订单(fresh_student_ui):
    """按状态筛选后列表中只应剩下该状态的订单。"""
    create_order(fresh_student_ui)

    page = OrderListPage(fresh_student_ui).load()
    page.wait_loaded()
    page.cancel_first()
    page.assert_toast("已取消", kind="success")
    page.wait_row_status("已取消")

    page.filter_status("已取消").wait_loaded()
    statuses = page.row_statuses()
    assert statuses, "「已取消」筛选下应有记录"
    assert set(statuses) == {"已取消"}, f"筛选结果混入了其他状态：{statuses}"

    page.filter_status("已支付").wait_loaded()
    assert page.row_count() == 0, "未支付过任何订单，筛选「已支付」应为空"
    assert page.visible(page.EMPTY)


def test_UI_TC_056_订单号可点击进入详情(fresh_student_ui):
    """点击列表中的订单号单元格应跳转到对应订单详情页。"""
    _, order_id = create_order(fresh_student_ui)

    page = OrderListPage(fresh_student_ui).load()
    page.wait_loaded()
    page.click(page.FIRST_ORDER_NO)
    page.wait_url(f"/order/{order_id}")


def test_UI_TC_057_订单详情展示课程明细与合计(fresh_student_ui):
    """订单详情页应展示订单号、课程明细行、合计金额与操作按钮。"""
    order, order_id = create_order(fresh_student_ui)

    assert order.text(order.ORDER_NO), "订单号不应为空"
    assert order.count(order.ITEM_ROWS) == 1, "本单只含一门课程"
    assert PAID_COURSE_NAME in order.text(order.ITEM_ROWS)
    assert order.text(order.TOTAL).startswith("¥")
    assert order.visible(order.BTN_PAY)
    assert order.visible(order.BTN_CANCEL)
    assert order.path == f"/order/{order_id}"


def test_UI_TC_058_详情页取消订单回到已取消态(fresh_student_ui):
    """详情页点「取消订单」后状态应变为已取消，操作按钮消失。"""
    order, _ = create_order(fresh_student_ui)

    order.cancel()
    order.assert_toast("已取消", kind="success")
    order.wait_status("已取消")

    assert not order.visible(order.BTN_PAY, timeout=1), "已取消订单不应再有支付按钮"
    assert not order.visible(order.BTN_CANCEL, timeout=1), "已取消订单不应再有取消按钮"


def test_UI_TC_059_已支付订单不可重复支付(fresh_student_ui):
    """已支付订单的详情页不再渲染支付/取消按钮，避免重复支付。"""
    order = create_order(fresh_student_ui)[0]
    order.pay()
    order.assert_toast("支付成功", kind="success")
    order.wait_status("已支付")

    assert not order.visible(order.BTN_PAY, timeout=1)
    assert not order.visible(order.BTN_CANCEL, timeout=1)
    assert order.visible(order.BTN_GO_STUDY), "已支付订单应出现「去学习」按钮"


def test_UI_TC_060_已支付订单可跳转学习页(fresh_student_ui):
    """已支付订单详情页点「去学习」应进入该课程学习页。"""
    order = create_order(fresh_student_ui)[0]
    order.pay()
    order.assert_toast("支付成功", kind="success")
    order.wait_status("已支付")

    order.click(order.BTN_GO_STUDY)
    order.wait_url_matches(r"/course/study/\d+$")


def test_UI_TC_061_订单详情页入口(fresh_student_ui):
    """从列表页「详情」按钮也能进入订单详情。"""
    _, order_id = create_order(fresh_student_ui)

    page = OrderListPage(fresh_student_ui).load()
    page.wait_loaded()
    page.click(page.BTN_DETAIL_ROW)
    page.wait_url(f"/order/{order_id}")

    detail = OrderDetailPage(fresh_student_ui)
    assert detail.visible(detail.ORDER_NO)
