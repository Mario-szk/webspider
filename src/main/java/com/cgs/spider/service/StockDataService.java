package com.cgs.spider.service;

import com.alibaba.fastjson.JSONObject;
import com.cgs.spider.constant.Constant;
import com.cgs.spider.constant.RabbitKeys;
import com.cgs.spider.constant.WebAttributeConstant;
import com.cgs.spider.dao.MarketValueDao;
import com.cgs.spider.entity.MarketValue;
import com.cgs.spider.message.AMQPClient;
import com.cgs.spider.service.cache.MarketValueCache;
import com.cgs.spider.vo.MarketValueVO;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Administrator on 2017/5/7.
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class StockDataService {

    private static final String PREFIX = "var hq_str_.*=";
    private static final String FIELD_SEPERATOR = ",";

    private CloseableHttpClient httpClient = HttpClients.createDefault();
    private ThreadLocal<SimpleDateFormat> threadLocal = new ThreadLocal();
    @Resource
    private MarketValueDao marketValueDao;
    @Resource
    private MarketValueCache marketValueCache;
    @Resource
    private AMQPClient amqpClient;

    public void requestStockData(List<String> stockIdList){
        try {
            for (String stockId : stockIdList){
                String url = Constant.MARKET_VALUE_URL + stockId;
                String content = requestUrl(url);
                MarketValue marketValue = parseMarketValue(content,stockId);
                if (!ObjectUtils.isEmpty(marketValue)){
                    String value = JSONObject.toJSONString(marketValue);
                    if (marketValueCache.putOrBack(String.valueOf(marketValue.getStockId()),value)){
                        String context = RabbitKeys.MARKET_VALUE.getContext();
                        amqpClient.sendMessage(RabbitKeys.MARKET_VALUE.getContext(),value);
                        persistent(marketValue);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String requestUrl(String url) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        CloseableHttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        return EntityUtils.toString(entity, WebAttributeConstant.COMPANY_PAGE_ENCODE);
    }

    private MarketValue parseMarketValue(String content,String stockId) throws ParseException {
        MarketValueVO marketValueVO = new MarketValueVO();
        content = content.replace(PREFIX,"");
        List<String> fieldList = Arrays.asList(content.split(FIELD_SEPERATOR));
        if (fieldList.size()< 2){
            return null;
        }
        marketValueVO.setStockId(stockId);
        marketValueVO.setStockName(fieldList.get(0));
        marketValueVO.setOpen(fieldList.get(1));
        marketValueVO.setPreClose(fieldList.get(2));
        marketValueVO.setCurrent(fieldList.get(3));
        marketValueVO.setHighest(fieldList.get(4));
        marketValueVO.setLowest(fieldList.get(5));
        marketValueVO.setBidOne(fieldList.get(6));
        marketValueVO.setBidSellOne(fieldList.get(7));
        marketValueVO.setSettlementAmount(fieldList.get(8));
        marketValueVO.setSettlement(fieldList.get(9));
        marketValueVO.setBuyOneAmount(fieldList.get(10));
        marketValueVO.setBuyOne(fieldList.get(11));
        marketValueVO.setBuyTwoAmount(fieldList.get(12));
        marketValueVO.setBuyTwo(fieldList.get(13));
        marketValueVO.setBuyThreeAmount(fieldList.get(14));
        marketValueVO.setBuyThree(fieldList.get(15));
        marketValueVO.setBuyFourAmount(fieldList.get(16));
        marketValueVO.setBuyFour(fieldList.get(17));
        marketValueVO.setBuyFiveAmount(fieldList.get(18));
        marketValueVO.setBuyFive(fieldList.get(19));
        marketValueVO.setSellOneAmount(fieldList.get(20));
        marketValueVO.setSellOne(fieldList.get(21));
        marketValueVO.setSellTwoAmount(fieldList.get(22));
        marketValueVO.setSellTwo(fieldList.get(23));
        marketValueVO.setSellThreeAmount(fieldList.get(24));
        marketValueVO.setSellThree(fieldList.get(25));
        marketValueVO.setSellFourAmount(fieldList.get(26));
        marketValueVO.setSellFour(fieldList.get(27));
        StringBuilder sb = new StringBuilder();
        sb.append(fieldList.get(30) + " " + fieldList.get(31));
        SimpleDateFormat sdf = getSimpleDateFormat();
        marketValueVO.setTime(sdf.parse(sb.toString()));
        return marketValueVO.toMarketValue();
    }

    private SimpleDateFormat getSimpleDateFormat(){
        if (threadLocal.get() != null){
            return threadLocal.get();
        }else {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            threadLocal.set(simpleDateFormat);
            return simpleDateFormat;
        }
    }

    private void persistent(MarketValue marketValue){
        if (!ObjectUtils.isEmpty(marketValue)){
            marketValueDao.saveMarketValue(String.valueOf(marketValue.getStockId()),marketValue);
        }
    }
}
