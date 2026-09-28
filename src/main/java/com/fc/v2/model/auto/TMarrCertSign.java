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
 * 逐级核准签账流水（逐笔留账：同一分钟两人各签也是两笔，绝不并笔）
 *
 * @author fuce
 * @date 2026-09-28
 */
@TableName("t_marr_cert_sign")
@ApiModel(value = "TMarrCertSign", description = "逐级核准签账流水")
public class TMarrCertSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 核准单主键 */
    @TableField("bill_id")
    @ApiModelProperty(value = "核准单主键")
    private Long billId;

    /** 落印时所属核准轮次 */
    @TableField("review_round")
    @ApiModelProperty(value = "落印时所属核准轮次")
    private Integer reviewRound;

    /** 落在哪一格 0受理 1核对 2分管双签 3市局备案 */
    @TableField("node_no")
    @ApiModelProperty(value = "落在哪一格 0受理 1核对 2分管双签 3市局备案")
    private Integer nodeNo;

    /** 格次内名目 SINGLE单签 SCOPE事由核 TEXT记载文本核 */
    @TableField("sign_role")
    @ApiModelProperty(value = "格次内名目 SINGLE单签 SCOPE事由核 TEXT记载文本核")
    private String signRole;

    /** 落印人(谁签记谁,不许代笔) */
    @TableField("approver")
    @ApiModelProperty(value = "落印人(谁签记谁,不许代笔)")
    private String approver;

    /** 动作 1落印核准 2不予出具 3市局打回 4倒序复核确认 5出证 */
    @TableField("action")
    @ApiModelProperty(value = "动作 1落印核准 2不予出具 3市局打回 4倒序复核确认 5出证")
    private Integer action;

    /** 签注 */
    @TableField("comment")
    @ApiModelProperty(value = "签注")
    private String comment;

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

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public Integer getReviewRound() {
        return reviewRound;
    }

    public void setReviewRound(Integer reviewRound) {
        this.reviewRound = reviewRound;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getSignRole() {
        return signRole;
    }

    public void setSignRole(String signRole) {
        this.signRole = signRole;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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
}
