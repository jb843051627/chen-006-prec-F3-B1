package com.fc.v2.model.custom;

/**
 * 浓度分档判定结果：服务层对外只回这一个词，前台不许再加工。
 *
 * <ul>
 *   <li>{@link #EXEMPT}  免管：比最松那道上界还淡；</li>
 *   <li>{@link #CLASS3}  三类；</li>
 *   <li>{@link #CLASS2}  二类（系统能给的最严一档，压三道线及以上都收在这）；</li>
 *   <li>{@link #NO_RULE} 那日无规可依：报备当日没有一条线作数，不抓旁边日子的线凑。</li>
 * </ul>
 * 压线不单列：浓度正压某道上界的，按更严一档收（10.00% 正压三类上界即回“二类”）。
 *
 * @author fuce
 * @date 2026-09-24
 */
public enum PrecGrade {

    /** 免管 */
    EXEMPT("免管"),
    /** 三类 */
    CLASS3("三类"),
    /** 二类 */
    CLASS2("二类"),
    /** 报备当日无规可依 */
    NO_RULE("那日无规可依");

    private final String word;

    PrecGrade(String word) {
        this.word = word;
    }

    /** 系统回出去的那一个词 */
    public String word() {
        return word;
    }

    /** 直接打印即为回出去的词，不留英文码 */
    @Override
    public String toString() {
        return word;
    }
}
