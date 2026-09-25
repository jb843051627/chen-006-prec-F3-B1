package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TPrecReportRowMapper;
import com.fc.v2.model.auto.TPrecReportRow;
import com.fc.v2.service.ITPrecReportRowService;

/**
 * 季度报送册核收明细 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TPrecReportRowServiceImpl implements ITPrecReportRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TPrecReportRowMapper precReportRowMapper;

    @Override
    public TPrecReportRow selectTPrecReportRowById(Long id) {
        return this.precReportRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TPrecReportRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TPrecReportRow> errors = new java.util.ArrayList<TPrecReportRow>();
        int seq = 0;
        for (TPrecReportRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.precReportRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TPrecReportRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.precReportRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TPrecReportRow> listErrors(String batchNo) {
        return this.precReportRowMapper.selectList(new QueryWrapper<TPrecReportRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
