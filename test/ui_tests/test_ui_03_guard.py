"""UI 自动化 —— 路由守卫与权限视觉（router/index.js、components/AppHeader.vue）

对应用例编号：UI_TC_021 ~ UI_TC_032

守卫实现（src/router/index.js:43-49）：
    if (to.meta.requiresAuth && !auth.isLogin)   return '/login'
    if (to.meta.roles && !to.meta.roles.includes(auth.role)) return '/'
    if (是登录/注册/找回页 && auth.isLogin)       return '/'

注意两个容易写错的点：
  1. 角色不符时跳的是首页 '/'，不是 403 页面。
  2. 跳转是异步的，必须用 wait_url 等待，不能取一次 current_url 就断言。
"""

import pytest

from config import FRONT_URL
from ui_tests.pages import AdminPage, Header, LoginPage

pytestmark = pytest.mark.ui


def _open(driver, path):
    driver.get(FRONT_URL + path)
    return LoginPage(driver)   # 借用它的 wait_url


# ------------------------------------------------------------ 未登录被拦截

@pytest.mark.parametrize("path", ["/user", "/cart", "/order", "/admin/apply", "/user/resume"])
def test_UI_TC_021_未登录访问受保护页跳登录页(ui, path):
    """未登录访问任何 requiresAuth 页面，都应被重定向到 /login。"""
    _open(ui, path).wait_url("/login")


# ------------------------------------------------------------ 角色不符被拦截

@pytest.mark.parametrize("path", ["/admin/apply", "/admin/users", "/admin/orders"])
def test_UI_TC_022_学生访问后台跳首页(student_ui, path):
    """学生访问 /admin/** 应被重定向到首页而不是 403 页面。"""
    _open(student_ui, path).wait_url("/")


@pytest.mark.parametrize("path", ["/course/apply", "/job/apply", "/job/applicants"])
def test_UI_TC_023_学生访问讲师页跳首页(student_ui, path):
    """学生访问仅讲师可见的页面应被重定向到首页。"""
    _open(student_ui, path).wait_url("/")


@pytest.mark.parametrize("path", ["/cart", "/order", "/user/my-course"])
def test_UI_TC_024_讲师访问学生页跳首页(teacher_ui, path):
    """讲师访问仅学生可见的页面应被重定向到首页。"""
    _open(teacher_ui, path).wait_url("/")


def test_UI_TC_025_讲师访问后台跳首页(teacher_ui):
    """讲师同样没有后台权限。"""
    _open(teacher_ui, "/admin/apply").wait_url("/")


# ------------------------------------------------------------ 有权限可进入

def test_UI_TC_026_管理员可进入后台(admin_ui):
    """管理员访问 /admin/apply 应正常停留，不触发任何跳转。"""
    page = AdminPage(admin_ui).load("/admin/apply")
    assert page.path == "/admin/apply"


def test_UI_TC_027_学生可进入购物车(student_ui):
    """学生访问 /cart 应正常停留。"""
    page = _open(student_ui, "/cart")
    page.wait_url("/cart")
    assert page.path == "/cart"


# ------------------------------------------------------------ 页头权限视觉

def test_UI_TC_028_未登录页头只显示公开菜单(ui):
    """未登录时页头只有 首页 / 课程 / 职位，以及登录、注册按钮。"""
    ui.get(FRONT_URL)
    header = Header(ui)
    header.find_visible(header.LOGIN_BTN)

    assert header.nav_texts() == ["首页", "课程", "职位"]
    assert header.visible(header.LOGIN_BTN)
    assert header.visible(header.REGISTER_BTN)
    assert not header.visible(header.ADMIN_ENTRY, timeout=1)
    assert not header.visible(header.USER_LINK, timeout=1)


def test_UI_TC_029_学生页头显示学生菜单且无后台入口(student_ui):
    """学生页头应出现学生专属菜单，且不显示「后台管理」入口。"""
    student_ui.get(FRONT_URL)
    header = Header(student_ui)
    header.find_visible(header.USER_LINK)

    nav = header.nav_texts()
    for item in ["我的课程", "我的投递", "购物车", "我的订单"]:
        assert item in nav, f"学生页头缺少菜单：{item}"
    assert "后台管理" not in nav, "学生不应看到后台管理入口"
    assert "申请课程" not in nav, "学生不应看到讲师菜单"
    assert header.role_text() == "学生"


def test_UI_TC_030_讲师页头显示讲师菜单且无后台入口(teacher_ui):
    """讲师页头应出现讲师专属菜单，且不显示「后台管理」入口。"""
    teacher_ui.get(FRONT_URL)
    header = Header(teacher_ui)
    header.find_visible(header.USER_LINK)

    nav = header.nav_texts()
    for item in ["申请课程", "申请职位", "候选人"]:
        assert item in nav, f"讲师页头缺少菜单：{item}"
    assert "后台管理" not in nav, "讲师不应看到后台管理入口"
    assert "购物车" not in nav, "讲师不应看到学生菜单"
    assert header.role_text() == "讲师/企业"


def test_UI_TC_031_管理员页头显示后台入口(admin_ui):
    """管理员页头应出现「后台管理」入口。"""
    admin_ui.get(FRONT_URL)
    header = Header(admin_ui)
    header.find_visible(header.USER_LINK)

    assert "后台管理" in header.nav_texts()
    assert header.visible(header.ADMIN_ENTRY)
    assert header.role_text() == "管理员"


def test_UI_TC_032_点击后台管理进入后台(admin_ui):
    """点击页头「后台管理」应进入 /admin。"""
    admin_ui.get(FRONT_URL)
    header = Header(admin_ui)
    header.click(header.ADMIN_ENTRY)
    header.wait_url_matches(r"/admin(/.*)?$")
