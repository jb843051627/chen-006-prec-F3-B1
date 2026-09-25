package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fc.v2.mapper.auto.TPrecCtrlLineMapper;
import com.fc.v2.model.auto.TPrecCtrlLine;
import com.fc.v2.model.custom.PrecGrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 现场六桩毛病的稳定复现：每桩都按「库里正序摆 / 倒序摆」各判一遍，
 * 同一个报备日、同一批货，两回必须是同一个档——结果不许随库里摆放次序飘。
 *
 * <p>毛病与用例对照：
 * <ol>
 *   <li>换版复算：取哪一版只认报备当日（旧单按旧版，新单按新版，不拿今天的线重判老账）；</li>
 *   <li>并列先后定死：顺位相同比线号，线号大者胜，与摆放次序无关；</li>
 *   <li>交棒当日两版都作数：先比顺位号，当日判档唯一；</li>
 *   <li>骑分界线（压线从严）：正压上界收更严一档，不许记低一级；</li>
 *   <li>多版够得着时取优：顺位号大的胜（列表/看板的头名同样认顺位）；</li>
 *   <li>过期作废的线拦死：交棒次日旧线不再作数，复算/计数不许凭空冒出档；
 *       交棒当日旧线仍作数（双闭）。</li>
 * </ol>
 */
class GradingBugReproTest {

    private TPrecCtrlLineMapper mapper;
    private final TPrecCtrlLineServiceImpl svc = new TPrecCtrlLineServiceImpl();
    private final Map<Long, TPrecCtrlLine> lines = new LinkedHashMap<>();

    /** 两种库内摆放：正序（按 id 升）、倒序（按 id 降） */
    private enum Order { ASC, DESC }

    @BeforeEach
    void setUp() {
        lines.clear();

        // 浓硫酸：旧版 pri10 管到 2025-10-01（当日仍作数），新版 pri20 当日起算
        put(101L, "H2SO4-2024", "浓硫酸", 10, 70, null, "2024-01-01", "2025-10-01", 10, 1);
        put(102L, "H2SO4-2025", "浓硫酸", 10, 50, null, "2025-10-01", null,         20, 0);

        // 浓硝酸：交棒当日故意让旧版顺位更高(30 > 5)——双闭下两版都作数，必须认旧版；
        // 半开实现把旧版在交棒当日摘掉，只剩新版，档就飘了
        put(201L, "HNO3-OLD", "浓硝酸", 10, 70, null, "2024-01-01", "2025-03-01", 30, 1);
        put(202L, "HNO3-NEW", "浓硝酸", 10, 50, null, "2025-03-01", null,          5, 0);

        // 同顺位并列对：顺位都是 7，线号 302 的三类上界更严(10)，必须压过 301(20)
        put(301L, "TIE-SMALL", "同顺位品", 5, 20, null, "2025-05-01", null, 7, 0);
        put(302L, "TIE-BIG",   "同顺位品", 5, 10, null, "2025-05-01", null, 7, 0);

        // 压线品：免管上界 5，三类上界 10
        put(401L, "PRESS", "压线品", 5, 10, null, "2025-01-01", null, 1, 0);

        // 三道界齐全：专试旧 int 口径压线（th3=80，正压应从严越界）
        put(501L, "FULL-3", "三道界品", 10, 30, 80, "2025-01-01", null, 1, 0);

        // 过期线：2022 年就交了棒，且无新版接——交棒当日仍作数，次日起 NO_RULE
        put(601L, "EXPIRED-OLD", "断档品", 10, 30, null, "2020-01-01", "2022-01-01", 1, 1);

        // 顺位列表对：pri10 的 code 字典序反而更大，专试列表头名是否真按顺位（而非 code）
        put(701L, "ZZZZ-LOW-PRI", "列表品", 10, 60, null, "2025-01-01", null, 10, 0);
        put(702L, "AAAA-HIGH-PRI", "列表品", 10, 40, null, "2025-01-01", null, 20, 0);

        mapper = mock(TPrecCtrlLineMapper.class);
        ReflectionTestUtils.setField(svc, "precCtrlLineMapper", mapper);
        bind(Order.ASC);
    }

