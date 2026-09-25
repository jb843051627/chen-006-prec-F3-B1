package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fc.v2.mapper.auto.TPrecDueTaskMapper;
import com.fc.v2.model.auto.TPrecDueTask;
import com.fc.v2.model.custom.PrecDueRunResult;
import com.fc.v2.service.ITSysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 满期提醒周期执行单测：不连库，mapper 用内存账顶替，经手人在册与否用可替换名册顶替。
 * 验的是规矩：办公日白天窗口、挑条目与落痕同一把尺子、老数据同一套说法、
 * 当日不重复、隔轮再催、三催转上门、卡住不拖住整轮、本轮无事不算毛病。
 */
class TPrecDueTaskServiceImplTest {

    private TPrecDueTaskMapper dueMapper;
    private ITSysUserService userService;

    /** 内存台账 */
    private final Map<Long, TPrecDueTask> rows = new LinkedHashMap<>();
    /** 在册经手人名册（不在册=已调离） */
    private final Set<String> roster = new HashSet<>();

    /** 名册可替换的被测服务 */
    private final TPrecDueTaskServiceImpl svc = new TPrecDueTaskServiceImpl() {
        @Override
        protected boolean handlerAlive(String handler) {
            return roster.contains(handler);
        }
    };

    @BeforeEach
    void setUp() {
        rows.clear();
        roster.clear();
        roster.add("keeper");

        dueMapper = mock(TPrecDueTaskMapper.class);
        userService = mock(ITSysUserService.class);
        ReflectionTestUtils.setField(svc, "precDueTaskMapper", dueMapper);
        ReflectionTestUtils.setField(svc, "sysUserService", userService);

        // 内存账顶替：按 SQL 语义先滤 del_flag/status、按 id 排序，余下由服务层用墙钟日历日挑
        when(dueMapper.selectList(any())).thenAnswer(inv -> rows.values().stream()
                .filter(r -> (r.getDelFlag() == null || r.getDelFlag() == 0)
                        && r.getStatus() != null && r.getStatus() >= 0 && r.getStatus() <= 2)
                .sorted(Comparator.comparing(TPrecDueTask::getId))
                .collect(Collectors.toList()));
        when(dueMapper.updateById(any(TPrecDueTask.class))).thenReturn(1);
        when(dueMapper.selectById(any())).thenAnswer(inv -> rows.get(inv.getArgument(0)));
    }

    private static Date at(String ymdHms) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(ymdHms);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    private TPrecDueTask entry(long id, String itemNo, String dueAt, String amount,
                               String handler, int count, String lastRemind, int status) {
        TPrecDueTask r = new TPrecDueTask();
        r.setId(id);
        r.setItemNo(itemNo);
        r.setDueAt(dueAt == null ? null : at(dueAt + (dueAt.length() == 10 ? " 00:00:00" : "")));
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        r.setHandler(handler);
        r.setRemindCount(count);
        r.setLastRemindAt(lastRemind == null ? null : at(lastRemind + (lastRemind.length() == 10 ? " 00:00:00" : "")));
        r.setStatus(status);
        r.setDelFlag(0);
        rows.put(id, r);
        return r;
    }

    // ---- 规矩一：只在办公日的白天开（09:00-17:00），钟点没到/过了下班点一律不动 ----

    @Test
    void outsideWindow_nothingMoves() {
        TPrecDueTask r = entry(1, "Z001", "2026-09-23 14:00:00", "0", "keeper", 0, null, 0);

        // 钟点没到：上午九点前（周三）
        PrecDueRunResult morning = svc.runOnce(at("2026-09-23 08:59:59"), new PrecDueRunResult());
        assertEquals(0, morning.getReminded());
        assertTrue(morning.summary().contains("搁置"));
        assertEquals(0, (int) r.getStatus());
        assertNull(r.getLastRemindAt());

        // 过了下班点：17:00 整点起算已下班
        PrecDueRunResult evening = svc.runOnce(at("2026-09-23 17:00:00"), new PrecDueRunResult());
        assertEquals(0, evening.getReminded());
        assertEquals(0, (int) r.getStatus());
        assertNull(r.getLastRemindAt());

        // 周末不是办公日：周六、周日白天也不动
        assertEquals(0, svc.runOnce(at("2026-09-26 10:00:00"), new PrecDueRunResult()).getReminded());
        assertEquals(0, svc.runOnce(at("2026-09-27 10:00:00"), new PrecDueRunResult()).getReminded());
        assertEquals(0, (int) r.getStatus());
        assertNull(r.getLastRemindAt());
    }

