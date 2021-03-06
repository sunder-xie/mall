package com.plateno.booking.internal.base.vo;

import com.plateno.booking.internal.util.vo.BaseSearchVO;


/**
 * 商城查询基类
 * @author mogt
 * @date 2016年11月25日
 */
public class MallBaseSearchVO extends BaseSearchVO {
	/**
	 * 请求来源
	 * 请求来源：

		1-供应商后台
		
		2-营销通后台
		
		3-商城前端
	 */
	private Integer plateForm;
	
	/**
	 * 请求来源为 3-商城前端必填，其他选填（其他来源需作为过滤条件时填写）
	 */
	private Integer memberId;
	
	/**
	 * 请求来源为 1-供应商后台必填，其他选填（其他来源需作为过滤条件时填写）
	 */
	private Integer channelId;
	
	/**
	 * 后台操作人ID
	 */
	private String operateUserid;
	
	/**
	 * 后台操作人用户名
	 */
	private String operateUsername;

	public Integer getPlateForm() {
		return plateForm;
	}

	public String getOperateUserid() {
		return operateUserid;
	}

	public void setOperateUserid(String operateUserid) {
		this.operateUserid = operateUserid;
	}

	public String getOperateUsername() {
		return operateUsername;
	}

	public void setOperateUsername(String operateUsername) {
		this.operateUsername = operateUsername;
	}

	public void setPlateForm(Integer plateForm) {
		this.plateForm = plateForm;
	}

	public Integer getMemberId() {
		return memberId;
	}

	public void setMemberId(Integer memberId) {
		this.memberId = memberId;
	}

	public Integer getChannelId() {
		return channelId;
	}

	public void setChannelId(Integer channelId) {
		this.channelId = channelId;
	}
}
