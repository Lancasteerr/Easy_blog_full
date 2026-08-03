package com.febrie.demo_bk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.febrie.demo_bk.pojo.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogDAO extends BaseMapper<OperationLog> {
}
