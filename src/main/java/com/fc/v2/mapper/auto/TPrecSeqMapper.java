package com.fc.v2.mapper.auto;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 易毒政务发号序列数据层
 *
 * @author fuce
 * @date 2026-09-24
 */
public interface TPrecSeqMapper {

    /**
     * 占一个号。必须与 {@link #currentSeq()} 在同一事务(同一连接)内连续调用：
     * 借助连接级 LAST_INSERT_ID 取号，并发各连接各取各的，不重号不跳号感知。
     */
    @Update("INSERT INTO t_prec_seq(seq_key, seq_val) VALUES(#{seqKey}, LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE seq_val = LAST_INSERT_ID(seq_val + 1)")
    int takeSeq(@Param("seqKey") String seqKey);

    /** 取本连接刚占到的号 */
    @Select("SELECT LAST_INSERT_ID()")
    long currentSeq();
}
