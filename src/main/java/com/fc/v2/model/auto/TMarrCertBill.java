package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 婚姻登记记录证明逐级核准单对象 t_marr_cert_bill
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_marr_cert_bill")
@ApiModel(value = "TMarrCertBill", description = "婚姻登记记录证明逐级核准单")
public class TMarrCertBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 婚姻登记记录证明核准单号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "婚姻登记记录证明核准单号")
    private String billNo;

    /** 当前压在第几格 0受理 1核对 2分管双签 3市局备案 4已挪满落定 */
    @TableField("node_no")
    @ApiModelProperty(value = "当前压在第几格 0受理 1核对 2分管双签 3市局备案 4已挪满落定")
    private Integer nodeNo;

    /** 当前格核准方式 0任一人 1两名点齐 */
    @TableField("sign_mode")
    @ApiModelProperty(value = "当前格核准方式 0任一人 1两名点齐")
    private Integer signMode;

    /** 当前格应到名目数(由格次算出) */
    @TableField("need_count")
    @ApiModelProperty(value = "当前格应到名目数(由格次算出)")
    private Integer needCount;

    /** 当前格已落印笔数(由签账一笔笔算出,不容手填) */
    @TableField("sign_count")
    @ApiModelProperty(value = "当前格已落印笔数(由签账一笔笔算出,不容手填)")
    private Integer signCount;

    /** 报批情形 0在核 1已核讫 2已打回 3不予出具 */
    @TableField("status")
    @ApiModelProperty(value = "报批情形 0在核 1已核讫 2已打回 3不予出具")
    private Integer status;

    /** 申请人姓名 */
    @TableField("applicant_name")
    @ApiModelProperty(value = "申请人姓名")
    private String applicantName;

    /** 申请人证件号码 */
    @TableField("applicant_id_no")
    @ApiModelProperty(value = "申请人证件号码")
    private String applicantIdNo;

    /** 查档人身份类别 1本人 2监护人 3继承人 4受托人 5办案机关 */
    @TableField("applicant_type")
    @ApiModelProperty(value = "查档人身份类别 1本人 2监护人 3继承人 4受托人 5办案机关")
    private String applicantType;

    /** 按身份所持查档凭证编号 */
    @TableField("credential_no")
    @ApiModelProperty(value = "按身份所持查档凭证编号")
    private String credentialNo;

    /** 查档指向(来查哪一档) */
    @TableField("query_target")
    @ApiModelProperty(value = "查档指向(来查哪一档)")
    private String queryTarget;

    /** 所挂卷宗字号(档案编号) */
    @TableField("volume_no")
    @ApiModelProperty(value = "所挂卷宗字号(档案编号)")
    private String volumeNo;

    /** 出证事由代码(须落在民政部通知可出证清单内) */
    @TableField("reason_code")
    @ApiModelProperty(value = "出证事由代码(须落在民政部通知可出证清单内)")
    private String reasonCode;

    /** 拟写入证明的文本表述 */
    @TableField("draft_text")
    @ApiModelProperty(value = "拟写入证明的文本表述")
    private String draftText;

    /** 核准轮次(市局每打回一轮加一) */
    @TableField("review_round")
    @ApiModelProperty(value = "核准轮次(市局每打回一轮加一)")
    private Integer reviewRound;

    /** 不予出具时定格的格次(定此后封栏) */
    @TableField("freeze_node")
    @ApiModelProperty(value = "不予出具时定格的格次(定此后封栏)")
    private Integer freezeNode;

    /** 出证标记 0未出手 1已出手 */
    @TableField("issue_flag")
    @ApiModelProperty(value = "出证标记 0未出手 1已出手")
    private Integer issueFlag;

    /** 出证批准文号 */
    @TableField("cert_no")
    @ApiModelProperty(value = "出证批准文号")
    private String certNo;

    /** 由核准单转出的对外证明文本 */
    @TableField("cert_text")
    @ApiModelProperty(value = "由核准单转出的对外证明文本")
    private String certText;

    /** 证明文本出手之时 */
    @TableField("issue_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "证明文本出手之时")
    private Date issueTime;

    /** 倒序复核剩余待复格数 4..0 */
    @TableField("review_level")
    @ApiModelProperty(value = "倒序复核剩余待复格数 4..0")
    private Integer reviewLevel;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getSignMode() {
        return signMode;
    }

    public void setSignMode(Integer signMode) {
        this.signMode = signMode;
    }

    public Integer getNeedCount() {
        return needCount;
    }

    public void setNeedCount(Integer needCount) {
        this.needCount = needCount;
    }

    public Integer getSignCount() {
        return signCount;
    }

    public void setSignCount(Integer signCount) {
        this.signCount = signCount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public Integer getReviewRound() {
        return reviewRound;
    }

    public void setReviewRound(Integer reviewRound) {
        this.reviewRound = reviewRound;
    }

    public Integer getFreezeNode() {
        return freezeNode;
    }

    public void setFreezeNode(Integer freezeNode) {
        this.freezeNode = freezeNode;
    }

    public Integer getIssueFlag() {
        return issueFlag;
    }

    public void setIssueFlag(Integer issueFlag) {
        this.issueFlag = issueFlag;
    }

    public String getCertNo() {
        return certNo;
    }

    public void setCertNo(String certNo) {
        this.certNo = certNo;
    }

    public String getCertText() {
        return certText;
    }

    public void setCertText(String certText) {
        this.certText = certText;
    }

    public Date getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(Date issueTime) {
        this.issueTime = issueTime;
    }

    public Integer getReviewLevel() {
        return reviewLevel;
    }

    public void setReviewLevel(Integer reviewLevel) {
        this.reviewLevel = reviewLevel;
    }
}
