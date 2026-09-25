package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TPrecAuditSigner;
import org.apache.ibatis.annotations.Param;

/**
 * 市局核签点名册数据层
 *
 * @author fuce
 * @date 2026-09-24
 */
public interface TPrecAuditSignerMapper extends BaseMapper<TPrecAuditSigner> {

    /** 查点名席位：slotNo 为 null 时按人反查 */
    TPrecAuditSigner findSeat(@Param("nodeNo") int nodeNo,
                              @Param("slotNo") Integer slotNo,
                              @Param("signer") String signer);
}
