package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TPrecAuditBill;
import com.fc.v2.model.auto.TPrecAuditSigner;
import com.fc.v2.model.custom.AuditTraceVo;

import java.util.Date;
import java.util.List;

/**
 * 购买许可逐级核签单 Service接口（approval-chain 形状：三道死序、末道两人点名齐签）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITPrecAuditBillService {

    /** 厂里递单：生成局里统一格式报批单号，落在派出所预审道 */
    TPrecAuditBill submit(TPrecAuditBill form);

    /** 台账查询 */
    List<TPrecAuditBill> selectList(QueryWrapper<TPrecAuditBill> queryWrapper);

    /** 受理纸：单据 + 末道往回捋的底细（账面核对一并给出） */
    AuditTraceVo trace(Long id);

    /**
     * 画押。expectNode 为屏上当前道（防迟到重复请求误落下一道）；
     * slotNo 仅末道点名时传（1用途合规 2量数），前两道不传。
     * 画押人取登录会话，不由前端填；规则不符抛 IllegalStateException。
     */
    TPrecAuditBill sign(Long id, Integer expectNode, Integer slotNo, String opinion);

    /** 道否：本道把话说死，单据当场折住，谁也补不进押。expectNode 为屏上当前道 */
    TPrecAuditBill veto(Long id, Integer expectNode, String reason);

    /** 打回厂里重报：前轮落押一并勾销，轮次+1，回预审道重新起头积 */
    TPrecAuditBill returnForResubmit(Long id, String reason);

    /** 厂里在预审落押前改单（品种/数量/用途/库房）；已落押或已出证不许改 */
    TPrecAuditBill editBeforeTrial(Long id, String chemName, java.math.BigDecimal qty, String unit,
                                   String purpose, String storeSiteNo);

    /** 出证：三道齐后锁品种/量数/凭证期限，发许可证号 */
    TPrecAuditBill issue(Long id, String permitNo, Date permitDue);

    /** 末道点名册（在册） */
    List<TPrecAuditSigner> roster();
}
