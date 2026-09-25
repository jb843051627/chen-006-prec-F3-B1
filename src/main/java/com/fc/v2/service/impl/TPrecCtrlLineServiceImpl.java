package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TPrecCtrlLineMapper;
import com.fc.v2.model.auto.TPrecCtrlLine;
import com.fc.v2.model.custom.PrecGrade;
import com.fc.v2.model.custom.PrecGradeQuery;
import com.fc.v2.service.ITPrecCtrlLineService;

/**
 * 产品浓度分档管控线 Service业务层处理（rule-eval 形状：只判档，无增删改）
 *
 * <p>钉死的口径（受理 / 自查同一把尺子，都走 {@link #classify}）：
 * <br>1. 回算只认报备当日作数的线：起算日 ≤ 报备日 ≤ 交棒日（两端当日都作数，双闭），
 *       交棒日空着视作至今有效；不看今天哪条在用，绝不抓旁边日子的线凑。
 * <br>2. 同一天两条线都够得着同一品种：先比顺位号 priority（大者先），顺位相同再比线号 id（大者先）。
 * <br>3. 压线从严：浓度正压某道上界，按更严一档收（10.00% 正压三类上界即判二类）。
 * <br>4. 缺的那道上界视作不设上限，线照样认，不许按“这条线废了”处理。
 * <br>5. 浓度为负、到 120.00% 及以上，或品种/浓度/报备日缺失：IllegalArgumentException 原封退回。
 * <br>6. 报备当日没有一条线作数：回 {@link PrecGrade#NO_RULE}。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TPrecCtrlLineServiceImpl implements ITPrecCtrlLineService {

    /** 浓度肉眼上界（百分数）：到它及以上一律是填错（一百二十往上），原封退回 */
    private static final BigDecimal MAX_CONCENTRATION = new BigDecimal("120.00");

    /** 列表口径：优先级降序；并列按 ruleCode 降序（旧签名契约，保持不动） */
    private static final Comparator<TPrecCtrlLine> PRIORITY_THEN_CODE_DESC = new Comparator<TPrecCtrlLine>() {
        @Override
        public int compare(TPrecCtrlLine a, TPrecCtrlLine b) {
            int byPriority = comparePriority(a, b);
            if (byPriority != 0) {
                return byPriority;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    /** 判档口径：优先级降序；顺位相同比线号 id，取线号大的那条 */
    private static final Comparator<TPrecCtrlLine> PRIORITY_THEN_LINE_NO_DESC = new Comparator<TPrecCtrlLine>() {
        @Override
        public int compare(TPrecCtrlLine a, TPrecCtrlLine b) {
            // 先看顺位号：大者在前
            int byPriority = comparePriority(a, b);
            if (byPriority != 0) {
                return byPriority;
            }
            // 顺位一样再比线号：线号大者在前；线号缺失按 0 兜底
            long ia = a.getId() == null ? 0L : a.getId().longValue();
            long ib = b.getId() == null ? 0L : b.getId().longValue();
            return Long.compare(ib, ia);
        }
    };

    @javax.annotation.Resource
    private TPrecCtrlLineMapper precCtrlLineMapper;

    /**
     * 日历日统一走**墙钟字符串**（yyyy-MM-dd）：JDBC 的 serverTimezone 与本机时区不对称，
     * 直接把 java.util.Date 作参数会整体偏移，使交棒当日这类边界用例错认线（满期提醒同一踩坑）。
     */
    private static String day(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd").format(d);
    }

    /** 顺位号比较：数值大者在前；空顺位按 0 兜底 */
    private static int comparePriority(TPrecCtrlLine a, TPrecCtrlLine b) {
        int pa = a.getPriority() == null ? 0 : a.getPriority().intValue();
        int pb = b.getPriority() == null ? 0 : b.getPriority().intValue();
        return Integer.compare(pb, pa);
    }

    @Override
    public TPrecCtrlLine selectTPrecCtrlLineById(Long id) {
        return this.precCtrlLineMapper.selectById(id);
    }

    @Override
    public List<TPrecCtrlLine> listAvailable(Date at) {
        List<TPrecCtrlLine> open = new ArrayList<TPrecCtrlLine>();
        if (at == null) {
            return open;
        }
        String dayStr = day(at);
        List<TPrecCtrlLine> all = this.precCtrlLineMapper.selectList(
                new QueryWrapper<TPrecCtrlLine>().eq("del_flag", 0));
        for (TPrecCtrlLine r : all) {
            // 只把删掉的剔出去，再按当日作不作数一条条过：过期/未起算的都不许进可用列表
            if (r.getDelFlag() != null && r.getDelFlag() != 0) {
                continue;
            }
            if (r.getRuleCode() == null || r.getRuleCode().trim().isEmpty()) {
                continue;
            }
            if (r.getTh1Max() == null && r.getTh2Max() == null && r.getTh3Max() == null) {
                // 三道界都没填的线当废线跳过
                continue;
            }
            if (!covers(r, dayStr)) {
                continue;
            }
            open.add(r);
        }
        open.sort(PRIORITY_THEN_CODE_DESC);
        return open;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
            return 0;
        }
        String dayStr = day(at);
        TPrecCtrlLine hit = null;
        for (TPrecCtrlLine r : this.precCtrlLineMapper.selectList(new QueryWrapper<TPrecCtrlLine>()
                .eq("del_flag", 0).eq("rule_code", ruleCode.trim()))) {
            if ((r.getDelFlag() != null && r.getDelFlag() != 0)
                    || !ruleCode.trim().equals(r.getRuleCode()) || !covers(r, dayStr)) {
                continue;
            }
            if (hit == null || PRIORITY_THEN_LINE_NO_DESC.compare(r, hit) < 0) {
                hit = r;
            }
        }
        return hit == null ? 0 : legacyLevelOf(hit, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
            return 0;
        }
        List<TPrecCtrlLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return legacyLevelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TPrecCtrlLine r = this.precCtrlLineMapper.selectById(id);
        return r != null && (r.getDelFlag() == null || r.getDelFlag() == 0) && covers(r, day(at));
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    // ------------------------------------------------------------------
    // 服务直调：档位唯一出口。受理、自查都走这里，前台不许拿中间值再判一遍。
    // ------------------------------------------------------------------

    @Override
    public PrecGrade classify(String chemName, BigDecimal concentration, Date reportDate) {
        return doClassify(chemName, concentration, reportDate);
    }

    @Override
    public PrecGrade classify(PrecGradeQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("判定请求不能为空");
        }
        return doClassify(query.getChemName(), query.getConcentration(), query.getReportDate());
    }

    private PrecGrade doClassify(String chemName, BigDecimal concentration, Date reportDate) {
        if (chemName == null || chemName.trim().isEmpty()) {
            throw new IllegalArgumentException("品种不能为空");
        }
        if (concentration == null) {
            throw new IllegalArgumentException("实际浓度不能为空");
        }
        if (reportDate == null) {
            throw new IllegalArgumentException("报备日子不能为空");
        }
        if (concentration.compareTo(BigDecimal.ZERO) < 0
                || concentration.compareTo(MAX_CONCENTRATION) >= 0) {
            // 负的、一百二十往上这种肉眼就知道填错的，不收，原封退回
            throw new IllegalArgumentException("浓度超出可收范围(0.00%以上、不足120.00%):" + concentration + "%");
        }

        String dayStr = day(reportDate);
        String chem = chemName.trim();
        List<TPrecCtrlLine> all = this.precCtrlLineMapper.selectList(
                new QueryWrapper<TPrecCtrlLine>().eq("del_flag", 0));
        TPrecCtrlLine chosen = null;
        for (TPrecCtrlLine r : all) {
            // 品种、删除标记在 Java 侧再过一道：旁路调用（内存账/批导）不经 SQL 条件时口径不松
            if ((r.getDelFlag() != null && r.getDelFlag() != 0) || !chem.equals(r.getChemName())) {
                continue;
            }
            if (covers(r, dayStr) && (chosen == null
                    || PRIORITY_THEN_LINE_NO_DESC.compare(r, chosen) < 0)) {
                chosen = r;
            }
        }

        // 报备那天前不着村后不着店：回“那日无规可依”，不抓旁边日子的线凑
        if (chosen == null) {
            return PrecGrade.NO_RULE;
        }
        return gradeOf(chosen, concentration);
    }

    /**
     * 一条线在某个日历日作不作数：起算日 ≤ 当日 ≤ 交棒日，双闭（交棒日当日旧线仍作数，
     * 新线起算当日也作数，重叠由顺位号/线号决胜）。交棒日空=至今有效；起算日空=来路不明不作数。
     */
    private static boolean covers(TPrecCtrlLine r, String dayStr) {
        String start = r.getEffStart() == null ? null : day(r.getEffStart());
        String end = r.getEffEnd() == null ? null : day(r.getEffEnd());
        if (start == null) {
            // 没填起算日：来路不明，不作数（绝不抓这种线凑档）
            return false;
        }
        if (dayStr.compareTo(start) < 0) {
            // 还没到起算日
            return false;
        }
        if (end == null) {
            // 没填交棒：从起算日往后一直有效
            return true;
        }
        // 两头都填：起算日、交棒日当日都作数（双闭），交棒次日才退场
        return dayStr.compareTo(end) <= 0;
    }

    /**
     * 压线从严的判档：等于上界即越界，收更严一档。
     * 缺的上界视作该档不设上限——迈不过去就留在当前档，线照样认：
     * th1 缺=免管不设上限（全单免管）；th2 缺=三类不设上限（过免管后收在三类）；
     * th3 缺=二类不设上限（系统能回的最严就是二类，th3 在不在都收在二类）。
     */
    private static PrecGrade gradeOf(TPrecCtrlLine line, BigDecimal conc) {
        BigDecimal th1 = line.getTh1Max();
        if (th1 == null) {
            return PrecGrade.EXEMPT;
        }
        if (conc.compareTo(th1) < 0) {
            return PrecGrade.EXEMPT;
        }
        BigDecimal th2 = line.getTh2Max();
        if (th2 == null) {
            return PrecGrade.CLASS3;
        }
        if (conc.compareTo(th2) < 0) {
            return PrecGrade.CLASS3;
        }
        // 压三类上界、超过三类上界，一律收二类；压 th3 从严也无处更严，仍回二类
        return PrecGrade.CLASS2;
    }

    /**
     * 旧 int 口径 1免管 2三类 3二类 4越出最高上界：压线同样从严，缺阈同样视作不设上限
     * （th1 缺=全量免管回 1；th2 缺=迈过免管后收在 2；th3 缺=迈过三类后收在 3，
     * 只有 th3 在且浓度压/过它时才回 4）。
     */
    private static int legacyLevelOf(TPrecCtrlLine line, BigDecimal conc) {
        // 从松到严逐档量过去：只有真比上界淡才留在当前档，正压上界即往下收更严一档
        BigDecimal th1 = line.getTh1Max();
        if (th1 == null) {
            // 免管不设上限：全量免管
            return 1;
        }
        if (conc.compareTo(th1) < 0) {
            // 比免管上界淡：免管；正压免管上界的往下收三类
            return 1;
        }
        BigDecimal th2 = line.getTh2Max();
        if (th2 == null) {
            // 三类不设上限：迈过免管就收三类
            return 2;
        }
        if (conc.compareTo(th2) < 0) {
            // 比三类上界淡：三类；正压三类上界的往下收二类
            return 2;
        }
        BigDecimal th3 = line.getTh3Max();
        if (th3 == null) {
            // 二类不设上限：迈过三类就收二类
            return 3;
        }
        if (conc.compareTo(th3) < 0) {
            return 3;
        }
        // 正压/迈过二类上界
        return 4;
    }

    private static boolean outOfRange(BigDecimal conc) {
        return conc.compareTo(BigDecimal.ZERO) < 0 || conc.compareTo(MAX_CONCENTRATION) >= 0;
    }
}
