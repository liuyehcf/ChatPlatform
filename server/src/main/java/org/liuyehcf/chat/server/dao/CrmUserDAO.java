package org.liuyehcf.chat.server.dao;

import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;
import org.liuyehcf.chat.server.entity.CrmUser;
import org.springframework.stereotype.Repository;

/**
 * Created by Liuye on 2017/6/5.
 */
@Repository("crmUserDAO")
public interface CrmUserDAO {
    @Select({
            "SELECT " +
                    "id, " +
                    "user_name, " +
                    "user_pwd, " +
                    "create_time, " +
                    "update_time " +
                    "FROM crm_user " +
                    "where user_name = #{userName, jdbcType=INTEGER} "
    })
    @Results({
            @Result(column = "id", property = "id", jdbcType = JdbcType.INTEGER, id = true),
            @Result(column = "user_name", property = "userName", jdbcType = JdbcType.VARCHAR),
            @Result(column = "user_pwd", property = "userPwd", jdbcType = JdbcType.VARCHAR),
            @Result(column = "create_time", property = "createTime", jdbcType = JdbcType.TIMESTAMP),
            @Result(column = "update_time", property = "updateTime", jdbcType = JdbcType.TIMESTAMP),
    })
    CrmUser selectCrmUserById(@Param("userName") String userName);

    @Insert({
            "INSERT INTO crm_user " +
                    "( " +
                    "user_name, " +
                    "user_pwd " +
                    ")VALUES(" +
                    "#{crmUser.userName, jdbcType=VARCHAR}, " +
                    "#{crmUser.userPwd, jdbcType=VARCHAR} " +
                    ") "
    })
    Integer insertCrmUser(@Param("crmUser") CrmUser crmUser);
}
