package com.fc.v2.model.custom;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 分档判定请求载体（服务直调）：品种 + 实际浓度(百分数) + 报备日子。
 * 回算老账认的是报备当日哪条线作数，不看今天哪条在用。
 *
 * @author fuce
 * @date 2026-09-24
 */
public class PrecGradeQuery {

    /** 品种（浓硫酸/稀盐酸/丙酮等，须与线上的 chem_name 一字对上） */
    private String chemName;

    /** 实际浓度（百分数，如 10.00 表示 10.00%） */
    private BigDecimal concentration;

    /** 报备日子（只认日历日，钟点不挪日子） */
    private Date reportDate;

    public PrecGradeQuery() {
    }

    public PrecGradeQuery(String chemName, BigDecimal concentration, Date reportDate) {
        this.chemName = chemName;
        this.concentration = concentration;
        this.reportDate = reportDate;
    }

    public String getChemName() {
        return chemName;
    }

    public void setChemName(String chemName) {
        this.chemName = chemName;
    }

    public BigDecimal getConcentration() {
        return concentration;
    }

    public void setConcentration(BigDecimal concentration) {
        this.concentration = concentration;
    }

    public Date getReportDate() {
        return reportDate;
    }

    public void setReportDate(Date reportDate) {
        this.reportDate = reportDate;
    }
}
