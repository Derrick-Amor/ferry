"""页面对象（Page Object）：每个页面一个类，封装元素定位与常用操作。

用例只调用这里的方法，不直接写定位器 —— 前端改版时只需改这一个文件。

定位器全部依据前端源码书写，关键处标注了来源文件与行为，
改动前请先核对源码（路径相对 Smart_education_platform_frontend/src）。
"""

import re

from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import Select

from config import UI_TIMEOUT
from ui_tests.base import BasePage

# index.html 里 Vue 的挂载点，也是全项目唯一存在的 id
APP_ROOT = (By.CSS_SELECTOR, "#app")


def _field_input(label):
    """按 label 文案定位 .field 内的输入框。

    注册页 / 个人中心的输入框既没有 id 也没有 name，有的还没有 placeholder
    （注册页全部没有），唯一稳定的锚点是 label 文案，
    例如 <div class="field"><label>手机号 <span class="req">*</span></label><input .../></div>
    """
    return (By.XPATH,
            f"//div[contains(@class,'field')]"
            f"[label[contains(normalize-space(.), '{label}')]]/input")


def _field_select(label):
    return (By.XPATH,
            f"//div[contains(@class,'field')]"
            f"[label[contains(normalize-space(.), '{label}')]]/select")


# ============================================================ 页头（全站公共）

class Header(BasePage):
    """顶部导航，见 components/AppHeader.vue。

    注意：/admin 下 App.vue 不渲染页头，后台用例不要用这个类。
    """

    ROOT = (By.CSS_SELECTOR, ".header")
    NAV_LINKS = (By.CSS_SELECTOR, ".header .nav a")
    LOGIN_BTN = (By.CSS_SELECTOR, '.header .user-area a[href="/login"] button')
    REGISTER_BTN = (By.CSS_SELECTOR, '.header .user-area a[href="/register"] button')
    USER_LINK = (By.CSS_SELECTOR, ".header .user-area a.user")
    ROLE_TAG = (By.CSS_SELECTOR, ".header .user-area .tag")
    LOGOUT_BTN = (By.XPATH,
                  "//div[contains(@class,'user-area')]//button[normalize-space(text())='退出']")
    ADMIN_ENTRY = (By.XPATH, "//nav[contains(@class,'nav')]//a[normalize-space(text())='后台管理']")

    def nav_texts(self):
        """页头导航项文案，按 DOM 顺序。

        未登录：首页 / 课程 / 职位
        学生：  首页 / 课程 / 职位 / 我的课程 / 我的投递 / 购物车 / 我的订单
        讲师：  首页 / 课程 / 职位 / 申请课程 / 申请职位 / 候选人
        管理员：以上 + 后台管理
        """
        return self.texts(self.NAV_LINKS)

    def logout(self):
        self.click(self.LOGOUT_BTN)
        return self

    def user_display_name(self):
        return self.text(self.USER_LINK)

    def role_text(self):
        return self.text(self.ROLE_TAG)


# ==================================================================== 登录页

class LoginPage(BasePage):
    """views/Login.vue"""

    CARD = (By.CSS_SELECTOR, ".auth-card")
    TITLE = (By.CSS_SELECTOR, ".auth-card .auth-title")
    # 登录页的输入框是唯一带 placeholder 的，可直接用 placeholder 定位
    USERNAME = (By.CSS_SELECTOR, '.auth-card input[placeholder="用户名"]')
    PASSWORD = (By.CSS_SELECTOR, '.auth-card input[type="password"]')
    # 必须限定在 .auth-card 内：未登录时页头也有一个「登录」按钮
    SUBMIT = (By.XPATH,
              "//div[contains(@class,'auth-card')]//button[normalize-space(text())='登录']")
    LINK_REGISTER = (By.CSS_SELECTOR, '.auth-card a[href="/register"]')
    LINK_FORGOT = (By.CSS_SELECTOR, '.auth-card a[href="/forgot"]')

    def load(self):
        self.open("/login")
        self.find_visible(self.SUBMIT)
        return self

    def fill(self, username, password):
        self.type(self.USERNAME, username)
        self.type(self.PASSWORD, password)
        return self

    def submit(self):
        self.click(self.SUBMIT)
        return self

    def submit_by_enter(self):
        """密码框支持 @keyup.enter 提交，用来验证回车登录。"""
        self.find_visible(self.PASSWORD).send_keys(Keys.ENTER)
        return self

    def login(self, username, password):
        return self.fill(username, password).submit()