    /** 按指定摆放次序把内存账接给 mapper */
    private void bind(Order order) {
        List<TPrecCtrlLine> all = new ArrayList<>(lines.values());
        if (order == Order.DESC) {
            Collections.reverse(all);
        }
        when(mapper.selectList(any())).thenReturn(all);
        when(mapper.selectById(any())).thenAnswer(inv -> lines.get(inv.getArgument(0)));
    }

    // ---- 毛病一：回算只认报备当日那版，不许拿新版重判老账 ----

    @Test
    void repro1_oldBillUsesOldVersion_newBillUsesNew() {
        for (Order order : Order.values()) {
            bind(order);
            // 2025-09-30 的旧账：60.00 在旧版(三类<70)手下明明白白三类
            assertEquals(PrecGrade.CLASS3, classify("浓硫酸", "60.00", "2025-09-30"),
                    "旧账[" + order + "]被新版重判了");
            // 2026 年的新账：旧版早退位，60.00 按新版(三类<50)是二类，不许搬旧版
            assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "60.00", "2026-09-24"),
                    "新账[" + order + "]搬了旧版");
        }
    }

    // ---- 毛病二：并列先后定死，同顺位比线号，与摆放次序无关 ----

    @Test
    void repro2_samePriority_tieBrokenByLineNo_neverByStorageOrder() {
        for (Order order : Order.values()) {
            bind(order);
            // 12.00 在小号线手下是三类，大号线手下是二类——必须两回都是二类
            assertEquals(PrecGrade.CLASS2, classify("同顺位品", "12.00", "2025-05-01"),
                    "同顺位[" + order + "]没按线号取大，随库里次序飘了");
        }
    }

    // ---- 毛病三：交棒当日两版都作数，先比顺位，判档唯一 ----

    @Test
    void repro3_handoffDayBothVersionsCount_priorityWins() {
        for (Order order : Order.values()) {
            bind(order);
            // 浓硝酸交棒日：旧版 pri30 胜过新版 pri5，60.00 按旧版是三类
            assertEquals(PrecGrade.CLASS3, classify("浓硝酸", "60.00", "2025-03-01"),
                    "交棒当日[" + order + "]没让两版都作数/没比顺位");
            // 交棒次日只剩新版：60.00 二类
            assertEquals(PrecGrade.CLASS2, classify("浓硝酸", "60.00", "2025-03-02"),
                    "交棒次日[" + order + "]旧版该退场了");
            // 交棒前一日只剩旧版：60.00 三类
            assertEquals(PrecGrade.CLASS3, classify("浓硝酸", "60.00", "2025-02-28"),
                    "交棒前[" + order + "]不该够得着新版");
            // 浓硫酸交棒日：新版 pri20 胜旧版 pri10，60.00 新版二类
            assertEquals(PrecGrade.CLASS2, classify("浓硫酸", "60.00", "2025-10-01"),
                    "浓硫酸交棒日[" + order + "]顺位取错");
        }
    }

    // ---- 毛病四：骑分界线压线从严，不许记低一级 ----

    @Test
    void repro4_exactlyOnBoundaryGoesStricter() {
        for (Order order : Order.values()) {
            bind(order);
            // classify 口径：5.00 正压免管上界→三类；10.00 正压三类上界→二类
            assertEquals(PrecGrade.CLASS3, classify("压线品", "5.00", "2025-06-01"),
                    "压免管界[" + order + "]记低了");
            assertEquals(PrecGrade.CLASS2, classify("压线品", "10.00", "2025-06-01"),
                    "压三类界[" + order + "]记低了");
            // 旧 int 口径同样压线从严：30.00→3，80.00 正压二类上界→4（不是 3）
            assertEquals(3, svc.evaluate("FULL-3", new BigDecimal("30.00"), at("2025-06-01")),
                    "旧口径压三类界[" + order + "]记低了");
            assertEquals(4, svc.evaluate("FULL-3", new BigDecimal("80.00"), at("2025-06-01")),
                    "旧口径压二类界[" + order + "]记低了");
        }
    }

    // ---- 毛病五：多版够得着取优，顺位栏不能白设（列表/看板同口径） ----

    @Test
    void repro5_priorityDrivesTopPick_notRuleCodeOrStorageOrder() {
        for (Order order : Order.values()) {
            bind(order);
            // 列表头名必须是顺位 20 的 AAAA-HIGH-PRI，不是 code 字典序大的 ZZZZ-LOW-PRI
            TPrecCtrlLine top = svc.listAvailable(at("2025-07-01")).get(0);
            assertEquals(702L, top.getId().longValue(), "列表头名[" + order + "]没认顺位");
            // 看板求值：60.00 在高顺位新版(三类<40)下已入二类(3)，不许按低顺位旧版松判
            assertEquals(3, svc.evaluateTop(new BigDecimal("60.00"), at("2025-07-01")),
                    "看板取优[" + order + "]顺位白设了");
            // 交棒日全场最高顺位是浓硝酸旧版 pri30：60.00 按其三类上界70 → 三类(2)
            assertEquals(2, svc.evaluateTop(new BigDecimal("60.00"), at("2025-03-01")),
                    "交棒日全场取优[" + order + "]错了");
        }
    }

    // ---- 毛病六：过期作废的线拦死，复算/计数不许凭空冒档；交棒当日双闭仍作数 ----

    @Test
    void repro6_retiredLinesBlockedButHandoffDayStillCounts() {
        for (Order order : Order.values()) {
            bind(order);
            // 交棒当日(2022-01-01)旧线双闭仍作数：20.00 三类
            assertEquals(PrecGrade.CLASS3, classify("断档品", "20.00", "2022-01-01"),
                    "交棒当日[" + order + "]旧线应仍作数(双闭)");
            assertTrue(svc.usable(601L, at("2022-01-01")), "交棒当日[" + order + "]usable 应为真");
            // 交棒次日旧线作废：判档 NO_RULE，不许凭空冒出一个档
            assertEquals(PrecGrade.NO_RULE, classify("断档品", "20.00", "2022-01-02"),
                    "过期线[" + order + "]没拦住，复算冒档了");
            assertFalse(svc.usable(601L, at("2022-01-02")), "过期线[" + order + "]usable 应为假");
            // 2023 年的看板/计数：过期线不许算进去（该品种当日无规）
            assertEquals(0, svc.evaluate("EXPIRED-OLD", new BigDecimal("20.00"), at("2023-01-01")),
                    "旧码复算[" + order + "]捞了过期线");
        }
        // 列表/计数按日子过滤：2019 年所有线都没起算 → 0；2023 年断档品线已过期，不进可用列表
        bind(Order.ASC);
        assertEquals(0, svc.countAvailable(at("2019-01-01")), "2019 年不该有任何可用线");
        bind(Order.DESC);
        assertEquals(0, svc.countAvailable(at("2019-01-01")), "2019 年不该有任何可用线(倒序)");
        for (Order order : Order.values()) {
            bind(order);
            // 2025-06-01 作数的：浓硫酸旧、浓硝酸新、同顺位2、压线、三道界、列表2 = 8；
            // 断档品(2022 年就交棒)必须被拦在外面
            int n = svc.countAvailable(at("2025-06-01"));
            assertEquals(8, n, "可用计数[" + order + "]把过期线算进去了");
            assertTrue(svc.listAvailable(at("2025-06-01")).stream()
                            .noneMatch(r -> "EXPIRED-OLD".equals(r.getRuleCode())),
                    "可用列表[" + order + "]混进了过期线");
        }
    }

    // ---- helpers ----

    private PrecGrade classify(String chem, String conc, String day) {
        return svc.classify(chem, new BigDecimal(conc), at(day));
    }

    private static Date at(String ymd) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(ymd);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    private void put(long id, String code, String chem, Integer th1, Integer th2, Integer th3,
                     String start, String end, Integer priority, int status) {
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
        lines.put(id, r);
    }
}
