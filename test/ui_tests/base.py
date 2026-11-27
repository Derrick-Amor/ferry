"""Selenium UI 自动化公共层：显式等待封装 + 页面对象基类。

下面这些约定全部来自前端源码事实（Smart_education_platform_frontend），
写用例前请先确认仍然成立，不要凭直觉改：

1. 前端没有引入任何 UI 组件库（无 Element Plus / Ant Design Vue / Vant），
   页面全是手写原生 HTML，因此定位器只能用语义化 class（.input / .select /
   .btn / .field / .toast / .specimen / .table）加中文文案 XPath。
   项目中不存在 .el-input__inner、.el-table__row 这类节点。

2. 全项目没有 data-testid、没有 id、没有 name 属性
   （唯一的 id 是 index.html 里的 #app），所以禁止使用 By.ID / By.NAME。

3. 页头 AppHeader 与登录/注册表单页存在同名按钮（"登录"、"注册"）。
   直接写 //button[text()='登录'] 会匹配到 2 个元素，
   表单页定位必须用 //div[contains(@class,'auth-card')] 限定作用域。

4. 所有表单校验错误和接口错误都统一以顶部 Toast 呈现（App.vue 底部的
   <Toast/>），页面上没有任何行内错误节点，不要去找输入框下方的红字。
   Toast 在 2.6 秒后会被自动移除，因此 wait_toast 的超时给 3 秒，
   且必须在触发动作之后立刻断言。

5. 不使用隐式等待(implicitly_wait)。隐式等待会让 WebDriverWait 的每一次
   轮询都先等满隐式超时，反而把等待时间放大；本工程统一用显式等待。
"""

import re
import time

from selenium.common.exceptions import StaleElementReferenceException, TimeoutException
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

from config import FRONT_URL, UI_TIMEOUT

# 登录态 token 的 localStorage key，见前端 src/utils/auth.js
TOKEN_KEY = "smart_edu_token"


