"""UI 自动化专属 fixture。

只在本目录生效，不会影响 test_01 ~ test_09 的接口用例。

前置条件（缺一不可）：
  1. 后端已启动        http://localhost:8080
  2. 前端已启动        http://localhost:5173   （npm run dev）
  3. 数据库已按前置脚本重置（free 课程 id=1、付费课程 id=2、职位 id=1）
"""

import os
import sys
import time

import pytest
import requests

# 保证能 import 到工程根目录下的 config.py
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import ACCOUNTS, BASE_URL, FRONT_URL, UI_BROWSER, UI_HEADLESS, UI_TIMEOUT
from selenium import webdriver
from selenium.common.exceptions import SessionNotCreatedException

from ui_tests.base import TOKEN_KEY


# --------------------------------------------------------------- 环境就绪检查

def _require(url, name, hint):
    try:
        resp = requests.get(url, timeout=5)
        resp.raise_for_status()
    except requests.RequestException as e:
        pytest.exit(
            f"\n[UI 自动化] {name} 未就绪，无法开始。\n"
            f"  地址：{url}\n"
            f"  原因：{e}\n"
            f"  {hint}\n",
            returncode=1,
        )


@pytest.fixture(scope="session", autouse=True)
def _services_ready():
    """执行任何 UI 用例前，先确认前后端都在跑，避免一堆看不懂的超时失败。"""
    _require(f"{BASE_URL}/api/course/list", "后端服务",
             "请先启动 Spring Boot 后端（端口 8080）。")
    _require(FRONT_URL, "前端 dev server",
             "请在 Smart_education_platform_frontend 下执行 npm run dev（端口 5173）。")
    return True


def _login_ok(acc):
    """账号能否登录（只看返回码，不关心 token 内容）。"""
    try:
        body = requests.post(f"{BASE_URL}/api/user/login",
                             json={"username": acc["username"],
                                   "password": acc["password"]}, timeout=10).json()
    except requests.RequestException:
        return False
    return body.get("code") == 200


@pytest.fixture(scope="session", autouse=True)
def _ensure_accounts(_services_ready):
    """确保 student01 / teacher01 存在，缺失就按接口套件的规则自动注册。

    数据库刚重建时 user 表是空的，这两个账号并不存在；而注册它们的逻辑在
    工程根目录 conftest 的 tokens fixture 里，只有请求了 tokens 的用例才会触发。
    test_ui_01_auth.py 的登录用例走真实登录页、不依赖 tokens，又是整套里最先
    执行的，没有这个前置就会因为「账号不存在」而超时失败 —— 表现为用例时好时坏，
    取决于库里是否恰好已经有人注册过。

    admin 不在此列：管理员账号由后端 DataInitializer 在启动时创建，
    也不应该通过公开的注册接口去造（RegisterDTO 的 role 是客户端可控的）。
    """
    for role, acc in ACCOUNTS.items():
        if role == "admin" or _login_ok(acc):
            continue
        requests.post(f"{BASE_URL}/api/user/register", json=acc, timeout=15)
        assert _login_ok(acc), f"前置账号 {acc['username']} 注册后仍无法登录"
        print(f"\n[UI 自动化] 前置账号 {acc['username']} 不存在，已自动注册。")
    return True


# ------------------------------------------------------------------- 浏览器

# PATH 里可能残留的 webdriver 可执行文件名
_DRIVER_EXES = (
    "chromedriver.exe", "chromedriver",
    "msedgedriver.exe", "msedgedriver",
    "geckodriver.exe", "geckodriver",
)


def _drop_stale_drivers_from_path():
    """把 PATH 中残留的 webdriver 目录摘掉（仅在版本不匹配时作为兜底）。

    Selenium Manager 会优先使用 PATH 中已存在的 driver。如果它是旧版本
    （本机 D:\\测试\\chromedriver 曾是 146 版，而 Chrome 已升到 152），
    启动会直接抛：
        SessionNotCreatedException: This version of ChromeDriver only supports Chrome version 146

    摘掉该目录后 Selenium Manager 会自动下载并缓存与浏览器匹配的版本
    （缓存位置 ~/.cache/selenium），无需手动管理 driver。

    想保留 PATH 里的 driver，可设环境变量 UI_KEEP_PATH_DRIVER=1。
    """
    if os.getenv("UI_KEEP_PATH_DRIVER") == "1":
        return []

    removed, kept = [], []
    for d in os.environ.get("PATH", "").split(os.pathsep):
        if d and any(os.path.exists(os.path.join(d, exe)) for exe in _DRIVER_EXES):
            removed.append(d)
        else:
            kept.append(d)

    if removed:
        os.environ["PATH"] = os.pathsep.join(kept)
    return removed


