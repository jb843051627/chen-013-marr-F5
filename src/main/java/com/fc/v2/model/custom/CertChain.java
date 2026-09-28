package com.fc.v2.model.custom;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 婚姻登记记录证明逐级核准——格次、名目、身份、事由目录（现行利用办法 + 民政部通知清单）。
 * 一张单子从进门到送出挪四格，次序颠倒不得：
 * 0受理岗 -> 1档案核对岗 -> 2分管双签(事由核/记载文本核) -> 3市局备案 -> 4挪满落定。
 *
 * @author fuce
 * @date 2026-09-28
 */
public final class CertChain {

    private CertChain() {
    }

    /** 第一格：受理岗（任一人） */
    public static final int NODE_ACCEPT = 0;
    /** 第二格：档案核对岗（只认卷宗编号，任一人） */
    public static final int NODE_CHECK = 1;
    /** 第三格：分管双签（事由核 + 记载文本核，两双眼各管各的） */
    public static final int NODE_DOUBLE = 2;
    /** 第四格：市局备案（任一人） */
    public static final int NODE_BUREAU = 3;
    /** 四格挪满，落定待出证 */
    public static final int NODE_DONE = 4;

    /** 同层核准方式：任一人 */
    public static final int MODE_OR = 0;
    /** 同层核准方式：两名点齐 */
    public static final int MODE_AND = 1;

    /** 名目：单签格次 */
    public static final String ROLE_SINGLE = "SINGLE";
    /** 名目：分管负责人甲——事由落没落在可出证范围里 */
    public static final String ROLE_SCOPE = "SCOPE";
    /** 名目：分管负责人乙——档案记载与拟出文本合不合得拢 */
    public static final String ROLE_TEXT = "TEXT";

    /** 动作：落印核准 */
    public static final int ACTION_APPROVE = 1;
    /** 动作：不予出具（定格封栏） */
    public static final int ACTION_VETO = 2;
    /** 动作：市局打回（表述不合规范） */
    public static final int ACTION_BOUNCE = 3;
    /** 动作：出手后倒序复核确认 */
    public static final int ACTION_REVIEW = 4;
    /** 动作：证明文本出手 */
    public static final int ACTION_ISSUE = 5;

    /** 报批情形：在核 */
    public static final int STATUS_RUNNING = 0;
    /** 报批情形：已核讫（四格挪满） */
    public static final int STATUS_PASS = 1;
    /** 报批情形：已打回（市局打回、从上一轮第二格重攒的过渡态） */
    public static final int STATUS_BOUNCED = 2;
    /** 报批情形：不予出具（定在此处，谁也不能再补一枚） */
    public static final int STATUS_VETO = 3;

    /** 查档人身份类别（现行利用办法按身份分类，各拿各的凭证） */
    public static final Map<String, String> APPLICANT_TYPES;

    /**
     * 可出证事由清单（按民政部通知往里收过一截，不在范围内的当场截住）。
     * TWN 涉台公证事项；LST 清单所列国家公证事项（哈萨克斯坦、芬兰、德国、
     * 奥地利、荷兰、南非、乌拉圭、墨西哥、波兰）；JUD 司法机关依法调查。
     */
    public static final Map<String, String> REASONS;

    static {
        Map<String, String> types = new LinkedHashMap<String, String>();
        types.put("1", "本人(身份证件)");
        types.put("2", "监护人(监护关系凭证)");
        types.put("3", "继承人(继承关系凭证)");
        types.put("4", "受托人(经公证的授权委托书)");
        types.put("5", "办案机关(单位介绍信及公务证件)");
        APPLICANT_TYPES = Collections.unmodifiableMap(types);

        Map<String, String> reasons = new LinkedHashMap<String, String>();
        reasons.put("TWN", "涉台公证事项");
        reasons.put("LST", "列入清单国家公证事项(哈萨克斯坦、芬兰、德国、奥地利、荷兰、南非、乌拉圭、墨西哥、波兰)");
        reasons.put("JUD", "司法机关依法调查事项");
        REASONS = Collections.unmodifiableMap(reasons);
    }

    /** 第三格两双眼各自的名目，缺一不可、互不替签 */
    public static final List<String> DOUBLE_ROLES =
            Collections.unmodifiableList(Arrays.asList(ROLE_SCOPE, ROLE_TEXT));

    /** 该格应到名目数：件自己在账上一笔笔加出来的底数 */
    public static int needCount(int node) {
        return node == NODE_DOUBLE ? 2 : (node >= NODE_BUREAU ? 0 : 1);
    }

    /** 该格核准方式 */
    public static int modeOf(int node) {
        return node == NODE_DOUBLE ? MODE_AND : MODE_OR;
    }

    /** 格次名目（页面与账上同一回算出来的数） */
    public static String nodeName(int node) {
        switch (node) {
            case NODE_ACCEPT:
                return "受理岗";
            case NODE_CHECK:
                return "档案核对岗";
            case NODE_DOUBLE:
                return "分管双签";
            case NODE_BUREAU:
                return "市局备案";
            case NODE_DONE:
                return "已挪满落定";
            default:
                return "未知格次";
        }
    }
}
