package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fc.v2.mapper.auto.TPrecCtrlLineMapper;
import com.fc.v2.model.auto.TPrecCtrlLine;
import com.fc.v2.model.custom.PrecGrade;
import com.fc.v2.model.custom.PrecGradeQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 浓度分档管控线单测：不连库，mapper 用内存账顶替（全量返回，品种/删除标记/日子全由服务层滤）。
 * 验的是规矩：一线一档、回算只认报备当日、交棒日双闭、顺位/线号决胜、压线从严、
 * 缺阈不设上限线照认、越界原封退回、那日无规可依、旧线不判新单、两个入口同一结果。
 */
class TPrecCtrlLineServiceImplTest {

    private TPrecCtrlLineMapper mapper;
    private final TPrecCtrlLineServiceImpl svc = new TPrecCtrlLineServiceImpl();

    /** 内存线档：与 doc/schema/prec.sql 种子同口径，另加几条专试边界的线 */
    private final Map<Long, TPrecCtrlLine> lines = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        lines.clear();
        // 浓硫酸：旧线管到 2025-10-01（当日仍作数），新线当日起算；顺位新20 > 旧10
        put(920000000000000101L, "H2SO4-2024", "浓硫酸", 10, 70, null, "2024-01-01", "2025-10-01", 10, 1);
        put(920000000000000102L, "H2SO4-2025", "浓硫酸", 10, 50, null, "2025-10-01", null,         20, 0);
        // 稀盐酸：老文件翻录，只有两道界（th3 缺=二类不设上限），线照认
        put(920000000000000201L, "HCL-OLD",  "稀盐酸", 10, 20, null, "2023-01-01", "2025-01-01",  5, 1);
        put(920000000000000202L, "HCL-2025", "稀盐酸",  5, 15, null, "2025-01-01", null,         15, 0);
        // 丙酮
        put(920000000000000301L, "ACETONE-2024", "丙酮", 10, 60, null, "2024-01-01", "2026-01-01",  8, 1);
        put(920000000000000302L, "ACETONE-2026", "丙酮",  5, 50, null, "2026-01-01", null,         18, 0);
        // 已删除线：日子品种都够得着也不许捞
        TPrecCtrlLine deleted = line(920000000000000999L, "H2SO4-DELETED", "浓硫酸", 10, 30, null,
                "2024-01-01", null, 99, 0);
        deleted.setDelFlag(1);
        lines.put(deleted.getId(), deleted);
        // 压线试品：三类上界就是 10.00
        put(920000000000000401L, "PRESS-2025", "压线试品", 5, 10, null, "2025-01-01", null, 1, 0);
        // 同顺位交棒对：两条线同日都够得着、顺位相同，取线号大的（其三类上界更严=10）
        put(920000000000000501L, "TIE-SMALL", "同顺位试品", 5, 20, null, "2025-05-01", null, 7, 0);
        put(920000000000000502L, "TIE-BIG",   "同顺位试品", 5, 10, null, "2025-05-01", null, 7, 0);
        // 断档品：2024 年有条线，2025 年全年无规——不许抓旁边日子的线凑
        put(920000000000000601L, "GAP-2024", "断档试品", 10, 50, null, "2024-01-01", "2024-12-31", 1, 0);
        // 免管界缺失的老线：缺的那道视作不设上限，线照样认，全量免管
        put(920000000000000701L, "NOTH1-OLD", "缺界试品", null, 30, null, "2024-01-01", null, 1, 0);
        // 三道界齐全的线：专试旧 int 口径第 4 档（压/过二类上界）
        put(920000000000000801L, "FULL-3", "三道界试品", 10, 30, 80, "2025-01-01", null, 1, 0);

