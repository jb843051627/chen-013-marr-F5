package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.mapper.auto.TMarrCertBillMapper;
import com.fc.v2.model.auto.TMarrCertBill;
import com.fc.v2.model.auto.TMarrCertText;
import com.fc.v2.service.ITMarrCertBillService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 婚姻登记记录证明逐级核准单 Controller（approval-chain 形状：四格逐级签批）
 *
 * 页面上没有凭空造单、改动已封栏目的口子；件压在第几格、本格凑了几双手，
 * 一律以业务服务层签字方法给回的结论为准。落印人强制取登录本人，页面代传的名字不采信。
 * 表格沿用现行查档申请表，不再另做。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Api(value = "婚姻登记记录证明逐级核准单")
@Controller
@RequestMapping("/marrCertBill")
public class MarrCertBillController extends BaseController {

    private final String prefix = "admin/marrCertBill";

    @Autowired
    private ITMarrCertBillService marrCertBillService;

    /** 台账只读，不经过签发服务，也不提供新增/改栏目入口 */
    @Autowired
    private TMarrCertBillMapper marrCertBillMapper;

    @ApiOperation(value = "逐级核准台账跳转", notes = "逐级核准台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("marrCertBill:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "婚姻登记记录证明逐级核准台账", action = "list")
    @ApiOperation(value = "核准台账", notes = "核准台账")
    @GetMapping("/list")
    @RequiresPermissions("marrCertBill:list")
    @ResponseBody
    public ResultTable list(TMarrCertBill record) {
        QueryWrapper<TMarrCertBill> queryWrapper = new QueryWrapper<TMarrCertBill>()
                .eq("del_flag", 0)
                .orderByDesc("create_time");
        startPage();
        List<TMarrCertBill> rows = marrCertBillMapper.selectList(queryWrapper);
        com.github.pagehelper.PageInfo<TMarrCertBill> page =
                new com.github.pagehelper.PageInfo<TMarrCertBill>(rows);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "逐级核准落印", action = "approve")
    @ApiOperation(value = "本格落印", notes = "在指定格以指定名目本人亲签；第三格须 PURPOSE/CONTENT 各落一笔")
    @PostMapping("/approve")
    @RequiresPermissions("marrCertBill:approve")
    @ResponseBody
    public AjaxResult approve(Long id, Integer nodeNo, String roleCode, String comment) {
        // 落印人只认登录本人，谁也替不了谁
        String approver = ShiroUtils.getLoginName();
        TMarrCertBill bill = marrCertBillService.approve(id, approver, nodeNo, roleCode, comment);
        return bill == null ? AjaxResult.error("落印不成：次序未到、名目不符、代签或已封死")
                : AjaxResult.success(200, "落印已成", bill);
    }

    @Log(title = "逐级核准不予出具", action = "reject")
    @ApiOperation(value = "不予出具", notes = "在当前格写下不予出具，件即定在此处终局封死")
    @PostMapping("/reject")
    @RequiresPermissions("marrCertBill:reject")
    @ResponseBody
    public AjaxResult reject(Long id, String comment) {
        String approver = ShiroUtils.getLoginName();
        TMarrCertBill bill = marrCertBillService.reject(id, approver, comment);
        return bill == null ? AjaxResult.error("驳回不成：件不在办或已封死")
                : AjaxResult.success(200, "已写下不予出具，本件封死", bill);
    }

    @Log(title = "市局回退重攒", action = "rollback")
    @ApiOperation(value = "市局回退", notes = "此前各格名目尽数抹平，从第二格（核对）重新攒")
    @PostMapping("/rollback")
    @RequiresPermissions("marrCertBill:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String comment) {
        TMarrCertBill bill = marrCertBillService.rollback(id, comment);
        return bill == null ? AjaxResult.error("回退不成：件未压在市局桌上，或备案印已落定封死（只能另起新件）")
                : AjaxResult.success(200, "已退回处里，自核对格重新攒", bill);
    }

    @Log(title = "对外证明文本出手", action = "issue")
    @ApiOperation(value = "出证文本", notes = "四格挪满后由核准单结论直接转换；同一申请人重复抄发拦住")
    @PostMapping("/issue")
    @RequiresPermissions("marrCertBill:issue")
    @ResponseBody
    public AjaxResult issue(Long id) {
        TMarrCertText text = marrCertBillService.issueCertText(id);
        return text == null ? AjaxResult.error("出证不成：四格未挪满，或该申请人已有一条文本")
                : AjaxResult.success(200, "文本已出手", text);
    }

    @ApiOperation(value = "倒序复核账", notes = "文本出手后按格倒序一级级复核有效落印名目")
    @GetMapping("/trail")
    @RequiresPermissions("marrCertBill:trail")
    @ResponseBody
    public AjaxResult trail(Long id) {
        return AjaxResult.successData(200, marrCertBillService.certTrail(id));
    }
}
