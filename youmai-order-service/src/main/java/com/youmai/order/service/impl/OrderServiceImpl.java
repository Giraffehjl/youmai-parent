package com.youmai.order.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.youmai.mapper.TbOrderItemMapper;
import com.youmai.mapper.TbPayLogMapper;
import com.youmai.pojo.TbOrderItem;
import com.youmai.pojo.TbPayLog;
import com.youmai.pojogroup.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import com.alibaba.dubbo.config.annotation.Service;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.youmai.mapper.TbOrderMapper;
import com.youmai.pojo.TbOrder;
import com.youmai.pojo.TbOrderExample;
import com.youmai.pojo.TbOrderExample.Criteria;
import com.youmai.order.service.OrderService;

import entity.PageResult;
import org.springframework.data.redis.core.RedisTemplate;
import util.IdWorker;

/**
 * 服务实现层
 *
 * @author Administrator
 */
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private IdWorker idWorker;

    @Autowired
    private TbOrderMapper orderMapper;

    @Autowired
    private TbOrderItemMapper orderItemMapper;

    @Autowired
    private TbPayLogMapper payLogMapper;

    /**
     * 查询全部
     */
    @Override
    public List<TbOrder> findAll() {
        return orderMapper.selectByExample(null);
    }

    /**
     * 按分页查询
     */
    @Override
    public PageResult findPage(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        Page<TbOrder> page = (Page<TbOrder>) orderMapper.selectByExample(null);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * @return void
     * @Description 更新订单状态
     * @Date 22:21 2018/10/12
     * @Param [out_trade_no, transaction_id]
     **/
    @Override
    public void updateOrderStatus(String out_trade_no, String transaction_id) {
        System.out.println("支付成功修改订单状态");
        //1 修改支付日志
        TbPayLog payLog = payLogMapper.selectByPrimaryKey(out_trade_no);
        payLog.setPayTime(new Date());
        //已支付
        payLog.setTradeState("1");
        //交易号
        payLog.setTransactionId(transaction_id);
        payLogMapper.updateByPrimaryKey(payLog);
        //2 修改订单状态
        //订单号列表
        String orderList = payLog.getOrderList();
        //订单号数组
        String[] orderIds = orderList.split(",");

        for (String orderId : orderIds) {
            TbOrder order = orderMapper.selectByPrimaryKey(Long.parseLong(orderId));
            if (order != null) {
                //已付款
                order.setStatus("2");
                orderMapper.updateByPrimaryKey(order);
            }
        }

        //3 清除缓存
        redisTemplate.boundHashOps("payLog").delete(payLog.getUserId());
    }

    /**
     * @return com.youmai.pojo.TbPayLog
     * @Description 从Redis搜索支付日志
     * @Date 21:56 2018/10/12
     * @Param [userId]
     **/
    @Override
    public TbPayLog searchPayLogFromRedis(String userId) {
        return (TbPayLog) redisTemplate.boundHashOps("payLog").get(userId);
    }


    /**
     * 增加
     */
    @Override
    public void add(TbOrder order) {
        //得到购物车数据
        List<Cart> cartList = (List<Cart>) redisTemplate.boundHashOps("cartList").get(order.getUserId());

        //订单id列表
        List<String> orderIdList = new ArrayList();
        //总金额
        double total_money = 0;

        for (Cart cart : cartList) {
            long orderId = idWorker.nextId();
            System.out.println("sellerId = " + cart.getSellerId());
            TbOrder tbOrder = new TbOrder();
            //订单号
            tbOrder.setOrderId(orderId);
            //用户名
            tbOrder.setUserId(order.getUserId());
            //支付类型
            tbOrder.setPaymentType(order.getPaymentType());
            //状态 ：付款状态
            tbOrder.setStatus("1");
            //订单创建日期
            tbOrder.setCreateTime(new Date());
            //订单修改日期
            tbOrder.setUpdateTime(new Date());
            //地址
            tbOrder.setReceiverAreaName(order.getReceiverAreaName());
            //手机号
            tbOrder.setReceiverMobile(order.getReceiverMobile());
            //收货人
            tbOrder.setReceiver(order.getReceiver());
            //订单来源
            tbOrder.setSourceType(order.getSourceType());
            //商家id
            tbOrder.setSellerId(order.getSellerId());


            double money = 0;
            //循环购物车明细
            for (TbOrderItem orderItem : cart.getOrderItemList()) {
                orderItem.setId(idWorker.nextId());
                //订单编号
                orderItem.setOrderId(orderId);
                //商家id
                orderItem.setSellerId(cart.getSellerId());
                //金额累加++++++=======
                money += orderItem.getTotalFee().longValue();
                orderItemMapper.insert(orderItem);
            }

            tbOrder.setPayment(new BigDecimal(money));
            orderMapper.insert(tbOrder);

            //添加到订单列表
            orderIdList.add(orderId + "");
            //累计总金额
            total_money += money;
        }
        //如果是微信支付
        if ("1".equals(order.getPaymentType())) {
            TbPayLog payLog = new TbPayLog();
            //支付订单号
            String outTradeNo = idWorker.nextId() + "";
            payLog.setOutTradeNo(outTradeNo);
            //创建时间
            payLog.setCreateTime(new Date());
            //订单号列表
            String ids = orderIdList.toString().replace("[", "").replace("]", "").replace(" ", "");
            //订单列表，逗号分隔
            payLog.setOrderList(ids);
            //支付类型
            payLog.setPayType("1");
            //总金额(分)
            payLog.setTotalFee((long) (total_money * 100));
            //支付状态
            payLog.setTradeState("0");
            //用户id
            payLog.setUserId(order.getUserId());
            //插入到支付日志表
            payLogMapper.insert(payLog);
            //放入缓存
            redisTemplate.boundHashOps("payLog").put(order.getUserId(), payLog);
        }


        redisTemplate.boundHashOps("cartList").delete(order.getUserId());
    }


    /**
     * 修改
     */
    @Override
    public void update(TbOrder order) {
        orderMapper.updateByPrimaryKey(order);
    }

    /**
     * 根据ID获取实体
     *
     * @param id
     * @return
     */
    @Override
    public TbOrder findOne(Long id) {
        return orderMapper.selectByPrimaryKey(id);
    }

    /**
     * 批量删除
     */
    @Override
    public void delete(Long[] ids) {
        for (Long id : ids) {
            orderMapper.deleteByPrimaryKey(id);
        }
    }


    @Override
    public PageResult findPage(TbOrder order, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);

        TbOrderExample example = new TbOrderExample();
        Criteria criteria = example.createCriteria();

        if (order != null) {
            if (order.getPaymentType() != null && order.getPaymentType().length() > 0) {
                criteria.andPaymentTypeLike("%" + order.getPaymentType() + "%");
            }
            if (order.getPostFee() != null && order.getPostFee().length() > 0) {
                criteria.andPostFeeLike("%" + order.getPostFee() + "%");
            }
            if (order.getStatus() != null && order.getStatus().length() > 0) {
                criteria.andStatusLike("%" + order.getStatus() + "%");
            }
            if (order.getShippingName() != null && order.getShippingName().length() > 0) {
                criteria.andShippingNameLike("%" + order.getShippingName() + "%");
            }
            if (order.getShippingCode() != null && order.getShippingCode().length() > 0) {
                criteria.andShippingCodeLike("%" + order.getShippingCode() + "%");
            }
            if (order.getUserId() != null && order.getUserId().length() > 0) {
                criteria.andUserIdLike("%" + order.getUserId() + "%");
            }
            if (order.getBuyerMessage() != null && order.getBuyerMessage().length() > 0) {
                criteria.andBuyerMessageLike("%" + order.getBuyerMessage() + "%");
            }
            if (order.getBuyerNick() != null && order.getBuyerNick().length() > 0) {
                criteria.andBuyerNickLike("%" + order.getBuyerNick() + "%");
            }
            if (order.getBuyerRate() != null && order.getBuyerRate().length() > 0) {
                criteria.andBuyerRateLike("%" + order.getBuyerRate() + "%");
            }
            if (order.getReceiverAreaName() != null && order.getReceiverAreaName().length() > 0) {
                criteria.andReceiverAreaNameLike("%" + order.getReceiverAreaName() + "%");
            }
            if (order.getReceiverMobile() != null && order.getReceiverMobile().length() > 0) {
                criteria.andReceiverMobileLike("%" + order.getReceiverMobile() + "%");
            }
            if (order.getReceiverZipCode() != null && order.getReceiverZipCode().length() > 0) {
                criteria.andReceiverZipCodeLike("%" + order.getReceiverZipCode() + "%");
            }
            if (order.getReceiver() != null && order.getReceiver().length() > 0) {
                criteria.andReceiverLike("%" + order.getReceiver() + "%");
            }
            if (order.getInvoiceType() != null && order.getInvoiceType().length() > 0) {
                criteria.andInvoiceTypeLike("%" + order.getInvoiceType() + "%");
            }
            if (order.getSourceType() != null && order.getSourceType().length() > 0) {
                criteria.andSourceTypeLike("%" + order.getSourceType() + "%");
            }
            if (order.getSellerId() != null && order.getSellerId().length() > 0) {
                criteria.andSellerIdLike("%" + order.getSellerId() + "%");
            }

        }

        Page<TbOrder> page = (Page<TbOrder>) orderMapper.selectByExample(example);
        return new PageResult(page.getTotal(), page.getResult());
    }


}
