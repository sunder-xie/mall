package com.plateno.booking.internal.validator.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.plateno.booking.internal.bean.contants.BookingConstants;
import com.plateno.booking.internal.bean.request.custom.MAddBookingParam;
import com.plateno.booking.internal.coupon.constant.CouponPlatformType;
import com.plateno.booking.internal.coupon.service.CouponService;
import com.plateno.booking.internal.coupon.vo.AttrValInfo;
import com.plateno.booking.internal.coupon.vo.CouponInfo;
import com.plateno.booking.internal.coupon.vo.QueryParam;
import com.plateno.booking.internal.coupon.vo.QueryResponse;
import com.plateno.booking.internal.goods.vo.OrderCheckDetail;
import com.plateno.booking.internal.goods.vo.OrderCheckInfo;
import com.plateno.booking.internal.interceptor.adam.common.bean.ResultCode;
import com.plateno.booking.internal.interceptor.adam.common.bean.ResultVo;
import com.plateno.booking.internal.interceptor.adam.common.bean.annotation.service.ServiceErrorCode;

@Service
@ServiceErrorCode(BookingConstants.CODE_VERIFY_ERROR)
public class CouponValidateService {

    private final static Logger logger = LoggerFactory.getLogger(CouponValidateService.class);

    @Autowired
    private CouponService couponService;

    /**
     * 
     * @Title: checkCoupon
     * @Description: 校验优惠券规则
     * @param @param addBookingParam
     * @param @param output
     * @return void
     * @throws
     */
    public void checkCoupon(MAddBookingParam addBookingParam, ResultVo output) {
        ResultVo<QueryResponse> result = findCoupon(addBookingParam);
        //检查优惠券参数是否正确
        checkCouponResult(result, addBookingParam, output);
        if(!output.getResultCode().equals(ResultCode.SUCCESS))
            return;

        CouponInfo couponInfo = result.getData().getCouponInfo().get(0);
        Map<String, List<AttrValInfo>> extAttrs = couponInfo.getExtAttrs();
        // 适用商品金额
        int productApplyAmout = 0;
        OrderCheckDetail orderCheckDetail = (OrderCheckDetail) output.getData();
        productApplyAmout = calProductCoupon(addBookingParam, orderCheckDetail, couponInfo, output);
        if(!output.getResultCode().equals(ResultCode.SUCCESS))
            return;
        
        //计算是否符合满减券规则
        BigDecimal productApplyAmoutBig = calFullDeductPrice(productApplyAmout,addBookingParam, extAttrs, output);
        if(!output.getResultCode().equals(ResultCode.SUCCESS))
            return;
        
        // 设置优惠券信息
        orderCheckDetail.setCouponOrderAmount(productApplyAmoutBig);
        addBookingParam.setCouponName(StringUtils.trimToEmpty(couponInfo.getCouponName()));
        addBookingParam.setCouponAmount(couponInfo.getAmount());
        addBookingParam.setValidCouponAmount(couponInfo.getAmount());
        addBookingParam.setConfigId(couponInfo.getConfigId());
    }

    private ResultVo<QueryResponse> findCoupon(MAddBookingParam addBookingParam){
        QueryParam param = new QueryParam();
        param.setPlatformId(CouponPlatformType.fromResource(addBookingParam.getResource())
                .getPlatformId());
        param.setCouponId(addBookingParam.getCouponId());
        param.setMebId(addBookingParam.getMemberId());
        ResultVo<QueryResponse> result = couponService.queryCoupon(param);
        return result;
    }
    
