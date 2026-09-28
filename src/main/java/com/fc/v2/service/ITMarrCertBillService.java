package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TMarrCertBill;
import com.fc.v2.model.auto.TMarrCertSign;
import com.fc.v2.model.auto.TMarrCertText;

/**
 * 婚姻登记记录证明逐级核准单 Service接口（approval-chain 形状：四格逐级签批，无增删改查入口）
 *
 * 四格：0受理岗 → 1档案核对岗 → 2分管核准岗（事由、内容两双眼各签各的，谁也替不了谁）→ 3报市局备案。
 * 次序颠倒不得；当前压在哪一格由系统按有效落印账算出，页面不能凭空摆一格。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMarrCertBillService {

    /** 按主键回查单据 */
    TMarrCertBill selectTMarrCertBillById(Long id);

    /**
     * 签批一票（旧式三参签名，保持不动）：默认走受理名目，落印成败以方法给回的结论为准，被拒返回 null。
     */
    TMarrCertBill approve(Long id, String approver, String comment);

    /**
     * 落印（同名重载，新式用法）：在指定格以指定名目本人亲签，代签/跳格/越权/终局补签一律当没签，返回 null。
     *
     * @param id       核准单主键
     * @param approver 落印人本人登录名（控制器强制取登录人，页面传来的代签名字不采信）
     * @param nodeNo   落印之格 0受理 1核对 2分管核准 3备案
     * @param roleCode 本笔名目：RECV 受理 / CHECK 核对 / PURPOSE 事由核准 / CONTENT 内容核准 / FILING 备案
     * @param comment  落印批注（备案名目时为市局批准文号）
     */
    TMarrCertBill approve(Long id, String approver, Integer nodeNo, String roleCode, String comment);

    /**
     * 否决（签名保持不动）：在当前格写下不予出具，件定在此处终局封死，事后谁也不能再补一枚；被拒返回 null。
     */
    TMarrCertBill reject(Long id, String approver, String comment);

    /**
     * 退回（签名保持不动）：件压在市局桌上、备案印尚未落时，市局嫌拟出文本表述不规范把件回退到处里，
     * 此前各格落过的名目尽数抹平，从第二格（核对）那一阶重新攒；抹掉的逐笔置 void 留痕，
     * 不与后落的并排摆作有效名目。备案印一落定各栏封住，退回也不收，要改只能另起一份新件。
     */
    TMarrCertBill rollback(Long id, String comment);

    /**
     * 出证文本：四格挪满后把结论直接转换成对外证明文本，姓名、档案字号、事由、批准文号
     * 一并从同一张单子带出，不由第二名人员再抄；一位申请人只配一条文本，重复抄发返回 null 拦住。
     */
    TMarrCertText issueCertText(Long id);

    /**
     * 倒序复核账：只回有效落印名目，按格从高到低、格内按落印时辰倒序，
     * 供文本出手之后一级一级倒着复核，与当日办理那一屏瞧见的对得上号。
     */
    List<TMarrCertSign> certTrail(Long id);
}
