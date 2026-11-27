import requests
from config import BASE_URL , ACCOUNTS
import pytest
import time


# 登录
def _login(base_url, username, password):
    r = requests.post(f"{base_url}/api/user/login",json ={"username":username , "password":password},timeout=10)
    body = r.json()
    return body["data"]["token"] if body.get("code") == 200 else None

# 注册
def _register(base_url, acc):
    requests.post(f"{base_url}/api/user/register",json={"username": acc["username"], "password": acc["password"],"phone": acc["phone"], "email": acc["email"], "role": acc["role"]},timeout=15)

# 带token的请求
# 由于存在权限认证 不同用户与普通游客权限有区别 因此需要保存登录的状态

def _post(base_url,token , path , json = None  , params = None , files = None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    return requests.post(f"{base_url}{path}", params=params,  headers=headers, files=files, json = json ,timeout=20).json()

def _get(base_url, token , path , params = None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    return requests.get(f"{base_url}{path}", params=params, headers=headers, timeout=20).json()


@pytest.fixture(scope="session")
def base_url():
    return BASE_URL


@pytest.fixture(scope="session")
def tokens(base_url):
    """登录三种角色并缓存 token；学生/讲师不存在则先自动注册。"""
    result = {}
    for role, acc in ACCOUNTS.items():
        token = _login(base_url, acc["username"], acc["password"])
        if token is None and role != "admin":
            _register(base_url, acc)
            token = _login(base_url, acc["username"], acc["password"])
        result[role] = token
    return result

@pytest.fixture(scope="session")
def resume_id(base_url, tokens):
    files = {"file": ("resume.pdf", b"%PDF-1.4 fixture", "application/pdf")}
    r = requests.post(f"{base_url}/api/resume/upload",headers={"Authorization": f"Bearer {tokens['student']}"},files=files, timeout=20)
    body = r.json()
    assert body.get("code") == 200, f"简历预置失败：{body}"
    return body["data"]["resumeId"]