# ==================================================================== 注册页

class RegisterPage(BasePage):
    """views/Register.vue

    注册页的输入框全部没有 placeholder / id / name，只能用 label 文案定位。
    校验顺序（源码 validate()）：用户名 -> 密码 -> 昵称 -> 手机号 -> 邮箱。
    """

    CARD = (By.CSS_SELECTOR, ".auth-card")
    TITLE = (By.CSS_SELECTOR, ".auth-card .auth-title")
    # 必填红星 *。必须限定在 .field 内：.req-tip（表单下方的"* 为必填项"说明）
    # 里也用了同一个 class，不加 .field 会多统计一个。
    REQ_MARKS = (By.CSS_SELECTOR, ".auth-card .field .req")
    REQ_TIP = (By.CSS_SELECTOR, ".auth-card .req-tip")

    USERNAME = _field_input("用户名")
    PASSWORD = _field_input("密码")
    EMAIL = _field_input("邮箱")
    PHONE = _field_input("手机号")
    NICKNAME = _field_input("昵称")
    ROLE = _field_select("身份")

    SUBMIT = (By.XPATH,
              "//div[contains(@class,'auth-card')]//button[normalize-space(text())='注册']")
    LINK_LOGIN = (By.CSS_SELECTOR, '.auth-card a[href="/login"]')

    def load(self):
        self.open("/register")
        self.find_visible(self.SUBMIT)
        return self

    def fill(self, username=None, password=None, email=None,
             phone=None, role=None, nickname=None):
        """只填写传入的字段，便于构造「缺某一项」的校验用例。"""
        if username is not None:
            self.type(self.USERNAME, username)
        if password is not None:
            self.type(self.PASSWORD, password)
        if email is not None:
            self.type(self.EMAIL, email)
        if phone is not None:
            self.type(self.PHONE, phone)
        if role is not None:
            Select(self.find(self.ROLE)).select_by_visible_text(role)
        if nickname is not None:
            self.type(self.NICKNAME, nickname)
        return self

    def submit(self):
        self.click(self.SUBMIT)
        return self

    def required_mark_count(self):
        return self.count(self.REQ_MARKS)


# ================================================================ 课程列表页

