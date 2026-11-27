# UI 自动化测试（Selenium + pytest）

对 `http://localhost:5173` 的 Vue 3 前端做真实浏览器端到端验证，
覆盖登录注册、路由守卫、课程浏览、购买闭环、订单流转、职位投递、后台管理等核心业务。

---

## 一、前置条件

三样都必须就绪，缺一个用例会直接失败：

| # | 条件 | 说明 |
|---|---|---|
| 1 | 后端已启动 | `http://localhost:8080` |
| 2 | **前端已启动** | `http://localhost:5173`，在 `Smart_education_platform_frontend` 下执行 `npm run dev` |
| 3 | 数据库已重置 | 需要 免费课 id=1（含 3 章节）、付费课 id=2、职位 id=1；admin / student01 / teacher01 可用 |

数据库重置脚本见 `C:\Users\csw\AppData\Local\Temp\reset_test_db.sql`。

---

## 二、运行方式

```bash
cd Smart_education_platform_testing/SEP_testing

# 只跑 UI 自动化
.venv/Scripts/python.exe -m pytest -m ui

# 只跑某一个模块
.venv/Scripts/python.exe -m pytest -m ui ui_tests/test_ui_01_auth.py

# 跑单条用例
.venv/Scripts/python.exe -m pytest -m ui -k "UI_TC_002"

# 接口 + UI 全跑
.venv/Scripts/python.exe -m pytest -m ""
```

> `pytest.ini` 里设了 `addopts = -m "not ui"`，所以**直接 `pytest` 只跑接口用例**，
> 不会因为没开前端而报一堆超时。

### 可选环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `UI_HEADLESS` | `0` | 设为 `1` 走无头模式，跑得更快但看不到过程 |
| `UI_BROWSER` | `chrome` | 可换 `edge` / `firefox` |
| `UI_TIMEOUT` | `10` | 显式等待超时（秒） |
| `UI_KEEP_PATH_DRIVER` | — | 设为 `1` 则不绕过 PATH 中的旧版 driver |

---

## 三、目录结构

```
ui_tests/
├── conftest.py            # driver 与登录态 fixture、环境就绪检查
├── base.py                # BasePage：显式等待、Toast 断言、localStorage 操作
├── pages.py               # 页面对象：每个页面一个类，定位器集中在这里
├── flows.py               # 跨用例复用的业务流（加购→结算→支付）与前置数据常量
├── test_ui_01_auth.py     # UI_TC_001~010  登录 / 登出
├── test_ui_02_register.py # UI_TC_011~020  注册与表单校验
├── test_ui_03_guard.py    # UI_TC_021~032  路由守卫与页头权限
├── test_ui_04_course.py   # UI_TC_033~044  课程列表与详情
├── test_ui_05_purchase.py # UI_TC_045~051  学生购买付费课端到端
├── test_ui_06_order.py    # UI_TC_052~061  订单列表 / 详情 / 状态流转
├── test_ui_07_job.py      # UI_TC_062~072  职位浏览与投递入口
├── test_ui_08_misc.py     # UI_TC_073~082  返回按钮 / 个人中心 / 404
└── test_ui_09_admin.py    # UI_TC_083~090  后台管理
```

页面对象模式：用例只调用 `pages.py` 里的方法，不直接写定位器。
前端改版时只需要改 `pages.py` 一个文件。

---

## 四、写定位器前必须知道的事实

这些结论来自阅读前端源码，**改动前请先核对源码**，不要凭直觉写：

1. **前端没有用任何 UI 组件库**（无 Element Plus / Ant Design Vue / Vant），
   页面全是手写原生 HTML。所以不存在 `.el-input__inner`、`.el-table__row` 这类节点。

2. **全项目没有 `data-testid`、没有 `id`、没有 `name` 属性**，
   唯一的 `id` 是 `index.html` 里的 `#app`。
   因此定位器只能用语义化 class + 中文文案 XPath，
   且**禁止使用 `By.ID` / `By.NAME`**。

3. **页头和表单页有同名按钮**。未登录时页头也有「登录」「注册」按钮，
   直接写 `//button[text()='登录']` 会匹配到 2 个元素。
   表单页定位必须限定作用域：`//div[contains(@class,'auth-card')]//button[...]`。

4. **所有校验错误和接口错误都走顶部 Toast**（`App.vue` 底部的 `<Toast/>`），
   页面上**没有任何行内错误节点**，不要去找输入框下面的红字。
   Toast 在 **2.6 秒**后自动移除，所以断言必须在触发动作后立刻做。

5. **注册页的输入框连 placeholder 都没有**，只能用 label 文案定位，
   见 `pages._field_input()`。

6. **路由守卫的跳转目标**：
   - 未登录访问受保护页 → `/login`
   - 已登录但角色不符 → `/`（**是首页，不是 403 页面**）
   - 已登录访问登录/注册页 → `/`

7. **列表页和详情页按钮文案不同**，别搞混：
   - 订单列表页：`支付` / `取消` / `详情`
   - 订单详情页：`立即支付` / `取消订单` / `去学习`

8. **不使用隐式等待**（`implicitly_wait`）。隐式等待会让 `WebDriverWait`
   的每次轮询都先等满隐式超时，反而放大等待时间。本工程统一用显式等待。

---

## 五、用例设计上的两个取舍

### 1. 只有需要「已登录」的用例才走 localStorage 注入

`login_by_token()` 把 token 直接写进 `localStorage` 跳过登录页，
比每次点一遍登录快得多。但**专门验证登录功能的用例仍然走真实登录页交互**。

### 2. 购买闭环用例每次注册全新学生

`fresh_student` fixture 用毫秒时间戳注册一个没买过课的学生。
如果复用 `student01`，第二次执行就会因为「已购买」而失败，用例无法重复运行。

副作用是会往 `user` 表里留下若干 `ui<时间戳>` 账号，重置数据库即可清掉。

---

## 六、常见问题

**Q：`SessionNotCreatedException: This version of ChromeDriver only supports Chrome version 146`**

PATH 里残留了旧版 chromedriver，而 Chrome 已经升级。
代码已做自愈处理（捕获异常后临时移除 PATH 中的 driver 目录重试），
也可以手动把 `D:\测试\chromedriver\chromedriver.exe` 换成与 Chrome 大版本一致的那份。

**Q：一堆用例卡在等待元素超时**

先确认前端 `npm run dev` 真的跑起来了、端口是 5173。
`conftest.py` 的 `_services_ready` 会在开跑前检查前后端，服务没起会直接提示并退出，
而不是给出一堆看不懂的超时。

**Q：Toast 断言偶发失败**

Toast 只存在 2.6 秒。如果某个操作后隔了别的耗时步骤才去断言 toast，
就会抓不到。断言 toast 必须紧跟在触发动作之后。

**Q：后台审核用例被 skip**

`UI_TC_089` / `UI_TC_090` 需要库里存在**待审核的申请**才会执行，没有就跳过。
造数据路径：讲师端提交课程/职位上架申请，管理员端才有得审。
