package com.fc.v2.controller.admin;

import java.util.Date;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TPrecAuditBill;
import com.fc.v2.model.custom.AuditTraceVo;
import com.fc.v2.service.ITPrecAuditBillService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 购买许可逐级核签单 Controller
 * （三道死序：派出所预审 → 大队复核 → 市局两人点名核签；受理纸样式办理）
 *
 * @author fuce
 * @date 2026-09-24
 */
@Api(value = "购买许可逐级核签单")
@Controller
@RequestMapping("/precAudit")
public class PrecAuditController extends BaseController {

    private final String prefix = "admin/precAudit";

    @Autowired
    private ITPrecAuditBillService precAuditBillService;

    @ApiOperation(value = "核签台账跳转", notes = "核签台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("precAudit:view")
    public String view() {
        return prefix + "/list";
    }

    @Log(title = "购买许可核签台账", action = "list")
    @ApiOperation(value = "台账分页查询", notes = "台账分页查询")
    @GetMapping("/list")
    @RequiresPermissions("precAudit:list")
    @ResponseBody
    public ResultTable list(TPrecAuditBill query) {
        QueryWrapper<TPrecAuditBill> qw = new QueryWrapper<TPrecAuditBill>()
                .eq("del_flag", 0)
                .orderByDesc("create_time");
        if (query.getBillNo() != null && !query.getBillNo().trim().isEmpty()) {
            qw.like("bill_no", query.getBillNo().trim());
        }
        if (query.getChemName() != null && !query.getChemName().trim().isEmpty()) {
            qw.like("chem_name", query.getChemName().trim());
        }
        if (query.getStatus() != null) {
            qw.eq("status", query.getStatus());
        }
        if (query.getNodeNo() != null) {
            qw.eq("node_no", query.getNodeNo());
        }
        startPage();
        com.github.pagehelper.PageInfo<TPrecAuditBill> page =
                new com.github.pagehelper.PageInfo<TPrecAuditBill>(precAuditBillService.selectList(qw));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "厂里递单页", notes = "厂里递单页")
    @GetMapping("/add")
    @RequiresPermissions("precAudit:add")
    public String add() {
        return prefix + "/add";
    }

    @Log(title = "购买许可报批递单", action = "add")
    @ApiOperation(value = "厂里递单", notes = "厂里递单：生成报批单号，落派出所预审道")
    @PostMapping("/add")
    @RequiresPermissions("precAudit:add")
    @ResponseBody
    public AjaxResult addSave(TPrecAuditBill form) {
        TPrecAuditBill bill = precAuditBillService.submit(form);
        return AjaxResult.success("递单成功，报批单号：" + bill.getBillNo());
    }

    @ApiOperation(value = "受理纸", notes = "受理纸：单在哪一道、谁画的押、几时画的")
    @GetMapping("/trace/{id}")
    @RequiresPermissions("precAudit:view")
    public String trace(@PathVariable("id") Long id, ModelMap model) {
        model.put("id", id);
        model.put("loginName", ShiroUtils.getLoginName());
        return prefix + "/trace";
    }

    @ApiOperation(value = "受理纸数据", notes = "单据+三道底细+账面积数核对")
    @GetMapping("/traceData")
    @RequiresPermissions("precAudit:view")
    @ResponseBody
    public AjaxResult traceData(Long id) {
        AuditTraceVo vo = precAuditBillService.trace(id);
        AjaxResult r = AjaxResult.success();
        r.put("data", vo);
        return r;
    }

    @Log(title = "核签画押", action = "sign")
    @ApiOperation(value = "画押", notes = "画押：人取登录会话；expectNode 屏上当前道；末道按点名席位 1用途合规 2量数")
    @PostMapping("/sign")
    @RequiresPermissions("precAudit:sign")
    @ResponseBody
    public AjaxResult sign(Long id, Integer expectNode, Integer slotNo, String opinion) {
        precAuditBillService.sign(id, expectNode, slotNo, opinion);
        return AjaxResult.success("画押已落账");
    }

    @Log(title = "核签道否", action = "veto")
    @ApiOperation(value = "道否", notes = "本道把话说死，单据当场折住")
    @PostMapping("/veto")
    @RequiresPermissions("precAudit:veto")
    @ResponseBody
    public AjaxResult veto(Long id, Integer expectNode, String reason) {
        precAuditBillService.veto(id, expectNode, reason);
        return AjaxResult.success("已道否，单据折住");
    }

    @Log(title = "打回厂里重报", action = "resubmit")
    @ApiOperation(value = "打回重报", notes = "前轮落押一并勾销，回预审道重新起头积")
    @PostMapping("/resubmit")
    @RequiresPermissions("precAudit:resubmit")
    @ResponseBody
    public AjaxResult resubmit(Long id, String reason) {
        precAuditBillService.returnForResubmit(id, reason);
        return AjaxResult.success("已打回重报，前轮画押勾销，从预审重新起积");
    }

    @ApiOperation(value = "预审前改单页", notes = "预审前改单页")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("precAudit:edit")
    public String edit(@PathVariable("id") Long id, ModelMap model) {
        AuditTraceVo vo = precAuditBillService.trace(id);
        model.put("bill", vo.getBill());
        return prefix + "/edit";
    }

    @Log(title = "预审前改单", action = "edit")
    @ApiOperation(value = "预审前改单", notes = "未落押、在预审道才许改")
    @PostMapping("/edit")
    @RequiresPermissions("precAudit:edit")
    @ResponseBody
    public AjaxResult editSave(Long id, String chemName, String qty, String unit,
                               String purpose, String storeSiteNo) {
        precAuditBillService.editBeforeTrial(id, chemName,
                qty == null || qty.trim().isEmpty() ? null : new java.math.BigDecimal(qty.trim()),
                unit, purpose, storeSiteNo);
        return AjaxResult.success("改单成功");
    }

    @Log(title = "许可出证", action = "issue")
    @ApiOperation(value = "出证", notes = "三道齐后锁品种/量数/凭证期限，发许可证号")
    @PostMapping("/issue")
    @RequiresPermissions("precAudit:issue")
    @ResponseBody
    public AjaxResult issue(Long id, String permitNo, Date permitDue) {
        TPrecAuditBill bill = precAuditBillService.issue(id, permitNo, permitDue);
        return AjaxResult.success("已出证，许可证编号：" + bill.getPermitNo());
    }

    @ApiOperation(value = "末道点名册", notes = "末道点名册")
    @GetMapping("/roster")
    @RequiresPermissions("precAudit:view")
    @ResponseBody
    public AjaxResult roster() {
        AjaxResult r = AjaxResult.success();
        r.put("data", precAuditBillService.roster());
        return r;
    }
}
