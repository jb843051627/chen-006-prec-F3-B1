package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fc.v2.mapper.auto.TPrecAuditBillMapper;
import com.fc.v2.mapper.auto.TPrecAuditSignMapper;
import com.fc.v2.mapper.auto.TPrecAuditSignerMapper;
import com.fc.v2.mapper.auto.TPrecSeqMapper;
import com.fc.v2.model.auto.TPrecAuditBill;
import com.fc.v2.model.auto.TPrecAuditSign;
import com.fc.v2.model.auto.TPrecAuditSigner;
import com.fc.v2.service.ITSysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 三道核签状态机单测：不连库，mapper 用内存账顶替。
 * 验的是规矩：死序、末道两人点名齐签、积数幂等、道否折住、重报勾旧、出证锁定。
 */
class TPrecAuditBillServiceImplTest {

    private TPrecAuditBillMapper billMapper;
    private TPrecAuditSignMapper signMapper;
    private TPrecAuditSignerMapper signerMapper;
    private TPrecSeqMapper seqMapper;
    private ITSysUserService userService;

    private final Map<Long, TPrecAuditBill> bills = new HashMap<>();
    private final List<TPrecAuditSign> ledger = new ArrayList<>();
    private long idSeq = 1000;
    private long noSeq = 0;
    private String login = "admin";

    /** 可切换画押人的被测服务 */
    private final TPrecAuditBillServiceImpl svc = new TPrecAuditBillServiceImpl() {
        @Override
        protected String currentLoginName() {
            return login;
        }
    };

