package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 查档申请表进单字段（沿用现行查档申请表，不另做新表）。
 * 四样缺一样报不进门：申请人是谁、来查哪一档、拟写进证明的话、挂在哪个卷宗字号下。
 *
 * @author fuce
 * @date 2026-09-28
 */
@ApiModel(value = "CertAcceptVo", description = "查档申请表进单字段")
public class CertAcceptVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 核准单号（留空则按规矩编排） */
    @ApiModelProperty(value = "核准单号(留空自动编排)")
    private String billNo;

    /** 申请人姓名（四样之一） */
    @ApiModelProperty(value = "申请人姓名", required = true)
    private String applicantName;

    /** 申请人证件号码（一位申请人只配一条出证文本） */
    @ApiModelProperty(value = "申请人证件号码", required = true)
    private String applicantIdNo;

    /** 查档人身份类别 1本人 2监护人 3继承人 4受托人 5办案机关 */
    @ApiModelProperty(value = "查档人身份类别", required = true)
    private String applicantType;

    /** 按身份所持查档凭证编号（各拿各的凭证） */
    @ApiModelProperty(value = "查档凭证编号", required = true)
    private String credentialNo;

    /** 查档指向：来查哪一档（四样之一） */
    @ApiModelProperty(value = "查档指向(来查哪一档)", required = true)
    private String queryTarget;

    /** 所挂卷宗字号（四样之一；核对岗只认编号） */
    @ApiModelProperty(value = "所挂卷宗字号", required = true)
    private String volumeNo;

    /** 出证事由代码（须在民政部通知清单内，不在范围当场截住） */
    @ApiModelProperty(value = "出证事由代码 TWN/LST/JUD", required = true)
    private String reasonCode;

    /** 拟写入证明的文本表述（四样之一） */
    @ApiModelProperty(value = "拟写入证明的文本表述", required = true)
    private String draftText;

    /** 备注 */
    @ApiModelProperty(value = "备注")
    private String remark;

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public String getApplicantIdNo() {
        return applicantIdNo;
    }

    public void setApplicantIdNo(String applicantIdNo) {
        this.applicantIdNo = applicantIdNo;
    }

    public String getApplicantType() {
        return applicantType;
    }

    public void setApplicantType(String applicantType) {
        this.applicantType = applicantType;
    }

    public String getCredentialNo() {
        return credentialNo;
    }

    public void setCredentialNo(String credentialNo) {
        this.credentialNo = credentialNo;
    }

    public String getQueryTarget() {
        return queryTarget;
    }

    public void setQueryTarget(String queryTarget) {
        this.queryTarget = queryTarget;
    }

    public String getVolumeNo() {
        return volumeNo;
    }

    public void setVolumeNo(String volumeNo) {
        this.volumeNo = volumeNo;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getDraftText() {
        return draftText;
    }

    public void setDraftText(String draftText) {
        this.draftText = draftText;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
