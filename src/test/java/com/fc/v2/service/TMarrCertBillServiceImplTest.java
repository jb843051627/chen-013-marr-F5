package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fc.v2.mapper.auto.TMarrCertBillMapper;
import com.fc.v2.mapper.auto.TMarrCertSignMapper;
import com.fc.v2.mapper.auto.TMarrCertTextMapper;
import com.fc.v2.mapper.auto.TMarrVolDocMapper;
import com.fc.v2.model.auto.TMarrCertBill;
import com.fc.v2.model.auto.TMarrCertSign;
import com.fc.v2.model.auto.TMarrCertText;
import com.fc.v2.model.auto.TMarrVolDoc;
import com.fc.v2.service.impl.TMarrCertBillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 逐级核准单业务规矩的内存测试：不起 Spring、不连库，四只 Mapper 全用假账。
 * 故事都对着题面来：四样齐才进门、核对只认编号、两双眼各记各账代签当没签、
 * 市局回退抹账从核对重攒、不予出具终局封死、一人一条文本、倒序复核。
 */
class TMarrCertBillServiceImplTest {

    private TMarrCertBillServiceImpl svc;
    private FakeBillMapper bills;
    private FakeSignMapper signs;
    private FakeTextMapper texts;
    private FakeVolDocMapper docs;

    private static final String RECV = "RECV";
    private static final String CHECK = "CHECK";
    private static final String PURPOSE = "PURPOSE";
    private static final String CONTENT = "CONTENT";
    private static final String FILING = "FILING";

    @BeforeEach
    void setUp() {
        svc = new TMarrCertBillServiceImpl();
        bills = new FakeBillMapper();
        signs = new FakeSignMapper();
        texts = new FakeTextMapper();
        docs = new FakeVolDocMapper();
        inject(svc, "marrCertBillMapper", bills);
        inject(svc, "marrCertSignMapper", signs);
        inject(svc, "marrCertTextMapper", texts);
        inject(svc, "marrVolDocMapper", docs);
        docs.put(seedDoc("MH00", 0));
        docs.put(seedDoc("MH01", 1));
    }

    private TMarrCertBill seedBill() {
        TMarrCertBill b = new TMarrCertBill();
        b.setId(1L);
        b.setBillNo("HZ-2026-001");
        b.setStatus(0);
        b.setDelFlag(0);
        b.setApplicantName("王秀英");
        b.setApplicantKind("本人");
        b.setCredentialNo("SFZ110101199001011234");
        b.setSiteNo("MH00");
        b.setVolumeNo("临川婚字第2026-091号");
        b.setPurpose("结婚登记需要");
        b.setDraftText("MH00 卷宗载明：王秀英与李建国于2020年5月20日在临川办理结婚登记");
        return b;
    }

    private TMarrVolDoc seedDoc(String siteNo, int status) {
        TMarrVolDoc d = new TMarrVolDoc();
        d.setId((long) ("MH00".equals(siteNo) ? 0 : 1));
        d.setSiteNo(siteNo);
        d.setSiteName("卷宗" + siteNo);
        d.setStatus(status);
        d.setDelFlag(0);
        return d;
    }

