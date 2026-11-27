"""UI 自动化 —— 职位浏览与投递入口（views/job/JobList.vue、JobDetail.vue）

对应用例编号：UI_TC_062 ~ UI_TC_069

依赖前置数据：id=1 Java 后端开发工程师 / 示例科技有限公司 / 已上架
"""

import pytest

from ui_tests.flows import JOB_ID, JOB_NAME
from ui_tests.pages import JobDetailPage, JobListPage, LoginPage

pytestmark = pytest.mark.ui


@pytest.fixture
def job_list(ui):
    return JobListPage(ui).load()


# ------------------------------------------------------------------ 列表页

def test_UI_TC_062_职位列表页正常渲染(job_list):
    """职位列表页应有标题与搜索框。"""
    assert job_list.text(job_list.TITLE) == "职位中心"
    assert job_list.visible(job_list.SEARCH)
    assert job_list.path == "/job"


def test_UI_TC_063_职位卡片展示名称公司与薪资(job_list):
    """职位卡片应展示职位名、公司与薪资范围。"""
    assert job_list.count(job_list.CARDS) > 0, "职位列表不应为空"

    names = job_list.card_names()
    companies = job_list.texts(job_list.CARD_COMPANIES)
    salaries = job_list.texts(job_list.CARD_SALARIES)

    assert all(names), "职位名不应为空"
    assert all(companies), "公司名不应为空"
    assert all("–" in s for s in salaries), f"薪资区间格式异常：{salaries}"
    assert JOB_NAME in names


def test_UI_TC_064_职位类别筛选有选中态(job_list):
    """点击类别 chip 后该 chip 应变为选中态。"""
    chips = job_list.finds(job_list.CHIPS)
    assert len(chips) >= 2, "应有「全部类别」加至少一个具体类别"

    label = chips[1].text.strip()
    job_list.click_chip(label)

    assert job_list.active_chip_texts() == [label]


def test_UI_TC_065_职位搜索命中(job_list):
    """搜索关键字应过滤出相关职位。"""
    job_list.search("Java")

    names = job_list.card_names()
    assert names, "搜索 Java 应有结果"
    assert any("Java" in n for n in names), f"结果与关键字不符：{names}"


def test_UI_TC_066_职位搜索无结果空态(job_list):
    """搜索不存在的关键字应展示空态提示。"""
    job_list.search("zzz-this-job-does-not-exist")

    assert job_list.count(job_list.CARDS) == 0
    assert job_list.visible(job_list.EMPTY)


def test_UI_TC_067_点击卡片进入职位详情(job_list):
    """点击职位卡片应跳转到该职位详情页。"""
    job_list.click(job_list.CARDS)
    job_list.wait_url_matches(r"/job/\d+$")


# ------------------------------------------------------------------ 详情页

def test_UI_TC_068_职位详情页展示信息与操作按钮(ui):
    """职位详情页应展示职位名、薪资与三个操作按钮。"""
    detail = JobDetailPage(ui).load(JOB_ID)

    assert detail.title_text() == JOB_NAME
    assert "–" in detail.salary_text(), "薪资应为区间格式"
    assert detail.visible(detail.BTN_INTERVIEW)
    assert detail.visible(detail.BTN_APPLY)
    assert detail.visible(detail.BTN_COLLECT)
    assert detail.texts(detail.TAGS), "详情页应展示类型/学历/经验标签"


def test_UI_TC_069_未登录投递职位跳登录页(ui):
    """未登录点击「投递简历」应跳转到登录页。"""
    detail = JobDetailPage(ui).load(JOB_ID)
    detail.click(detail.BTN_APPLY)

    LoginPage(ui).wait_url("/login")


def test_UI_TC_070_学生投递弹出简历选择弹窗(student_ui):
    """学生点击「投递简历」应弹出简历选择弹窗（自绘 modal，不是组件库弹窗）。"""
    detail = JobDetailPage(student_ui).load(JOB_ID)
    detail.open_apply_modal()

    assert detail.text(detail.MODAL_TITLE) == "投递简历"
    assert detail.visible(detail.MODAL_CONFIRM), "弹窗应有确认投递按钮"
    assert (detail.visible(detail.MODAL_RESUME_OPTS, timeout=2)
            or detail.visible(detail.MODAL_EMPTY, timeout=2)), \
        "弹窗内应展示简历列表或「还没有简历」的空态"


def test_UI_TC_071_投递弹窗可关闭(student_ui):
    """点击弹窗「取消」应关闭弹窗且不跳转。"""
    detail = JobDetailPage(student_ui).load(JOB_ID)
    detail.open_apply_modal()
    detail.click((detail.BTN_MODAL_CANCEL))

    assert detail.absent(detail.MODAL, timeout=2), "弹窗应已关闭"
    assert detail.path == f"/job/{JOB_ID}"


def test_UI_TC_072_收藏职位(student_ui):
    """学生点击「收藏」应提示已收藏。"""
    detail = JobDetailPage(student_ui).load(JOB_ID)
    detail.collect()

    detail.assert_toast("已收藏", kind="success")