    @Test
    void windowEdge_nineOpens_seventeenClosed() {
        // 09:00:00 整点开
        entry(1, "Z001", "2026-09-23", "0", "keeper", 0, null, 0);
        assertEquals(1, svc.runOnce(at("2026-09-23 09:00:00"), new PrecDueRunResult()).getReminded());

        // 16:59:59 还在窗口内
        entry(2, "Z002", "2026-09-23", "0", "keeper", 0, null, 0);
        assertEquals(1, svc.runOnce(at("2026-09-23 16:59:59"), new PrecDueRunResult()).getReminded());
    }

    // ---- 规矩二：到点判定与落痕认同一把尺子（哪天算哪天的账） ----

    @Test
    void dueBoundary_selectionAndTraceSameDay() {
        // 满期 09-25，提前 2 天 → 开口日 09-23，当日即该出声
        TPrecDueTask hit = entry(1, "Z001", "2026-09-25 14:00:00", "2", "keeper", 0, null, 0);
        // 满期 09-26，提前 2 天 → 开口日 09-24，09-23 还不到点
        TPrecDueTask notYet = entry(2, "Z002", "2026-09-26 09:00:00", "2", "keeper", 0, null, 0);

        PrecDueRunResult r = svc.runOnce(at("2026-09-23 10:00:00"), new PrecDueRunResult());
        assertEquals(1, r.getReminded());
        // 落痕的日子就是挑条目那个当日，不许一个按当日一个按前一日
        assertEquals("2026-09-23 10:00:00", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(hit.getLastRemindAt()));
        assertEquals(1, (int) hit.getStatus());
        assertEquals(0, (int) notYet.getStatus());
        assertNull(notYet.getLastRemindAt());
    }

    // ---- 规矩三：老数据只写日期，与新数据同一套说法（日历日尺子，钟点不挪开口日） ----

    @Test
    void legacyDateOnly_sameRuleAsNewData() {
        // 老数据：只写了日期，落库即当日 00:00:00
        TPrecDueTask legacy = entry(1, "L001", "2026-09-25 00:00:00", "2", "keeper", 0, null, 0);
        // 新数据：准到钟点
        TPrecDueTask fresh = entry(2, "N001", "2026-09-25 14:30:00", "2", "keeper", 0, null, 0);

        // 同一轮（开口日都是 09-23）一起被挑中，谁也不多算一天、不少算一天
        PrecDueRunResult r = svc.runOnce(at("2026-09-23 10:00:00"), new PrecDueRunResult());
        assertEquals(2, r.getReminded());
        assertEquals(1, (int) legacy.getStatus());
        assertEquals(1, (int) fresh.getStatus());
    }

    // ---- 规矩四：留痕、当日不重复、隔轮再催、三催转上门 ----

    @Test
    void traceWritten_andNoRepeatSameDay() {
        TPrecDueTask r = entry(1, "Z001", "2026-09-23", "0", "keeper", 0, null, 0);

        PrecDueRunResult first = svc.runOnce(at("2026-09-23 10:00:00"), new PrecDueRunResult());
        assertEquals(1, first.getReminded());
        assertEquals(1, (int) r.getRemindCount());
        assertEquals(1, (int) r.getStatus());
        assertEquals(at("2026-09-23 10:00:00"), r.getLastRemindAt());
        assertTrue(r.getRemark().contains("已出声"));

        // 同一天再扫：不许接二连三收到同样一番话
        PrecDueRunResult again = svc.runOnce(at("2026-09-23 15:00:00"), new PrecDueRunResult());
        assertEquals(0, again.getReminded());
        assertEquals("本轮无事", again.summary());
        assertEquals(1, (int) r.getRemindCount());
    }

    @Test
    void threeStrikes_thenEscalateToDutyOfficer() {
        TPrecDueTask r = entry(1, "Z001", "2026-09-21", "0", "keeper", 0, null, 0);

        // 第 1、2、3 遍：接了话没准信，隔一轮（隔日）再催
        assertEquals(1, svc.runOnce(at("2026-09-21 10:00:00"), new PrecDueRunResult()).getReminded());
        assertEquals(1, svc.runOnce(at("2026-09-22 10:00:00"), new PrecDueRunResult()).getReminded());
        assertEquals(1, svc.runOnce(at("2026-09-23 10:00:00"), new PrecDueRunResult()).getReminded());
        assertEquals(3, (int) r.getRemindCount());

        // 催满三遍仍没回音：转值班员上门，系统不再出声
        PrecDueRunResult fourth = svc.runOnce(at("2026-09-24 10:00:00"), new PrecDueRunResult());
        assertEquals(0, fourth.getReminded());
        assertEquals(1, fourth.getEscalated());
        assertEquals(3, (int) r.getStatus());
        assertEquals(3, (int) r.getRemindCount());
        assertTrue(r.getRemark().contains("转值班员上门"));

        // 转上门之后：系统不再对着它出声
        PrecDueRunResult fifth = svc.runOnce(at("2026-09-25 10:00:00"), new PrecDueRunResult());
        assertEquals(0, fifth.getReminded());
        assertEquals(0, fifth.getEscalated());
        assertEquals("本轮无事", fifth.summary());
    }

