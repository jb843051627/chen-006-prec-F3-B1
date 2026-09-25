package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TPrecAuditBill;
import com.fc.v2.model.auto.TPrecAuditSign;

import java.io.Serializable;
import java.util.List;

/**
 * 核签单受理纸：单据 + 从末道往回捋的画押底细 + 账面积数核对。
 * 页面所见与回溯所出同源，均取自画押流水账。
 *
 * @author fuce
 * @date 2026-09-24
 */
public class AuditTraceVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 单据 */
    private TPrecAuditBill bill;

    /** 当前轮各道底细，按道次倒序(末道在前) */
    private List<NodeView> nodes;

    /** 已勾销的历轮旧押(只存档,不进积数) */
    private List<TPrecAuditSign> archived;

    /** 账上本轮有效落押数(流水账 count) */
    private Integer countedSignTotal;

    /** 单面积数(必须等于账上数,岔一枚这单没人敢认) */
    private Integer storedSignTotal;

    /** 账面是否对得上 */
    private Boolean matched;

    public TPrecAuditBill getBill() {
        return bill;
    }

    public void setBill(TPrecAuditBill bill) {
        this.bill = bill;
    }

    public List<NodeView> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeView> nodes) {
        this.nodes = nodes;
    }

    public List<TPrecAuditSign> getArchived() {
        return archived;
    }

    public void setArchived(List<TPrecAuditSign> archived) {
        this.archived = archived;
    }

    public Integer getCountedSignTotal() {
        return countedSignTotal;
    }

    public void setCountedSignTotal(Integer countedSignTotal) {
        this.countedSignTotal = countedSignTotal;
    }

    public Integer getStoredSignTotal() {
        return storedSignTotal;
    }

    public void setStoredSignTotal(Integer storedSignTotal) {
        this.storedSignTotal = storedSignTotal;
    }

    public Boolean getMatched() {
        return matched;
    }

    public void setMatched(Boolean matched) {
        this.matched = matched;
    }

    /** 一道门的底细 */
    public static class NodeView implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 道次 0派出所 1大队 2市局 */
        private Integer nodeNo;

        /** 道名 */
        private String nodeName;

        /** 同层核签方式 0任一人 1名单点齐 */
        private Integer signMode;

        /** 本道应到 */
        private Integer needCount;

        /** 本道已到(账上 count) */
        private Integer signCount;

        /** 本道画押流水,按时刻先后 */
        private List<TPrecAuditSign> signs;

        public Integer getNodeNo() {
            return nodeNo;
        }

        public void setNodeNo(Integer nodeNo) {
            this.nodeNo = nodeNo;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
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

        public List<TPrecAuditSign> getSigns() {
            return signs;
        }

        public void setSigns(List<TPrecAuditSign> signs) {
            this.signs = signs;
        }
    }
}
