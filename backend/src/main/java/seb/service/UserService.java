package seb.service;

import seb.dto.ForgotPasswordDTO;
import seb.dto.LoginDTO;
import seb.dto.RegisterDTO;
import seb.dto.ResetPasswordDTO;
import seb.dto.UpdateUserInfoDTO;
import seb.vo.UserVO;

import java.util.Map;

/**
 * 用户业务接口：注册、登录、资料与密码管理。
 *
 * <p>方法声明只包含业务能力。持久化 CRUD（getById / save / updateById 等）
 * 由实现类继承 MyBatis-Plus 的 ServiceImpl 获得，属于实现细节，不对外暴露。
 */
public interface UserService {

    /**
     * 注册新用户，密码以 BCrypt 加密后落库。
     *
     * @param dto 注册参数
     */
    void register(RegisterDTO dto);

    /**
     * 账号密码登录，成功后返回 token 与脱敏后的用户信息。
     *
     * @param dto 登录参数
     * @return 包含 {@code token} 与 {@code user} 两个键的 Map
     */
    Map<String, Object> login(LoginDTO dto);

    /**
     * 查询指定用户的对外信息（不含密码）。
     *
     * @param userId 用户ID
     * @return 脱敏后的用户视图对象
     */
    UserVO getInfo(Long userId);

    /**
     * 更新当前用户的昵称、真实姓名、头像。
     *
     * @param userId 用户ID
     * @param dto    待更新字段
     */
    void updateInfo(Long userId, UpdateUserInfoDTO dto);

    /**
     * 校验原密码后修改密码。
     *
     * @param userId 用户ID
     * @param dto    原密码与新密码
     */
    void resetPassword(Long userId, ResetPasswordDTO dto);

    /**
     * 通过手机号重置密码（忘记密码场景）。
     *
     * @param dto 手机号与新密码
     */
    void forgotPassword(ForgotPasswordDTO dto);
}
