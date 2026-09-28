package com.fc.v2.controller.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TMarrCertBill;
import com.fc.v2.model.auto.TMarrCertSign;
import com.fc.v2.model.custom.CertAcceptVo;
import com.fc.v2.model.custom.CertChain;
import com.fc.v2.service.ITMarrCertBillService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 婚姻登记记录证明逐级核准 Controller（approval-chain 形状：只留受理与逐级签批入口，
 * 不在页面上凭空造单，也不开改动已封栏目的口子；造单只能走查档申请表受理）。
 *
 * @author fuce
 * @date 2026-09-28
 */
@Api(value = "婚姻登记记录证明逐级核准")
@Controller
@RequestMapping("/marrCertBill")
public class MarrCertBillController extends BaseController {

    private final String prefix = "admin/marrCertBill";

    @Autowired
    private ITMarrCertBillService marrCertBillService;

    @ApiOperation(value = "核准台账跳转", notes = "核准台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("marr:marrCertBill:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "逐级核准台账", action = "list")
    @ApiOperation(value = "核准台账", notes = "当前压在第几格由系统按签账算出")
    @GetMapping("/list")
    @RequiresPermissions("marr:marrCertBill:list")
    @ResponseBody
    public ResultTable list(TMarrCertBill record) {
        QueryWrapper<TMarrCertBill> queryWrapper = new QueryWrapper<TMarrCertBill>()
                .eq("del_flag", 0)
                .orderByDesc("create_time");
        if (record.getBillNo() != null && !record.getBillNo().trim().isEmpty()) {
            queryWrapper.like("bill_no", record.getBillNo().trim());
        }
        if (record.getApplicantName() != null && !record.getApplicantName().trim().isEmpty()) {
            queryWrapper.like("applicant_name", record.getApplicantName().trim());
        }
        if (record.getNodeNo() != null) {
            queryWrapper.eq("node_no", record.getNodeNo());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        startPage();
        com.github.pagehelper.PageInfo<TMarrCertBill> page =
                new com.github.pagehelper.PageInfo<TMarrCertBill>(
                        marrCertBillService.selectTMarrCertBillList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "查档申请表(受理进单)", notes = "沿用现行查档申请表，不另做")
    @GetMapping("/apply")
    @RequiresPermissions("marr:marrCertBill:accept")
    public String apply(ModelMap model) {
        model.put("applicantTypes", CertChain.APPLICANT_TYPES);
        model.put("reasons", CertChain.REASONS);
        return prefix + "/apply";
    }

    @Log(title = "查档申请受理进单", action = "accept")
    @ApiOperation(value = "受理进单", notes = "四样齐备、身份凭证相符、事由在册方报得进门")
    @PostMapping("/accept")
    @RequiresPermissions("marr:marrCertBill:accept")
    @ResponseBody
    public AjaxResult accept(CertAcceptVo vo) {
        TMarrCertBill bill = marrCertBillService.accept(vo);
        return bill == null ? error("受理不成：四样有缺、凭证不符、事由不在可出证范围或卷宗编号未在册")
                : success("受理成功，核准单号：" + bill.getBillNo());
    }

    @ApiOperation(value = "核准单办理屏跳转", notes = "核准单办理屏跳转")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("marr:marrCertBill:view")
    public String detail(@PathVariable("id") Long id, ModelMap model) {
        model.put("billId", id);
        model.put("loginName", ShiroUtils.getLoginName());
        return prefix + "/detail";
    }

    @ApiOperation(value = "核准单与签账回查", notes = "屏上摆的与账上存的是同一回算出来的数")
    @GetMapping("/get/{id}")
    @RequiresPermissions("marr:marrCertBill:view")
    @ResponseBody
    public AjaxResult get(@PathVariable("id") Long id) {
        TMarrCertBill bill = marrCertBillService.selectTMarrCertBillById(id);
        if (bill == null) {
            return error("单子不存在");
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("bill", bill);
        data.put("trail", marrCertBillService.selectSignTrail(id));
        data.put("trailAll", marrCertBillService.selectSignTrail(id, true));
        data.put("loginName", ShiroUtils.getLoginName());
        data.put("doubleRoles", CertChain.DOUBLE_ROLES);
        return retobject(200, data);
    }

    @Log(title = "逐级核准落印(单签格)", action = "approve")
    @ApiOperation(value = "落印核准(受理后核对/市局备案)", notes = "成不成看业务方法给回的结论")
    @PostMapping("/approve")
    @RequiresPermissions("marr:marrCertBill:approve")
    @ResponseBody
    public AjaxResult approve(Long id, String comment) {
        return result(marrCertBillService.approve(id, ShiroUtils.getLoginName(), comment));
    }

    @Log(title = "分管负责人落印(带名目)", action = "approve")
    @ApiOperation(value = "分管负责人落印", notes = "SCOPE事由核/TEXT记载文本核，各管各的不许替签")
    @PostMapping("/approveDouble")
    @RequiresPermissions("marr:marrCertBill:approve")
    @ResponseBody
    public AjaxResult approveDouble(Long id, String signRole, String comment) {
        return result(marrCertBillService.approve(id, ShiroUtils.getLoginName(), signRole, comment));
    }

    @Log(title = "不予出具(单签格)", action = "reject")
    @ApiOperation(value = "不予出具", notes = "件定在此格封栏，事后谁也不能再补一枚")
    @PostMapping("/reject")
    @RequiresPermissions("marr:marrCertBill:reject")
    @ResponseBody
    public AjaxResult reject(Long id, String comment) {
        return vetoResult(marrCertBillService.reject(id, ShiroUtils.getLoginName(), comment));
    }

    @Log(title = "分管负责人不予出具(带名目)", action = "reject")
    @ApiOperation(value = "分管负责人不予出具", notes = "第三格定格封栏")
    @PostMapping("/rejectDouble")
    @RequiresPermissions("marr:marrCertBill:reject")
    @ResponseBody
    public AjaxResult rejectDouble(Long id, String signRole, String comment) {
        return vetoResult(marrCertBillService.reject(id, ShiroUtils.getLoginName(), signRole, comment));
    }

    @Log(title = "市局打回", action = "rollback")
    @ApiOperation(value = "市局打回", notes = "表述不合规范打回处里，此前名目抹平、从第二格重攒")
    @PostMapping("/rollback")
    @RequiresPermissions("marr:marrCertBill:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String comment) {
        TMarrCertBill bill = marrCertBillService.rollback(id, ShiroUtils.getLoginName(), comment);
        return bill == null ? error("打回不成：只有压在市局备案格的件能回退")
                : success("已打回，第 " + bill.getReviewRound() + " 轮自档案核对格重攒");
    }

    @Log(title = "证明文本出手", action = "issue")
    @ApiOperation(value = "核准结论转对外证明文本出手", notes = "姓名/档案字号/事由/批准文号同一单带出")
    @PostMapping("/issue")
    @RequiresPermissions("marr:marrCertBill:issue")
    @ResponseBody
    public AjaxResult issue(Long id, String certNo, String comment) {
        TMarrCertBill bill = marrCertBillService.issue(id, certNo, ShiroUtils.getLoginName(), comment);
        return bill == null ? error("出证不成：四格未挪满、批准文号空或该申请人已有一条出证文本")
                : success("证明文本已出手，批准文号：" + bill.getCertNo());
    }

    @Log(title = "出手后倒序复核", action = "review")
    @ApiOperation(value = "倒序复核确认一格", notes = "与当日办理那一屏对得上号方确认")
    @PostMapping("/review")
    @RequiresPermissions("marr:marrCertBill:review")
    @ResponseBody
    public AjaxResult review(Long id, String comment) {
        TMarrCertBill bill = marrCertBillService.review(id, ShiroUtils.getLoginName(), comment);
        return bill == null ? error("复核不成：须由该格原落印人本人按市局→双签→核对→受理次序确认")
                : (bill.getReviewLevel() != null && bill.getReviewLevel() == 0
                        ? success("倒序复核全部对上号") : success("本格复核对上号，继续倒着复上一格"));
    }

    private AjaxResult result(TMarrCertBill bill) {
        if (bill == null) {
            return error("这一签没签成：格次未到、此名目已签过、代签或件已封栏");
        }
        return Integer.valueOf(CertChain.STATUS_PASS).equals(bill.getStatus())
                ? success("四格挪满，已核讫")
                : success("已落印，当前在第 " + bill.getNodeNo() + " 格，本格 "
                        + bill.getSignCount() + "/" + bill.getNeedCount());
    }

    private AjaxResult vetoResult(TMarrCertBill bill) {
        return bill == null ? error("不予出具记不成：格次未到或件已封栏")
                : success("已写下不予出具，件定在第 " + bill.getFreezeNode() + " 格，各栏封住");
    }
}
