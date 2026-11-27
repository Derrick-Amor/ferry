"""UI 自动化 —— 后台管理（views/admin/*）

对应用例编号：UI_TC_083 ~ UI_TC_090

后台布局特点（views/admin/AdminLayout.vue）：
  - 左侧固定菜单，前台页头不渲染
  - 五个子页面：申请审核 / 用户管理 / 课程管理 / 职位管理 / 订单管理
"""

import pytest

from ui_tests.pages import AdminPage

pytestmark = pytest.mark.ui


@pytest.fixture
def admin(admin_ui):
    return AdminPage(admin_ui).load("/admin/apply")


# ------------------------------------------------------------------ 布局

def test_UI_TC_083_后台左侧菜单完整(admin):
    """后台左侧应有五个菜单项，顺序与路由一致。"""
    assert admin.menu_texts() == ["申请审核", "用户管理", "课程管理", "职位管理", "订单管理"]
    assert admin.visible(admin.SIDE)
    assert admin.visible(admin.MAIN)


def test_UI_TC_084_进入后台默认跳申请审核(admin_ui):
    """访问 /admin 应重定向到 /admin/apply。"""
    page = AdminPage(admin_ui).load("/admin")
    page.wait_url("/admin/apply")


@pytest.mark.parametrize("menu, path", [
    ("申请审核", "/admin/apply"),
    ("用户管理", "/admin/users"),
    ("课程管理", "/admin/courses"),
    ("职位管理", "/admin/jobs"),
    ("订单管理", "/admin/orders"),
])
def test_UI_TC_085_后台各子页面可打开(admin, menu, path):
    """点击左侧菜单应能切换到对应子页面，且该菜单项为选中态。"""
    admin.click(("xpath",
                 f"//aside[contains(@class,'side')]//a[normalize-space(text())='{menu}']"))
    admin.wait_url(path)

    assert admin.path == path
    assert admin.texts(admin.ACTIVE_MENU) == [menu], f"{menu} 菜单应处于选中态"
    assert admin.visible(admin.MAIN), "子页面主区域应已渲染"


# ------------------------------------------------------------------ 申请审核

def test_UI_TC_086_申请审核页结构正确(admin):
    """申请审核页应有「待审核」「已处理」两个页签。"""
    assert admin.text(admin.PAGE_TITLE) == "申请审核"
    assert admin.visible(admin.TAB_PENDING)
    assert admin.visible(admin.TAB_AUDITED)


def test_UI_TC_087_待审核列表结构(admin):
    """待审核页签下列表应包含五列表头，无数据时展示空态。"""
    admin.wait_page_loaded()

    if admin.count(admin.AUDIT_ROWS) > 0:
        assert admin.header_texts() == ["类型", "申请理由", "状态", "申请时间", "操作"]
    else:
        assert admin.visible(admin.AUDIT_EMPTY), "无待审申请时应展示空态"


def test_UI_TC_088_切换到已处理页签(admin):
    """切换到「已处理」页签应加载已审核列表。"""
    admin.click(admin.TAB_AUDITED)
    admin.wait_page_loaded()

    if admin.count(admin.AUDIT_ROWS) > 0:
        assert admin.header_texts() == ["类型", "申请理由", "状态", "申请时间"]
        assert not admin.visible(admin.BTN_PASS, timeout=1), \
            "已处理列表不应再出现审核操作按钮"
    else:
        assert admin.visible(admin.AUDIT_EMPTY)


def test_UI_TC_089_审核通过待审申请(admin):
    """待审列表有数据时，点「通过」应提示已通过并刷新列表。

    前置数据中没有待审申请时跳过 —— 完整链路是
    「讲师提交上架申请 -> 管理员审核」，需要先在讲师端造数据。
    """
    admin.wait_page_loaded()
    before = admin.count(admin.AUDIT_ROWS)
    if before == 0:
        pytest.skip("当前没有待审核申请，跳过审核操作验证")

    admin.click(admin.BTN_PASS)
    admin.assert_toast("已通过", kind="success")
    admin.wait_page_loaded()


def test_UI_TC_090_审核驳回待审申请(admin_ui):
    """驳回操作与通过对称：点「驳回」应提示已驳回。

    同样需要待审数据，没有则跳过。
    """
    admin = AdminPage(admin_ui).load("/admin/apply")
    admin.wait_page_loaded()
    if admin.count(admin.AUDIT_ROWS) == 0:
        pytest.skip("当前没有待审核申请，跳过驳回操作验证")

    admin.click(admin.BTN_REJECT)
    admin.assert_toast("已驳回", kind="success")
    admin.wait_page_loaded()
