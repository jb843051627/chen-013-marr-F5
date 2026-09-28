package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TMarrCertBill;
import com.fc.v2.model.auto.TMarrCertSign;
import com.fc.v2.model.custom.CertAcceptVo;

/**
 * 婚姻登记记录证明逐级核准单 Service接口（approval-chain 形状：四格逐级签批，无增删改查入口）
 *
 * 四格：0受理岗 -> 1档案核对岗 -> 2分管双签(事由核/记载文本核) -> 3市局备案，挪满即已核讫。
 * 次序颠倒不得，前一格的人没到齐，件推不开下一格的门。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMarrCertBillService {

    /** 按主键回查单据；格次进度由签账重算，页面拿到的与账上存的是同一回数 */
    TMarrCertBill selectTMarrCertBillById(Long id);

    /** 核准台账只读查询（不开造单、改栏的口子，造单只能走查档申请表受理） */
    List<TMarrCertBill> selectTMarrCertBillList(
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TMarrCertBill> queryWrapper);

    /**
     * 落印核准（既有签名，单签格次使用）：受理/核对/市局备案。
     * 第三格两名分管负责人须带名目的重载，无名目此签不成，返回 null。
     */
    TMarrCertBill approve(Long id, String approver, String comment);

    /** 不予出具（既有签名）：件定在当前格，各栏封住，谁也不能再补一枚 */
    TMarrCertBill reject(Long id, String approver, String comment);

    /** 退回（既有签名）：退回上一环节；在市局备案格即市局打回，抹平此前名目、从第二格重攒 */
    TMarrCertBill rollback(Long id, String comment);

    /**
     * 受理进单：查档申请表四样（申请人/查档指向/拟出文本/卷宗字号）齐、
     * 身份与凭证对得上类、事由落在民政部通知清单内、卷宗编号在册，方报得进门。
     * 不在范围的当场截住，返回 null。
     */
    TMarrCertBill accept(CertAcceptVo vo);

    /**
     * 落印核准（同名重载）：第三格两名分管负责人各带名目，
     * SCOPE 事由合规核、TEXT 档案记载与拟出文本核，两双眼各管各的、互不替签；
     * 同一名目只认本人一笔，重复落印不成。
     */
    TMarrCertBill approve(Long id, String approver, String signRole, String comment);

    /** 不予出具（同名重载）：第三格由落印的该名目负责人写下，定格封栏 */
    TMarrCertBill reject(Long id, String approver, String signRole, String comment);

    /** 市局打回（同名重载）：仅在第四格可用，此前各格落过的名目尽数抹平，
     * 核准轮次加一，从第二格那一阶重新攒；抹掉的与后落的不并排摆着。 */
    TMarrCertBill rollback(Long id, String approver, String comment);

    /**
     * 四格挪满后把核准结论直接转换成对外证明文本出手：
     * 姓名、档案字号、事由、批准文号同一张单子带出，不用第二个人再抄。
     * 同一位申请人已出手过一条文本的，第二条重复抄发当场拦住，返回 null。
     */
    TMarrCertBill issue(Long id, String certNo, String operator, String comment);

    /**
     * 出手后逐级倒着复核回来一格（市局→双签→核对→受理），
     * 与当日办理那一屏对得上号方确认；对不上返回 null，停在原阶。
     */
    TMarrCertBill review(Long id, String reviewer, String comment);

    /** 当前轮次签账（工作台账只摆当前轮，抹平的不与后落的并排），按落账先后 */
    List<TMarrCertSign> selectSignTrail(Long billId);

    /** 全轮次签账（含被打回抹平的旧轮名目，留痕备查），按轮次、格次、落账先后 */
    List<TMarrCertSign> selectSignTrail(Long billId, boolean allRounds);
}
