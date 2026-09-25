package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TPrecFirmBill;

import java.util.List;

/**
 * 从业单位建档单 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITPrecFirmBillService {

    /** 按主键查询 */
    TPrecFirmBill selectTPrecFirmBillById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TPrecFirmBill> selectTPrecFirmBillList(Wrapper<TPrecFirmBill> queryWrapper);

    /** 新增 */
    int insertTPrecFirmBill(TPrecFirmBill record);

    /** 修改 */
    int updateTPrecFirmBill(TPrecFirmBill record);

    /** 批量删除 */
    int deleteTPrecFirmBillByIds(String ids);

    /** 按主键删除 */
    int deleteTPrecFirmBillById(Long id);
}
