package com.future.trader.service;


import com.future.trader.api.*;
import com.future.trader.common.exception.BusinessException;
import com.future.trader.common.exception.DataConflictException;
import com.future.trader.service.impl.OrderNotifyCallbackImpl;
import com.future.trader.service.impl.QuoteCallbackImpl;
import com.future.trader.util.RedisManager;
import com.future.trader.util.StringUtils;
import com.future.trader.util.TradeUtil;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class AccountInfoService {

    Logger log= LoggerFactory.getLogger(AccountInfoService.class);

    @Autowired
    RedisManager redisManager;
    @Autowired
    OrderUpdateCallback orderUpdateCallbackImpl;
    @Autowired
    OrderNotifyCallback orderNotifyCallbackImpl;
    @Resource
    ConnectionService connectionService;

    /**
     * 设置信号源账户 监听
     * @param brokerName
     * @param username
     * @param password
     * @return
     */
    public int setSignalMonitor(String brokerName,int username,String password){
        if(StringUtils.isEmpty(brokerName)||StringUtils.isEmpty(password)||username==0){
            log.error("设置信号源账户 监听,传入传入参数为空！");
            throw new DataConflictException("设置信号源账户 监听,传入传入参数为空！");
        }
        int clientId = connectionService.getUserConnectWithConnectCallback(brokerName,username,password);
        if(clientId==0){
            // 初始化失败！
            log.error("client init error !");
            throw new BusinessException("client init error !");
        }
        /*设置信号源监听*/
        setOrderUpdateEventHandler(clientId);
        log.info("set signal monitor account: brokerName:"+brokerName+",username:"+username);

        return clientId;
    }

    /**
     * 设置信号源账户 监听
     * @param clientId
     * @return
     */
    public int setAccountMonitor(int clientId){
        boolean login = ConnectLibrary.library.MT4API_Connect(clientId);
        if(!login){
            log.error("clientId connnect error !");
            throw new BusinessException("clientId connnect error !");
        }
        /*设置订单结果回调*/
        OrderNotifyCallback notifyCallback = new OrderNotifyCallbackImpl();
        TraderLibrary.library.MT4API_SetOrderNotifyEventHandler(clientId, notifyCallback, clientId);
        log.info("set signal monitor account: clientId"+clientId);
        return clientId;
    }

    /**
     * 链接账户
     * @param brokerName
     * @param username
     * @param password
     * @return
     */
    public int setAccountConnnect(String brokerName,int username,String password){
//        int clientId = TradeUtil.getUserConnect(brokerName,username,password);
        int clientId = connectionService.getUserConnectWithConnectCallback(brokerName,username,password);
        if(clientId==0){
            // 初始化失败！
            log.error("client init error !");
            throw new BusinessException("client init error !");
        }
        /*设置订单结果回调*/
        TraderLibrary.library.MT4API_SetOrderNotifyEventHandler(clientId, orderNotifyCallbackImpl, clientId);

        redisManager.hset("account-connect-clientId",String.valueOf(username),clientId);
        log.info("set account connnect: brokerName:"+brokerName+",username:"+username);
        return clientId;
    }

    /**
     * 断开链接账户
     * @param clientId
     * @return
     */
    public boolean setAccountDisConnnect(int clientId){
        if (ConnectLibrary.library.MT4API_IsConnect(clientId)) {
            try {
                InstanceLibrary.library.MT4API_Destory(clientId);
                log.info("connection break success!");
                return true;
            }catch (Exception e){
                log.error("connection break fail!");
                TradeUtil.printError(clientId);
                return false;
            }
        }else {
            log.info("connection already break!");
        }
        return true;
    }

    /**
     * 获取账户信息
     * @param clientId
     */
    public void obtainAccoutInfo(int clientId) {
        IntByReference account = new IntByReference();
        AccountInfoLibrary.library.MT4API_GetUser(clientId, account);
        System.out.println("get user " + account.getValue());

        int size = 64;
        byte[] name = new byte[size];
        AccountInfoLibrary.library.MT4API_GetUserName(clientId, name, size);
        System.out.println("get user name : " + new String(name));

        //获取账号资金情况
        DoubleByReference banlan = new DoubleByReference();
        DoubleByReference credit = new DoubleByReference();
        DoubleByReference margin = new DoubleByReference();
        DoubleByReference freeMargin = new DoubleByReference();
        DoubleByReference equity = new DoubleByReference();
        DoubleByReference profit = new DoubleByReference();
        AccountInfoLibrary.library.MT4API_GetMoneyInfo(clientId, banlan, credit, margin,
                freeMargin, equity, profit);
        System.out.println("余额 ：" + banlan.getValue());
        System.out.println("信用 ：" + credit.getValue());
        System.out.println("占用保证金 ：" + margin.getValue());
        System.out.println("可用保证金 ：" + freeMargin.getValue());
        System.out.println("净值 ：" + equity.getValue());
        System.out.println("浮动盈亏 ：" + profit.getValue());

        IntByReference leverage = new IntByReference();
        AccountInfoLibrary.library.MT4API_GetLeverage(clientId, leverage);
        System.out.println("杠杆比例：" + leverage.getValue());

        System.out.println();
    }

    /**
     * 获取服务器信息
     * @param clientId
     */
    public void obtainServerInfo(int clientId) {
        IntByReference time = new IntByReference();
        ServerInfoLibrary.library.MT4API_GetServerTime(clientId, time);
        int timeValue = time.getValue();
        System.out.println("服务器时间：" + timeValue);

        LocalDateTime ldt = LocalDateTime.ofEpochSecond(Long.valueOf(timeValue), 0, ZoneOffset.ofHours(8));
        String formatTime = ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("服务器时间，格式化：" + formatTime);

        ShortByReference serverBuild = new ShortByReference();
        ServerInfoLibrary.library.MT4API_GetServerBuild(clientId, serverBuild);
        System.out.println("服务器版本号：" + serverBuild.getValue());

        IntByReference serverInfoCount = new IntByReference();
        ServerInfoLibrary.library.MT4API_GetSeverInfoCount(clientId, serverInfoCount);
        System.out.println("服务器个数：" + serverInfoCount.getValue());

        int count = serverInfoCount.getValue();
        ServerInfoLibrary.ServerInfo serverInfo = new ServerInfoLibrary.ServerInfo();
        ServerInfoLibrary.ServerInfo[] serverInfos = (ServerInfoLibrary.ServerInfo[]) serverInfo.toArray(count);
        IntByReference intByReference = new IntByReference(count);
        boolean isSuccess = ServerInfoLibrary.library.MT4API_GetSeverInfos(clientId, serverInfos, intByReference);
        System.out.println("get server：" + isSuccess);
        if (isSuccess) {
            System.out.println("服务服务器列表：" + isSuccess);
            for (int i = 0; i < count; i++) {
                ServerInfoLibrary.ServerInfo server = serverInfos[i];
                System.out.println("服务器地址：" + new String(server.server));
            }
        } else {
            TradeUtil.printError(clientId);
        }

        System.out.println();
    }

    /**
     * 设置证劵行情报价数据回调函数指针
     * @param clientId
     * @return
     */
    public boolean setQuoteEventHandler(int clientId) {
        if(clientId==0){
            return false;
        }
        QuoteCallback quoteCallback = new QuoteCallbackImpl();
        QuoteLibrary.library.MT4API_SetQuoteEventHandler(clientId, quoteCallback, clientId);
        return true;
    }

    /**
     * 设置订单处理结果函数
     * @param clientId
     * @return
     */
    public boolean setOrderNotifyEventHandler(int clientId) {
        if(clientId==0){
            return false;
        }
        OrderNotifyCallback notifyCallback = new OrderNotifyCallbackImpl();
        TraderLibrary.library.MT4API_SetOrderNotifyEventHandler(clientId, notifyCallback, clientId);
        return true;
    }

    /**
     * 设置订单处理过程回调函数
     * @param clientId
     * @return
     */
    public boolean setOrderUpdateEventHandler(int clientId) {
        if(clientId==0){
            return false;
        }
        //TODO /*事先查询下在仓订单 不然监听会失败*/
//        orderInfoService.obtainOpenOrderInfo(clientId);
//        OrderUpdateCallback updateCallback = new OrderUpdateCallbackImpl();
        TraderLibrary.library.MT4API_SetOrderUpdateEventHandler(clientId, orderUpdateCallbackImpl, clientId);
        log.info("success set signal mode, clientId: "+clientId);
        return true;
    }
}
