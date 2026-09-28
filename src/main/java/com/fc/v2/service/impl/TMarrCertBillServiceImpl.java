package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMarrCertBillMapper;
import com.fc.v2.mapper.auto.TMarrCertSignMapper;
import com.fc.v2.mapper.auto.TMarrCertTextMapper;
import com.fc.v2.mapper.auto.TMarrVolDocMapper;
import com.fc.v2.model.auto.TMarrCertBill;
import com.fc.v2.model.auto.TMarrCertSign;
import com.fc.v2.model.auto.TMarrCertText;
import com.fc.v2.model.auto.TMarrVolDoc;
import com.fc.v2.service.ITMarrCertBillService;

/**
 * 婚姻登记记录证明逐级核准单 Service业务层处理（approval-chain 形状：四格逐级签批）
 *
 * 规矩都收在这一层的签字方法里：
 * 0受理岗 → 1档案核对岗 → 2分管核准岗（事由、内容两双眼各签各的）→ 3报市局备案，次序颠倒不得；
 * 件压在哪一格由有效落印账一笔一笔算出来回写，页面不能凭空摆一格，签成签不成只看方法给回的结论。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMarrCertBillServiceImpl implements ITMarrCertBillService {

    /** 四格：受理岗 */
    private static final int NODE_RECV = 0;
    /** 四格：档案核对岗 */
    private static final int NODE_CHECK = 1;
    /** 四格：分管核准岗（两双眼） */
    private static final int NODE_APPROVE = 2;
    /** 四格：报市局备案 */
    private static final int NODE_FILE = 3;
    /** 四格挪满的内部读数（账面上 node_no 仍停在 3） */
    private static final int NODE_DONE = 4;

    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_BACK = 2;
    private static final int STATUS_VETO = 3;

    private static final int FLAG_NORMAL = 0;
    private static final int FLAG_VOID = 1;

    /** 名目：受理 */
    private static final String ROLE_RECV = "RECV";
    /** 名目：核对 */
    private static final String ROLE_CHECK = "CHECK";
    /** 名目：分管—事由核准（看事由落没落在可出证范围里） */
    private static final String ROLE_PURPOSE = "PURPOSE";
    /** 名目：分管—内容核准（看档案记载与拟出文本合不合得拢） */
    private static final String ROLE_CONTENT = "CONTENT";
    /** 名目：市局备案 */
    private static final String ROLE_FILE = "FILING";
    /** 名目：不予出具（终局，不计入任何一格的凑手数） */
    private static final String ROLE_VETO = "VETO";

    /** 现行利用办法按身份把来查档的人分类，各拿各的凭证；身份不在册、凭证字号空缺，受理即截住 */
    private static final Set<String> APPLICANT_KINDS = new HashSet<String>(Arrays.asList(
            "本人", "监护人", "受托代理人", "司法机关", "公证机构"));

    /** 民政部前年通知收口后的可出证事由；不在范围内的当场截住，不予落印 */
    private static final Set<String> ALLOWED_PURPOSES = new HashSet<String>(Arrays.asList(
            "结婚登记需要", "离婚登记需要", "司法诉讼需要", "公证事务需要"));

    private static final Comparator<TMarrCertSign> TRAIL_ORDER = new Comparator<TMarrCertSign>() {
        @Override
        public int compare(TMarrCertSign a, TMarrCertSign b) {
            int byNode = b.getNodeNo() - a.getNodeNo();
            if (byNode != 0) {
                return byNode;
            }
            Date ta = a.getCreateTime();
            Date tb = b.getCreateTime();
            int byTime = tb == null ? (ta == null ? 0 : -1) : (ta == null ? 1 : tb.compareTo(ta));
            if (byTime != 0) {
                return byTime;
            }
            // 同一分钟两人各签是两笔：按主键倒序保确定性，绝不并成一笔
            Long ia = a.getId();
            Long ib = b.getId();
            return ib == null ? (ia == null ? 0 : -1) : (ia == null ? 1 : ib.compareTo(ia));
        }
    };

    @javax.annotation.Resource
    private TMarrCertBillMapper marrCertBillMapper;

    @javax.annotation.Resource
    private TMarrCertSignMapper marrCertSignMapper;

    @javax.annotation.Resource
    private TMarrCertTextMapper marrCertTextMapper;

    @javax.annotation.Resource
    private TMarrVolDocMapper marrVolDocMapper;

    @Override
    public TMarrCertBill selectTMarrCertBillById(Long id) {
        return this.marrCertBillMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill approve(Long id, String approver, String comment) {
        // 旧式三参签名不动：它说不出第三格是哪只眼，故在 0/1/3 格代行该格唯一名目，
        // 到第三格（两双眼必须本人各签各的名目）一律拒收，请走同名重载。
        TMarrCertBill bill = this.marrCertBillMapper.selectById(id);
        if (bill == null) {
            return null;
        }
        List<TMarrCertSign> signs = validSigns(id);
        int node = currentNode(signs);
        String role;
        if (node == NODE_RECV) {
            role = ROLE_RECV;
        } else if (node == NODE_CHECK) {
            role = ROLE_CHECK;
        } else if (node == NODE_FILE) {
            role = ROLE_FILE;
        } else {
            return null;
        }
        return stamp(bill, signs, approver, node, role, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill approve(Long id, String approver, Integer nodeNo, String roleCode, String comment) {
        if (nodeNo == null || roleCode == null) {
            return null;
        }
        TMarrCertBill bill = this.marrCertBillMapper.selectById(id);
        if (bill == null) {
            return null;
        }
        return stamp(bill, validSigns(id), approver, nodeNo.intValue(), roleCode.trim(), comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill reject(Long id, String approver, String comment) {
        TMarrCertBill bill = this.marrCertBillMapper.selectById(id);
        if (bill == null || !isRunning(bill) || isBlank(approver)) {
            return null;
        }
        List<TMarrCertSign> signs = validSigns(id);
        int node = currentNode(signs);
        if (node >= NODE_DONE) {
            return null;
        }
        // 哪一格写下不予出具，这份件就定在此处：落一笔 VETO 留账，件封死，事后谁也补不了一枚
        TMarrCertSign veto = newSign(bill.getId(), node, ROLE_VETO, approver.trim(), comment);
        this.marrCertSignMapper.insert(veto);
        bill.setStatus(Integer.valueOf(STATUS_VETO));
        bill.setRemark(comment);
        this.marrCertBillMapper.updateById(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertBill rollback(Long id, String comment) {
        TMarrCertBill bill = this.marrCertBillMapper.selectById(id);
        if (bill == null || bill.getDelFlag() != null && bill.getDelFlag().intValue() != FLAG_NORMAL) {
            return null;
        }
        // 已写下不予出具的件定在原处终局封死，退回也不能让它还魂
        if (bill.getStatus() != null && bill.getStatus().intValue() == STATUS_VETO) {
            return null;
        }
        List<TMarrCertSign> signs = validSigns(id);
        int node = currentNode(signs);
        // 件到了市局这一格的桌上、备案印尚未落时，市局才退得回；
        // 印一落定（批准文号落定、四格挪满）各栏封住，要改只能另起一份新件
        if (node != NODE_FILE) {
            return null;
        }
        // 此前各格落过的名目尽数抹平：逐笔置 void 原样留痕，抹掉的不与后落的并排摆作有效名目。
        // 受理那一笔保留——重新攒从第二格（核对）那一阶起，申请人不必再进一次门。
        List<TMarrCertSign> all = this.marrCertSignMapper.selectList(new QueryWrapper<TMarrCertSign>()
                .eq("bill_id", id)
                .eq("del_flag", FLAG_NORMAL));
        for (TMarrCertSign s : all) {
            if (s.getNodeNo() != null && s.getNodeNo().intValue() >= NODE_CHECK
                    && s.getVoidFlag() != null && s.getVoidFlag().intValue() == FLAG_NORMAL) {
                s.setVoidFlag(Integer.valueOf(FLAG_VOID));
                this.marrCertSignMapper.updateById(s);
            }
        }
        bill.setApproveNo(null);
        bill.setStatus(Integer.valueOf(STATUS_RUNNING));
        bill.setRemark(comment);
        syncStage(bill, validSigns(id));
        this.marrCertBillMapper.updateById(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMarrCertText issueCertText(Long id) {
        TMarrCertBill bill = this.marrCertBillMapper.selectById(id);
        if (bill == null || bill.getStatus() == null || bill.getStatus().intValue() != STATUS_PASS) {
            return null;
        }
        if (currentNode(validSigns(id)) != NODE_DONE) {
            return null;
        }
        String applicant = bill.getApplicantName();
        if (isBlank(applicant)) {
            return null;
        }
        // 一位申请人只配一条文本：同一人第二条重复抄发当场拦住
        Integer exists = this.marrCertTextMapper.selectCount(new QueryWrapper<TMarrCertText>()
                .eq("applicant_name", applicant)
                .eq("del_flag", FLAG_NORMAL));
        if (exists != null && exists.intValue() > 0) {
            return null;
        }
        TMarrCertText text = new TMarrCertText();
        text.setBillId(bill.getId());
        text.setBillNo(bill.getBillNo());
        text.setApplicantName(applicant);
        text.setSiteNo(bill.getVolumeNo());
        text.setPurpose(bill.getPurpose());
        text.setApproveNo(bill.getApproveNo());
        text.setCertText(buildCertText(bill));
        text.setDelFlag(Integer.valueOf(FLAG_NORMAL));
        this.marrCertTextMapper.insert(text);
        return text;
    }

    @Override
    public List<TMarrCertSign> certTrail(Long id) {
        if (id == null || this.marrCertBillMapper.selectById(id) == null) {
            return Collections.emptyList();
        }
        List<TMarrCertSign> trail = new ArrayList<TMarrCertSign>(validSigns(id));
        Collections.sort(trail, TRAIL_ORDER);
        return trail;
    }

    // ---------------------------------------------------------------------
    // 落印本体：门控、校验、记账、回算都在这里
    // ---------------------------------------------------------------------

    private TMarrCertBill stamp(TMarrCertBill bill, List<TMarrCertSign> signs, String approver,
                                int nodeNo, String roleCode, String comment) {
        if (!isRunning(bill) || isBlank(approver)) {
            return null;
        }
        approver = approver.trim();
        // 次序颠倒不得：只认账上算出来的当前格，页面凭空摆一格、点前一格都推不开门
        int current = currentNode(signs);
        if (nodeNo != current || current >= NODE_DONE) {
            return null;
        }
        if (!rolesOf(nodeNo).contains(roleCode)) {
            return null;
        }
        // 同一格同一名目只能落一笔；同一分钟里两人各签也是两笔，但不许同一个人换名目再落
        for (TMarrCertSign s : signs) {
            if (s.getNodeNo() == null || s.getNodeNo().intValue() != nodeNo) {
                continue;
            }
            if (roleCode.equals(s.getRoleCode())) {
                return null;
            }
            if (approver.equals(s.getSigner())) {
                // 代人签的一律当没签：这只眼已经是他，另一只眼替不了
                return null;
            }
        }
        if (!gateAllows(bill, nodeNo, roleCode)) {
            return null;
        }
        TMarrCertSign sign = newSign(bill.getId(), nodeNo, roleCode, approver, comment);
        // 备案落印即落定批准文号，此后各栏封住
        if (nodeNo == NODE_FILE) {
            if (isBlank(comment)) {
                return null;
            }
            bill.setApproveNo(comment.trim());
        }
        this.marrCertSignMapper.insert(sign);
        syncStage(bill, validSigns(bill.getId()));
        this.marrCertBillMapper.updateById(bill);
        return bill;
    }

    /** 各格自己的门槛：受理看四样与身份凭证、核对只认卷宗编号、事由眼看范围、内容眼看字号合垄、备案要文号 */
    private boolean gateAllows(TMarrCertBill bill, int nodeNo, String roleCode) {
        if (nodeNo == NODE_RECV) {
            // 受理四样：申请人是谁、来查哪一档、拟写进证明的话、卷宗字号，缺一样报不进门
            if (isBlank(bill.getApplicantName()) || isBlank(bill.getSiteNo())
                    || isBlank(bill.getDraftText()) || isBlank(bill.getVolumeNo())) {
                return false;
            }
            // 按身份分类各拿各的凭证
            if (bill.getApplicantKind() == null || !APPLICANT_KINDS.contains(bill.getApplicantKind().trim())
                    || isBlank(bill.getCredentialNo())) {
                return false;
            }
            return true;
        }
        if (nodeNo == NODE_CHECK) {
            // 只认编号：按卷宗档案编号去在册卷宗里对点，姓名撞得再像也不作数；已封卷宗不出证
            TMarrVolDoc doc = this.marrVolDocMapper.selectOne(new QueryWrapper<TMarrVolDoc>()
                    .eq("site_no", bill.getSiteNo())
                    .eq("del_flag", FLAG_NORMAL)
                    .last("limit 1"));
            return doc != null && doc.getStatus() != null && doc.getStatus().intValue() == FLAG_NORMAL;
        }
        if (nodeNo == NODE_APPROVE && ROLE_PURPOSE.equals(roleCode)) {
            // 事由须落在民政部通知收口后的可出证范围里，不在范围内当场截住
            return bill.getPurpose() != null && ALLOWED_PURPOSES.contains(bill.getPurpose().trim());
        }
        if (nodeNo == NODE_APPROVE && ROLE_CONTENT.equals(roleCode)) {
            // 档案记载与拟出文本合不合得拢：拟出文本必须把同一卷宗编号带在身上
            return !isBlank(bill.getDraftText()) && bill.getSiteNo() != null
                    && bill.getDraftText().contains(bill.getSiteNo());
        }
        return true;
    }

    /** 当前压在哪一格：由件自己在账上一笔一笔算出来，页面上没有框容人手填 */
    private int currentNode(List<TMarrCertSign> signs) {
        if (!hasRole(signs, NODE_RECV, ROLE_RECV)) {
            return NODE_RECV;
        }
        if (!hasRole(signs, NODE_CHECK, ROLE_CHECK)) {
            return NODE_CHECK;
        }
        if (!hasRole(signs, NODE_APPROVE, ROLE_PURPOSE)
                || !hasRole(signs, NODE_APPROVE, ROLE_CONTENT)) {
            return NODE_APPROVE;
        }
        if (!hasRole(signs, NODE_FILE, ROLE_FILE)) {
            return NODE_FILE;
        }
        return NODE_DONE;
    }

    /** 屏上摆的与账上存的是同一回算出来的数：短一个便算这格没满 */
    private void syncStage(TMarrCertBill bill, List<TMarrCertSign> signs) {
        int current = currentNode(signs);
        int shownNode = Math.min(current, NODE_FILE);
        int need = current == NODE_APPROVE ? 2 : 1;
        int signed = countAtNode(signs, shownNode);
        bill.setNodeNo(Integer.valueOf(shownNode));
        bill.setNeedCount(Integer.valueOf(need));
        bill.setSignCount(Integer.valueOf(signed));
        bill.setSignMode(Integer.valueOf(current == NODE_APPROVE ? MODE_AND : MODE_OR));
        bill.setStatus(Integer.valueOf(current == NODE_DONE ? STATUS_PASS : STATUS_RUNNING));
    }

    private List<TMarrCertSign> validSigns(Long billId) {
        List<TMarrCertSign> all = this.marrCertSignMapper.selectList(new QueryWrapper<TMarrCertSign>()
                .eq("bill_id", billId)
                .eq("void_flag", FLAG_NORMAL)
                .eq("del_flag", FLAG_NORMAL));
        List<TMarrCertSign> valid = new ArrayList<TMarrCertSign>();
        for (TMarrCertSign s : all) {
            if (!ROLE_VETO.equals(s.getRoleCode())) {
                valid.add(s);
            }
        }
        return valid;
    }

    private boolean hasRole(List<TMarrCertSign> signs, int node, String role) {
        for (TMarrCertSign s : signs) {
            if (s.getNodeNo() != null && s.getNodeNo().intValue() == node && role.equals(s.getRoleCode())) {
                return true;
            }
        }
        return false;
    }

    private int countAtNode(List<TMarrCertSign> signs, int node) {
        int n = 0;
        for (TMarrCertSign s : signs) {
            if (s.getNodeNo() != null && s.getNodeNo().intValue() == node) {
                n++;
            }
        }
        return n;
    }

    private Set<String> rolesOf(int nodeNo) {
        if (nodeNo == NODE_RECV) {
            return new HashSet<String>(Arrays.asList(ROLE_RECV));
        }
        if (nodeNo == NODE_CHECK) {
            return new HashSet<String>(Arrays.asList(ROLE_CHECK));
        }
        if (nodeNo == NODE_APPROVE) {
            return new HashSet<String>(Arrays.asList(ROLE_PURPOSE, ROLE_CONTENT));
        }
        if (nodeNo == NODE_FILE) {
            return new HashSet<String>(Arrays.asList(ROLE_FILE));
        }
        return Collections.emptySet();
    }

    private TMarrCertSign newSign(Long billId, int nodeNo, String roleCode, String signer, String comment) {
        TMarrCertSign s = new TMarrCertSign();
        s.setBillId(billId);
        s.setNodeNo(Integer.valueOf(nodeNo));
        s.setRoleCode(roleCode);
        s.setSigner(signer);
        s.setSignComment(comment);
        s.setVoidFlag(Integer.valueOf(FLAG_NORMAL));
        s.setDelFlag(Integer.valueOf(FLAG_NORMAL));
        Date now = new Date();
        s.setCreateBy(signer);
        s.setCreateTime(now);
        s.setUpdateBy(signer);
        s.setUpdateTime(now);
        return s;
    }

    private boolean isRunning(TMarrCertBill bill) {
        if (bill.getDelFlag() != null && bill.getDelFlag().intValue() != FLAG_NORMAL) {
            return false;
        }
        Integer st = bill.getStatus();
        return st == null || st.intValue() == STATUS_RUNNING || st.intValue() == STATUS_BACK;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 结论直接转换为对外证明文本：姓名、档案字号、事由、批准文号同源于这一张单子 */
    private String buildCertText(TMarrCertBill bill) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("婚姻登记记录证明。兹有申请人").append(bill.getApplicantName());
        if (bill.getApplicantKind() != null) {
            sb.append("（").append(bill.getApplicantKind());
            if (!isBlank(bill.getCredentialNo())) {
                sb.append("，凭证字号").append(bill.getCredentialNo());
            }
            sb.append("）");
        }
        sb.append("，因").append(bill.getPurpose()).append("申请查阅婚姻登记档案。");
        sb.append("经查，档案字号").append(bill.getVolumeNo());
        if (!isBlank(bill.getSiteNo())) {
            sb.append("（卷宗档案编号").append(bill.getSiteNo()).append("）");
        }
        sb.append("，记载如下：").append(bill.getDraftText()).append("。");
        sb.append("经逐级核准，准予出具，并报市局备案，批准文号：").append(bill.getApproveNo()).append("。");
        return sb.toString();
    }
}