    private TMarrCertBill fullWalk() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        assertNotNull(svc.approve(b.getId(), "受理员甲", 0, RECV, "材料齐"));
        assertNotNull(svc.approve(b.getId(), "核对员乙", 1, CHECK, "编号对得上"));
        assertNotNull(svc.approve(b.getId(), "分管丙", 2, PURPOSE, "事由在册"));
        assertNotNull(svc.approve(b.getId(), "分管丁", 2, CONTENT, "记载合垄"));
        TMarrCertBill done = svc.approve(b.getId(), "市局戊", 3, FILING, "临民备字[2026]第15号");
        assertNotNull(done);
        return done;
    }

    // ---- 头一格：四样缺一样报不进门；身份分类与凭证各认各的 ----

    @Test
    void recvMissingOneOfFourItemsRejected() {
        TMarrCertBill b = seedBill();
        b.setDraftText(" ");
        bills.put(b);
        assertNull(svc.approve(b.getId(), "受理员甲", 0, RECV, "材料齐"));
        assertEquals(0, signs.store.size());
    }

    @Test
    void recvUnknownApplicantKindOrMissingCredentialRejected() {
        TMarrCertBill b = seedBill();
        b.setCredentialNo(null);
        bills.put(b);
        assertNull(svc.approve(b.getId(), "受理员甲", 0, RECV, null));

        b.setCredentialNo("W123");
        b.setApplicantKind("远房亲戚");
        bills.put(b);
        assertNull(svc.approve(b.getId(), "受理员甲", 0, RECV, null));
    }

    @Test
    void recvStampsAndAdvancesToOne() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        TMarrCertBill r = svc.approve(b.getId(), "受理员甲", 0, RECV, "材料齐");
        assertNotNull(r);
        // 受理这一笔已在账上，件推进到第二格；新格尚未有人落印
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(0, r.getSignCount().intValue());
        assertEquals(1, signsForBill(b.getId()).size());
    }

    // ---- 次序颠倒不得；页面不能凭空摆一格 ----

    @Test
    void cannotSkipStagesOrWrongRole() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        assertNull(svc.approve(b.getId(), "x", 1, CHECK, null));
        assertNull(svc.approve(b.getId(), "x", 3, FILING, "文号"));
        assertNull(svc.approve(b.getId(), "受理员甲", 0, CHECK, null));
        assertNull(svc.approve(b.getId(), "受理员甲", 9, RECV, null));
    }

    // ---- 第二格：只认卷宗档案编号，已封卷对点不过 ----

    @Test
    void checkBySiteNumberOnly() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);

        b.setSiteNo("MH99"); // 姓名撞得再像也不作数：编号根本不在册
        assertNull(svc.approve(b.getId(), "核对员乙", 1, CHECK, null));
        b.setSiteNo("MH01"); // 在册但已封卷
        assertNull(svc.approve(b.getId(), "核对员乙", 1, CHECK, null));

        b.setSiteNo("MH00");
        assertNotNull(svc.approve(b.getId(), "核对员乙", 1, CHECK, null));
    }

    // ---- 第三格：两双眼各记各账；只落一只眼件推不开第四格的门 ----

    @Test
    void thirdStageNeedsTwoDistinctHands() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);
        svc.approve(b.getId(), "核对员乙", 1, CHECK, null);

        assertNotNull(svc.approve(b.getId(), "分管丙", 2, PURPOSE, null));
        // 只落了一个人，件仍留在这一格里，去不到第四格
        assertEquals(2, b.getNodeNo().intValue());
        assertEquals(1, b.getSignCount().intValue());
        assertEquals(2, b.getNeedCount().intValue());
        assertEquals(0, b.getStatus().intValue());
        assertNull(svc.approve(b.getId(), "市局戊", 3, FILING, "文号"));

        // 同一分钟里两人各签也是两笔：补第二只眼才凑齐，件随即推进到第四格
        assertNotNull(svc.approve(b.getId(), "分管丁", 2, CONTENT, null));
        assertEquals(3, b.getNodeNo().intValue());
        assertEquals(0, b.getSignCount().intValue());
        assertEquals(2, signsForBill(b.getId()).stream()
                .filter(s -> s.getNodeNo() == 2).count());
    }

    @Test
    void proxySignatureCountsAsUnsigned() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);
        svc.approve(b.getId(), "核对员乙", 1, CHECK, null);
        svc.approve(b.getId(), "分管丙", 2, PURPOSE, null);
        // 代人签的那一律当没签：同一个人想替另一只眼
        assertNull(svc.approve(b.getId(), "分管丙", 2, CONTENT, null));
        assertEquals(1, b.getSignCount().intValue());
        // 同一个名目也不许落第二笔
        assertNull(svc.approve(b.getId(), "分管丁", 2, PURPOSE, null));
    }

    @Test
    void purposeOutOfRangeBlockedAndContentEyeIndependent() {
        TMarrCertBill b = seedBill();
        b.setPurpose("出境定居需要"); // 不在民政部通知收口后的范围里
        bills.put(b);
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);
        svc.approve(b.getId(), "核对员乙", 1, CHECK, null);
        // 事由眼当场截住
        assertNull(svc.approve(b.getId(), "分管丙", 2, PURPOSE, null));
        // 内容眼各管各的，先落不碍事，但件仍卡在第三格
        assertNotNull(svc.approve(b.getId(), "分管丁", 2, CONTENT, null));
        assertEquals(2, b.getNodeNo().intValue());
    }

    @Test
    void contentEyeRequiresDraftCarryingSiteNumber() {
        TMarrCertBill b = seedBill();
        b.setDraftText("卷宗记载与申请人所述一致"); // 拟出文本没带同一卷宗编号，合不拢
        bills.put(b);
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);
        svc.approve(b.getId(), "核对员乙", 1, CHECK, null);
        assertNull(svc.approve(b.getId(), "分管丁", 2, CONTENT, null));
    }

    // ---- 第四格：备案落印即落批准文号，四格挪满才核讫 ----

    @Test
    void filingRequiresApproveNumberAndPasses() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);
        svc.approve(b.getId(), "核对员乙", 1, CHECK, null);
        svc.approve(b.getId(), "分管丙", 2, PURPOSE, null);
        svc.approve(b.getId(), "分管丁", 2, CONTENT, null);
        assertNull(svc.approve(b.getId(), "市局戊", 3, FILING, "  "));
        TMarrCertBill done = svc.approve(b.getId(), "市局戊", 3, FILING, "临民备字[2026]第15号");
        assertNotNull(done);
        assertEquals(1, done.getStatus().intValue());
        assertEquals(3, done.getNodeNo().intValue());
        assertEquals("临民备字[2026]第15号", done.getApproveNo());
        // 印一落定各栏封住：再没有下一格可推
        assertNull(svc.approve(b.getId(), "市局戊", 3, FILING, "又一文号"));
    }

    // ---- 旧式三参签名不动：在 0/1/3 格代行唯一名目，第三格必须走重载 ----

    @Test
    void legacyThreeArgApproveStaysCompatible() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        assertNotNull(svc.approve(b.getId(), "受理员甲", "齐"));
        assertNotNull(svc.approve(b.getId(), "核对员乙", "对"));
        assertNull(svc.approve(b.getId(), "分管丙", "替不了眼"));
        assertNotNull(svc.approve(b.getId(), "分管丙", 2, PURPOSE, null));
        assertNotNull(svc.approve(b.getId(), "分管丁", 2, CONTENT, null));
        TMarrCertBill done = svc.approve(b.getId(), "市局戊", "临民备字[2026]第15号");
        assertNotNull(done);
        assertEquals(1, done.getStatus().intValue());
    }

    // ---- 哪一格写下不予出具，件定在此处，事后谁也补不了 ----

    @Test
    void vetoFreezesBillForever() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);
        TMarrCertBill vetoed = svc.reject(b.getId(), "核对员乙", "档案记载缺失，不予出具");
        assertNotNull(vetoed);
        assertEquals(3, vetoed.getStatus().intValue());
        assertNull(svc.approve(b.getId(), "核对员乙", 1, CHECK, null));
        assertNull(svc.reject(b.getId(), "谁", "再补一枚也不行"));
        assertNull(svc.rollback(b.getId(), "退回也不能还魂"));
    }

    // ---- 市局回退：件压在市局桌上、备案印未落时可退；名目尽数抹平，从第二格重攒 ----

    @Test
    void cityRollbackWipesStagesAndRestartsFromCheck() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        assertNotNull(svc.approve(b.getId(), "受理员甲", 0, RECV, "材料齐"));
        assertNotNull(svc.approve(b.getId(), "核对员乙", 1, CHECK, "编号对得上"));
        assertNotNull(svc.approve(b.getId(), "分管丙", 2, PURPOSE, "事由在册"));
        assertNotNull(svc.approve(b.getId(), "分管丁", 2, CONTENT, "记载合垄"));
        // 此刻件推到市局桌上、备案印尚未落
        assertEquals(3, b.getNodeNo().intValue());
        assertEquals(4, svc.certTrail(b.getId()).size());

        TMarrCertBill back = svc.rollback(b.getId(), "拟出文本表述不规范");
        assertNotNull(back);
        assertEquals(1, back.getNodeNo().intValue()); // 停在第二格（核对）之前
        assertEquals(0, back.getSignCount().intValue());
        assertEquals(0, back.getStatus().intValue());
        assertNull(back.getApproveNo());

        // 抹掉的留痕但不进有效账：核对与两双眼共 3 笔 void，只剩受理 1 笔有效
        List<TMarrCertSign> all = signs.store;
        long voided = all.stream().filter(s -> s.getVoidFlag() == 1).count();
        assertEquals(3, voided);
        assertEquals(1, svc.certTrail(b.getId()).size());
        assertEquals(RECV, svc.certTrail(b.getId()).get(0).getRoleCode());

        // 受理不必重来，自核对重新攒到备案核讫
        assertNotNull(svc.approve(b.getId(), "核对员己", 1, CHECK, null));
        assertNotNull(svc.approve(b.getId(), "分管庚", 2, PURPOSE, null));
        assertNotNull(svc.approve(b.getId(), "分管辛", 2, CONTENT, null));
        assertNotNull(svc.approve(b.getId(), "市局壬", 3, FILING, "临民备字[2026]第16号"));
        // 有效账 5 笔与抹平的 3 笔不并排摆作有效名目
        assertEquals(5, svc.certTrail(b.getId()).size());
        assertEquals(8, signs.store.size());
    }

    @Test
    void rollbackRejectedBeforeFilingDeskAndAfterSealed() {
        TMarrCertBill b = seedBill();
        b.setId(3L);
        bills.put(b);
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);
        assertNull(svc.rollback(b.getId(), "还没到市局这格"));

        // 印一落定各栏封住：已核讫的件退不回，只能另起一份新件
        TMarrCertBill done = fullWalk();
        assertNull(svc.rollback(done.getId(), "印已落，封死"));
        assertEquals(5, svc.certTrail(done.getId()).size());
        assertEquals("临民备字[2026]第15号", done.getApproveNo());
    }

    // ---- 出证文本：四格挪满才出手，同源带出，一人一条 ----

    @Test
    void issueCertTextCarriesFieldsAndBlocksDuplicateApplicant() {
        TMarrCertBill done = fullWalk();
        TMarrCertText t = svc.issueCertText(done.getId());
        assertNotNull(t);
        assertEquals("王秀英", t.getApplicantName());
        assertEquals("临川婚字第2026-091号", t.getSiteNo());
        assertEquals("结婚登记需要", t.getPurpose());
        assertEquals("临民备字[2026]第15号", t.getApproveNo());
        String body = t.getCertText();
        assertTrue(body.contains("王秀英") && body.contains("临川婚字第2026-091号")
                && body.contains("结婚登记需要") && body.contains("临民备字[2026]第15号"));

        // 同一申请人第二条重复抄发拦住
        assertNull(svc.issueCertText(done.getId()));
        TMarrCertBill second = seedBill();
        second.setId(2L);
        second.setBillNo("HZ-2026-002");
        bills.put(second);
        walkWithoutIssue(second);
        assertNull(svc.issueCertText(second.getId()));
    }

    @Test
    void issueBeforeAllStagesDoneRejected() {
        TMarrCertBill b = seedBill();
        bills.put(b);
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);
        assertNull(svc.issueCertText(b.getId()));
    }

    // ---- 倒序复核：按格倒着一级级对回当日那一屏 ----

    @Test
    void trailIsReverseStageOrder() {
        TMarrCertBill done = fullWalk();
        List<TMarrCertSign> trail = svc.certTrail(done.getId());
        assertEquals(5, trail.size());
        assertEquals(FILING, trail.get(0).getRoleCode());
        assertEquals(CONTENT, trail.get(1).getRoleCode());
        assertEquals(PURPOSE, trail.get(2).getRoleCode());
        assertEquals(CHECK, trail.get(3).getRoleCode());
        assertEquals(RECV, trail.get(4).getRoleCode());
    }

    @Test
    void trailOfUnknownBillIsEmpty() {
        assertTrue(svc.certTrail(999L).isEmpty());
    }

    @Test
    void blankApproverOrMissingBillRejected() {
        assertNull(svc.approve(null, "x", 0, RECV, null));
        TMarrCertBill b = seedBill();
        bills.put(b);
        assertNull(svc.approve(b.getId(), "  ", 0, RECV, null));
        assertNull(svc.reject(b.getId(), "  ", null));
    }

    private void walkWithoutIssue(TMarrCertBill b) {
        svc.approve(b.getId(), "受理员甲", 0, RECV, null);
        svc.approve(b.getId(), "核对员乙", 1, CHECK, null);
        svc.approve(b.getId(), "分管丙", 2, PURPOSE, null);
        svc.approve(b.getId(), "分管丁", 2, CONTENT, null);
        svc.approve(b.getId(), "市局戊", 3, FILING, "临民备字[2026]第17号");
    }

    private List<TMarrCertSign> signsForBill(Long billId) {
        List<TMarrCertSign> r = new ArrayList<TMarrCertSign>();
        for (TMarrCertSign s : signs.store) {
            if (Objects.equals(s.getBillId(), billId) && s.getVoidFlag() == 0) {
                r.add(s);
            }
        }
        return r;
    }

    private static void inject(Object target, String name, Object dep) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, dep);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // 内存假账：从 QueryWrapper 的 SQL 片段里按列名取等值参数
    // ------------------------------------------------------------------

    private static final Pattern COL_EQ = Pattern.compile(
            "([a-z_]+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}");

    private static Object eqVal(Wrapper<?> w, String column) {
        if (!(w instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper)) {
            return null;
        }
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> aw =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) w;
        Matcher m = COL_EQ.matcher(w.getSqlSegment());
        while (m.find()) {
            if (m.group(1).equals(column)) {
                return aw.getParamNameValuePairs().get(m.group(2));
            }
        }
        return null;
    }

    private abstract static class FakeBaseMapper<T> implements BaseMapper<T> {
        @Override public int insert(T entity) { throw unsupported(); }
        @Override public int deleteById(Serializable id) { throw unsupported(); }
        @Override public int deleteByMap(Map<String, Object> map) { throw unsupported(); }
        @Override public int delete(Wrapper<T> wrapper) { throw unsupported(); }
        @Override public int deleteBatchIds(Collection<? extends Serializable> idList) { throw unsupported(); }
        @Override public int updateById(T entity) { throw unsupported(); }
        @Override public int update(T entity, Wrapper<T> wrapper) { throw unsupported(); }
        @Override public T selectById(Serializable id) { throw unsupported(); }
        @Override public List<T> selectBatchIds(Collection<? extends Serializable> idList) { throw unsupported(); }
        @Override public List<T> selectByMap(Map<String, Object> map) { throw unsupported(); }
        @Override public T selectOne(Wrapper<T> wrapper) { throw unsupported(); }
        @Override public Integer selectCount(Wrapper<T> wrapper) { throw unsupported(); }
        @Override public List<T> selectList(Wrapper<T> wrapper) { throw unsupported(); }
        @Override public List<Map<String, Object>> selectMaps(Wrapper<T> wrapper) { throw unsupported(); }
        @Override public List<Object> selectObjs(Wrapper<T> wrapper) { throw unsupported(); }
        @Override public <E extends IPage<T>> E selectPage(E page, Wrapper<T> wrapper) { throw unsupported(); }
        @Override public <E extends IPage<Map<String, Object>>> E selectMapsPage(E page, Wrapper<T> wrapper) {
            throw unsupported();
        }

        UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("假账不伺候");
        }
    }

    private static class FakeBillMapper extends FakeBaseMapper<TMarrCertBill> implements TMarrCertBillMapper {
        final Map<Serializable, TMarrCertBill> map = new LinkedHashMap<Serializable, TMarrCertBill>();
        void put(TMarrCertBill b) { map.put(b.getId(), b); }
        @Override public TMarrCertBill selectById(Serializable id) { return map.get(id); }
        @Override public int updateById(TMarrCertBill b) { map.put(b.getId(), b); return 1; }
    }

    private static class FakeSignMapper extends FakeBaseMapper<TMarrCertSign> implements TMarrCertSignMapper {
        final List<TMarrCertSign> store = new ArrayList<TMarrCertSign>();
        final AtomicLong seq = new AtomicLong(1000);
        @Override public int insert(TMarrCertSign s) {
            s.setId(seq.incrementAndGet());
            store.add(s);
            return 1;
        }
        @Override public int updateById(TMarrCertSign s) { return 1; }
        @Override
        public List<TMarrCertSign> selectList(Wrapper<TMarrCertSign> w) {
            Object billId = eqVal(w, "bill_id");
            Object voidFlag = eqVal(w, "void_flag");
            Object delFlag = eqVal(w, "del_flag");
            List<TMarrCertSign> r = new ArrayList<TMarrCertSign>();
            for (TMarrCertSign s : store) {
                if (billId != null && !Objects.equals(String.valueOf(s.getBillId()), String.valueOf(billId))) {
                    continue;
                }
                if (voidFlag != null && !Objects.equals(s.getVoidFlag(), ((Number) voidFlag).intValue())) {
                    continue;
                }
                if (delFlag != null && !Objects.equals(s.getDelFlag(), ((Number) delFlag).intValue())) {
                    continue;
                }
                r.add(s);
            }
            return r;
        }
    }

    private static class FakeTextMapper extends FakeBaseMapper<TMarrCertText> implements TMarrCertTextMapper {
        final List<TMarrCertText> store = new ArrayList<TMarrCertText>();
        final AtomicLong seq = new AtomicLong(2000);
        @Override public int insert(TMarrCertText t) {
            t.setId(seq.incrementAndGet());
            store.add(t);
            return 1;
        }
        @Override
        public Integer selectCount(Wrapper<TMarrCertText> w) {
            Object name = eqVal(w, "applicant_name");
            Object delFlag = eqVal(w, "del_flag");
            int n = 0;
            for (TMarrCertText t : store) {
                if (name != null && !Objects.equals(t.getApplicantName(), name)) {
                    continue;
                }
                if (delFlag != null && !Objects.equals(t.getDelFlag(), ((Number) delFlag).intValue())) {
                    continue;
                }
                n++;
            }
            return n;
        }
    }

    private static class FakeVolDocMapper extends FakeBaseMapper<TMarrVolDoc> implements TMarrVolDocMapper {
        final Map<Serializable, TMarrVolDoc> map = new LinkedHashMap<Serializable, TMarrVolDoc>();
        void put(TMarrVolDoc d) { map.put(d.getId(), d); }
        @Override public TMarrVolDoc selectById(Serializable id) { return map.get(id); }
        @Override
        public TMarrVolDoc selectOne(Wrapper<TMarrVolDoc> w) {
            Object siteNo = eqVal(w, "site_no");
            Object delFlag = eqVal(w, "del_flag");
            for (TMarrVolDoc d : map.values()) {
                if (siteNo != null && !Objects.equals(d.getSiteNo(), siteNo)) {
                    continue;
                }
                if (delFlag != null && !Objects.equals(d.getDelFlag(), ((Number) delFlag).intValue())) {
                    continue;
                }
                return d;
            }
            return null;
        }
    }
}
