package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid=#{openid}")
    User getByOpenid(String openid);

    /**
     * 插入数据
     * @param user
     */
    void insert(User user);

    @Select("SELECT  * FROM user WHERE id=#{ID}")
    User getById(Long userId);

    /**
     * 根据动态条件统计数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}