class BasePage:
    """所有页面对象的基类，封装等待、取值与断言。"""

    def __init__(self, driver, timeout=UI_TIMEOUT):
        self.driver = driver
        self.timeout = timeout

    # ------------------------------------------------------------------ 导航

    def open(self, path="/"):
        self.driver.get(FRONT_URL + path)
        return self

    @property
    def url(self):
        return self.driver.current_url

    @property
    def path(self):
        """当前 URL 去掉域名后的路径，便于断言跳转。"""
        u = self.driver.current_url.split("#")[0].split("?")[0]
        return u[len(FRONT_URL):] if u.startswith(FRONT_URL) else u

    def wait_url(self, path, timeout=None):
        """等待路径精确变为 path（SPA 路由跳转是异步的）。

        前端路由守卫的跳转目标（来自 src/router/index.js）：
          未登录访问受保护页  -> /login
          已登录但角色不符    -> /（首页，不是 403 页面）
          已登录访问登录页    -> /
        """
        target = (FRONT_URL + path).rstrip("/")

        def _reached(d):
            cur = d.current_url.split("#")[0].split("?")[0].rstrip("/")
            return cur == target

        WebDriverWait(self.driver, timeout or self.timeout).until(_reached)
        return self

    def wait_url_matches(self, pattern, timeout=None):
        """等待路径匹配正则，用于 /order/123 这类带 id 的跳转。"""
        WebDriverWait(self.driver, timeout or self.timeout).until(
            lambda d: re.search(pattern, d.current_url.split("#")[0].split("?")[0]))
        return self

    def assert_path(self, path):
        assert self.path == path, f"当前路径不符：期望 {path}，实际 {self.path}"

    # -------------------------------------------------------------- 元素操作

    def find(self, locator, timeout=None):
        """等待元素出现在 DOM 中（不要求可见）。"""
        return WebDriverWait(self.driver, timeout or self.timeout).until(
            EC.presence_of_element_located(locator))

    def find_visible(self, locator, timeout=None):
        """等待元素可见。数据未加载完时根节点可能是 v-if=false，必须用这个。"""
        return WebDriverWait(self.driver, timeout or self.timeout).until(
            EC.visibility_of_element_located(locator))

    def finds(self, locator):
        return self.driver.find_elements(*locator)

    def click(self, locator, timeout=None):
        WebDriverWait(self.driver, timeout or self.timeout).until(
            EC.element_to_be_clickable(locator)).click()
        return self

    def type(self, locator, text):
        el = self.find_visible(locator)
        el.clear()
        el.send_keys(text)
        return self

    def text(self, locator, timeout=None):
        return self.find_visible(locator, timeout).text.strip()

    def texts(self, locator, retries=3):
        """取一组元素的文案。

        列表异步刷新时旧节点会被 Vue 整批替换，读取途中可能命中
        StaleElementReferenceException，这里整批重试而不是让用例直接挂掉。
        """
        for attempt in range(retries):
            try:
                return [e.text.strip() for e in self.finds(locator) if e.text.strip()]
            except StaleElementReferenceException:
                time.sleep(0.2 * (attempt + 1))
        return []

    def count(self, locator):
        return len(self.finds(locator))

    def wait_settled(self, locator, timeout=UI_TIMEOUT, quiet=0.5, min_wait=0.0):
        """等一组元素「稳定」下来，返回稳定时的元素数量。

        列表页的搜索/筛选是异步请求：新数据回来之前，**旧数据仍然挂在 DOM 上**。
        如果只等「元素存在」，会在旧数据上就返回，随后 Vue 替换节点，
        断言读到过期内容，甚至抛 StaleElementReferenceException。

        这里要求元素数量在 quiet 秒内保持不变才算刷新完成；
        min_wait 用于「结果和刷新前一样」的场景（比如搜索前后都是同一批卡片），
        此时数量永远不变，需要先等一个最短时长让请求有机会返回。

        注意这是有界的经验等待，不是真正的网络等待 —— 本地后端响应在百毫秒级，
        quiet=0.5 足够；如果哪天后端变慢，调大 quiet 即可。
        """
        deadline = time.time() + timeout
        started = time.time()
        last_n, stable_since = None, None

        while time.time() < deadline:
            try:
                n = len(self.driver.find_elements(*locator))
            except StaleElementReferenceException:
                n = None

            now = time.time()
            if n == last_n:
                if (stable_since is not None
                        and now - stable_since >= quiet
                        and now - started >= min_wait):
                    return n
            else:
                last_n, stable_since = n, now

            time.sleep(0.1)
        return last_n

    def visible(self, locator, timeout=3):
        """元素在超时内是否可见（用于「应该出现」的软断言）。"""
        try:
            WebDriverWait(self.driver, timeout).until(
                EC.visibility_of_element_located(locator))
            return True
        except TimeoutException:
            return False

    def absent(self, locator, timeout=2):
        """元素在超时内始终不出现（用于「不应该存在」的断言）。"""
        try:
            WebDriverWait(self.driver, timeout).until(
                EC.presence_of_element_located(locator))
            return False
        except TimeoutException:
            return True

    # ------------------------------------------------------- Toast 消息断言

    def wait_toast(self, text=None, kind=None, timeout=3):
        """等待一条 Toast 出现并返回其文案；text 为 None 时返回任意一条。

        Toast 2.6 秒后自动移除，超时务必给得小，并在触发动作后立刻调用。
        """
        css = f".toast--{kind}" if kind else ".toast"

        def _found(d):
            for el in d.find_elements(By.CSS_SELECTOR, css):
                t = el.text.strip()
                if t and (text is None or t == text):
                    return t
            return False

        return WebDriverWait(self.driver, timeout).until(_found)

    def assert_toast(self, text, kind=None):
        actual = self.wait_toast(text=text, kind=kind)
        assert actual == text, f"Toast 文案不符：期望 {text!r}，实际 {actual!r}"
        return self

    def assert_any_toast(self, kind=None, timeout=3):
        """断言出现了提示但不断言具体文案（后端错误文案不稳定时用）。"""
        return self.wait_toast(kind=kind, timeout=timeout)

    def assert_no_toast(self, kind="error", timeout=2):
        assert self.absent((By.CSS_SELECTOR, f".toast--{kind}"), timeout), \
            f"出现了意料之外的 .toast--{kind} 提示"
        return self

    def clear_toasts(self, timeout=4):
        """等当前屏幕上的 Toast 全部消失，避免污染下一步断言。"""
        try:
            WebDriverWait(self.driver, timeout).until(
                lambda d: not d.find_elements(By.CSS_SELECTOR, ".toast"))
        except TimeoutException:
            pass
        return self

    # ------------------------------------------------------------ 登录态

    def clear_login(self):
        """清空 localStorage 中的登录态。"""
        self.driver.execute_script("window.localStorage.clear();")
        return self

    def set_token(self, token):
        """直接把 token 写进 localStorage，跳过登录页 UI。

        前端 isLogin 只判断 token 是否存在（src/store/auth.js），
        路由守卫会在首次跳转时自动补拉用户信息，因此注入后刷新页面即视为已登录。
        这是给「前置条件只需要已登录」的用例提速用的；
        专门验证登录功能的用例仍然要走真实的登录页交互。
        """
        self.driver.execute_script(
            "window.localStorage.setItem(arguments[0], arguments[1]);",
            TOKEN_KEY, token)
        return self