class CourseListPage(BasePage):
    """views/course/CourseList.vue"""

    TITLE = (By.CSS_SELECTOR, ".page-title")
    # 搜索框只有 @keyup.enter 触发，页面上没有搜索按钮
    SEARCH = (By.CSS_SELECTOR, 'input[placeholder="搜索课程或讲师"]')
    SORT = (By.CSS_SELECTOR, "select.select")
    CHIPS = (By.CSS_SELECTOR, "button.chip")
    ACTIVE_CHIPS = (By.CSS_SELECTOR, "button.chip.active")
    CARDS = (By.CSS_SELECTOR, ".specimen")            # components/CourseCard.vue 根节点
    CARD_NAMES = (By.CSS_SELECTOR, ".specimen .name")
    CARD_PRICES = (By.CSS_SELECTOR, ".specimen .price")
    EMPTY = (By.CSS_SELECTOR, ".empty")
    PAGE_INFO = (By.CSS_SELECTOR, ".pagination .info")

    def load(self):
        self.open("/course")
        self.find_visible(self.SEARCH)
        self.wait_result()
        return self

    def wait_result(self, min_wait=0.0):
        """等列表稳定：先等首屏出现卡片或空态，再等数量不再变化。

        搜索/筛选触发的刷新必须带 min_wait —— 旧卡片在新数据回来前仍在 DOM 上，
        只等「元素存在」会立刻返回旧结果。
        """
        from selenium.webdriver.support.ui import WebDriverWait

        WebDriverWait(self.driver, self.timeout).until(
            lambda d: d.find_elements(By.CSS_SELECTOR, ".specimen")
            or d.find_elements(By.CSS_SELECTOR, ".empty"))
        self.wait_settled(self.CARDS, min_wait=min_wait)
        return self

    def search(self, keyword):
        self.type(self.SEARCH, keyword)
        self.find(self.SEARCH).send_keys(Keys.ENTER)
        return self.wait_result(min_wait=0.8)

    def sort_by(self, visible_text):
        Select(self.find(self.SORT)).select_by_visible_text(visible_text)
        return self.wait_result(min_wait=0.8)

    def click_chip(self, text):
        self.click((By.XPATH,
                    f"//button[contains(@class,'chip') and normalize-space(text())='{text}']"))
        return self.wait_result(min_wait=0.8)

    def chip_texts(self):
        return self.texts(self.CHIPS)

    def active_chip_texts(self):
        return self.texts(self.ACTIVE_CHIPS)

    def card_names(self):
        return self.texts(self.CARD_NAMES)

    def card_prices(self):
        return self.texts(self.CARD_PRICES)

    def open_course(self, course_id):
        self.open(f"/course/{course_id}")
        return CourseDetailPage(self.driver)


# ================================================================ 课程详情页

class CourseDetailPage(BasePage):
    """views/course/CourseDetail.vue

    根节点是 v-if="data.course"，数据回来之前整个页面是空的，
    所以 load() 必须等待 .side-card 出现。
    """

    TITLE = (By.CSS_SELECTOR, "h1.title")
    SIDE_CARD = (By.CSS_SELECTOR, ".side-card")
    SIDE_PRICE = (By.CSS_SELECTOR, ".side-price")
    TAGS = (By.CSS_SELECTOR, ".tags .tag")
    CHAPTERS = (By.CSS_SELECTOR, "ol.chapters li")

    BTN_ADD_CART = (By.XPATH, "//button[normalize-space(text())='加入购物车']")
    BTN_START_STUDY = (By.XPATH, "//button[normalize-space(text())='开始学习']")
    BTN_COLLECT = (By.XPATH, "//button[normalize-space(text())='收藏']")

    def load(self, course_id):
        self.open(f"/course/{course_id}")
        self.find_visible(self.SIDE_CARD)
        return self

    def title_text(self):
        return self.text(self.TITLE)

    def price_text(self):
        return self.text(self.SIDE_PRICE)

    def chapter_names(self):
        return self.texts((By.CSS_SELECTOR, "ol.chapters .ch-name"))

    def add_to_cart(self):
        """未登录点击会跳登录页；已登录非学生弹 info 提示。"""
        self.click(self.BTN_ADD_CART)
        return self

    def collect(self):
        self.click(self.BTN_COLLECT)
        return self

    def start_study(self):
        self.click(self.BTN_START_STUDY)
        return self


# ================================================================ 购物车页

class CartPage(BasePage):
    """views/Cart.vue"""

    TITLE = (By.CSS_SELECTOR, ".page-title")
    ITEMS = (By.CSS_SELECTOR, ".cart-item")
    NAMES = (By.CSS_SELECTOR, ".cart-item .main .name")
    CHECKBOXES = (By.CSS_SELECTOR, ".cart-item input[type='checkbox']")
    CHECKED_SUMMARY = (By.CSS_SELECTOR, ".checkout p.text-muted")
    TOTAL = (By.CSS_SELECTOR, ".checkout .total strong")
    EMPTY = (By.CSS_SELECTOR, ".empty")
    BTN_CHECKOUT = (By.XPATH, "//button[normalize-space(text())='去结算']")

    def load(self):
        self.open("/cart")
        self.find_visible(self.BTN_CHECKOUT)
        return self

    def wait_loaded(self, timeout=UI_TIMEOUT):
        from selenium.webdriver.support.ui import WebDriverWait
        WebDriverWait(self.driver, timeout).until(
            lambda d: d.find_elements(By.CSS_SELECTOR, ".cart-item")
            or d.find_elements(By.CSS_SELECTOR, ".empty"))
        return self

    def course_names(self):
        return self.texts(self.NAMES)

    def check_all(self):
        """勾选所有未选中的条目（结算要求至少勾选一件）。"""
        for cb in self.finds(self.CHECKBOXES):
            if not cb.is_selected():
                cb.click()
        return self

    def uncheck_all(self):
        for cb in self.finds(self.CHECKBOXES):
            if cb.is_selected():
                cb.click()
        return self

    def checked_summary_text(self):
        return self.text(self.CHECKED_SUMMARY)

    def checkout(self):
        self.click(self.BTN_CHECKOUT)
        return self


