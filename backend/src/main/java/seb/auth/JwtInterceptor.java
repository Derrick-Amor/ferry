package seb.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import seb.common.BusinessException;
import seb.common.ResultCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.annotation.Resource;


@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        if (isPublic(request, path)) {
            // 公开接口：若携带有效 token 也解析并写入上下文，
            // 便于课程详情等接口判断“是否已购买”等登录态信息，但不强制登录。
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                if (jwtUtil.verify(token)) {
                    UserContext.set(jwtUtil.getUserId(token), jwtUtil.getRole(token));
                }
            }
            return true;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        String token = auth.substring(7);
        if (!jwtUtil.verify(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录已过期");
        }

        Long userId = jwtUtil.getUserId(token);
        Integer role = jwtUtil.getRole(token);
        UserContext.set(userId, role);

        if (isAdminPath(path) && role != 2) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权限");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean isPublic(HttpServletRequest request, String path) {
        if ("/api/user/register".equals(path) || "/api/user/login".equals(path)) return true;
        if ("/api/user/forgotPassword".equals(path)) return true;
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            if ("/api/course/list".equals(path) || path.matches("/api/course/\\d+")) return true;
            if ("/api/job/list".equals(path) || path.matches("/api/job/\\d+")) return true;
        }
        return false;
    }

    private boolean isAdminPath(String path) {
        return path.startsWith("/api/admin/")
                || "/api/apply/pending".equals(path)
                || "/api/apply/audit".equals(path)
                || "/api/apply/audited".equals(path);
    }
}