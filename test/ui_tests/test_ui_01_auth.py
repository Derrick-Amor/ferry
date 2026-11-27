"""UI 自动化 —— 登录 / 登出（views/Login.vue、components/AppHeader.vue）

对应用例编号：UI_TC_001 ~ UI_TC_010
"""

import pytest

from config import ACCOUNTS, FRONT_URL
from ui_tests.base import TOKEN_KEY
from ui_tests.pages import Header, LoginPage

pytestmark = pytest.mark.ui


@pytest.fixture
def login_page(ui):
    return LoginPage(ui).load()


# ------------------------------------------------------------------ 页面渲染

def test_UI_TC_001_登录页正常渲染(login_page):
    """登录页应展示标题、两个输入框、登录按钮与两个辅助链接。"""
    assert login_page.text(login_page.TITLE) == "欢迎登录"
    assert login_page.visible(login_page.USERNAME)
    assert login_page.visible(login_page.PASSWORD)
    assert login_page.visible(login_page.SUBMIT)
    assert login_page.visible(login_page.LINK_REGISTER)
    assert login_page.visible(login_page.LINK_FORGOT)
    assert login_page.path == "/login"


# ------------------------------------------------------------------ 登录成功

def test_UI_TC_002_正确账号登录成功(login_page, ui):
    """正确账号登录后提示「登录成功」、跳转首页，页头切换为已登录态。"""
    acc = ACCOUNTS["student"]
    login_page.login(acc["username"], acc["password"])

    login_page.assert_toast("登录成功", kind="success")
    login_page.wait_url("/")

    header = Header(ui)
    assert header.visible(header.USER_LINK), "登录后页头应显示用户名入口"
    assert header.user_display_name(), "页头用户名不应为空"
    assert header.role_text() == "学生"


def test_UI_TC_003_密码框回车可提交(login_page, ui):
    """密码输入框绑定了 @keyup.enter，回车应等价于点击登录按钮。"""
    acc = ACCOUNTS["student"]
    login_page.fill(acc["username"], acc["password"]).submit_by_enter()

    login_page.assert_toast("登录成功", kind="success")
    login_page.wait_url("/")


# ------------------------------------------------------------------ 登录失败

def test_UI_TC_004_密码错误登录失败(login_page):
    """密码错误时应弹出错误提示，且停留在登录页不跳转。"""
    acc = ACCOUNTS["student"]
    login_page.login(acc["username"], "wrong-password")

    msg = login_page.assert_any_toast(timeout=5)
    assert msg, "密码错误应给出提示文案"
    assert login_page.path == "/login", "登录失败不应跳转"


def test_UI_TC_005_用户名为空提示(login_page):
    """用户名为空时前端直接拦截，提示「请输入用户名和密码」。"""
    login_page.fill("", ACCOUNTS["student"]["password"]).submit()

    login_page.assert_toast("请输入用户名和密码", kind="error")
    assert login_page.path == "/login"


def test_UI_TC_006_密码为空提示(login_page):
    """密码为空时同样被前端拦截。"""
    login_page.fill(ACCOUNTS["student"]["username"], "").submit()

    login_page.assert_toast("请输入用户名和密码", kind="error")
    assert login_page.path == "/login"


# ------------------------------------------------------------------ 页面跳转

def test_UI_TC_007_注册账号链接跳转(login_page):
    """「注册账号」链接应跳到注册页。"""
    login_page.click(login_page.LINK_REGISTER)
    login_page.wait_url("/register")


def test_UI_TC_008_忘记密码链接跳转(login_page):
    """「忘记密码」链接应跳到找回密码页。"""
    login_page.click(login_page.LINK_FORGOT)
    login_page.wait_url("/forgot")


def test_UI_TC_009_已登录访问登录页被弹回首页(login_page, ui, tokens):
    """路由守卫：已登录用户访问 /login 会被重定向到首页。"""
    ui.execute_script(
        "window.localStorage.setItem(arguments[0], arguments[1]);",
        TOKEN_KEY, tokens["student"]
    )
    ui.get(f"{FRONT_URL}/login")

    login_page.wait_url("/")


# ------------------------------------------------------------------ 退出登录

def test_UI_TC_010_退出登录恢复未登录态(student_ui):
    """点击页头「退出」后清空登录态，页头恢复「登录 / 注册」按钮。"""
    header = Header(student_ui)
    assert header.visible(header.LOGOUT_BTN), "已登录时页头应有退出按钮"

    header.logout()
    header.wait_url("/")

    assert header.visible(header.LOGIN_BTN), "退出后页头应显示登录按钮"
    assert header.visible(header.REGISTER_BTN), "退出后页头应显示注册按钮"
    assert not header.visible(header.USER_LINK, timeout=1), "退出后不应再显示用户名入口"
    token = student_ui.execute_script(
        "return window.localStorage.getItem(arguments[0]);", TOKEN_KEY)
    assert token is None, "退出登录应清除 localStorage 中的 token"