# ================================================================ 订单列表页

class OrderListPage(BasePage):
    """views/order/OrderList.vue"""

    TITLE = (By.CSS_SELECTOR, ".page-title")
    STATUS_SELECT = (By.CSS_SELECTOR, "select.select")
    ROWS = (By.CSS_SELECTOR, "table.table tbody tr")
    HEADERS = (By.CSS_SELECTOR, "table.table thead th")
    EMPTY = (By.CSS_SELECTOR, ".empty")
    FIRST_ORDER_NO = (By.CSS_SELECTOR, "table.table tbody tr:first-child td:first-child")

    # 行内按钮：列表页的「支付」「取消」只在订单状态为待支付时渲染
    BTN_PAY_ROW = (By.XPATH,
                   "//table[contains(@class,'table')]//button[normalize-space(text())='支付']")
    BTN_CANCEL_ROW = (By.XPATH,
                      "//table[contains(@class,'table')]//button[normalize-space(text())='取消']")
    BTN_DETAIL_ROW = (By.XPATH,
                      "//table[contains(@class,'table')]//button[normalize-space(text())='详情']")

    def load(self):
        self.open("/order")
        self.find_visible(self.TITLE)
        return self

    def wait_loaded(self, timeout=UI_TIMEOUT):
        from selenium.webdriver.support.ui import WebDriverWait
        WebDriverWait(self.driver, timeout).until(
            lambda d: d.find_elements(By.CSS_SELECTOR, "table.table tbody tr")
            or d.find_elements(By.CSS_SELECTOR, ".empty"))
        return self

    def header_texts(self):
        return self.texts(self.HEADERS)

    def row_count(self):
        return self.count(self.ROWS)

    def row_statuses(self):
        return [r.find_element(By.CSS_SELECTOR, "td:nth-child(3)").text.strip()
                for r in self.finds(self.ROWS)]

    def wait_row_status(self, expected, timeout=UI_TIMEOUT):
        """等列表中出现指定状态的订单（支付/取消后页面会重新拉取列表）。"""
        from selenium.webdriver.support.ui import WebDriverWait

        def _is(d):
            try:
                return expected in self.row_statuses()
            except Exception:
                return False

        WebDriverWait(self.driver, timeout).until(_is)
        return self

    def pay_first(self):
        self.click(self.BTN_PAY_ROW)
        return self

    def cancel_first(self):
        self.click(self.BTN_CANCEL_ROW)
        return self

    def filter_status(self, visible_text):
        Select(self.find(self.STATUS_SELECT)).select_by_visible_text(visible_text)
        return self


# ================================================================ 订单详情页

