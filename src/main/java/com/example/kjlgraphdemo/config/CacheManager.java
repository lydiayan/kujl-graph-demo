package com.example.kjlgraphdemo.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class CacheManager {

    private static final Cache<String, Object> CACHE =
            CacheBuilder.newBuilder()
                    .maximumSize(1000) // 最大缓存条数
                    .expireAfterWrite(60, TimeUnit.MINUTES) // 写入后60分钟过期
                    .build();
    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

    private CacheManager() {}

    public static Cache<String, Object> getCache() {
        return CACHE;
    }

    @PostConstruct
    public void initData() {
        Map<String,String> mealplanInfomation=new HashMap<>();
        mealplanInfomation.put("mealKind",null);
        mealplanInfomation.put("mealData",null);
        CACHE.put("mealplanInfomation", mealplanInfomation);
        Map<String,String> cache_minfomation=(Map<String,String>)CACHE.getIfPresent("mealplanInfomation");
        log.info("mealplanInfomation:{}",cache_minfomation);
        //生成食谱的食物列表
        List<String> foodList=new ArrayList<>();
        CACHE.put("foodList", foodList);
        List<String> c_food_list=(List<String>)CACHE.getIfPresent("foodList");
        log.info("foodList:{}",c_food_list);
        //替换的食物
        Map<String,String> replaceFood=new HashMap<>();
        replaceFood.put("source", null);
        replaceFood.put("target", null);
        CACHE.put("replaceFood", replaceFood);
        Map<String,String> changeFood=(Map<String,String>) CACHE.getIfPresent("replaceFood");
        log.info("replaceFood:{}",changeFood);
        //需要添加到食材库的食材
        CACHE.put("addFood", null);
        //memoryId
        //对话记忆的唯一标识
        String conversantId = UUID.randomUUID().toString();
        CACHE.put("conversantId", conversantId);
        log.info("conversantId:{}",conversantId);
        System.out.println("Guava Cache 初始化食材库完成");
    }
}
