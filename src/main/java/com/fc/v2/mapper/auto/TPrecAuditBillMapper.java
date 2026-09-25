package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TPrecAuditBill;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 购买许可逐级核签单数据层
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface TPrecAuditBillMapper extends BaseMapper<TPrecAuditBill> {

    /**
     * 行锁取单。同一单的画押/道否/打回借此串行：
     * 两位同志同时翻同一张单时后到者排队，积数从账上重算，不翻倍也不吞枚。
     */
    @Select("SELECT * FROM t_prec_audit_bill WHERE id = #{id} AND del_flag = 0 FOR UPDATE")
    TPrecAuditBill selectByIdForUpdate(@Param("id") Long id);
}