class OrderDetailPage(BasePage):
    """views/order/OrderDetail.vue

    注意与列表页的文案差异：详情页是「立即支付」「取消订单」，
    列表页是「支付」「取消」。
    """

    TITLE = (By.CSS_SELECTOR, ".page-title")
    ORDER_NO = (By.CSS_SELECTOR, ".order-no")
    STATUS_TAG = (By.CSS_SELECTOR, ".panel .tag")
    TOTAL = (By.CSS_SELECTOR, ".total")
    ITEM_ROWS = (By.CSS_SELECTOR, "table.table tbody tr")

    BTN_PAY = (By.XPATH, "//button[normalize-space(text())='立即支付']")
    BTN_CANCEL = (By.XPATH, "//button[normalize-space(text())='取消订单']")
    BTN_GO_STUDY = (By.XPATH, "//button[normalize-space(text())='去学习']")

    def load(self, order_id):
        self.open(f"/order/{order_id}")
        self.find_visible(self.ORDER_NO)
        return self

    def order_no(self):
        return self.text(self.ORDER_NO)

    def status_text(self):
        return self.text(self.STATUS_TAG)

    def wait_status(self, expected, timeout=UI_TIMEOUT):
        """等状态标签变为指定文案（支付/取消后页面会重新拉取订单）。"""
        from selenium.webdriver.support.ui import WebDriverWait

        def _is(d):
            try:
                return d.find_element(*self.STATUS_TAG).text.strip() == expected
            except Exception:
                return False

        WebDriverWait(self.driver, timeout).until(_is)
        return self

    def pay(self):
        self.click(self.BTN_PAY)
        return self

    def cancel(self):
        self.click(self.BTN_CANCEL)
        return self


# ==================================================================== 我的课程

class MyCoursePage(BasePage):
    """views/course/MyCourse.vue（路由 /user/my-course）"""

    TITLE = (By.CSS_SELECTOR, ".page-title")
    ITEMS = (By.CSS_SELECTOR, ".course-item")
    NAMES = (By.CSS_SELECTOR, ".course-item .name")
    EMPTY = (By.CSS_SELECTOR, ".empty")

    def load(self):
        self.open("/user/my-course")
        self.find_visible(self.TITLE)
        return self

    def wait_loaded(self, timeout=UI_TIMEOUT):
        from selenium.webdriver.support.ui import WebDriverWait
        WebDriverWait(self.driver, timeout).until(
            lambda d: d.find_elements(By.CSS_SELECTOR, ".course-item")
            or d.find_elements(By.CSS_SELECTOR, ".empty"))
        return self

    def course_names(self):
        return self.texts(self.NAMES)


# ==================================================================== 学习页

class CourseStudyPage(BasePage):
    """views/course/CourseStudy.vue（路由 /course/study/:id）

    学习接口报错时页面会 router.replace 回课程详情页，
    所以「学生访问未购买的付费课」的预期结果是最终停在 /course/:id。
    """

    GRID = (By.CSS_SELECTOR, ".study-grid")
    SIDE_TITLE = (By.CSS_SELECTOR, ".side-title")
    CHAPTER_ITEMS = (By.CSS_SELECTOR, "ol.chapters li")
    BTN_MARK_DONE = (By.XPATH, "//button[normalize-space(text())='标记完成']")
    PROGRESS_TEXT = (By.CSS_SELECTOR, ".page p.text-muted")

    def load(self, course_id):
        self.open(f"/course/study/{course_id}")
        return self

    def wait_ready(self, timeout=UI_TIMEOUT):
        self.find_visible(self.GRID, timeout)
        return self

    def chapter_count(self):
        return self.count(self.CHAPTER_ITEMS)

    def mark_done(self):
        self.click(self.BTN_MARK_DONE)
        return self


# ==================================================================== 职位列表

