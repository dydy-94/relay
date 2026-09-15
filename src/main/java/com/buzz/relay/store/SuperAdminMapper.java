package com.buzz.relay.store;

import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * MyBatis Mapper — relay_super_admins 表（Dashboard 超管账号）CRUD.
 */
public interface SuperAdminMapper {

    /** 插入账号（username 已存在时唯一键冲突，调用方需处理）. */
    int insert(@Param("row") SuperAdminRow row);

    /** 按用户名查找. */
    SuperAdminRow findByUsername(@Param("username") String username);

    /** 查询全部账号. */
    List<SuperAdminRow> findAll();

    /** 删除账号. */
    int deleteByUsername(@Param("username") String username);

    /** 更新最近登录时间. */
    int updateLastLogin(@Param("username") String username, @Param("lastLoginAtMs") long lastLoginAtMs);

    /** 更新密码哈希. */
    int updatePassword(@Param("username") String username, @Param("passwordHash") String passwordHash);

    /** 账号总数. */
    long countAll();
}
