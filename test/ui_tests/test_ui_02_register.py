"""UI 自动化 —— 注册（views/Register.vue）

对应用例编号：UI_TC_011 ~ UI_TC_020

说明：注册页所有输入框既没有 id 也没有 name，连 placeholder 都没有，
只能按 label 文案定位（见 pages._field_input）。
前端校验顺序为 用户名 -> 密码 -> 昵称 -> 手机号 -> 邮箱，构造用例时要注意先后。
"""

import time

import pytest

from config import ACCOUNTS
from ui_tests.pages import RegisterPage

pytestmark = pytest.mark.ui


def _unique():
    """生成一组不会与库中数据冲突的注册信息。"""
    ts = str(int(time.time() * 1000))[-10:]
    return {
        "username": f"ui{ts}",
        "password": "123456",
        "phone": f"1{ts}",
        "email": f"ui{ts}@test.com",
    }


@pytest.fixture
def register_page(ui):
    return RegisterPage(ui).load()


# ------------------------------------------------------------------ 页面渲染

def test_UI_TC_011_注册页必填项标记(register_page):
    """注册页应标注 5 个必填项：用户名、密码、邮箱、手机号、身份。"""
    assert register_page.text(register_page.TITLE) == "注册账号"
    assert register_page.required_mark_count() == 5, "应有 5 个红色 * 必填标记"
    assert "必填" in register_page.text(register_page.REQ_TIP)
    assert register_page.path == "/register"


# ------------------------------------------------------------------ 前端校验

def test_UI_TC_012_全部为空提示用户名不能为空(register_page):
    """所有字段留空提交，应被前端拦截并提示用户名不能为空。"""
    register_page.submit()

    register_page.assert_toast("用户名不能为空", kind="error")
    assert register_page.path == "/register"


def test_UI_TC_013_用户名长度不足(register_page):
    """用户名少于 3 个字符应被拦截。"""
    d = _unique()
    register_page.fill(username="ab", password=d["password"],
                       email=d["email"], phone=d["phone"]).submit()

    register_page.assert_toast("用户名长度需在3-20个字符之间", kind="error")


def test_UI_TC_014_用户名含非法字符(register_page):
    """用户名含字母数字下划线以外的字符应被拦截。"""
    d = _unique()
    register_page.fill(username="user-name", password=d["password"],
                       email=d["email"], phone=d["phone"]).submit()

    register_page.assert_toast("用户名只能包含字母、数字和下划线", kind="error")


def test_UI_TC_015_密码长度不足(register_page):
    """密码少于 6 位应被拦截。"""
    d = _unique()
    register_page.fill(username=d["username"], password="123",
                       email=d["email"], phone=d["phone"]).submit()

    register_page.assert_toast("密码长度需在6-20个字符之间", kind="error")


def test_UI_TC_016_昵称超长(register_page):
    """昵称是可选项，但超过 20 字符仍应被拦截。"""
    d = _unique()
    register_page.fill(username=d["username"], password=d["password"],
                       nickname="昵" * 21, email=d["email"],
                       phone=d["phone"]).submit()

    register_page.assert_toast("昵称不能超过20个字符", kind="error")


def test_UI_TC_017_手机号格式错误(register_page):
    """手机号不是 11 位数字应被拦截。"""
    d = _unique()
    register_page.fill(username=d["username"], password=d["password"],
                       email=d["email"], phone="12345").submit()

    register_page.assert_toast("手机号必须为11位数字", kind="error")


def test_UI_TC_018_邮箱格式错误(register_page):
    """邮箱格式不合法应被拦截。"""
    d = _unique()
    register_page.fill(username=d["username"], password=d["password"],
                       email="not-an-email", phone=d["phone"]).submit()

    register_page.assert_toast("邮箱格式不正确", kind="error")


# ------------------------------------------------------------------ 提交结果

def test_UI_TC_019_注册成功跳转登录页(register_page):
    """昵称是选填项，只填必填项就应注册成功，提示后跳转到登录页。

    【已知缺陷 BUG-003】本用例当前为红，是后端缺陷不是用例问题，详见桌面缺陷报告：
      - 前端 Register.vue:7 的表单初值是 nickname: ''，validate() 里昵称只校验
        长度不校验非空（选填），提交时 register(form) 会把空串一起发给后端；
      - 后端 UserService.register:28 只判 nickname != null，空串不为 null，
        于是执行 SELECT count(*) FROM user WHERE nickname = ''；
      - 结果：库里只要已有昵称为空串的用户，此后所有「不填昵称」的注册都会被拒绝，
        提示「昵称已被使用」。而注册页把昵称标成选填，用户完全无从理解。
      修复方式：判空改成 nickname != null && !nickname.isBlank()（updateInfo 同样问题）。

    这里先断言实际弹出的 Toast 再断言文案，失败信息里会直接带上后端给的提示，
    便于一眼看出是 BUG-003 而不是用例写错。
    """
    d = _unique()
    register_page.fill(username=d["username"], password=d["password"],
                       email=d["email"], phone=d["phone"], role="学生").submit()

    msg = register_page.assert_any_toast(timeout=5)
    assert msg == "注册成功，请登录", \
        f"昵称是选填项，不填也应注册成功，实际提示：{msg!r}"
    register_page.wait_url("/login")


def test_UI_TC_020_用户名已存在注册失败(register_page):
    """用户名重复时由后端拒绝，应弹出错误提示并停留在注册页。"""
    d = _unique()
    register_page.fill(username=ACCOUNTS["student"]["username"],
                       password=d["password"], email=d["email"],
                       phone=d["phone"]).submit()

    msg = register_page.assert_any_toast(timeout=5)
    assert msg, "用户名重复应给出提示"
    assert register_page.path == "/register", "注册失败不应跳转"
