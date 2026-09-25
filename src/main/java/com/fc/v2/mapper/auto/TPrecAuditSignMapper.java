package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TPrecAuditSign;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 购买许可核签画押流水账数据层
 *
 * @author fuce
 * @date 2026-09-24
 */
public interface TPrecAuditSignMapper extends BaseMapper<TPrecAuditSign> {

    /**
     * 有效画押计数（积数唯一来源）。条件传 null 不参与过滤。
     */
    int countSigns(@Param("billId") Long billId,
                   @Param("roundNo") Integer roundNo,
                   @Param("nodeNo") Integer nodeNo,
                   @Param("slotNo") Integer slotNo,
                   @Param("signer") String signer,
                   @Param("excludeSlot") Integer excludeSlot,
                   @Param("action") Integer action);

    /**
     * 勾销一轮全部旧押（打回重报用）。
     */
    @Update("UPDATE t_prec_audit_sign SET valid = 0, update_time = NOW() "
            + "WHERE bill_id = #{billId} AND round_no = #{roundNo} AND valid = 1 AND del_flag = 0")
    int invalidateRound(@Param("billId") Long billId, @Param("roundNo") Integer roundNo);
}
