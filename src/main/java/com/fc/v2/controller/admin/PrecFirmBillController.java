package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TPrecFirmBill;
import com.fc.v2.service.ITPrecFirmBillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 从业单位建档单 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "从业单位建档单")
@Controller
@RequestMapping("/PrecFirmBillController")
public class PrecFirmBillController extends BaseController {

    private final String prefix = "admin/precFirmBill";

    @Autowired
    private ITPrecFirmBillService precFirmBillService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("prec:precFirmBill:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "从业单位建档单集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("prec:precFirmBill:list")
    @ResponseBody
    public ResultTable list(TPrecFirmBill record) {
        QueryWrapper<TPrecFirmBill> queryWrapper = new QueryWrapper<TPrecFirmBill>();
        startPage();
        com.github.pagehelper.PageInfo<TPrecFirmBill> page =
                new com.github.pagehelper.PageInfo<TPrecFirmBill>(precFirmBillService.selectTPrecFirmBillList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "从业单位建档单新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("prec:precFirmBill:add")
    @ResponseBody
    public AjaxResult add(TPrecFirmBill record) {
        return toAjax(precFirmBillService.insertTPrecFirmBill(record));
    }

    @Log(title = "从业单位建档单修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("prec:precFirmBill:edit")
    @ResponseBody
    public AjaxResult editSave(TPrecFirmBill record) {
        return toAjax(precFirmBillService.updateTPrecFirmBill(record));
    }

    @Log(title = "从业单位建档单删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("prec:precFirmBill:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(precFirmBillService.deleteTPrecFirmBillByIds(ids));
    }
}
