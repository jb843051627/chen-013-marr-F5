package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fc.v2.mapper.auto.TMarrCertBillMapper;
import com.fc.v2.mapper.auto.TMarrCertSignMapper;
import com.fc.v2.mapper.auto.TMarrVolDocMapper;
import com.fc.v2.model.auto.TMarrCertBill;
import com.fc.v2.model.auto.TMarrCertSign;
import com.fc.v2.model.auto.TMarrVolDoc;
import com.fc.v2.model.custom.CertAcceptVo;
import com.fc.v2.model.custom.CertChain;
import com.fc.v2.service.ITMarrCertBillService;
import com.fc.v2.shiro.util.ShiroUtils;

/**
 * 婚姻登记记录证明逐级核准单 Service业务层处理（approval-chain 形状：四格逐级签批）。
 *
 * 规矩：
 * 1. 件压第几格由签账算出，页面不能凭空摆一格；这一签成不成只看本方法给回的结论（null 即没签成）。
 * 2. 第三格两双眼各管各的，同一分钟签完也各是一笔；只落一个人件仍留在第三格，代签一律当没签。
 * 3. 哪一格写下不予出具，件定在此处，事后谁也不能再补一枚；印落定各栏封住，要改只能另起新件。
 * 4. 市局打回：此前各格落过的名目尽数抹平（旧轮留痕、不与新轮并排），轮次加一，从第二格重攒。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMarrCertBillServiceImpl implements ITMarrCertBillService {

    @javax.annotation.Resource
    private TMarrCertBillMapper marrCertBillMapper;

    @javax.annotation.Resource
    private TMarrCertSignMapper marrCertSignMapper;

    @javax.annotation.Resource
    private TMarrVolDocMapper marrVolDocMapper;

    @Override
    public TMarrCertBill selectTMarrCertBillById(Long id) {
        TMarrCertBill bill = this.marrCertBillMapper.selectById(id);
        if (bill != null) {
            // 屏上摆的与账上存的是同一回算出来的数
            syncFromTrail(bill);
        }
        return bill;
    }

    @Override
    public List<TMarrCertBill> selectTMarrCertBillList(QueryWrapper<TMarrCertBill> queryWrapper) {
        List<TMarrCertBill> list = this.marrCertBillMapper.selectList(queryWrapper);
        for (TMarrCertBill bill : list) {
            syncFromTrail(bill);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill accept(CertAcceptVo vo) {
        if (vo == null || !present(vo.getApplicantName()) || !present(vo.getApplicantIdNo())
                || !present(vo.getQueryTarget()) || !present(vo.getDraftText())
                || !present(vo.getVolumeNo())) {
            // 受理四样缺一样，报不进门
            return null;
        }
        if (!CertChain.APPLICANT_TYPES.containsKey(trim(vo.getApplicantType()))
                || !present(vo.getCredentialNo())) {
            // 现行利用办法按身份分类，各拿各的凭证
            return null;
        }
        if (!CertChain.REASONS.containsKey(trim(vo.getReasonCode()))) {
            // 民政部通知把可出证事由收过一截，不在范围内当场截住
            return null;
        }
        TMarrVolDoc vol = findVolume(vo.getVolumeNo());
        if (vol == null || vol.getStatus() == null || vol.getStatus() != 0) {
            // 只认在册卷宗编号；已封卷的仅供翻查，不挂新单
            return null;
        }

        long id = IdWorker.getId();
        TMarrCertBill bill = new TMarrCertBill();
        bill.setId(Long.valueOf(id));
        bill.setBillNo(present(vo.getBillNo()) ? trim(vo.getBillNo()) : buildBillNo(id));
        bill.setApplicantName(trim(vo.getApplicantName()));
        bill.setApplicantIdNo(trim(vo.getApplicantIdNo()));
        bill.setApplicantType(trim(vo.getApplicantType()));
        bill.setCredentialNo(trim(vo.getCredentialNo()));
        bill.setQueryTarget(trim(vo.getQueryTarget()));
        bill.setVolumeNo(trim(vo.getVolumeNo()));
        bill.setReasonCode(trim(vo.getReasonCode()));
        bill.setDraftText(trim(vo.getDraftText()));
        bill.setRemark(vo.getRemark());
        bill.setNodeNo(CertChain.NODE_ACCEPT);
        bill.setSignMode(CertChain.modeOf(CertChain.NODE_ACCEPT));
        bill.setNeedCount(CertChain.needCount(CertChain.NODE_ACCEPT));
        bill.setSignCount(0);
        bill.setStatus(CertChain.STATUS_RUNNING);
        bill.setReviewRound(1);
        bill.setIssueFlag(0);
        bill.setReviewLevel(0);
        bill.setDelFlag(0);
        this.marrCertBillMapper.insert(bill);

        // 受理岗验过四样当场落印，头一格这一笔把件推进核对岗
        String acceptor = ShiroUtils.getLoginName();
        writeSign(bill, CertChain.NODE_ACCEPT, CertChain.ROLE_SINGLE,
                acceptor, CertChain.ACTION_APPROVE, "受理：四样齐备，身份凭证相符，事由在册");
        advance(bill);
        this.marrCertBillMapper.updateById(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill approve(Long id, String approver, String comment) {
        TMarrCertBill bill = loadMutable(id);
        if (bill == null || !present(approver)) {
            return null;
        }
        int node = bill.getNodeNo();
        if (node != CertChain.NODE_CHECK && node != CertChain.NODE_BUREAU) {
            // 第三格须带名目走重载，谁也替不了谁；余格无权在此签
            return null;
        }
        if (existsApprove(bill.getId(), bill.getReviewRound(), node, null, approver)) {
            // 同一格同人不得重复落印
            return null;
        }
        if (node == CertChain.NODE_CHECK) {
            // 档案核对：在册卷宗与查档底单只认编号比对，姓名撞得再像也不作数
            TMarrVolDoc vol = findVolume(bill.getVolumeNo());
            if (vol == null || !trim(vol.getSiteNo()).equals(trim(bill.getVolumeNo()))) {
                return null;
            }
        }
        writeSign(bill, node, CertChain.ROLE_SINGLE, trim(approver),
                CertChain.ACTION_APPROVE, comment);
        if (!advanceWhenFull(bill)) {
            this.marrCertBillMapper.updateById(bill);
        }
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill approve(Long id, String approver, String signRole, String comment) {
        TMarrCertBill bill = loadMutable(id);
        if (bill == null || !present(approver) || !CertChain.DOUBLE_ROLES.contains(signRole)) {
            return null;
        }
        if (bill.getNodeNo() != CertChain.NODE_DOUBLE) {
            // 两名分管负责人只站在第三格
            return null;
        }
        if (existsApprove(bill.getId(), bill.getReviewRound(), CertChain.NODE_DOUBLE, signRole, null)
                || existsApprove(bill.getId(), bill.getReviewRound(), CertChain.NODE_DOUBLE, null, approver)) {
            // 同名目重复签不成；同一双手不许把另一双眼的名目也代了
            return null;
        }
        if (CertChain.ROLE_SCOPE.equals(signRole)) {
            // 甲双眼：事由落没落在可出证范围里
            if (!CertChain.REASONS.containsKey(trim(bill.getReasonCode()))) {
                return null;
            }
        } else {
            // 乙双眼：档案记载与拟出文本合不合得拢（编号对得上、表述不空）
            TMarrVolDoc vol = findVolume(bill.getVolumeNo());
            if (vol == null || !present(bill.getDraftText())
                    || !trim(vol.getSiteNo()).equals(trim(bill.getVolumeNo()))) {
                return null;
            }
        }
        writeSign(bill, CertChain.NODE_DOUBLE, signRole, trim(approver),
                CertChain.ACTION_APPROVE, comment);
        if (!advanceWhenFull(bill)) {
            this.marrCertBillMapper.updateById(bill);
        }
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill reject(Long id, String approver, String comment) {
        TMarrCertBill bill = loadMutable(id);
        if (bill == null || !present(approver)) {
            return null;
        }
        int node = bill.getNodeNo();
        if (node != CertChain.NODE_CHECK && node != CertChain.NODE_BUREAU) {
            // 第三格写下不予出具须带名目走重载，免得记不清落的是哪双眼
            return null;
        }
        return veto(bill, CertChain.ROLE_SINGLE, trim(approver), comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill reject(Long id, String approver, String signRole, String comment) {
        TMarrCertBill bill = loadMutable(id);
        if (bill == null || !present(approver) || !CertChain.DOUBLE_ROLES.contains(signRole)) {
            return null;
        }
        if (bill.getNodeNo() != CertChain.NODE_DOUBLE) {
            return null;
        }
        return veto(bill, signRole, trim(approver), comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill rollback(Long id, String comment) {
        // 无具名落印人时，以市局备案岗位名义记这笔打回
        return bounce(id, "市局备案岗", comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill rollback(Long id, String approver, String comment) {
        if (!present(approver)) {
            return null;
        }
        return bounce(id, trim(approver), comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill issue(Long id, String certNo, String operator, String comment) {
        TMarrCertBill bill = loadMutable(id);
        if (bill == null || !present(certNo) || !present(operator)) {
            return null;
        }
        if (bill.getNodeNo() != CertChain.NODE_DONE || bill.getStatus() != CertChain.STATUS_PASS) {
            // 四格没挪满，结论转不成对外文本
            return null;
        }
        // 同一申请人已出手过一条文本的，第二条重复抄发当场拦住
        Integer dup = this.marrCertBillMapper.selectCount(new QueryWrapper<TMarrCertBill>()
                .eq("applicant_id_no", trim(bill.getApplicantIdNo()))
                .eq("issue_flag", 1)
                .eq("del_flag", 0)
                .ne("id", bill.getId()));
        if (dup != null && dup.intValue() > 0) {
            return null;
        }

        bill.setCertNo(trim(certNo));
        bill.setCertText(buildCertText(bill));
        bill.setIssueTime(new Date());
        bill.setIssueFlag(1);
        bill.setReviewLevel(CertChain.NODE_DONE);
        writeSign(bill, CertChain.NODE_DONE, CertChain.ROLE_SINGLE, trim(operator),
                CertChain.ACTION_ISSUE, comment);
        this.marrCertBillMapper.updateById(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill review(Long id, String reviewer, String comment) {
        TMarrCertBill bill = this.marrCertBillMapper.selectById(id);
        if (bill == null || !present(reviewer) || !Integer.valueOf(1).equals(bill.getIssueFlag())
                || bill.getReviewLevel() == null || bill.getReviewLevel() <= 0
                || Integer.valueOf(1).equals(bill.getDelFlag())) {
            return null;
        }
        int node = bill.getReviewLevel() - 1;
        Map<String, String> original = originalApprovers(bill, node);
        if (original.isEmpty() || !original.containsValue(trim(reviewer))) {
            // 与当日办理那一屏对不上号：复核人须是该格原本落印的名目本人
            return null;
        }
        String role = CertChain.ROLE_SINGLE;
        for (Map.Entry<String, String> e : original.entrySet()) {
            if (trim(reviewer).equals(e.getValue())) {
                role = e.getKey();
            }
        }
        if (existsReview(bill.getId(), bill.getReviewRound(), node, role, reviewer)) {
            // 同名目已复核过，不重复记
            return null;
        }
        writeSign(bill, node, role, trim(reviewer), CertChain.ACTION_REVIEW, comment);

        // 从市局那一格往下数，连着确认到第几格，就还剩几格待复
        int confirmedThrough = -1;
        for (int n = CertChain.NODE_BUREAU; n >= CertChain.NODE_ACCEPT; n--) {
            Map<String, String> orig = originalApprovers(bill, n);
            boolean ok = !orig.isEmpty();
            if (ok) {
                for (Map.Entry<String, String> e : orig.entrySet()) {
                    if (!existsReview(bill.getId(), bill.getReviewRound(), n, e.getKey(), e.getValue())) {
                        ok = false;
                    }
                }
            }
            if (ok) {
                confirmedThrough = n;
            } else {
                break;
            }
        }
        bill.setReviewLevel(confirmedThrough == CertChain.NODE_ACCEPT ? 0 : confirmedThrough);
        this.marrCertBillMapper.updateById(bill);
        return bill;
    }

    @Override
    public List<TMarrCertSign> selectSignTrail(Long billId) {
        return selectSignTrail(billId, false);
    }

    @Override
    public List<TMarrCertSign> selectSignTrail(Long billId, boolean allRounds) {
        TMarrCertBill bill = this.marrCertBillMapper.selectById(billId);
        if (bill == null) {
            return new ArrayList<TMarrCertSign>();
        }
        QueryWrapper<TMarrCertSign> qw = new QueryWrapper<TMarrCertSign>()
                .eq("bill_id", billId)
                .orderByAsc("review_round", "node_no", "create_time", "id");
        if (!allRounds) {
            // 工作台账只摆当前轮：抹掉的旧轮名目不与后落的并排
            qw.eq("review_round", bill.getReviewRound()).eq("del_flag", 0);
        }
        return this.marrCertSignMapper.selectList(qw);
    }

    // ------------------------------------------------------------------

    /** 取出可动笔的件；不予出具已定格、已出手、已删的件一律封栏 */
    private TMarrCertBill loadMutable(Long id) {
        if (id == null) {
            return null;
        }
        TMarrCertBill bill = this.marrCertBillMapper.selectById(id);
        if (bill == null || Integer.valueOf(1).equals(bill.getDelFlag())) {
            return null;
        }
        if (Integer.valueOf(CertChain.STATUS_VETO).equals(bill.getStatus())) {
            return null;
        }
        if (Integer.valueOf(1).equals(bill.getIssueFlag())) {
            return null;
        }
        return bill;
    }

    /** 写下不予出具：件定在此格，各栏封住 */
    private TMarrCertBill veto(TMarrCertBill bill, String role, String approver, String comment) {
        writeSign(bill, bill.getNodeNo(), role, approver, CertChain.ACTION_VETO, comment);
        bill.setStatus(CertChain.STATUS_VETO);
        bill.setFreezeNode(bill.getNodeNo());
        this.marrCertBillMapper.updateById(bill);
        return bill;
    }

    /** 市局打回：旧轮落过的名目尽数抹平（留痕不并排），轮次加一，从第二格重攒 */
    private TMarrCertBill bounce(Long id, String approver, String comment) {
        TMarrCertBill bill = loadMutable(id);
        if (bill == null || bill.getNodeNo() != CertChain.NODE_BUREAU) {
            // 只有第四格市局能把件回退到处里
            return null;
        }
        writeSign(bill, CertChain.NODE_BUREAU, CertChain.ROLE_SINGLE,
                approver, CertChain.ACTION_BOUNCE, comment);

        TMarrCertSign wipe = new TMarrCertSign();
        wipe.setDelFlag(1);
        // 此前各格落过的名目尽数抹平：只抹本轮各格的落印；市局这笔打回留痕保留。
        // 旧轮不并删，靠轮次隔开，抹掉的与后落的不并排摆着。
        this.marrCertSignMapper.update(wipe, new QueryWrapper<TMarrCertSign>()
                .eq("bill_id", bill.getId())
                .eq("review_round", bill.getReviewRound())
                .eq("action", CertChain.ACTION_APPROVE)
                .eq("del_flag", 0));

        bill.setReviewRound(bill.getReviewRound() == null ? 2 : bill.getReviewRound() + 1);
        bill.setNodeNo(CertChain.NODE_CHECK);
        bill.setSignMode(CertChain.modeOf(CertChain.NODE_CHECK));
        bill.setNeedCount(CertChain.needCount(CertChain.NODE_CHECK));
        bill.setSignCount(0);
        bill.setStatus(CertChain.STATUS_RUNNING);
        this.marrCertBillMapper.updateById(bill);
        return bill;
    }

    /** 当前格凑齐手数则挪下一格；第四格凑齐即四格挪满、已核讫 */
    private boolean advanceWhenFull(TMarrCertBill bill) {
        syncFromTrail(bill);
        int need = CertChain.needCount(bill.getNodeNo());
        if (bill.getSignCount() == null || bill.getSignCount() < need) {
            // 只落了一个人，件仍留在这一格，去不到下一格
            return false;
        }
        advance(bill);
        this.marrCertBillMapper.updateById(bill);
        return true;
    }

    private void advance(TMarrCertBill bill) {
        int next = bill.getNodeNo() + 1;
        bill.setNodeNo(next);
        bill.setSignMode(CertChain.modeOf(next));
        bill.setNeedCount(CertChain.needCount(next));
        bill.setSignCount(0);
        bill.setStatus(next == CertChain.NODE_DONE ? CertChain.STATUS_PASS : CertChain.STATUS_RUNNING);
    }

    /** 已落印笔数由件自己在账上一笔笔加出来：双签格按名目去重，余格按笔计 */
    private void syncFromTrail(TMarrCertBill bill) {
        int node = bill.getNodeNo() == null ? 0 : bill.getNodeNo();
        int round = bill.getReviewRound() == null ? 1 : bill.getReviewRound();
        int count = 0;
        if (node >= CertChain.NODE_ACCEPT && node <= CertChain.NODE_BUREAU) {
            List<TMarrCertSign> rows = activeRows(bill.getId(), round, node);
            if (node == CertChain.NODE_DOUBLE) {
                Set<String> roles = new HashSet<String>();
                for (TMarrCertSign r : rows) {
                    if (CertChain.ACTION_APPROVE == action(r)) {
                        roles.add(r.getSignRole());
                    }
                }
                count = roles.size();
            } else {
                for (TMarrCertSign r : rows) {
                    if (CertChain.ACTION_APPROVE == action(r)) {
                        count++;
                    }
                }
            }
        }
        bill.setSignCount(count);
        if (node <= CertChain.NODE_BUREAU) {
            bill.setSignMode(CertChain.modeOf(node));
            bill.setNeedCount(CertChain.needCount(node));
        }
    }

    private List<TMarrCertSign> activeRows(Long billId, int round, int node) {
        return this.marrCertSignMapper.selectList(new QueryWrapper<TMarrCertSign>()
                .eq("bill_id", billId)
                .eq("review_round", round)
                .eq("node_no", node)
                .eq("del_flag", 0)
                .orderByAsc("create_time", "id"));
    }

    private boolean existsApprove(Long billId, int round, int node, String role, String approver) {
        QueryWrapper<TMarrCertSign> qw = new QueryWrapper<TMarrCertSign>()
                .eq("bill_id", billId)
                .eq("review_round", round)
                .eq("node_no", node)
                .eq("action", CertChain.ACTION_APPROVE)
                .eq("del_flag", 0);
        if (role != null) {
            qw.eq("sign_role", role);
        }
        if (approver != null) {
            qw.eq("approver", approver);
        }
        return this.marrCertSignMapper.selectCount(qw) > 0;
    }

    private boolean existsReview(Long billId, int round, int node, String role, String reviewer) {
        return this.marrCertSignMapper.selectCount(new QueryWrapper<TMarrCertSign>()
                .eq("bill_id", billId)
                .eq("review_round", round)
                .eq("node_no", node)
                .eq("sign_role", role)
                .eq("approver", reviewer)
                .eq("action", CertChain.ACTION_REVIEW)
                .eq("del_flag", 0)) > 0;
    }

    /** 该格本轮各名目的原落印人（双签格两笔，余格一笔） */
    private Map<String, String> originalApprovers(TMarrCertBill bill, int node) {
        Map<String, String> map = new HashMap<String, String>();
        for (TMarrCertSign r : activeRows(bill.getId(), bill.getReviewRound(), node)) {
            if (CertChain.ACTION_APPROVE == action(r) && r.getApprover() != null) {
                map.put(r.getSignRole(), r.getApprover());
            }
        }
        return map;
    }

    private void writeSign(TMarrCertBill bill, int node, String role,
                           String approver, int action, String comment) {
        TMarrCertSign sign = new TMarrCertSign();
        sign.setId(IdWorker.getId());
        sign.setBillId(bill.getId());
        sign.setReviewRound(bill.getReviewRound() == null ? 1 : bill.getReviewRound());
        sign.setNodeNo(node);
        sign.setSignRole(role);
        sign.setApprover(approver);
        sign.setAction(action);
        sign.setComment(comment);
        sign.setDelFlag(0);
        this.marrCertSignMapper.insert(sign);
    }

    private TMarrVolDoc findVolume(String siteNo) {
        if (!present(siteNo)) {
            return null;
        }
        List<TMarrVolDoc> list = this.marrVolDocMapper.selectList(new QueryWrapper<TMarrVolDoc>()
                .eq("site_no", trim(siteNo))
                .eq("del_flag", 0)
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 核准结论直接转对外证明文本：姓名、档案字号、事由、批准文号同一张单子带出 */
    private String buildCertText(TMarrCertBill bill) {
        String reasonText = CertChain.REASONS.get(trim(bill.getReasonCode()));
        StringBuilder sb = new StringBuilder(256);
        sb.append("婚姻登记记录证明\r\n");
        sb.append("批准文号：").append(bill.getCertNo()).append("\r\n");
        sb.append("兹有申请人 ").append(bill.getApplicantName())
                .append("（证件号码：").append(bill.getApplicantIdNo()).append("），")
                .append("因").append(reasonText).append("需要，申请查阅：")
                .append(bill.getQueryTarget()).append("。\r\n");
        sb.append("经逐级核准，我处档案字号 ").append(bill.getVolumeNo())
                .append(" 号卷宗档案记载与申请事项相符，现出具婚姻登记记录证明如下：\r\n");
        sb.append(bill.getDraftText()).append("\r\n");
        sb.append("出证事由：").append(reasonText).append("。\r\n");
        sb.append("核准单号：").append(bill.getBillNo()).append("\r\n");
        sb.append("（婚姻登记机关盖章）");
        return sb.toString();
    }

    private String buildBillNo(long id) {
        String tail = String.format("%06d", id % 1000000L);
        return "HZ" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + tail;
    }

    private static int action(TMarrCertSign r) {
        return r.getAction() == null ? 0 : r.getAction().intValue();
    }

    private static boolean present(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