    @BeforeEach
    void setUp() {
        bills.clear();
        ledger.clear();

        billMapper = mock(TPrecAuditBillMapper.class);
        signMapper = mock(TPrecAuditSignMapper.class);
        signerMapper = mock(TPrecAuditSignerMapper.class);
        seqMapper = mock(TPrecSeqMapper.class);
        userService = mock(ITSysUserService.class);
        ReflectionTestUtils.setField(svc, "billMapper", billMapper);
        ReflectionTestUtils.setField(svc, "signMapper", signMapper);
        ReflectionTestUtils.setField(svc, "signerMapper", signerMapper);
        ReflectionTestUtils.setField(svc, "seqMapper", seqMapper);
        ReflectionTestUtils.setField(svc, "sysUserService", userService);

        when(billMapper.selectByIdForUpdate(anyLong())).thenAnswer((InvocationOnMock inv) ->
                bills.get(inv.getArgument(0)));
        when(billMapper.insert(any(TPrecAuditBill.class))).thenAnswer(inv -> {
            TPrecAuditBill b = inv.getArgument(0);
            b.setId(++idSeq);
            bills.put(b.getId(), b);
            return 1;
        });
        when(billMapper.updateById(any(TPrecAuditBill.class))).thenReturn(1);

        when(signMapper.insert(any(TPrecAuditSign.class))).thenAnswer(inv -> {
            TPrecAuditSign s = inv.getArgument(0);
            s.setId(++idSeq);
            ledger.add(s);
            return 1;
        });
        when(signMapper.countSigns(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> (int) ledger.stream().filter(s ->
                        s.getDelFlag() == 0 && s.getValid() == 1
                                && eq(inv.getArgument(0), s.getBillId())
                                && eq(inv.getArgument(1), s.getRoundNo())
                                && eq(inv.getArgument(2), s.getNodeNo())
                                && eq(inv.getArgument(3), s.getSlotNo())
                                && eq(inv.getArgument(4), s.getSigner())
                                && ne(inv.getArgument(5), s.getSlotNo())
                                && eq(inv.getArgument(6), s.getAction())
                ).count());
        when(signMapper.invalidateRound(anyLong(), anyInt())).thenAnswer(inv -> {
            long billId = inv.getArgument(0);
            int round = inv.getArgument(1);
            int n = 0;
            for (TPrecAuditSign s : ledger) {
                if (s.getBillId().equals(billId) && s.getRoundNo() == round && s.getValid() == 1) {
                    s.setValid(0);
                    n++;
                }
            }
            return n;
        });

        when(signerMapper.findSeat(anyInt(), isNull(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> roster(inv.getArgument(2), null));
        when(signerMapper.findSeat(anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> roster(inv.getArgument(2), inv.getArgument(1)));

        when(seqMapper.currentSeq()).thenAnswer(inv -> ++noSeq);
        when(seqMapper.takeSeq(any())).thenReturn(1);
    }

    private TPrecAuditSigner roster(String signer, Integer slot) {
        List<TPrecAuditSigner> all = Arrays.asList(
                seat(1, "用途合规核签", "admin", "管理员"),
                seat(2, "量数核签", "fuce", "付册"));
        return all.stream()
                .filter(s -> s.getSigner().equals(signer) && (slot == null || s.getSlotNo() == slot))
                .findFirst().orElse(null);
    }

    private TPrecAuditSigner seat(int slot, String name, String signer, String nick) {
        TPrecAuditSigner s = new TPrecAuditSigner();
        s.setNodeNo(2);
        s.setSlotNo(slot);
        s.setSlotName(name);
        s.setSigner(signer);
        s.setSignerName(nick);
        s.setStatus(1);
        s.setDelFlag(0);
        return s;
    }

    private static boolean eq(Object want, Object got) {
        return want == null || want.equals(got);
    }

    private static boolean ne(Object want, Object got) {
        return want == null || !want.equals(got);
    }

    private TPrecAuditBill newBill() {
        TPrecAuditBill f = new TPrecAuditBill();
        f.setChemName("盐酸");
        f.setQty(new BigDecimal("5.00"));
        f.setUnit("吨");
        f.setPurpose("金属酸洗除锈");
        f.setStoreSiteNo("KF00");
        return svc.submit(f);
    }

    private long validSigns(Long billId, int round) {
        return ledger.stream()
                .filter(s -> s.getBillId().equals(billId) && s.getRoundNo() == round
                        && s.getValid() == 1 && s.getAction() == 1).count();
    }

    // ---- 规矩一：三道死序，末道两人点名齐签，缺一不可、不得顶替 ----

    @Test
    void happyPath_threeGatesAndNamedPair() {
        TPrecAuditBill b = newBill();
        final Long bid = b.getId();
        assertEquals(0, b.getNodeNo());

        // 派出所预审
        login = "police01";
        b = svc.sign(bid, 0, null, "预审通过");
        assertEquals(1, b.getNodeNo());

        // 大队复核
        login = "brigade01";
        b = svc.sign(bid, 1, null, null);
        assertEquals(2, b.getNodeNo());

        // 末道：用途席先押，单还停在末道（缺量数席一名不行）
        login = "admin";
        b = svc.sign(bid, 2, 1, "用途合规");
        assertEquals(2, b.getNodeNo());
        assertEquals(1, b.getSignCount());

        // 量数席点的是 fuce，admin 不能顶替量数席
        login = "admin";
        assertThrows(IllegalStateException.class, () -> svc.sign(bid, 2, 2, null));

        // 外人也顶不了任一席
        login = "stranger";
        assertThrows(IllegalStateException.class, () -> svc.sign(bid, 2, 2, null));

        // 量数席本人落押，两道迈过
        login = "fuce";
        b = svc.sign(b.getId(), 2, 2, "数量未超");
        assertEquals(3, b.getNodeNo());

        // 账面积数 == 流水账现数 == 4（三道四笔）
        assertEquals(4, b.getSignTotal());
        assertEquals(4, (int) validSigns(bid, 1));
    }

    // ---- 规矩二：同一人不能占两名 ----

    @Test
    void cityOnePersonCannotTakeBothSeats() {
        TPrecAuditBill b = newBill();
        login = "p";
        svc.sign(b.getId(), 0, null, null);
        login = "g";
        svc.sign(b.getId(), 1, null, null);
        login = "admin";
        svc.sign(b.getId(), 2, 1, null);
        // admin 想再压量数席
        assertThrows(IllegalStateException.class, () -> svc.sign(b.getId(), 2, 2, null));
    }

    // ---- 规矩三：并挤不翻倍 ----

    @Test
    void duplicateSignIsIdempotent_countNotDoubled() {
        TPrecAuditBill b = newBill();
        final Long bid = b.getId();
        login = "police01";
        b = svc.sign(bid, 0, null, "第一次");
        assertEquals(1, b.getNodeNo());
        // 前两道任一人即过门：迟到的重发必然面对下一道，按迟到请求拒掉（不能落到下一道）
        assertThrows(IllegalStateException.class, () -> svc.sign(bid, 0, null, "又一次"));
        assertEquals(1, (int) validSigns(bid, 1));

        // 末道一人落押不过门：同人同席双击/并发第二次幂等不添笔，积数不翻倍
        login = "brigade01";
        svc.sign(bid, 1, null, null);
        login = "admin";
        TPrecAuditBill s1 = svc.sign(bid, 2, 1, null);
        TPrecAuditBill s2 = svc.sign(bid, 2, 1, null);
        TPrecAuditBill cur = bills.get(bid);
        assertEquals(2, cur.getNodeNo());
        assertEquals(1, cur.getSignCount());
        assertEquals(3, cur.getSignTotal().intValue());
        // 幂等返回不改变单据
        assertEquals(s1.getUpdateTime(), s2.getUpdateTime());
    }

    // ---- 规矩四：迟到的画押（单已流走）不许落到下一道 ----

    @Test
    void staleExpectNodeRejected() {
        TPrecAuditBill b = newBill();
        login = "police01";
        svc.sign(b.getId(), 0, null, null);
        login = "police02";
        // 屏上还看着预审道，实际已到大队
        assertThrows(IllegalStateException.class, () -> svc.sign(b.getId(), 0, null, null));
    }

    // ---- 规矩五：道否当场折住，谁也补不进押；重报旧押勾销从头积 ----

    @Test
    void vetoFreezesAndResubmitWipesScores() {
        TPrecAuditBill b = newBill();
        final Long vid = b.getId();
        login = "police01";
        svc.sign(b.getId(), 0, null, null);

        // 大队道否
        login = "brigade01";
        b = svc.veto(b.getId(), 1, "量数存疑");
        assertEquals(2, b.getStatus().intValue());
        assertEquals(1, b.getVetoNode());

        // 折住后任谁补押都拒（哪怕是末道点名人）
        login = "admin";
        assertThrows(IllegalStateException.class, () -> svc.sign(vid, 1, null, null));

        // 打回重报
        b = svc.returnForResubmit(b.getId(), "补正用途说明");
        assertEquals(2, b.getRoundNo().intValue());
        assertEquals(0, b.getNodeNo().intValue());
        assertEquals(0, b.getSignTotal().intValue());
        assertEquals(0, b.getSignCount().intValue());
        assertEquals(0, b.getStatus().intValue());

        // 旧轮 2 笔（1 押 + 1 否）全部勾销，不跟新数续在一处
        assertTrue(ledger.stream().filter(s -> s.getRoundNo() == 1).allMatch(s -> s.getValid() == 0));
        assertEquals(0, (int) validSigns(b.getId(), 2));

        // 新一轮从预审重新起头积
        login = "police01";
        b = svc.sign(b.getId(), 0, null, "重报预审");
        assertEquals(1, b.getSignTotal().intValue());
    }

    // ---- 规矩六：出证即锁，要改只许另起一单 ----

    @Test
    void issuedBillLocked() {
        TPrecAuditBill b = newBill();
        login = "p";
        svc.sign(b.getId(), 0, null, null);
        login = "g";
        svc.sign(b.getId(), 1, null, null);
        login = "admin";
        svc.sign(b.getId(), 2, 1, null);
        login = "fuce";
        svc.sign(b.getId(), 2, 2, null);

        TPrecAuditBill issued = svc.issue(b.getId(), null, new Date(System.currentTimeMillis() + 86400000L));
        assertEquals(1, issued.getStatus().intValue());
        assertTrue(issued.getPermitNo().startsWith("YPG-XK-"));

        login = "anyone";
        assertThrows(IllegalStateException.class, () -> svc.sign(b.getId(), 3, null, null));
        assertThrows(IllegalStateException.class, () -> svc.issue(b.getId(), null, new Date(System.currentTimeMillis() + 99999999L)));
    }

    // ---- 规矩七：三道没齐不许出证 ----

    @Test
    void cannotIssueBeforeAllCleared() {
        TPrecAuditBill b = newBill();
        login = "p";
        svc.sign(b.getId(), 0, null, null);
        login = "g";
        svc.sign(b.getId(), 1, null, null);
        login = "admin";
        svc.sign(b.getId(), 2, 1, null); // 只差量数席
        assertThrows(IllegalStateException.class,
                () -> svc.issue(b.getId(), null, new Date(System.currentTimeMillis() + 86400000L)));
    }
}
