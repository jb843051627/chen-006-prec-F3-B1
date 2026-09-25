package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fc.v2.mapper.auto.TPrecAuditBillMapper;
import com.fc.v2.mapper.auto.TPrecAuditSignMapper;
import com.fc.v2.mapper.auto.TPrecAuditSignerMapper;
import com.fc.v2.mapper.auto.TPrecSeqMapper;
import com.fc.v2.model.auto.TPrecAuditBill;
import com.fc.v2.model.auto.TPrecAuditSign;
import com.fc.v2.model.auto.TPrecAuditSigner;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.model.custom.AuditTraceVo;
import com.fc.v2.service.ITPrecAuditBillService;
import com.fc.v2.service.ITSysUserService;
import com.fc.v2.shiro.util.ShiroUtils;

/**
 * 购买许可逐级核签单 Service业务层处理
 * （approval-chain 形状：三道死序，末道两人点名齐签；积数以画押流水账为唯一账）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TPrecAuditBillServiceImpl implements ITPrecAuditBillService {

    /** 道次：0派出所预审 1大队复核 2市局核签 3三道齐(待出证) */
    static final int NODE_POLICE = 0;
    static final int NODE_BRIGADE = 1;
    static final int NODE_CITY = 2;
    static final int NODE_DONE = 3;

    /** 同层核签方式 0任一人 1名单点齐 */
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;

    /** 报批情形 0在核 1已出证 2已道否 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_ISSUED = 1;
    private static final int STATUS_VETO = 2;

    /** 流水动作 1落押 2道否 */
    static final int ACTION_SIGN = 1;
    private static final int ACTION_VETO = 2;

    /** 点名席位 0不点名单人 1用途合规 2量数 */
    private static final int SLOT_NONE = 0;
    static final int SLOT_USE = 1;
    static final int SLOT_QTY = 2;

    private static final String[] NODE_NAMES = {"派出所预审", "大队复核", "市局核签"};
    /** 各道 (方式, 应到人数)：前两道任一人，末道名单点齐两人 */
    private static final int[][] NODE_RULES = {{MODE_OR, 1}, {MODE_OR, 1}, {MODE_AND, 2}};

    @Resource
    private TPrecAuditBillMapper billMapper;
    @Resource
    private TPrecAuditSignMapper signMapper;
    @Resource
    private TPrecAuditSignerMapper signerMapper;
    @Resource
    private TPrecSeqMapper seqMapper;
    @Resource
    private ITSysUserService sysUserService;

    /** 当前登录账号；单测可覆盖换人画押，生产取 Shiro 会话，不许由前端填 */
    protected String currentLoginName() {
        return ShiroUtils.getLoginName();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TPrecAuditBill submit(TPrecAuditBill form) {
        if (form == null) {
            throw new IllegalStateException("单据内容不能为空");
        }
        if (StrUtil.isBlank(form.getChemName())) {
            throw new IllegalStateException("品种必须填写");
        }
        if (form.getQty() == null || form.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("数量必须大于0");
        }
        if (StrUtil.isBlank(form.getPurpose())) {
            throw new IllegalStateException("用途必须填写");
        }
        if (StrUtil.isBlank(form.getStoreSiteNo())) {
            throw new IllegalStateException("存放库房必须填写");
        }

        TPrecAuditBill bill = new TPrecAuditBill();
        bill.setBillNo(nextBillNo());
        bill.setChemName(form.getChemName().trim());
        bill.setQty(form.getQty());
        bill.setUnit(StrUtil.isBlank(form.getUnit()) ? "吨" : form.getUnit().trim());
        bill.setPurpose(form.getPurpose().trim());
        bill.setStoreSiteNo(form.getStoreSiteNo().trim());
        bill.setRoundNo(1);
        bill.setNodeNo(NODE_POLICE);
        bill.setSignMode(NODE_RULES[NODE_POLICE][0]);
        bill.setNeedCount(NODE_RULES[NODE_POLICE][1]);
        bill.setSignCount(0);
        bill.setSignTotal(0);
        bill.setStatus(STATUS_RUNNING);
        bill.setDelFlag(0);
        if (StrUtil.isNotBlank(form.getRemark())) {
            bill.setRemark(form.getRemark().trim());
        }
        billMapper.insert(bill);
        return bill;
    }

    @Override
    public List<TPrecAuditBill> selectList(QueryWrapper<TPrecAuditBill> queryWrapper) {
        return billMapper.selectList(queryWrapper);
    }

    @Override
    public AuditTraceVo trace(Long id) {
        TPrecAuditBill bill = billMapper.selectById(id);
        if (bill == null || (bill.getDelFlag() != null && bill.getDelFlag() == 1)) {
            throw new IllegalStateException("单据不存在");
        }
        int round = nz(bill.getRoundNo());

        AuditTraceVo vo = new AuditTraceVo();
        vo.setBill(bill);

        // 三道底细：从末道起一格一格往回捋
        List<AuditTraceVo.NodeView> nodes = new ArrayList<>();
        for (int node = NODE_CITY; node >= NODE_POLICE; node--) {
            AuditTraceVo.NodeView nv = new AuditTraceVo.NodeView();
            nv.setNodeNo(node);
            nv.setNodeName(NODE_NAMES[node]);
            nv.setSignMode(NODE_RULES[node][0]);
            nv.setNeedCount(NODE_RULES[node][1]);

            List<TPrecAuditSign> signs = signMapper.selectList(new QueryWrapper<TPrecAuditSign>()
                    .eq("bill_id", id)
                    .eq("round_no", round)
                    .eq("node_no", node)
                    .eq("valid", 1)
                    .eq("del_flag", 0)
                    .orderByAsc("sign_time", "id"));
            nv.setSigns(signs);
            int cnt = 0;
            for (TPrecAuditSign s : signs) {
                if (s.getAction() != null && s.getAction() == ACTION_SIGN) {
                    cnt++;
                }
            }
            nv.setSignCount(cnt);
            nodes.add(nv);
        }
        vo.setNodes(nodes);

        // 历轮被勾销的旧押只存档，不进积数
        List<TPrecAuditSign> archived = signMapper.selectList(new QueryWrapper<TPrecAuditSign>()
                .eq("bill_id", id)
                .lt("round_no", round)
                .orderByDesc("round_no")
                .orderByAsc("node_no", "sign_time", "id"));
        vo.setArchived(archived);

        // 账面核对：数一律从账上现数，不由单上填
        int counted = signMapper.countSigns(id, round, null, null, null, null, ACTION_SIGN);
        int stored = nz(bill.getSignTotal());
        vo.setCountedSignTotal(counted);
        vo.setStoredSignTotal(stored);
        vo.setMatched(counted == stored);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TPrecAuditBill sign(Long id, Integer expectNode, Integer slotNo, String opinion) {
        TPrecAuditBill bill = lockBill(id);
        int node = nz(bill.getNodeNo());
        if (expectNode == null || expectNode != node) {
            throw new IllegalStateException("单据已流转到下一道，请刷新受理纸后再办");
        }
        int round = nz(bill.getRoundNo());
        String login = currentLoginName();

        int slot = SLOT_NONE;
        String slotName = null;
        if (node == NODE_CITY) {
            // 末道点名两人：对席对人，缺一名不行，互相顶替也不行
            if (slotNo == null || (slotNo != SLOT_USE && slotNo != SLOT_QTY)) {
                throw new IllegalStateException("末道须按点名席位画押（1用途合规 2量数）");
            }
            TPrecAuditSigner seat = signerMapper.findSeat(NODE_CITY, slotNo, login);
            if (seat == null) {
                throw new IllegalStateException(slotNo == SLOT_USE
                        ? "您不是本道点名的用途合规核签人，不能顶替画押"
                        : "您不是本道点名的量数核签人，不能顶替画押");
            }
            slot = slotNo;
            slotName = seat.getSlotName();
            // 同一人已在另一席落过押，不许把两道并一笔
            if (signMapper.countSigns(id, round, node, null, login, slot, ACTION_SIGN) > 0) {
                throw new IllegalStateException("您已在另一席位画押，不能一人占两名");
            }
        } else {
            if (slotNo != null && slotNo != SLOT_NONE) {
                throw new IllegalStateException("本道不点名席位，按单人画押即可");
            }
        }

        // 同人同席重复挤压（双击/并发）：账上已有就不添第二笔，积数不翻倍
        if (signMapper.countSigns(id, round, node, slot, login, null, ACTION_SIGN) > 0) {
            return bill;
        }

        TPrecAuditSign row = newRow(bill, round, node, slot, slotName, login, ACTION_SIGN);
        row.setOpinion(StrUtil.isBlank(opinion) ? null : opinion.trim());
        row.setSignerName(resolveSignerName(node, slot, login));
        try {
            signMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 唯一键兜底：极端并发下同人同席已落过，幂等返回，不翻倍
            return bill;
        }

        // 积数从账上重算后回写单面积数，屏上数与账上数同出一处
        recalcCounts(bill, round, node);

        int need = NODE_RULES[node][1];
        if (nz(bill.getSignCount()) >= need) {
            // 本道迈过：死序推进，下一道此前连单的影都见不着
            int next = node + 1;
            bill.setNodeNo(next);
            bill.setSignCount(0);
            if (next <= NODE_CITY) {
                bill.setSignMode(NODE_RULES[next][0]);
                bill.setNeedCount(NODE_RULES[next][1]);
            } else {
                bill.setSignMode(MODE_OR);
                bill.setNeedCount(0);
            }
        }
        billMapper.updateById(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TPrecAuditBill veto(Long id, Integer expectNode, String reason) {
        TPrecAuditBill bill = lockBill(id);
        int node = nz(bill.getNodeNo());
        if (expectNode == null || expectNode != node) {
            throw new IllegalStateException("单据已流转，请刷新受理纸后再办");
        }
        int round = nz(bill.getRoundNo());
        String login = currentLoginName();

        int slot = SLOT_NONE;
        String slotName = null;
        if (node == NODE_CITY) {
            TPrecAuditSigner seat = signerMapper.findSeat(NODE_CITY, null, login);
            if (seat == null) {
                throw new IllegalStateException("您不是末道点名核签人，不能道否");
            }
            slot = seat.getSlotNo();
            slotName = seat.getSlotName();
        }

        TPrecAuditSign row = newRow(bill, round, node, slot, slotName, login, ACTION_VETO);
        row.setOpinion(StrUtil.isBlank(reason) ? null : reason.trim());
        row.setSignerName(resolveSignerName(node, slot, login));
        signMapper.insert(row);

        // 话说死：当场折住，往后任谁也补不进一枚押
        bill.setStatus(STATUS_VETO);
        bill.setVetoNode(node);
        bill.setVetoReason(StrUtil.isBlank(reason) ? null : reason.trim());
        billMapper.updateById(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TPrecAuditBill returnForResubmit(Long id, String reason) {
        TPrecAuditBill bill = lockBillForResubmit(id);
        int round = nz(bill.getRoundNo());

        // 前一轮落的押（含道否行）一并勾销，别跟旧数续在一处
        signMapper.invalidateRound(id, round);

        int nextRound = round + 1;
        bill.setRoundNo(nextRound);
        bill.setNodeNo(NODE_POLICE);
        bill.setSignMode(NODE_RULES[NODE_POLICE][0]);
        bill.setNeedCount(NODE_RULES[NODE_POLICE][1]);
        bill.setSignCount(0);
        bill.setSignTotal(0);
        bill.setStatus(STATUS_RUNNING);
        bill.setVetoNode(null);
        bill.setVetoReason(null);
        if (StrUtil.isNotBlank(reason)) {
            bill.setRemark(StrUtil.isBlank(bill.getRemark())
                    ? reason.trim()
                    : bill.getRemark() + "；重报：" + reason.trim());
        }
        billMapper.updateById(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TPrecAuditBill editBeforeTrial(Long id, String chemName, BigDecimal qty, String unit,
                                          String purpose, String storeSiteNo) {
        TPrecAuditBill bill = lockBill(id);
        if (nz(bill.getNodeNo()) != NODE_POLICE) {
            throw new IllegalStateException("已离开预审道，不能再改单");
        }
        if (signMapper.countSigns(id, nz(bill.getRoundNo()), NODE_POLICE, null, null, null, ACTION_SIGN) > 0) {
            throw new IllegalStateException("预审已落押，不能再改单");
        }
        if (StrUtil.isNotBlank(chemName)) {
            bill.setChemName(chemName.trim());
        }
        if (qty != null) {
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("数量必须大于0");
            }
            bill.setQty(qty);
        }
        if (StrUtil.isNotBlank(unit)) {
            bill.setUnit(unit.trim());
        }
        if (StrUtil.isNotBlank(purpose)) {
            bill.setPurpose(purpose.trim());
        }
        if (StrUtil.isNotBlank(storeSiteNo)) {
            bill.setStoreSiteNo(storeSiteNo.trim());
        }
        billMapper.updateById(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TPrecAuditBill issue(Long id, String permitNo, Date permitDue) {
        TPrecAuditBill bill = lockBillForIssue(id);
        if (permitDue == null || !permitDue.after(new Date())) {
            throw new IllegalStateException("凭证期限必须晚于当前时刻");
        }
        String no = StrUtil.isBlank(permitNo) ? nextPermitNo() : permitNo.trim();

        // 证一出手，品种、量数、凭证期限通通锁住（status=已出证，后续任何动作一律拒）
        bill.setPermitNo(no);
        bill.setPermitDue(permitDue);
        bill.setIssueTime(new Date());
        bill.setStatus(STATUS_ISSUED);
        billMapper.updateById(bill);
        return bill;
    }

    @Override
    public List<TPrecAuditSigner> roster() {
        return signerMapper.selectList(new QueryWrapper<TPrecAuditSigner>()
                .eq("node_no", NODE_CITY)
                .eq("status", 1)
                .eq("del_flag", 0)
                .orderByAsc("slot_no"));
    }

    // ---- 内家功夫 ----

    /** 取单并校验可办状态，同时借行锁把同一单的并发动作串行化 */
    private TPrecAuditBill lockBill(Long id) {
        if (id == null) {
            throw new IllegalStateException("缺少单据主键");
        }
        TPrecAuditBill bill = billMapper.selectByIdForUpdate(id);
        if (bill == null) {
            throw new IllegalStateException("单据不存在");
        }
        int status = nz(bill.getStatus());
        if (status == STATUS_VETO) {
            throw new IllegalStateException("单据已道否折住，不能再补画押");
        }
        if (status == STATUS_ISSUED) {
            throw new IllegalStateException("凭证已出，单据已锁定，要改只许另起一单");
        }
        if (nz(bill.getNodeNo()) > NODE_CITY) {
            throw new IllegalStateException("三道已齐，只待出证，不能再画押");
        }
        return bill;
    }

    /** 打回重报只接已道否的单（状态机上与画押分开口，故另取一次行锁） */
    private TPrecAuditBill lockBillForResubmit(Long id) {
        if (id == null) {
            throw new IllegalStateException("缺少单据主键");
        }
        TPrecAuditBill bill = billMapper.selectByIdForUpdate(id);
        if (bill == null) {
            throw new IllegalStateException("单据不存在");
        }
        if (nz(bill.getStatus()) != STATUS_VETO) {
            throw new IllegalStateException("只有已道否的单才能打回重报");
        }
        return bill;
    }

    /** 出证只接三道齐在核的单 */
    private TPrecAuditBill lockBillForIssue(Long id) {
        if (id == null) {
            throw new IllegalStateException("缺少单据主键");
        }
        TPrecAuditBill bill = billMapper.selectByIdForUpdate(id);
        if (bill == null) {
            throw new IllegalStateException("单据不存在");
        }
        if (nz(bill.getStatus()) == STATUS_VETO) {
            throw new IllegalStateException("已道否的单不能出证");
        }
        if (nz(bill.getStatus()) == STATUS_ISSUED) {
            throw new IllegalStateException("凭证已出，不能重复出证");
        }
        if (nz(bill.getNodeNo()) != NODE_DONE) {
            throw new IllegalStateException("三道未齐，不能出证");
        }
        return bill;
    }

    private TPrecAuditSign newRow(TPrecAuditBill bill, int round, int node, int slot,
                                  String slotName, String login, int action) {
        TPrecAuditSign row = new TPrecAuditSign();
        row.setBillId(bill.getId());
        row.setBillNo(bill.getBillNo());
        row.setRoundNo(round);
        row.setNodeNo(node);
        row.setSlotNo(slot);
        row.setSlotName(slotName);
        row.setSigner(login);
        row.setAction(action);
        row.setSignTime(new Date());
        row.setValid(1);
        row.setDelFlag(0);
        return row;
    }

    private String resolveSignerName(int node, int slot, String login) {
        if (node == NODE_CITY) {
            TPrecAuditSigner seat = signerMapper.findSeat(NODE_CITY, slot == SLOT_NONE ? null : slot, login);
            if (seat != null && StrUtil.isNotBlank(seat.getSignerName())) {
                return seat.getSignerName();
            }
        }
        TSysUser user = sysUserService.getOne(new QueryWrapper<TSysUser>().eq("username", login));
        if (user != null && StrUtil.isNotBlank(user.getNickname())) {
            return user.getNickname();
        }
        return login;
    }

    /** 积数只从流水账现数：本道已到 + 本轮总计，一并回写单面积数 */
    private void recalcCounts(TPrecAuditBill bill, int round, int node) {
        int nodeCount = signMapper.countSigns(bill.getId(), round, node, null, null, null, ACTION_SIGN);
        int totalCount = signMapper.countSigns(bill.getId(), round, null, null, null, null, ACTION_SIGN);
        bill.setSignCount(nodeCount);
        bill.setSignTotal(totalCount);
    }

    private String nextBillNo() {
        return nextNo("AUDIT_BILL:", "YPG-BP-");
    }

    private String nextPermitNo() {
        return nextNo("AUDIT_PERMIT:", "YPG-XK-");
    }

    /**
     * 发号：INSERT ... ON DUPLICATE KEY UPDATE seq_val=LAST_INSERT_ID(seq_val+1)，
     * 紧接同一连接 SELECT LAST_INSERT_ID()。须在事务内调用，号段按日重置。
     */
    private String nextNo(String keyPrefix, String noPrefix) {
        String day = DateUtil.format(new Date(), "yyyyMMdd");
        seqMapper.takeSeq(keyPrefix + day);
        long seq = seqMapper.currentSeq();
        return noPrefix + day + "-" + String.format("%06d", seq);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
