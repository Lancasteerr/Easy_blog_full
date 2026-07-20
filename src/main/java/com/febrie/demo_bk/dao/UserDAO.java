package com.febrie.demo_bk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.febrie.demo_bk.pojo.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserDAO extends BaseMapper<User> {
    User selectByUserName(String userName);

    User getByUserNameAndPassword(String userName,String password);

}
