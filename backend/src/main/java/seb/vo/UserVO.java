package seb.vo;

import cn.hutool.core.bean.BeanUtil;
import lombok.Data;
import seb.entity.User;

import java.time.LocalDateTime;

/**
 * 用户信息对外视图对象（View Object）。
 *
 * <p>与 {@link User} 字段一一对应，但**不包含 password**，用于所有返回用户信息的接口。
 * 之所以不复用实体，是因为实体一旦新增字段（如 updatedBy、salt 等）会被动泄漏到接口响应里；
 * VO 是一份白名单，只有显式声明的字段才会被序列化出去。
 *
 * @see User
 */
@Data
public class UserVO {

    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 角色：0学生 1讲师/企业 2管理员 */
    private Integer role;

    /** 状态：0禁用 1正常 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由实体转换为视图对象，自动忽略 password 等 VO 未声明的字段。
     *
     * @param user 用户实体，允许为 null
     * @return 视图对象；入参为 null 时返回 null
     */
    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        // BeanUtil 只拷贝同名且有 setter 的属性，password 在 VO 中不存在，因此不会被带出来
        BeanUtil.copyProperties(user, vo);
        return vo;
    }
}