def _create_driver():
    """按 config.UI_BROWSER 创建浏览器实例。

    Selenium 4.6+ 自带 Selenium Manager，会自动下载并匹配对应版本的 driver，
    不需要再手动配置 chromedriver 路径。首次运行需要联网。
    """
    browser = UI_BROWSER.lower()
    if browser == "firefox":
        opts = webdriver.FirefoxOptions()
        opts.add_argument("--width=1440")
        opts.add_argument("--height=900")
        if UI_HEADLESS:
            opts.add_argument("-headless")
        return webdriver.Firefox(options=opts)
    if browser == "edge":
        opts = webdriver.EdgeOptions()
        opts.add_argument("--window-size=1440,900")
        if UI_HEADLESS:
            opts.add_argument("--headless=new")
        return webdriver.Edge(options=opts)

    opts = webdriver.ChromeOptions()
    opts.add_argument("--window-size=1440,900")
    if UI_HEADLESS:
        opts.add_argument("--headless=new")
    # 关掉 Chrome 的 DevTools 日志噪音
    opts.add_experimental_option("excludeSwitches", ["enable-logging"])
    return webdriver.Chrome(options=opts)


def _build_driver():
    """创建 driver，并在版本不匹配时自愈。

    先按正常方式启动（如果 PATH 里的 driver 版本正确就直接用它，不必重新下载）；
    只有当 Selenium Manager 因为 PATH 中残留旧版 driver 而报
    SessionNotCreatedException 时，才临时把那些目录摘掉重试一次。
    """
    try:
        return _create_driver()
    except SessionNotCreatedException:
        stale = _drop_stale_drivers_from_path()
        if not stale:
            raise
        print(f"\n[UI 自动化] 检测到 PATH 中的 driver 与浏览器版本不匹配，"
              f"已临时移除：{stale}")
        print("[UI 自动化] 改为使用 Selenium Manager 自动获取的匹配版本。\n")
        return _create_driver()


@pytest.fixture(scope="session")
def driver(_services_ready):
    """整个会话共用一个浏览器实例，只开一次、只关一次。

    不在浏览器上设 implicitly_wait：隐式等待会让 WebDriverWait 的每次轮询
    都先等满隐式超时，反而放大等待时间。本工程统一用显式等待（见 base.py）。
    """
    d = _build_driver()
    d.get(FRONT_URL)          # 先落到目标源上，之后才能操作 localStorage
    yield d
    d.quit()


@pytest.fixture
def ui(driver):
    """单个用例的干净起点：清空登录态，回到首页。"""
    driver.execute_script("window.localStorage.clear();")
    driver.get(FRONT_URL)
    return driver


# ------------------------------------------------------------------- 登录态

def login_by_token(driver, token, path="/"):
    """把 token 直接写进 localStorage 并打开页面，跳过登录页 UI。

    前端 isLogin 只判断 token 是否存在（src/store/auth.js），
    路由守卫会在首次跳转时自动补拉用户信息，所以注入后刷新即视为已登录。
    这是给「前置条件只要已登录」的用例提速用的；
    专门验证登录功能的用例仍然走真实登录页交互。
    """
    driver.execute_script("window.localStorage.clear();")
    driver.execute_script(
        "window.localStorage.setItem(arguments[0], arguments[1]);",
        TOKEN_KEY, token)
    driver.get(FRONT_URL + path)
    return driver


@pytest.fixture
def student_ui(ui, tokens):
    """已登录的学生会话。tokens 来自工程根目录 conftest，会自动注册缺失账号。"""
    return login_by_token(ui, tokens["student"])


@pytest.fixture
def teacher_ui(ui, tokens):
    """已登录的讲师/企业会话。"""
    return login_by_token(ui, tokens["teacher"])


@pytest.fixture
def admin_ui(ui, tokens):
    """已登录的管理员会话。"""
    return login_by_token(ui, tokens["admin"])


@pytest.fixture
def fresh_student(base_url):
    """通过接口注册一个全新的学生账号，返回 {token, username, password}。

    购买闭环用例必须是「没有买过课」的学生：
    复用 student01 会在第二次执行时因为已购状态而失败，无法重复运行。
    账号名/手机号带毫秒时间戳，保证每次运行都是新用户。
    """
    ts = str(int(time.time() * 1000))[-10:]
    account = {
        "username": f"ui{ts}",          # 12 位，满足 3-20 且仅含字母数字
        "password": "123456",
        "phone": f"1{ts}",              # 1 + 10 位数字 = 11 位
        "email": f"ui{ts}@test.com",
        "role": 0,
    }
    r = requests.post(f"{base_url}/api/user/register", json=account, timeout=15).json()
    assert r.get("code") == 200, f"UI 用例前置注册失败：{r}"

    r = requests.post(f"{base_url}/api/user/login",
                      json={"username": account["username"],
                            "password": account["password"]}, timeout=10).json()
    assert r.get("code") == 200, f"UI 用例前置登录失败：{r}"

    account["token"] = r["data"]["token"]
    return account


@pytest.fixture
def fresh_student_ui(ui, fresh_student):
    """全新的、未购买过任何课程的学生会话。"""
    return login_by_token(ui, fresh_student["token"])
