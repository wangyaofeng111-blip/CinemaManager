package com.rabbiter.cm.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.rabbiter.cm.domain.SysBill;
import com.rabbiter.cm.domain.SysMovie;
import com.rabbiter.cm.service.impl.SysBillServiceImpl;
import com.rabbiter.cm.service.impl.SysMovieServiceImpl;
import com.rabbiter.cm.service.impl.SysSessionServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import com.rabbiter.cm.domain.SysSession;


@RestController
@RequestMapping("/alipay")
public class AlipayController {

    private final AlipayClient alipayClient;
    private final SysBillServiceImpl sysBillService;
    private final SysSessionServiceImpl sysSessionService;
    private final SysMovieServiceImpl sysMovieService;

    public AlipayController(AlipayClient alipayClient,
                            SysBillServiceImpl sysBillService,
                            SysSessionServiceImpl sysSessionService,
                            SysMovieServiceImpl sysMovieService) {
        this.alipayClient = alipayClient;
        this.sysBillService = sysBillService;
        this.sysSessionService = sysSessionService;
        this.sysMovieService = sysMovieService;
    }

    @Value("${alipay.returnUrl}")
    private String returnUrl;

    @Value("${alipay.notifyUrl}")
    private String notifyUrl;

    @Value("${alipay.alipayPublicKey}")
    private String alipayPublicKey;

    @Value("${alipay.charset}")
    private String charset;

    @Value("${alipay.signType}")
    private String signType;

    // 1) 发起支付：返回 html form（前端直接渲染并自动提交）
    @GetMapping(value = "/pay/{billId}", produces = "text/html;charset=UTF-8")
    public String pay(@PathVariable Long billId) throws AlipayApiException {
        SysBill bill = sysBillService.findBillById(billId);
        if (bill == null) {
            throw new RuntimeException("订单不存在");
        }
        // 你可以加：已取消/已支付就不允许重复发起
        if (Boolean.TRUE.equals(bill.getPayState())) {
            throw new RuntimeException("订单已支付");
        }

        // 金额：建议用你订单实际金额字段；这里假设 bill.getTotalAmount()
        String totalAmount = "70"; // 你按实体字段改
        String outTradeNo = String.valueOf(bill.getBillId());      // 确保唯一（也可用你自己的订单号字段）
        String subject = "电影票订单-" + outTradeNo;

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setReturnUrl(returnUrl);
        request.setNotifyUrl(notifyUrl);

        // 注意 product_code：电脑网站支付固定 FAST_INSTANT_TRADE_PAY
        String bizContent = "{"
                + "\"out_trade_no\":\"" + outTradeNo + "\","
                + "\"total_amount\":\"" + totalAmount + "\","
                + "\"subject\":\"" + subject + "\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\""
                + "}";

        request.setBizContent(bizContent);

        // 返回的是一个可直接输出到浏览器的 form
        return alipayClient.pageExecute(request).getBody();
    }

    // 2) 支付宝异步回调：一定要验签，验签通过才改 payState
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) throws Exception {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v[0]));

        boolean signVerified = AlipaySignature.rsaCheckV1(
                params, alipayPublicKey, charset, signType
        );

        if (!signVerified) {
            return "failure";
        }

        // 关键字段
        String outTradeNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        // String totalAmount = params.get("total_amount"); // 可做金额校验（建议做）

        // 只在成功状态处理
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {

            Long billId = Long.valueOf(outTradeNo);
            SysBill bill = sysBillService.findBillById(billId);
            if (bill == null) return "failure";

            // 幂等：如果已经支付过，就直接 success
            if (Boolean.TRUE.equals(bill.getPayState())) {
                return "success";
            }

            // 1) 更新订单为已支付
            bill.setPayState(true);
            sysBillService.updateBill(bill);

            // 2) 复用你原来 SysBillController.pay() 的“票房更新逻辑”
            SysSession curSession = sysSessionService.findOneSession(bill.getSessionId());
            SysMovie curMovie = sysMovieService.findOneMovie(curSession.getMovieId());

            int seatNum = bill.getSeats().split(",").length;
            double price = curSession.getSessionPrice();
            curMovie.setMovieBoxOffice(curMovie.getMovieBoxOffice() + seatNum * price);
            sysMovieService.updateMovie(curMovie);

            return "success";
        }

        return "success";
    }
}
