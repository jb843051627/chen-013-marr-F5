import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TMarrCertBillMapper;
import com.fc.v2.mapper.auto.TMarrCertSignMapper;
import com.fc.v2.mapper.auto.TMarrVolDocMapper;
import com.fc.v2.model.auto.TMarrCertBill;
import com.fc.v2.model.auto.TMarrCertSign;
import com.fc.v2.model.auto.TMarrVolDoc;
import com.fc.v2.model.custom.CertAcceptVo;
import com.fc.v2.model.custom.CertChain;
import com.fc.v2.service.impl.TMarrCertBillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 逐级核准规矩的业务层验证（不启 Spring、不连库，mapper 以内存账模拟）。
 *
 * @author fuce
 * @date 2026-09-28
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TMarrCertBillServiceTest {

    @Mock
    private TMarrCertBillMapper billMapper;
    @Mock
    private TMarrCertSignMapper signMapper;
    @Mock
    private TMarrVolDocMapper volMapper;

    @InjectMocks
    private TMarrCertBillServiceImpl service;

    private final Map<Long, TMarrCertBill> bills = new LinkedHashMap<Long, TMarrCertBill>();
    private final List<TMarrCertSign> signs = new ArrayList<TMarrCertSign>();
    private final List<TMarrVolDoc> vols = new ArrayList<TMarrVolDoc>();

    /** 承办人（受理岗，无 shiro 时取登录名兜底为 Task） */
    private static final String ACCEPTOR = "Task";
    private static final String CHECKER = "c2";
    private static final String JIA = "j1";
    private static final String YI = "y1";
    private static final String BUREAU = "b3";

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(service, "marrCertBillMapper", billMapper);
        ReflectionTestUtils.setField(service, "marrCertSignMapper", signMapper);
        ReflectionTestUtils.setField(service, "marrVolDocMapper", volMapper);

        TMarrVolDoc mh00 = new TMarrVolDoc();
        mh00.setId(0L);
        mh00.setSiteNo("MH00");
        mh00.setStatus(0);
        mh00.setDelFlag(0);
        vols.add(mh00);
        TMarrVolDoc mh01 = new TMarrVolDoc();
        mh01.setId(1L);
        mh01.setSiteNo("MH01");
        mh01.setStatus(1);
        mh01.setDelFlag(0);
        vols.add(mh01);

        when(billMapper.selectById(any())).thenAnswer(inv -> bills.get(inv.getArgument(0)));
        when(billMapper.insert(any())).thenAnswer(inv -> {
            TMarrCertBill b = inv.getArgument(0);
            bills.put(b.getId(), b);
            return 1;
        });
        when(billMapper.updateById(any())).thenAnswer(inv -> {
            TMarrCertBill b = inv.getArgument(0);
            bills.put(b.getId(), b);
            return 1;
        });
        when(billMapper.selectList(any())).thenAnswer(inv -> {
            Map<String, Object> f = filters(inv.getArgument(0));
            List<TMarrCertBill> r = new ArrayList<TMarrCertBill>();
            for (TMarrCertBill b : bills.values()) {
                if (match(b.getApplicantIdNo(), f.get("applicant_id_no"))
                        && matchNum(b.getIssueFlag(), f.get("issue_flag"))
                        && matchNum(b.getDelFlag(), f.get("del_flag"))
                        && !b.getId().equals(f.get("id__ne"))) {
                    r.add(b);
                }
            }
            return r;
        });
        when(billMapper.selectCount(any())).thenAnswer(inv -> {
            Map<String, Object> f = filters(inv.getArgument(0));
            int n = 0;
            for (TMarrCertBill b : bills.values()) {
                if (match(b.getApplicantIdNo(), f.get("applicant_id_no"))
                        && matchNum(b.getIssueFlag(), f.get("issue_flag"))
                        && matchNum(b.getDelFlag(), f.get("del_flag"))
                        && !b.getId().equals(f.get("id__ne"))) {
                    n++;
                }
            }
            return n;
        });

        when(signMapper.insert(any())).thenAnswer(inv -> {
            signs.add(inv.getArgument(0));
            return 1;
        });
        when(signMapper.selectList(any())).thenAnswer(inv -> {
            final Map<String, Object> f = filters(inv.getArgument(0));
            List<TMarrCertSign> r = new ArrayList<TMarrCertSign>();
            for (TMarrCertSign s : signs) {
                if (matchNum(s.getBillId(), f.get("bill_id"))
                        && matchNum(s.getReviewRound(), f.get("review_round"))
                        && matchNum(s.getNodeNo(), f.get("node_no"))
                        && matchNum(s.getDelFlag(), f.get("del_flag"))
                        && matchNum(s.getAction(), f.get("action"))
                        && match(s.getSignRole(), f.get("sign_role"))
                        && match(s.getApprover(), f.get("approver"))) {
                    r.add(s);
                }
            }
            r.sort(Comparator.comparing((TMarrCertSign s) -> s.getReviewRound())
                    .thenComparing(TMarrCertSign::getNodeNo)
                    .thenComparing(TMarrCertSign::getId));
            return r;
        });
        when(signMapper.selectCount(any())).thenAnswer(inv -> {
            Map<String, Object> f = filters(inv.getArgument(0));
            int n = 0;
            for (TMarrCertSign s : signs) {
                if (matchNum(s.getBillId(), f.get("bill_id"))
                        && matchNum(s.getReviewRound(), f.get("review_round"))
                        && matchNum(s.getNodeNo(), f.get("node_no"))
                        && matchNum(s.getDelFlag(), f.get("del_flag"))
                        && matchNum(s.getAction(), f.get("action"))
                        && match(s.getSignRole(), f.get("sign_role"))
                        && match(s.getApprover(), f.get("approver"))) {
                    n++;
                }
            }
            return n;
        });
        when(signMapper.update(any(), any())).thenAnswer(inv -> {
            TMarrCertSign patch = inv.getArgument(0);
            Map<String, Object> f = filters(inv.getArgument(1));
            int n = 0;
            for (TMarrCertSign s : signs) {
                if (matchNum(s.getBillId(), f.get("bill_id"))
                        && matchNum(s.getReviewRound(), f.get("review_round"))
                        && matchNum(s.getNodeNo(), f.get("node_no"))
                        && matchNum(s.getDelFlag(), f.get("del_flag"))
                        && matchNum(s.getAction(), f.get("action"))) {
                    s.setDelFlag(patch.getDelFlag());
                    n++;
                }
            }
            return n;
        });

        when(volMapper.selectList(any())).thenAnswer(inv -> {
            Map<String, Object> f = filters(inv.getArgument(0));
            List<TMarrVolDoc> r = new ArrayList<TMarrVolDoc>();
            for (TMarrVolDoc v : vols) {
                if (match(v.getSiteNo(), f.get("site_no")) && matchNum(v.getDelFlag(), f.get("del_flag"))) {
                    r.add(v);
                }
            }
            return r;
        });
    }

    private CertAcceptVo baseVo() {
        CertAcceptVo vo = new CertAcceptVo();
        vo.setBillNo("HZ-0001");
        vo.setApplicantName("张三");
        vo.setApplicantIdNo("ID-ZS-1");
        vo.setApplicantType("1");
        vo.setCredentialNo("SFZ-1");
        vo.setQueryTarget("2016年结婚登记档");
        vo.setVolumeNo("MH00");
        vo.setReasonCode("TWN");
        vo.setDraftText("张三与李四于2016年5月1日在本处登记结婚。");
        return vo;
    }

    // ---------- 第一格：受理 ----------

    @Test
    public void acceptHappyThenSitsAtCheckNode() {
        TMarrCertBill b = service.accept(baseVo());
        assertNotNull(b);
        assertEquals(CertChain.NODE_CHECK, b.getNodeNo().intValue());
        assertEquals(CertChain.STATUS_RUNNING, b.getStatus().intValue());
        assertEquals(1, b.getReviewRound().intValue());
        assertEquals(0, b.getSignCount().intValue());
        assertEquals(1, service.selectSignTrail(b.getId()).size());
    }

    @Test
    public void acceptRejectsWhenAnyOfFourMissing() {
        CertAcceptVo vo = baseVo();
        vo.setDraftText(" ");
        assertNull(service.accept(vo));
        vo = baseVo();
        vo.setVolumeNo(null);
        assertNull(service.accept(vo));
    }

    @Test
    public void acceptRejectsUnknownIdentityOrMissingCredential() {
        CertAcceptVo vo = baseVo();
        vo.setApplicantType("9");
        assertNull(service.accept(vo));
        vo = baseVo();
        vo.setCredentialNo(null);
        assertNull(service.accept(vo));
    }

    @Test
    public void acceptRejectsReasonOutOfScope() {
        CertAcceptVo vo = baseVo();
        vo.setReasonCode("BANK_LOAN");
        assertNull(service.accept(vo), "不在民政部通知清单内的事由当场截住");
    }

    @Test
    public void acceptRejectsSealedOrUnknownVolume() {
        CertAcceptVo vo = baseVo();
        vo.setVolumeNo("MH01");
        assertNull(service.accept(vo), "已封卷卷宗不挂新单");
        vo = baseVo();
        vo.setVolumeNo("NO-SUCH");
        assertNull(service.accept(vo));
    }

    // ---------- 第二、三、四格 ----------

    @Test
    public void fullChainMovesFourNodesInOrder() {
        TMarrCertBill b = accepted();
        assertEquals(CertChain.NODE_CHECK, b.getNodeNo().intValue());

        // 第三格的门没到，双签推不开
        assertNull(service.approve(b.getId(), JIA, CertChain.ROLE_SCOPE, "越格签"));

        b = service.approve(b.getId(), CHECKER, "编号一致");
        assertEquals(CertChain.NODE_DOUBLE, b.getNodeNo().intValue());

        // 分管甲只落一笔：件仍留在第三格，1/2
        b = service.approve(b.getId(), JIA, CertChain.ROLE_SCOPE, "事由在册");
        assertEquals(CertChain.NODE_DOUBLE, b.getNodeNo().intValue());
        assertEquals(1, b.getSignCount().intValue());
        assertEquals(2, b.getNeedCount().intValue());

        // 甲替乙签另一名目：当没签
        assertNull(service.approve(b.getId(), JIA, CertChain.ROLE_TEXT, "代签"));
        // 甲同名目再签：不成
        assertNull(service.approve(b.getId(), JIA, CertChain.ROLE_SCOPE, "重复"));

        b = service.approve(b.getId(), YI, CertChain.ROLE_TEXT, "记载与拟出文本合拢");
        assertEquals(CertChain.NODE_BUREAU, b.getNodeNo().intValue());

        b = service.approve(b.getId(), BUREAU, "备案");
        assertEquals(CertChain.NODE_DONE, b.getNodeNo().intValue());
        assertEquals(CertChain.STATUS_PASS, b.getStatus().intValue());
    }

    @Test
    public void twoDoubleSignsAreTwoTrailRowsEvenWithoutNames() {
        TMarrCertBill b = throughDouble();
        List<TMarrCertSign> round1 = service.selectSignTrail(b.getId());
        long doubleSigns = round1.stream()
                .filter(s -> s.getNodeNo() == CertChain.NODE_DOUBLE && s.getAction() == CertChain.ACTION_APPROVE)
                .count();
        assertEquals(2L, doubleSigns, "同一分钟两人各签也是两笔，绝不并笔");
    }

    // ---------- 不予出具：定格封栏 ----------

    @Test
    public void vetoFreezesBillAndNoFurtherStampAllowed() {
        TMarrCertBill b = accepted();
        b = service.approve(b.getId(), CHECKER, "");
        b = service.reject(b.getId(), JIA, CertChain.ROLE_SCOPE, "事由不在范围");
        assertEquals(CertChain.STATUS_VETO, b.getStatus().intValue());
        assertEquals(CertChain.NODE_DOUBLE, b.getFreezeNode().intValue());

        // 事后谁也不能再补一枚，乙来签也进不去
        assertNull(service.approve(b.getId(), YI, CertChain.ROLE_TEXT, "补签"));
        assertNull(service.approve(b.getId(), BUREAU, "补备案"));
        assertNull(service.issue(b.getId(), "WZ-1", BUREAU, ""));
    }

    // ---------- 市局打回：抹平、从第二格重攒 ----------

    @Test
    public void bureauBounceWipesPriorNamesAndRestartsAtCheck() {
        TMarrCertBill b = sittingAtBureau();
        int signsBefore = service.selectSignTrail(b.getId(), true).size();

        b = service.rollback(b.getId(), BUREAU, "拟出文本表述不合规范");
        assertNotNull(b);
        assertEquals(CertChain.NODE_CHECK, b.getNodeNo().intValue(), "从第二格那一阶重新攒");
        assertEquals(2, b.getReviewRound().intValue());
        assertEquals(CertChain.STATUS_RUNNING, b.getStatus().intValue());
        assertEquals(0, b.getSignCount().intValue());

        // 工作台账不摆旧轮：抹掉的与后落的不并排
        assertTrue(service.selectSignTrail(b.getId()).isEmpty());
        // 全轮留痕：旧账还在（置抹），且多了打回一笔
        List<TMarrCertSign> all = service.selectSignTrail(b.getId(), true);
        assertEquals(signsBefore + 1, all.size());
        long wiped = all.stream().filter(s -> s.getReviewRound() == 1 && s.getDelFlag() == 1).count();
        assertEquals(4L, wiped, "受理/核对/两双眼共四笔落印尽数抹平");

        // 重攒一遍：四格仍能挪满
        service.approve(b.getId(), CHECKER, "重核编号");
        service.approve(b.getId(), JIA, CertChain.ROLE_SCOPE, "重核事由");
        service.approve(b.getId(), YI, CertChain.ROLE_TEXT, "重核文本");
        b = service.approve(b.getId(), BUREAU, "重新备案");
        assertEquals(CertChain.NODE_DONE, b.getNodeNo().intValue());
        assertEquals(CertChain.STATUS_PASS, b.getStatus().intValue());
    }

    @Test
    public void rollbackOnlyWorksAtBureau() {
        TMarrCertBill b = accepted();
        assertNull(service.rollback(b.getId(), BUREAU, "没到市局打回不成"));
        // 既有两参签名仍可用，等价市局岗位打回
        b = sittingAtBureau();
        assertNotNull(service.rollback(b.getId(), "表述不规范"));
    }

    // ---------- 出证：结论转文本、一人一条 ----------

    @Test
    public void issueConvertsBillToCertTextWithSameSourceFields() {
        TMarrCertBill b = throughDone();
        TMarrCertBill issued = service.issue(b.getId(), "江婚证字2026第001号", BUREAU, "");
        assertNotNull(issued);
        assertEquals(1, issued.getIssueFlag().intValue());
        String t = issued.getCertText();
        assertTrue(t.contains("张三"), t);
        assertTrue(t.contains("MH00"), t);
        assertTrue(t.contains("涉台公证事项"), t);
        assertTrue(t.contains("江婚证字2026第001号"), t);
        assertTrue(t.contains("张三与李四于2016年5月1日在本处登记结婚。"), t);
        assertEquals(CertChain.NODE_DONE, issued.getReviewLevel().intValue());
        // 出手后各格封住，不能再补签
        assertNull(service.approve(issued.getId(), CHECKER, ""));
    }

    @Test
    public void issueBlockedBeforeFourNodesDone() {
        TMarrCertBill b = accepted();
        assertNull(service.issue(b.getId(), "WZ-1", BUREAU, ""));
    }

    @Test
    public void duplicateIssueForSameApplicantBlocked() {
        TMarrCertBill first = throughDone();
        assertNotNull(service.issue(first.getId(), "WZ-1", BUREAU, ""));

        // 同一申请人另起一份新件，四格再挪满，第二条重复抄发仍拦住
        CertAcceptVo vo = baseVo();
        vo.setBillNo("HZ-0002");
        TMarrCertBill second = service.accept(vo);
        pushDone(second.getId());
        assertNull(service.issue(second.getId(), "WZ-2", BUREAU, ""), "一位申请人只配一条文本");
    }

    // ---------- 出手后倒序复核 ----------

    @Test
    public void reverseReviewMustMatchOriginalApproversAndOrder() {
        TMarrCertBill b = issued();

        // 与当日办理那一屏对不上号：非原落印人复不了
        assertNull(service.review(b.getId(), "stranger", ""));
        // 次序倒不得：还没复市局，受理人先复不成
        assertNull(service.review(b.getId(), ACCEPTOR, ""));

        b = service.review(b.getId(), BUREAU, "市局档对得上");
        assertEquals(3, b.getReviewLevel().intValue());

        // 双签格两双眼各复各的，缺一眼这格不算过
        b = service.review(b.getId(), JIA, "事由栏对得上");
        assertEquals(3, b.getReviewLevel().intValue(), "乙眼未复，仍停在双签格");
        b = service.review(b.getId(), YI, "文本栏对得上");
        assertEquals(2, b.getReviewLevel().intValue());

        b = service.review(b.getId(), CHECKER, "编号比对对得上");
        assertEquals(1, b.getReviewLevel().intValue());

        // 同人不重复记一笔
        assertNull(service.review(b.getId(), BUREAU, ""));

        b = service.review(b.getId(), ACCEPTOR, "受理四样对得上");
        assertEquals(0, b.getReviewLevel().intValue(), "一级一级倒回复核回来，全对上号");
    }

    // ---------- helpers ----------

    private TMarrCertBill accepted() {
        TMarrCertBill b = service.accept(baseVo());
        assertNotNull(b);
        return b;
    }

    private TMarrCertBill throughDouble() {
        TMarrCertBill b = accepted();
        b = service.approve(b.getId(), CHECKER, "");
        b = service.approve(b.getId(), JIA, CertChain.ROLE_SCOPE, "");
        b = service.approve(b.getId(), YI, CertChain.ROLE_TEXT, "");
        return b;
    }

    /** 双签两双眼点齐，件压在第四格市局备案、尚未落印 */
    private TMarrCertBill sittingAtBureau() {
        return throughDouble();
    }

    private TMarrCertBill throughBureau() {
        return service.approve(throughDouble().getId(), BUREAU, "");
    }

    private TMarrCertBill throughDone() {
        return throughBureau();
    }

    private TMarrCertBill issued() {
        return service.issue(throughDone().getId(), "WZ-1", BUREAU, "");
    }

    private void pushDone(Long id) {
        service.approve(id, CHECKER, "");
        service.approve(id, JIA, CertChain.ROLE_SCOPE, "");
        service.approve(id, YI, CertChain.ROLE_TEXT, "");
        service.approve(id, BUREAU, "");
    }

    // ---------- 极简 Wrapper 条件解析（供内存账匹配） ----------

    private static final Pattern COND =
            Pattern.compile("(\\w+)\\s*(=|<>)\\s*#\\{\\s*ew\\.paramNameValuePairs\\.(\\w+)\\s*\\}");

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> filters(Object wrapper) {
        AbstractWrapper w = (AbstractWrapper) wrapper;
        return parse(w.getSqlSegment(), w.getParamNameValuePairs());
    }

    private static Map<String, Object> parse(String segment, Map<String, Object> params) {
        Map<String, Object> f = new LinkedHashMap<String, Object>();
        if (segment == null) {
            return f;
        }
        Matcher m = COND.matcher(segment);
        while (m.find()) {
            String col = m.group(1);
            Object val = params.get(m.group(3));
            f.put("<>".equals(m.group(2)) ? col + "__ne" : col, val);
        }
        return f;
    }

    private static boolean match(Object actual, Object expected) {
        return expected == null || expected.equals(actual);
    }

    private static boolean matchNum(Number actual, Object expected) {
        if (expected == null) {
            return true;
        }
        return actual != null && actual.toString().equals(expected.toString());
    }
}