    /**
     * 
    * @Title: calFullDeductPrice 
    * @Description: 计算是否符合满减券规则
    * @param @param productApplyAmout
    * @param @param addBookingParam
    * @param @param extAttrs
    * @param @param output
    * @param @return    
    * @return BigDecimal    
    * @throws
     */
    private BigDecimal calFullDeductPrice(int productApplyAmout,MAddBookingParam addBookingParam,Map<String, List<AttrValInfo>> extAttrs,ResultVo output){
        // 适用商品金额
        BigDecimal productApplyAmoutBig =
                new BigDecimal(productApplyAmout).divide(new BigDecimal("100"));
        if (null != extAttrs.get("orderAmount")) {
            List<AttrValInfo> orderAmountAttrValInfoList = extAttrs.get("orderAmount");
            AttrValInfo orderAmountAttrValInfo = orderAmountAttrValInfoList.get(0);
            // 50-10,表示满50优惠10元，符合限制条件即可
            String couponAmoutStr = orderAmountAttrValInfo.getAttrVal1();
            String[] couponAmoutStrArr = couponAmoutStr.split("-");
            BigDecimal couponOrderAmout = new BigDecimal(couponAmoutStrArr[0]);
            // 商品金额是分，优惠券金额为元，需同步,将适用商品订单金额除以100
            if (productApplyAmoutBig.compareTo(couponOrderAmout) < 0) {
                logger.error("memberId:{}, couponId:{}, 适用商品金额:{}小于优惠券限制金额:{}",
                        addBookingParam.getMemberId(), addBookingParam.getCouponId(),
                        productApplyAmoutBig, couponOrderAmout);
                output.setResultCode(getClass(), ResultCode.FAILURE);
                output.setResultMsg("下单失败，不符合满减券规则");
                return null;
            }
        }
        return productApplyAmoutBig;
    }
    
    
    /**
     * 
    * @Title: calProductCoupon 
    * @Description: 查找使用优惠券商品以及计算适用优惠券的商品的价格
    * @param @param addBookingParam
    * @param @param orderCheckDetail
    * @param @param couponInfo
    * @param @param output
    * @param @return    
    * @return Integer    
    * @throws
     */
    private Integer calProductCoupon(MAddBookingParam addBookingParam,OrderCheckDetail orderCheckDetail,CouponInfo couponInfo,ResultVo output){
        Map<String, List<AttrValInfo>> extAttrs = couponInfo.getExtAttrs();
        // 适用商品金额
        int productApplyAmout = 0;
        // 适用商品集合,空则为全部商品
        List<OrderCheckInfo> couponProductList = Lists.newArrayList();
        List<OrderCheckInfo> orderCheckInfos = orderCheckDetail.getOrderCheckInfo();
        // 判断是否有商品符合，查询出使用商品以及适用商品的金额
        if (null != extAttrs.get("productId")) {
            List<AttrValInfo> productAttrValInfoList = extAttrs.get("productId");
            AttrValInfo attrValInfo = productAttrValInfoList.get(0);
            String productIds = attrValInfo.getAttrVal1();
            String[] productIdArr = productIds.split(",");
            boolean hasProduct = false;
            for (OrderCheckInfo orderCheckInfo : orderCheckInfos) {
                for (String temp : productIdArr) {
                    // 优惠券的适用商品为spuId
                    if (orderCheckInfo.getSpuId().equals(Long.valueOf(temp))) {
                        productApplyAmout +=
                                orderCheckInfo.getPrice() * orderCheckInfo.getQuantity();
                        couponProductList.add(orderCheckInfo);
                        hasProduct = true;
                        break;
                    }
                }
            }
            // 订单中的商品都不符合优惠券的使用商品
            if (!hasProduct) {
                logger.error("memberId:{}, couponId:{}, 订单中的商品都不符合优惠券适用商品:{}",
                        addBookingParam.getMemberId(), addBookingParam.getCouponId(), productIds);
                output.setResultCode(getClass(), ResultCode.FAILURE);
                output.setResultMsg("订单中的商品都不符合优惠券适用商品");
                return 0;
            }
            orderCheckDetail.setCouponProductList(couponProductList);
        }else if(null != extAttrs.get("categoryId")){
            List<AttrValInfo> productAttrValInfoList = extAttrs.get("categoryId");
            AttrValInfo attrValInfo = productAttrValInfoList.get(0);
            String categoryIds = attrValInfo.getAttrVal1();
            String[] categoryIdArr = categoryIds.split(",");
            boolean hasCategory = false;
            for (OrderCheckInfo orderCheckInfo : orderCheckInfos) {
                for (String temp : categoryIdArr) {
                    // 优惠券的适用类目
                    if (orderCheckInfo.getCategoryId().compareTo(Integer.valueOf(temp)) == 0) {
                        productApplyAmout +=
                                orderCheckInfo.getPrice() * orderCheckInfo.getQuantity();
                        couponProductList.add(orderCheckInfo);
                        hasCategory = true;
                        break;
                    }
                }
            }
            // 订单中的商品都不符合优惠券的类目
            if (!hasCategory) {
                logger.error("memberId:{}, couponId:{}, 订单中的商品都不符合优惠券适用商品:{}",
                        addBookingParam.getMemberId(), addBookingParam.getCouponId(), categoryIds);
                output.setResultCode(getClass(), ResultCode.FAILURE);
                output.setResultMsg("订单中的商品都不符合优惠券适用类目");
                return 0;
            }
            orderCheckDetail.setCouponProductList(couponProductList);
        }else {
            productApplyAmout = orderCheckDetail.getTotalPrice();
            //没找到使用商品，则插入全部商品
            orderCheckDetail.setCouponProductList(orderCheckDetail.getOrderCheckInfo());
        }
        return productApplyAmout;
    }
    
    
    /**
     * 
    * @Title: checkCouponResult 
    * @Description: 检查返回参数是否符合
    * @param @param result
    * @param @param addBookingParam
    * @param @param output    
    * @return void    
    * @throws
     */
    private void checkCouponResult(ResultVo<QueryResponse> result,MAddBookingParam addBookingParam,ResultVo output){
        if (!result.success()) {
            logger.error("memberId:{}, couponId:{}, 获取有优惠券信息失败:{}", addBookingParam.getMemberId(),
                    addBookingParam.getCouponId(), result);
            output.setResultCode(getClass(), ResultCode.FAILURE);
            output.setResultMsg("下单失败，获取优惠券信息失败，请稍后重试！");
            return;
        }

        if (result.getData().getCouponInfo().size() <= 0
                || result.getData().getCouponInfo().get(0).getFlag() == null
                || result.getData().getCouponInfo().get(0).getFlag() != 1) {
            logger.error("memberId:{}, couponId:{}, 优惠券不可用:{}", addBookingParam.getMemberId(),
                    addBookingParam.getCouponId(), result.getData().getCouponInfo());
            output.setResultCode(getClass(), ResultCode.FAILURE);
            output.setResultMsg("下单失败，优惠券无效或者不符合使用条件！");
            return;
        }

        if (result.getData().getCouponInfo().get(0).getAmount() == null) {
            logger.error("memberId:{}, couponId:{}, 优惠券金额为空", addBookingParam.getMemberId(),
                    addBookingParam.getCouponId());
            output.setResultCode(getClass(), ResultCode.FAILURE);
            output.setResultMsg("下单失败，获取优惠券信息失败，请稍后重试！");
            return;
        }
    }
    

}
