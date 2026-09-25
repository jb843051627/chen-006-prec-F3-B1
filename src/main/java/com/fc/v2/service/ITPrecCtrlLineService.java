package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TPrecCtrlLine;
import com.fc.v2.model.custom.PrecGrade;
import com.fc.v2.model.custom.PrecGradeQuery;

/**
 * 产品浓度分档管控线 Service接口（rule-eval 形状：规则求值，无增删改）
 *
 * <p>对外只开「服务直调」一扇门：档位全由 {@link #classify} 出结果，
 * 受理与自查都调它，前台不许拿中间值再自己动一遍。不设新增/编辑/删除口子。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITPrecCtrlLineService {

    /** 按主键查询规则 */
    TPrecCtrlLine selectTPrecCtrlLineById(Long id);

    /**
     * 单规则求值：按 ruleCode 在 at 时刻生效的版本判档（1..4）。
     * 无生效版本 / 输入越界 / 参数缺失一律返回 0。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻可用规则中优先级最高者的档位；无可用规则返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻可用规则（优先级降序、ruleCode 降序） */
    List<TPrecCtrlLine> listAvailable(Date at);

    /** 单条规则在 at 时刻是否可用（批量导入/报表等旁路复用，口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用规则条数（列表页角标 / 看板） */
    int countAvailable(Date at);

    /**
     * 分档判定（服务直调，三参重载）：递品种、实际浓度(百分数)、报备日子，只回一个词。
     * <ul>
     *   <li>回算只认报备当日作数的线（起算日/交棒日双闭），不看今天哪条在用；
     *       当日多条线都够得着：先比顺位号(priority 大者)，顺位相同比线号(id 大者)；</li>
     *   <li>压线从严：浓度正压某道上界按更严一档收；缺的上界视作不设上限，线照认；</li>
     *   <li>报备当日没有一条线作数 → {@link PrecGrade#NO_RULE}，不抓旁边日子的线凑；</li>
     *   <li>品种/浓度/日子缺失，或浓度为负、到 120.00% 及以上 → IllegalArgumentException 原封退回。</li>
     * </ul>
     */
    PrecGrade classify(String chemName, BigDecimal concentration, Date reportDate);

    /** 分档判定（服务直调，请求载体重载）：口径与三参版完全同一套，受理/自查都走这一个门 */
    PrecGrade classify(PrecGradeQuery query);
}