class JobListPage(BasePage):
    """views/job/JobList.vue"""

    TITLE = (By.CSS_SELECTOR, ".page-title")
    SEARCH = (By.CSS_SELECTOR, 'input[placeholder="搜索职位或公司"]')
    CHIPS = (By.CSS_SELECTOR, "button.chip")
    ACTIVE_CHIPS = (By.CSS_SELECTOR, "button.chip.active")
    CARDS = (By.CSS_SELECTOR, ".specimen")     # components/JobCard.vue 根节点
    CARD_NAMES = (By.CSS_SELECTOR, ".specimen .name")
    CARD_COMPANIES = (By.CSS_SELECTOR, ".specimen .company")
    CARD_SALARIES = (By.CSS_SELECTOR, ".specimen .salary")
    EMPTY = (By.CSS_SELECTOR, ".empty")

    def load(self):
        self.open("/job")
        self.find_visible(self.SEARCH)
        self.wait_result()
        return self

    def wait_result(self, min_wait=0.0):
        """见 CourseListPage.wait_result 的说明。"""
        from selenium.webdriver.support.ui import WebDriverWait

        WebDriverWait(self.driver, self.timeout).until(
            lambda d: d.find_elements(By.CSS_SELECTOR, ".specimen")
            or d.find_elements(By.CSS_SELECTOR, ".empty"))
        self.wait_settled(self.CARDS, min_wait=min_wait)
        return self

    def search(self, keyword):
        self.type(self.SEARCH, keyword)
        self.find(self.SEARCH).send_keys(Keys.ENTER)
        return self.wait_result(min_wait=0.8)

    def click_chip(self, text):
        self.click((By.XPATH,
                    f"//button[contains(@class,'chip') and normalize-space(text())='{text}']"))
        return self.wait_result(min_wait=0.8)

    def active_chip_texts(self):
        return self.texts(self.ACTIVE_CHIPS)

    def card_names(self):
        return self.texts(self.CARD_NAMES)


# ==================================================================== 职位详情

class JobDetailPage(BasePage):
    """views/job/JobDetail.vue

    根节点 v-if="job"，数据回来前是空白。
    「投递简历」会弹出一个自绘的 modal（不是组件库弹窗）。
    """

    TITLE = (By.CSS_SELECTOR, "h1.title")
    SIDE_CARD = (By.CSS_SELECTOR, ".side-card")
    SIDE_SALARY = (By.CSS_SELECTOR, ".side-salary")
    TAGS = (By.CSS_SELECTOR, ".tags .tag")

    BTN_INTERVIEW = (By.XPATH, "//button[normalize-space(text())='参加 AI 面试']")
    BTN_APPLY = (By.XPATH, "//button[normalize-space(text())='投递简历']")
    BTN_COLLECT = (By.XPATH, "//button[normalize-space(text())='收藏']")

    MODAL = (By.CSS_SELECTOR, ".modal-mask .modal")
    MODAL_TITLE = (By.CSS_SELECTOR, ".modal-head h3")
    MODAL_EMPTY = (By.CSS_SELECTOR, ".modal-empty")
    MODAL_RESUME_OPTS = (By.CSS_SELECTOR, ".resume-opts .opt")
    MODAL_CONFIRM = (By.XPATH, "//div[contains(@class,'modal-foot')]//button[normalize-space(text())='确认投递']")
    BTN_MODAL_CANCEL = (By.XPATH, "//div[contains(@class,'modal-foot')]//button[normalize-space(text())='取消']")

    def load(self, job_id):
        self.open(f"/job/{job_id}")
        self.find_visible(self.SIDE_CARD)
        return self

    def title_text(self):
        return self.text(self.TITLE)

    def salary_text(self):
        return self.text(self.SIDE_SALARY)

    def open_apply_modal(self):
        self.click(self.BTN_APPLY)
        self.find_visible(self.MODAL)
        return self

    def confirm_apply(self):
        self.click(self.MODAL_CONFIRM)
        return self

    def collect(self):
        self.click(self.BTN_COLLECT)
        return self


# ==================================================================== 个人中心

class ProfilePage(BasePage):
    """views/user/Profile.vue"""

    NAME = (By.CSS_SELECTOR, ".user-card .u-name-row .name")
    USERNAME = (By.CSS_SELECTOR, ".user-card .u-sub")
    ROLE_BADGE = (By.CSS_SELECTOR, ".user-card .role-badge")
    AVATAR = (By.CSS_SELECTOR, ".user-card .avatar")
    LINK_MY_APPLY = (By.CSS_SELECTOR, '.user-card a[href="/user/my-apply"]')
    LINK_RESUME = (By.CSS_SELECTOR, '.user-card a[href="/user/resume"]')

    def load(self):
        self.open("/user")
        self.find_visible(self.USERNAME)
        return self

    def display_name(self):
        return self.text(self.NAME)

    def username_text(self):
        return self.text(self.USERNAME)      # 渲染为 @username

    def role_text(self):
        return self.text(self.ROLE_BADGE)


