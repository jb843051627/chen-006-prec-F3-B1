package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TPrecRevokeCard;
import com.fc.v2.service.ITPrecRevokeCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 歇业注销办理卡 Controller（state-machine 形状：流转入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "歇业注销办理卡")
@Controller
@RequestMapping("/precRevokeCard")
public class PrecRevokeCardController extends BaseController {

    private final String prefix = "admin/precRevokeCard";

    @Autowired
    private ITPrecRevokeCardService precRevokeCardService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("precRevokeCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "歇业注销办理卡流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("precRevokeCard:list")
    @ResponseBody
    public ResultTable list(TPrecRevokeCard record) {
        QueryWrapper<TPrecRevokeCard> queryWrapper = new QueryWrapper<TPrecRevokeCard>();
        startPage();
        com.github.pagehelper.PageInfo<TPrecRevokeCard> page =
                new com.github.pagehelper.PageInfo<TPrecRevokeCard>(precRevokeCardService.selectTPrecRevokeCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "歇业注销办理卡推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("precRevokeCard:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(precRevokeCardService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "歇业注销办理卡回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("precRevokeCard:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(precRevokeCardService.rollback(id, remark) != null ? 1 : 0);
    }
}
