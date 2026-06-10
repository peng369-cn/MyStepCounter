package com.pengchangwei.stepserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pengchangwei.stepserver.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
