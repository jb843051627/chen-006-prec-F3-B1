package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 购买许可逐级核签单对象 t_prec_audit_bill
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_prec_audit_bill")
@ApiModel(value = "TPrecAuditBill", description = "购买许可逐级核签单")
public class TPrecAuditBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 许可报批单号(局里统一格式 YPG-BP-日-号) */
    @TableField("bill_no")
    @ApiModelProperty(value = "许可报批单号")
    private String billNo;

    /** 品种(盐酸/丙酮等) */
    @TableField("chem_name")
    @ApiModelProperty(value = "品种")
    private String chemName;

    /** 申报数量 */
    @TableField("qty")
    @ApiModelProperty(value = "申报数量")
    private BigDecimal qty;

    /** 数量单位 */
    @TableField("unit")
    @ApiModelProperty(value = "数量单位")
    private String unit;

    /** 用途 */
    @TableField("purpose")
    @ApiModelProperty(value = "用途")
    private String purpose;

    /** 存放库房核定号 */
    @TableField("store_site_no")
    @ApiModelProperty(value = "存放库房核定号")
    private String storeSiteNo;

    /** 报批轮次(打回重报一轮+1,旧押不续) */
    @TableField("round_no")
    @ApiModelProperty(value = "报批轮次")
    private Integer roundNo;

    /** 当前所在道 0派出所 1大队 2市局 3已出证 */
    @TableField("node_no")
    @ApiModelProperty(value = "当前所在道 0..3")
    private Integer nodeNo;

    /** 同层核签方式 0任一人 1名单点齐 */
    @TableField("sign_mode")
    @ApiModelProperty(value = "同层核签方式 0任一人 1名单点齐")
    private Integer signMode;

    /** 本道应画押人数 */
    @TableField("need_count")
    @ApiModelProperty(value = "本道应画押人数")
    private Integer needCount;

    /** 本道已画押人数(系统积,不由人填) */
    @TableField("sign_count")
    @ApiModelProperty(value = "本道已画押人数")
    private Integer signCount;

    /** 本轮累计有效画押(系统积,与流水账核对) */
    @TableField("sign_total")
    @ApiModelProperty(value = "本轮累计有效画押")
    private Integer signTotal;

    /** 报批情形 0在核 1已出证 2已道否 */
    @TableField("status")
    @ApiModelProperty(value = "报批情形 0在核 1已出证 2已道否")
    private Integer status;

    /** 许可证编号(出证时锁) */
    @TableField("permit_no")
    @ApiModelProperty(value = "许可证编号")
    private String permitNo;

    /** 凭证期限(出证时锁) */
    @TableField("permit_due")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "凭证期限")
    private Date permitDue;

    /** 出证时刻 */
    @TableField("issue_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "出证时刻")
    private Date issueTime;

    /** 道否卡在第几道 */
    @TableField("veto_node")
    @ApiModelProperty(value = "道否卡在第几道")
    private Integer vetoNode;

    /** 道否事由 */
    @TableField("veto_reason")
    @ApiModelProperty(value = "道否事由")
    private String vetoReason;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getChemName() {
        return chemName;
    }

    public void setChemName(String chemName) {
        this.chemName = chemName;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getStoreSiteNo() {
        return storeSiteNo;
    }

    public void setStoreSiteNo(String storeSiteNo) {
        this.storeSiteNo = storeSiteNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getSignMode() {
        return signMode;
    }

    public void setSignMode(Integer signMode) {
        this.signMode = signMode;
    }

    public Integer getNeedCount() {
        return needCount;
    }

    public void setNeedCount(Integer needCount) {
        this.needCount = needCount;
    }

    public Integer getSignCount() {
        return signCount;
    }

    public void setSignCount(Integer signCount) {
        this.signCount = signCount;
    }

    public Integer getSignTotal() {
        return signTotal;
    }

    public void setSignTotal(Integer signTotal) {
        this.signTotal = signTotal;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getPermitNo() {
        return permitNo;
    }

    public void setPermitNo(String permitNo) {
        this.permitNo = permitNo;
    }

    public Date getPermitDue() {
        return permitDue;
    }

    public void setPermitDue(Date permitDue) {
        this.permitDue = permitDue;
    }

    public Date getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(Date issueTime) {
        this.issueTime = issueTime;
    }

    public Integer getVetoNode() {
        return vetoNode;
    }

    public void setVetoNode(Integer vetoNode) {
        this.vetoNode = vetoNode;
    }

    public String getVetoReason() {
        return vetoReason;
    }

    public void setVetoReason(String vetoReason) {
        this.vetoReason = vetoReason;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