    // ---- 规矩五：卡住的单拎出来挂事由，别叫它把整轮拖住 ----

    @Test
    void stuckEntryDoesNotBlockRound() {
        TPrecDueTask stuck = entry(1, "S001", "2026-09-23", "0", null, 0, null, 0);
        TPrecDueTask gone = entry(2, "S002", "2026-09-23", "0", "ghost", 0, null, 0);
        TPrecDueTask fine = entry(3, "Z001", "2026-09-23", "0", "keeper", 0, null, 0);

        PrecDueRunResult r = svc.runOnce(at("2026-09-23 10:00:00"), new PrecDueRunResult());

        // 剩下的照扫不误：好条照出声
        assertEquals(1, r.getReminded());
        assertEquals(1, (int) fine.getStatus());

        // 卡住的两条各挂事由：经手那栏空着 / 接话的人调去了别处
        assertEquals(2, r.getStuck().size());
        assertEquals(2, (int) stuck.getStatus());
        assertTrue(stuck.getRemark().contains("经手人空缺"));
        assertEquals(2, (int) gone.getStatus());
        assertTrue(gone.getRemark().contains("已调离"));
        assertTrue(r.summary().contains("卡住2条"));
    }

    @Test
    void stuckEntrySelfHealsWhenHandlerBack() {
        // 之前卡住挂了事由，如今经手人补上了：下一轮自动归队出声
        TPrecDueTask r = entry(1, "S001", "2026-09-23", "0", "keeper", 0, null, 2);
        PrecDueRunResult run = svc.runOnce(at("2026-09-23 10:00:00"), new PrecDueRunResult());
        assertEquals(1, run.getReminded());
        assertEquals(0, run.getStuck().size());
        assertEquals(1, (int) r.getStatus());
        assertEquals(1, (int) r.getRemindCount());
    }

    @Test
    void singleFailureSkipped_roundGoesOn() {
        entry(1, "X001", "2026-09-23", "0", "keeper", 0, null, 0);
        TPrecDueTask fine = entry(2, "Z001", "2026-09-23", "0", "keeper", 0, null, 0);
        // id=1 落库时抛异常，模拟单条失败
        when(dueMapper.updateById(any(TPrecDueTask.class))).thenAnswer(inv -> {
            TPrecDueTask r = inv.getArgument(0);
            if (r.getId() == 1L) {
                throw new RuntimeException("落库失败");
            }
            return 1;
        });

        PrecDueRunResult r = svc.runOnce(at("2026-09-23 10:00:00"), new PrecDueRunResult());
        assertEquals(1, r.getReminded());
        assertEquals(1, r.getStuck().size());
        assertTrue(r.getStuck().get(0).getReason().contains("处理异常"));
        assertEquals(1, (int) fine.getStatus());
    }

    // ---- 规矩六：一轮一条都够不着，回"本轮无事"，不算毛病 ----

    @Test
    void emptyRound_nothingToDo() {
        // 台账空空
        PrecDueRunResult r = svc.runOnce(at("2026-09-23 10:00:00"), new PrecDueRunResult());
        assertEquals(0, r.getReminded());
        assertEquals("本轮无事", r.summary());
        assertEquals(0, svc.runOnce(at("2026-09-23 10:00:00")));

        // 有条目但都还没到点（月初刚统一换完证的那几天）
        entry(1, "Z001", "2026-10-20", "3", "keeper", 0, null, 0);
        PrecDueRunResult notYet = svc.runOnce(at("2026-09-23 10:00:00"), new PrecDueRunResult());
        assertEquals(0, notYet.getReminded());
        assertEquals("本轮无事", notYet.summary());
    }

    // ---- 原有方法签名保留：老入口照常可用 ----

    @Test
    void originalSignatures_stillWork() {
        TPrecDueTask r = entry(1, "Z001", "2026-09-23", "0", "keeper", 0, null, 0);
        entry(2, "Z002", "2026-09-30", "0", "keeper", 0, null, 0);

        List<TPrecDueTask> due = svc.listDue(at("2026-09-23 10:00:00"));
        assertEquals(1, due.size());
        assertEquals("Z001", due.get(0).getItemNo());

        int n = svc.runOnce(at("2026-09-23 10:00:00"));
        assertEquals(1, n);
        assertEquals(1, (int) r.getRemindCount());

        assertEquals("Z001", svc.selectTPrecDueTaskById(1L).getItemNo());
    }
}
