package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 证照满期提醒一轮周期执行的结果账。
 * 周期执行以服务返回的结果为准：调度入口只照此账说话，不另起一套算法。
 * 一轮里一条都够不着时 summary() 回"本轮无事"，不当作毛病。
 *
 * @author fuce
 * @date 2026-09-24
 */
public class PrecDueRunResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本轮是否落在出声窗口(办公日白天 09:00-17:00)内 */
    private boolean inWindow;

    /** 本轮出声条数 */
    private int reminded;

    /** 本轮转值班员上门条数(三催无回音,系统不再出声) */
    private int escalated;

    /** 本轮卡住的条目(单拎出来挂事由,不拖住整轮) */
    private List<StuckItem> stuck = new ArrayList<StuckItem>();

    public boolean isInWindow() {
        return inWindow;
    }

    public void setInWindow(boolean inWindow) {
        this.inWindow = inWindow;
    }

    public int getReminded() {
        return reminded;
    }

    public int getEscalated() {
        return escalated;
    }

    public List<StuckItem> getStuck() {
        return stuck;
    }

    public void addReminded() {
        this.reminded++;
    }

    public void addEscalated() {
        this.escalated++;
    }

    public void addStuck(String itemNo, String reason) {
        this.stuck.add(new StuckItem(itemNo, reason));
    }

    /** 本轮一句话：窗口外搁置 / 本轮无事 / 出声与卡住明细 */
    public String summary() {
        if (!inWindow) {
            return "不在出声窗口(办公日09:00-17:00)，本轮搁置，候下一个白天";
        }
        if (reminded == 0 && escalated == 0 && stuck.isEmpty()) {
            return "本轮无事";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本轮出声").append(reminded).append("条");
        if (escalated > 0) {
            sb.append("，转值班员上门").append(escalated).append("条");
        }
        if (!stuck.isEmpty()) {
            sb.append("，卡住").append(stuck.size()).append("条：");
            for (int i = 0; i < stuck.size(); i++) {
                if (i > 0) {
                    sb.append('；');
                }
                sb.append(stuck.get(i).getItemNo()).append('(').append(stuck.get(i).getReason()).append(')');
            }
        }
        return sb.toString();
    }

    /** 一条卡住的条目：证号 + 事由 */
    public static class StuckItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 凭证编号 */
        private String itemNo;

        /** 卡住事由 */
        private String reason;

        public StuckItem(String itemNo, String reason) {
            this.itemNo = itemNo;
            this.reason = reason;
        }

        public String getItemNo() {
            return itemNo;
        }

        public String getReason() {
            return reason;
        }
    }
}
