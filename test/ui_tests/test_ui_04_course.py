"""UI 自动化 —— 课程浏览（views/course/CourseList.vue、CourseDetail.vue）

对应用例编号：UI_TC_033 ~ UI_TC_044

依赖前置数据（见数据库重置脚本）：
    id=1  Java 基础入门   免费  已上架  含 3 个章节
    id=2  SpringBoot 实战 付费  已上架
"""

import pytest

from config import FRONT_URL
from ui_tests.flows import (
    FREE_COURSE_ID,
    FREE_COURSE_NAME,
    PAID_COURSE_ID,
    PAID_COURSE_NAME,
)
from ui_tests.pages import CourseDetailPage, CourseListPage, LoginPage

pytestmark = pytest.mark.ui


@pytest.fixture
def course_list(ui):
    return CourseListPage(ui).load()


# ------------------------------------------------------------------ 列表页

def test_UI_TC_033_课程列表页正常渲染(course_list):
    """课程列表页应有标题、搜索框与筛选控件。"""
    assert course_list.text(course_list.TITLE) == "课程中心"
    assert course_list.visible(course_list.SEARCH)
    assert course_list.visible(course_list.SORT)
    assert course_list.path == "/course"


def test_UI_TC_034_课程卡片展示名称与价格(course_list):
    """列表中的每张卡片都应展示课程名与价格。"""
    assert course_list.count(course_list.CARDS) > 0, "课程列表不应为空"

    names = course_list.card_names()
    prices = course_list.card_prices()
    assert len(names) == len(prices), "卡片名称与价格数量应一致"
    assert all(name for name in names), "卡片名称不应为空"
    assert all(price.startswith(("¥", "免费")) for price in prices), \
        f"价格格式异常：{prices}"


def test_UI_TC_035_按价格筛选免费课程(course_list):
    """选中「免费」筛选后，列表中所有课程的价格都应是免费。

    三个筛选维度各有一组 chip，每组的「全部X」在该维度未选中时也是 active 态
    （CourseList.vue:33,38,43 的 :class="{ active: q.xxx === '' }"），
    所以不能断言「active 的只有免费」，只能断言免费进入了选中态、
    且价格维度的「全部价格」让出了选中态。
    """
    course_list.click_chip("免费")

    active = course_list.active_chip_texts()
    assert "免费" in active, f"「免费」应处于选中态，实际选中：{active}"
    assert "全部价格" not in active, f"价格维度的「全部价格」应已让出选中态：{active}"

    assert course_list.count(course_list.CARDS) > 0, "免费课程不应为空"
    assert set(course_list.card_prices()) == {"免费"}


def test_UI_TC_036_按课程类型筛选有选中态(course_list):
    """点击类型筛选后该 chip 应变为选中态，同维度的「全部类型」取消选中。"""
    course_list.click_chip("理论")

    active = course_list.active_chip_texts()
    assert "理论" in active, f"「理论」应处于选中态，实际选中：{active}"
    assert "全部类型" not in active, f"「全部类型」应已让出选中态：{active}"

    assert course_list.count(course_list.CARDS) > 0, "「理论」类型下应有课程"


def test_UI_TC_037_关键字搜索命中(course_list):
    """搜索关键字应过滤出相关课程（搜索框只支持回车触发）。"""
    course_list.search("Java")

    names = course_list.card_names()
    assert names, "搜索 Java 应有结果"
    assert any("Java" in n for n in names), f"结果与关键字不符：{names}"


def test_UI_TC_038_搜索无结果显示空态(course_list):
    """搜索不存在的关键字应展示空态提示。"""
    course_list.search("zzz-this-course-does-not-exist")

    assert course_list.count(course_list.CARDS) == 0
    assert course_list.visible(course_list.EMPTY)


def test_UI_TC_039_点击卡片进入课程详情(course_list):
    """点击课程卡片应跳转到该课程的详情页。"""
    course_list.click(course_list.CARDS)
    course_list.wait_url_matches(r"/course/\d+$")


# ------------------------------------------------------------------ 详情页

def test_UI_TC_040_详情页展示课程信息与章节(ui):
    """详情页应展示课程名、价格与章节列表。"""
    detail = CourseDetailPage(ui).load(FREE_COURSE_ID)

    assert detail.title_text() == FREE_COURSE_NAME
    assert detail.price_text() == "免费"
    assert len(detail.chapter_names()) == 3, "该课程应有 3 个章节"


def test_UI_TC_041_付费课详情展示价格(ui):
    """付费课详情页应展示带 ¥ 的价格。"""
    detail = CourseDetailPage(ui).load(PAID_COURSE_ID)

    assert detail.title_text() == PAID_COURSE_NAME
    assert detail.price_text().startswith("¥")


def test_UI_TC_042_收藏课程(student_ui):
    """学生点击「收藏」应提示已收藏，且不跳转页面。"""
    detail = CourseDetailPage(student_ui).load(FREE_COURSE_ID)
    detail.collect()

    detail.assert_toast("已收藏", kind="success")
    assert detail.path == f"/course/{FREE_COURSE_ID}", "收藏不应跳转页面"


def test_UI_TC_043_未登录加购跳登录页(ui):
    """未登录点击「加入购物车」应跳转到登录页。"""
    detail = CourseDetailPage(ui).load(PAID_COURSE_ID)
    detail.add_to_cart()

    LoginPage(ui).wait_url("/login")


def test_UI_TC_044_未购买付费课无法进入学习页(fresh_student_ui):
    """学生直接访问未购买付费课的学习页，应被弹回课程详情页。

    必须用 fresh_student_ui 而不是 student_ui：student01 在其它用例里可能
    已经买过付费课，那时学习页能正常打开，用例就无法复现「未购买」这个前提。
    全新注册的学生没有买过任何课，前提才成立。

    CourseStudy.vue 在学习接口报错时执行 router.replace(`/course/:id`)，
    所以预期结果是最终停在详情页而不是停留在学习页或跳登录页。
    """
    fresh_student_ui.get(f"{FRONT_URL}/course/study/{PAID_COURSE_ID}")

    CourseDetailPage(fresh_student_ui).wait_url(f"/course/{PAID_COURSE_ID}")
