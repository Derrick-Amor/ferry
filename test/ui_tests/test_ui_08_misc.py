"""UI 自动化 —— 返回按钮、个人中心、404 页与站内导航

对应用例编号：UI_TC_073 ~ UI_TC_082

返回按钮逻辑（src/App.vue:10-11）：
    TOP_LEVEL = ['/', '/course', '/job', '/login', '/register', '/forgot']
    顶级页面不渲染返回按钮，其余子页面统一渲染；
    /admin 下既不渲染页头也不渲染返回按钮。
"""

import pytest
from selenium.webdriver.common.by import By

from config import FRONT_URL
from ui_tests.flows import FREE_COURSE_ID
from ui_tests.pages import (
    APP_ROOT,
    AdminPage,
    BackButton,
    BasePage,
    Header,
    NotFoundPage,
    ProfilePage,
)

pytestmark = pytest.mark.ui


# ------------------------------------------------------------------ 返回按钮

def test_UI_TC_073_子页面显示返回按钮(ui):
    """课程详情属于子页面，应显示返回按钮。"""
    page = BasePage(ui).open(f"/course/{FREE_COURSE_ID}")
    assert page.visible(BackButton.BTN), "课程详情页应显示返回按钮"


@pytest.mark.parametrize("path", ["/", "/course", "/job", "/login", "/register"])
def test_UI_TC_074_顶级页面不显示返回按钮(ui, path):
    """顶级页面不应渲染返回按钮。"""
    page = BasePage(ui).open(path)
    page.find_visible(APP_ROOT)                       # 先确认页面已渲染
    assert page.absent(BackButton.BTN, timeout=2), f"{path} 是顶级页面，不应有返回按钮"


def test_UI_TC_075_返回按钮回到上一页(ui):
    """从课程列表点进详情后，点返回应回到课程列表。"""
    page = BasePage(ui).open("/course")
    page.find_visible(("css selector", ".specimen"))
    page.click(("css selector", ".specimen"))
    page.wait_url_matches(r"/course/\d+$")

    BackButton(ui).click_back()
    page.wait_url("/course")


def test_UI_TC_076_后台不显示返回按钮和页头(admin_ui):
    """后台布局不渲染前台页头与返回按钮。"""
    page = AdminPage(admin_ui).load("/admin/apply")

    assert page.absent(Header.ROOT, timeout=2), "后台不应显示前台页头"
    assert page.absent(BackButton.BTN, timeout=2), "后台不应显示返回按钮"


# ------------------------------------------------------------------ 个人中心

def test_UI_TC_077_个人中心展示账号信息(student_ui):
    """个人中心应展示用户名、头像与角色徽章。"""
    profile = ProfilePage(student_ui).load()

    assert profile.username_text().startswith("@"), "用户名应带 @ 前缀"
    assert profile.display_name(), "昵称不应为空"
    assert profile.role_text() == "学生"
    assert profile.text(profile.AVATAR), "头像占位应有内容"


def test_UI_TC_078_个人中心链接可跳转(student_ui):
    """个人中心的「我的简历」链接应能正常跳转。"""
    profile = ProfilePage(student_ui).load()

    profile.click(profile.LINK_RESUME)
    profile.wait_url("/user/resume")
    assert profile.path == "/user/resume"


def test_UI_TC_079_讲师个人中心角色徽章(teacher_ui):
    """讲师个人中心的角色徽章应显示「讲师/企业」。"""
    assert ProfilePage(teacher_ui).load().role_text() == "讲师/企业"


def test_UI_TC_080_管理员个人中心角色徽章(admin_ui):
    """管理员个人中心的角色徽章应显示「管理员」。"""
    assert ProfilePage(admin_ui).load().role_text() == "管理员"


# ------------------------------------------------------------------ 404 页

def test_UI_TC_081_未知路由展示404页并可回首页(ui):
    """访问不存在的路由应展示 404 页，点「回到首页」可返回首页。"""
    page = NotFoundPage(ui).load("/this-page-does-not-exist")

    assert page.text(page.TITLE) == "页面不存在或已被移除"
    assert page.visible(page.HOME_BTN)

    page.click(page.HOME_BTN)
    page.wait_url("/")


# ------------------------------------------------------------------ 站内导航

def test_UI_TC_082_页头导航链接可用(ui):
    """页头「课程」「职位」导航应能正常跳转。"""
    ui.get(FRONT_URL)
    header = Header(ui)
    header.find_visible(header.LOGIN_BTN)

    header.click((By.XPATH, "//nav[contains(@class,'nav')]//a[normalize-space(text())='课程']"))
    header.wait_url("/course")

    header.click((By.XPATH, "//nav[contains(@class,'nav')]//a[normalize-space(text())='职位']"))
    header.wait_url("/job")
