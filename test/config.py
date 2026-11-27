# 由于是在本地环境运行直接使用8080端口进行本地测试

import os

# 基础地址（后端）
BASE_URL = "http://localhost:8080"

# ==================== UI 自动化（Selenium）====================
# 前端 dev server 地址（见前端 vite.config.js：server.port = 5173，/api 反向代理到 8080）
FRONT_URL = "http://localhost:5173"

# 浏览器：chrome / edge / firefox
UI_BROWSER = os.getenv("UI_BROWSER", "chrome")

# 是否无头运行。默认显示浏览器窗口，便于观察执行过程与定位失败原因；
# 设环境变量 UI_HEADLESS=1 可切换为无头模式（跑得更快）。
UI_HEADLESS = os.getenv("UI_HEADLESS", "0") == "1"

# 显式等待默认超时（秒）。前端首屏数据全靠 onMounted 异步请求，必须留足时间。
UI_TIMEOUT = int(os.getenv("UI_TIMEOUT", "10"))

# 三种登录账号
ACCOUNTS = {
    "student":{"username":"student01","password":"123456","phone":"13800000001","email":"student01@test.com" , "role":0},
    "teacher":{"username":"teacher01","password":"123456","phone":"13800000002","email":"teacher01@test.com" , "role":1},
    "admin":{"username":"admin","password":"123456","phone":"","email":"" , "role":2},
}

# 测试用例 Excel文件名
EXCEL_FILE = "test_case.xlsx"
