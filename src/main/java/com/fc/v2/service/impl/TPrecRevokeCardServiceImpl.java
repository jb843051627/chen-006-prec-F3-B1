package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TPrecRevokeCardMapper;
import com.fc.v2.model.auto.TPrecRevokeCard;
import com.fc.v2.service.ITPrecRevokeCardService;

/**
 * 歇业注销办理卡 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TPrecRevokeCardServiceImpl implements ITPrecRevokeCardService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TPrecRevokeCardMapper precRevokeCardMapper;

    @Override
    public TPrecRevokeCard selectTPrecRevokeCardById(Long id) {
        return this.precRevokeCardMapper.selectById(id);
    }

    @Override
    public List<TPrecRevokeCard> selectTPrecRevokeCardList(QueryWrapper<TPrecRevokeCard> queryWrapper) {
        return this.precRevokeCardMapper.selectList(queryWrapper);
    }

    @Override
    public TPrecRevokeCard advance(Long id, String remark) {
        TPrecRevokeCard r = this.precRevokeCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.precRevokeCardMapper.updateById(r);
        return r;
    }

    @Override
    public TPrecRevokeCard rollback(Long id, String remark) {
        TPrecRevokeCard r = this.precRevokeCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.precRevokeCardMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TPrecRevokeCard r = this.precRevokeCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.precRevokeCardMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TPrecRevokeCard r = this.precRevokeCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.precRevokeCardMapper.deleteById(id) > 0;
    }

}