# ==================================================================== 后台管理

class AdminPage(BasePage):
    """views/admin/AdminLayout.vue 及子页

    /admin 下 App.vue 不渲染前台页头，因此后台用例不要用 Header。
    """

    SIDE = (By.CSS_SELECTOR, "aside.side")
    MENU_LINKS = (By.CSS_SELECTOR, "aside.side nav a.link")
    ACTIVE_MENU = (By.CSS_SELECTOR, "aside.side nav a.link.router-link-active")
    MAIN = (By.CSS_SELECTOR, "main.main")
    PAGE_TITLE = (By.CSS_SELECTOR, ".page-title")

    # 各子页面的路由
    ROUTES = {
        "申请审核": "/admin/apply",
        "用户管理": "/admin/users",
        "课程管理": "/admin/courses",
        "职位管理": "/admin/jobs",
        "订单管理": "/admin/orders",
    }

    # 申请审核页（views/admin/ApplyAudit.vue）
    TAB_PENDING = (By.XPATH, "//button[normalize-space(text())='待审核']")
    TAB_AUDITED = (By.XPATH, "//button[normalize-space(text())='已处理']")
    AUDIT_ROWS = (By.CSS_SELECTOR, "table.table tbody tr")
    AUDIT_EMPTY = (By.CSS_SELECTOR, ".empty")
    HEADERS = (By.CSS_SELECTOR, "table.table thead th")
    BTN_PASS = (By.XPATH,
                "//table[contains(@class,'table')]//button[normalize-space(text())='通过']")
    BTN_REJECT = (By.XPATH,
                  "//table[contains(@class,'table')]//button[normalize-space(text())='驳回']")

    def load(self, path="/admin/apply"):
        self.open(path)
        self.find_visible(self.SIDE)
        return self

    def menu_texts(self):
        """左侧菜单文案：申请审核 / 用户管理 / 课程管理 / 职位管理 / 订单管理"""
        return self.texts(self.MENU_LINKS)

    def go(self, path):
        self.open(path)
        self.find_visible(self.SIDE)
        return self

    def wait_page_loaded(self, timeout=UI_TIMEOUT):
        from selenium.webdriver.support.ui import WebDriverWait
        WebDriverWait(self.driver, timeout).until(
            lambda d: d.find_elements(By.CSS_SELECTOR, "table.table tbody tr")
            or d.find_elements(By.CSS_SELECTOR, ".empty"))
        return self

    def header_texts(self):
        return self.texts(self.HEADERS)

    def page_title(self):
        return self.text(self.PAGE_TITLE)


# ==================================================================== 返回按钮

class BackButton(BasePage):
    """components/BackButton.vue

    顶级页面（/、/course、/job、/login、/register、/forgot）与 /admin 下都不渲染。
    按钮内除了「返回」文案还有一个 svg，所以按文案定位要用 contains 而非 text()。
    """

    BAR = (By.CSS_SELECTOR, ".backbar")
    BTN = (By.CSS_SELECTOR, ".backbar .back-btn")

    def click_back(self):
        self.click(self.BTN)
        return self


# ==================================================================== 404 页

class NotFoundPage(BasePage):
    """views/NotFound.vue"""

    TITLE = (By.CSS_SELECTOR, ".page.empty h1")
    HOME_BTN = (By.XPATH, "//button[normalize-space(text())='回到首页']")

    def load(self, path="/this-path-does-not-exist"):
        self.open(path)
        self.find_visible(self.TITLE)
        return self

    def title_text(self):
        return self.text(self.TITLE)


def parse_id_from_url(url):
    """从 .../order/12 这类 URL 中取出末尾的数字 id。"""
    m = re.search(r"/(\d+)(?:[/?#]|$)", url)
    return int(m.group(1)) if m else None