        mapper = mock(TPrecCtrlLineMapper.class);
        ReflectionTestUtils.setField(svc, "precCtrlLineMapper", mapper);
        // 内存账顶替：不替服务层做任何过滤，品种/删除/日子全交服务层口径
        when(mapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(lines.values()));
        when(mapper.selectById(any())).thenAnswer(inv -> lines.get(inv.getArgument(0)));
    }

    // ---- 规矩一：三档正常落位 + 比最松那道还淡算免管 ----

    @Test
    void threeTiersFallInPlace() {
        // 2025-09-01 作数的是旧版浓硫酸线：免管<10，三类<70
        assertEquals(PrecGrade.EXEMPT, classify("浓硫酸", "5.00", "2025-09-01"));
        assertEquals(PrecGrade.EXEMPT, classify("浓硫酸", "9.99", "2025-09-01"));
        assertEquals(PrecGrade.CLASS3, classify("浓硫酸", "10.00", "2025-09-01"));
        assertEquals(PrecGrade.CLASS3, classify("浓硫酸", "69.99", "2025-09-01"));
        assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "70.00", "2025-09-01"));
        assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "98.00", "2025-09-01"));
    }

    // ---- 规矩二：压线从严，正压上界按更严一档收 ----

    @Test
    void pressingBoundGoesToStricterTier() {
        // 10.00 正压三类上界 → 二类；差一丝 9.99 → 三类
        assertEquals(PrecGrade.CLASS2, classify("压线试品", "10.00", "2025-06-01"));
        assertEquals(PrecGrade.CLASS3, classify("压线试品", "9.99",  "2025-06-01"));
        // 正压免管上界 5.00 → 三类
        assertEquals(PrecGrade.CLASS3, classify("压线试品", "5.00",  "2025-06-01"));
        assertEquals(PrecGrade.EXEMPT, classify("压线试品", "4.99", "2025-06-01"));
        // 旧版浓硫酸正压三类上界 70.00 → 二类
        assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "70.00", "2025-09-01"));
        // 正压免管上界 10.00 → 三类
        assertEquals(PrecGrade.CLASS3, classify("浓硫酸", "10.00", "2025-09-01"));
    }

    // ---- 规矩三：负的、一百二十往上肉眼填错的，原封退回；缺参数同样不收 ----

    @Test
    void outOfRangeAndMissingArgsRejected() {
        assertThrows(IllegalArgumentException.class, () -> classify("浓硫酸", "-0.01", "2025-09-01"));
        assertThrows(IllegalArgumentException.class, () -> classify("浓硫酸", "120.00", "2025-09-01"));
        assertThrows(IllegalArgumentException.class, () -> classify("浓硫酸", "120.01", "2025-09-01"));
        assertThrows(IllegalArgumentException.class, () -> classify("浓硫酸", "500.00", "2025-09-01"));
        assertThrows(IllegalArgumentException.class, () -> classify("浓硫酸", null, "2025-09-01"));
        assertThrows(IllegalArgumentException.class, () -> classify(null, "10.00", "2025-09-01"));
        assertThrows(IllegalArgumentException.class, () -> classify("  ", "10.00", "2025-09-01"));
        assertThrows(IllegalArgumentException.class,
                () -> svc.classify("浓硫酸", new BigDecimal("10.00"), null));
        assertThrows(IllegalArgumentException.class, () -> svc.classify((PrecGradeQuery) null));
        // 119.99 还在可收范围内，正常判二类
        assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "119.99", "2025-09-01"));
        // 0.00 不是填错，比最松那道淡，免管
        assertEquals(PrecGrade.EXEMPT, classify("浓硫酸", "0.00", "2025-09-01"));
    }

    // ---- 规矩四：那日无规可依，不抓旁边日子的线凑 ----

    @Test
    void noRuleOnBlankDaysNeverGrabsNeighbor() {
        // 浓硫酸最早 2024-01-01 起算
        assertEquals(PrecGrade.NO_RULE, classify("浓硫酸", "60.00", "2023-12-31"));
        // 断档试品 2024 有线、2025 无线：年中年末都不许借 2024 的
        assertEquals(PrecGrade.CLASS3, classify("断档试品", "40.00", "2024-06-01"));
        assertEquals(PrecGrade.NO_RULE, classify("断档试品", "40.00", "2025-01-01"));
        assertEquals(PrecGrade.NO_RULE, classify("断档试品", "40.00", "2025-06-01"));
        // 听都没听过的品种
        assertEquals(PrecGrade.NO_RULE, classify("发烟硝酸", "60.00", "2025-09-01"));
    }

    // ---- 规矩五：交棒日双闭，当日两条都够得着，先比顺位号 ----

    @Test
    void handoffDayBothReachable_priorityWins() {
        // 2025-10-01：旧线(三类<70)与新线(三类<50)同时作数，新线顺位 20 > 10
        // 60.00 在旧线下够三类，在新线下已入二类——必须认新线
        assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "60.00", "2025-10-01"));
        // 交棒次日旧线已不交棒（旧线管到当日为止），只认新线
        assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "60.00", "2025-10-02"));
        // 稀盐酸 2025-01-01 同日交棒：新线顺位 15 > 5，18.00 老线三类、新线二类
        assertEquals(PrecGrade.CLASS2, classify("稀盐酸", "18.00", "2025-01-01"));
    }

    // ---- 规矩六：顺位相同再比线号，取线号大的 ----

    @Test
    void samePriority_tieBreakByLineNo() {
        // 两条线顺位都是 7：线号 502（三类上界10）压过 501（三类上界20）
        // 12.00 在小号线手下是三类，在大号线手下是二类
        assertEquals(PrecGrade.CLASS2, classify("同顺位试品", "12.00", "2025-05-01"));
        // 前一日（2025-04-30）两条都没起算
        assertEquals(PrecGrade.NO_RULE, classify("同顺位试品", "12.00", "2025-04-30"));
    }

    // ---- 规矩七：回算只认报备当日，早就退位的旧线今年新报不许搬 ----

    @Test
    void oldAccountsUseOldLines_newReportsNeverReviveRetired() {
        // 去年秋天的货按旧线：60.00 明明白白三类；今天的线套上去就成二类（前年翻车的坑）
        assertEquals(PrecGrade.CLASS3, classify("浓硫酸", "60.00", "2025-09-30"));
        // 今年新报（今天 2026-09-24）：旧线早退位，60.00 按新线是二类，不许搬旧线
        assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "60.00", "2026-09-24"));
        // status=1（已退位）只是台账情形，在其历史窗口内回算照样作数
        assertEquals(PrecGrade.CLASS3, classify("丙酮", "40.00", "2025-09-30"));
        // 丙酮 2026-01-01 交棒后，40.00 新线仍三类，但 55.00 已是二类
        assertEquals(PrecGrade.CLASS3, classify("丙酮", "40.00", "2026-01-02"));
        assertEquals(PrecGrade.CLASS2, classify("丙酮", "55.00", "2026-01-02"));
    }

    // ---- 规矩八：老文件只录两道界，缺的视作不设上限，线照样认 ----

    @Test
    void missingThresholdMeansNoCap_lineStillValid() {
        // 稀盐酸老线 th3 缺：过了三类上界一路收二类（系统最严一档），不报“线废了”
        assertEquals(PrecGrade.CLASS3, classify("稀盐酸", "19.99", "2024-06-01"));
        assertEquals(PrecGrade.CLASS2, classify("稀盐酸", "20.00", "2024-06-01"));
        assertEquals(PrecGrade.CLASS2, classify("稀盐酸", "37.00", "2024-06-01"));
        // th1 缺：免管不设上限，全量免管，线同样认
        assertEquals(PrecGrade.EXEMPT, classify("缺界试品", "29.99", "2025-06-01"));
        assertEquals(PrecGrade.EXEMPT, classify("缺界试品", "80.00", "2025-06-01"));
    }

    // ---- 规矩九：品种各管各，删除线永不捞 ----

    @Test
    void chemIsolationAndDeletedLineIgnored() {
        // 2023-06-01 只有稀盐酸老线在：浓硫酸不能借稀盐酸的线
        assertEquals(PrecGrade.NO_RULE, classify("浓硫酸", "60.00", "2023-06-01"));
        // 同一天稀盐酸自家的线照样作数（60.00 越过三类上界，二类）
        assertEquals(PrecGrade.CLASS2, classify("稀盐酸", "60.00", "2023-06-01"));
        // 删除线顺位再高（99）、窗口再宽，也不许作数；60.00 仍按正式新线判二类
        assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "60.00", "2026-09-24"));
    }

    @Test
    void chemBeforeFirstLineHasNoRule() {
        assertEquals(PrecGrade.NO_RULE, classify("稀盐酸", "60.00", "2022-12-31"));
    }

    // ---- 规矩十：三参重载与载体重载是同一把尺子；对外只回一个词 ----

    @Test
    void bothEntryPointsGiveSameWord() {
        PrecGrade a = svc.classify("浓硫酸", new BigDecimal("60.00"), at("2025-09-30"));
        PrecGrade b = svc.classify(new PrecGradeQuery("浓硫酸", new BigDecimal("60.00"), at("2025-09-30")));
        assertEquals(a, b);
        assertEquals(PrecGrade.CLASS3, a);
        // 系统只回一个词
        assertEquals("三类", a.word());
        assertEquals("免管", PrecGrade.EXEMPT.word());
        assertEquals("二类", PrecGrade.CLASS2.word());
        assertEquals("那日无规可依", PrecGrade.NO_RULE.word());
        assertEquals("三类", a.toString());
        // 受理与自查两边各调一遍，捞到同一条线、同一个词
        PrecGrade intake = svc.classify(new PrecGradeQuery("浓硫酸", new BigDecimal("60.00"), at("2025-10-01")));
        PrecGrade audit = svc.classify("浓硫酸", new BigDecimal("60.00"), at("2025-10-01"));
        assertEquals(intake, audit);
        assertEquals(PrecGrade.CLASS2, intake);
    }

    // ---- 旧签名原样保留，且同样守报备日（旁路不许绕开日子）----

    @Test
    void legacySignaturesStillHonorTheDay() {
        // 旧版线 code 在历史窗口内：60 < 70 → 2（三类，int 旧口径压线同样从严）
        assertEquals(2, svc.evaluate("H2SO4-2024", new BigDecimal("60.00"), at("2025-09-30")));
        assertEquals(3, svc.evaluate("H2SO4-2024", new BigDecimal("70.00"), at("2025-09-30")));
        // 同一 code 拿到今天：线早退位 → 0，不许捞
        assertEquals(0, svc.evaluate("H2SO4-2024", new BigDecimal("60.00"), at("2026-09-24")));
        // 越界/缺参仍是 0
        assertEquals(0, svc.evaluate("H2SO4-2024", new BigDecimal("120.00"), at("2025-09-30")));
        assertEquals(0, svc.evaluate("H2SO4-2024", null, at("2025-09-30")));
        // 三道界齐全的线：79.99 收二类(3)，80.00 正压二类上界从严越界(4)
        assertEquals(3, svc.evaluate("FULL-3", new BigDecimal("79.99"), at("2025-06-01")));
        assertEquals(4, svc.evaluate("FULL-3", new BigDecimal("80.00"), at("2025-06-01")));
        // evaluateTop 不挑品种，交棒当日顺位最高的是新版浓硫酸(20)：60.00 越过其三类上界50、
        // 该线 th3 缺=二类不设上限，收在二类(3)而非越界(4)
        assertEquals(3, svc.evaluateTop(new BigDecimal("60.00"), at("2025-10-01")));
        // 2023-06-01 只有稀盐酸老线(pri5)在：15.00 三类
        assertEquals(2, svc.evaluateTop(new BigDecimal("15.00"), at("2023-06-01")));
        // 列表/可用性/计数都守日子
        assertEquals(0, svc.countAvailable(at("2022-01-01")));
        // 交棒当日：浓硫酸新旧2 + 稀盐酸新1 + 压线1 + 同顺位2 + 缺界1 + 丙酮旧1 + 三道界1 = 9
        assertEquals(9, svc.countAvailable(at("2025-10-01")));
        // 列表头名：顺位最大者（浓硫酸新版 20）
        assertEquals("H2SO4-2025", svc.listAvailable(at("2025-10-01")).get(0).getRuleCode());
        // usable：旧线在交棒当日仍可用，次日不可用；删除线永不可用
        assertEquals(true, svc.usable(920000000000000101L, at("2025-10-01")));
        assertEquals(false, svc.usable(920000000000000101L, at("2025-10-02")));
        assertEquals(false, svc.usable(920000000000000999L, at("2025-10-01")));
        assertEquals(false, svc.usable(null, at("2025-10-01")));
        assertEquals(0, svc.countAvailable(null));
    }

    // ---- helpers ----

    private PrecGrade classify(String chem, String conc, String day) {
        return svc.classify(chem, conc == null ? null : new BigDecimal(conc), at(day));
    }

    private static Date at(String ymd) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(ymd);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    private void put(long id, String code, String chem, Integer th1, Integer th2, Integer th3,
                     String start, String end, int priority, int status) {
        lines.put(id, line(id, code, chem, th1, th2, th3, start, end, priority, status));
    }

    private static TPrecCtrlLine line(long id, String code, String chem,
                                      Integer th1, Integer th2, Integer th3,
                                      String start, String end, int priority, int status) {
        TPrecCtrlLine r = new TPrecCtrlLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName(code + "-名目");
        r.setChemName(chem);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1 + ".00"));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2 + ".00"));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3 + ".00"));
        r.setEffStart(at(start));
        r.setEffEnd(end == null ? null : at(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }
}
