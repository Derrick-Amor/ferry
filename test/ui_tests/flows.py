"""可复用的业务流与前置数据常量。

购买闭环和订单用例都要「先造一个订单」，把这段操作抽在这里，
避免两个测试模块各写一份、改一处漏一处。
"""

from ui_tests.pages import CartPage, CourseDetailPage, OrderDetailPage, parse_id_from_url

# 前置数据 id 与名称，来自数据库重置脚本
FREE_COURSE_ID = 1
PAID_COURSE_ID = 2
FREE_COURSE_NAME = "Java 基础入门"
PAID_COURSE_NAME = "SpringBoot 实战"

JOB_ID = 1
JOB_NAME = "Java 后端开发工程师"
JOB_COMPANY = "示例科技有限公司"

CART_TOAST = "已加入购物车，请结算支付后学习"


def add_paid_course_to_cart(driver):
    """在详情页把付费课加入购物车，返回已加载完的购物车页面对象。"""
    detail = CourseDetailPage(driver).load(PAID_COURSE_ID)
    detail.add_to_cart()
    # toast 2.6 秒后自动消失，必须在点击后立刻断言
    detail.assert_toast(CART_TOAST, kind="success")
    detail.wait_url("/cart")

    return CartPage(driver).wait_loaded()


def create_order(driver):
    """走完 加购 -> 勾选 -> 结算，返回 (订单详情页对象, 订单 id)。"""
    cart = add_paid_course_to_cart(driver)
    cart.check_all().checkout()
    cart.assert_toast("订单已创建", kind="success")
    cart.wait_url_matches(r"/order/\d+")

    order = OrderDetailPage(driver)
    order.find_visible(order.ORDER_NO)
    return order, parse_id_from_url(driver.current_url)


def pay_order(driver):
    """走完 下单 -> 支付，返回已支付的订单详情页对象。"""
    order, _ = create_order(driver)
    order.pay()
    order.assert_toast("支付成功", kind="success")
    order.wait_status("已支付")
    return order
