package com.example.kjlgraphdemo.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.kjlgraphdemo.config.CacheManager;
import com.example.kjlgraphdemo.entity.FoodIteam;
import com.example.kjlgraphdemo.mapper.FoodMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AiNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(AiNode.class);

    private static final String CHOOSE = "choose";
    private static final String FOODS = "foods";
    private static final String ADD = "add";
    private static final String CHANGE_FOOD = "changeFood";
    private static final String SOURCE = "source";
    private static final String TARGET = "target";
    private static final String QUERY = "query";
    private static final String QUERY_TARGET = "queryTarget";
    private static final String MEAL_KIND = "mealKind";
    private static final String MEAL_DATE = "mealDate";
    private static final String RESULT = "result";
    private static final String AI_RETURN = "aiReturn";

    private final ChatClient chatClient;
    private final FoodMapper foodMapper;
    Map<String, Function<Map<String,Object>, Map<String,Object>>> actionMap;
    public AiNode(ChatClient.Builder builder, FoodMapper foodMapper) {
        this.chatClient = builder.build();
        this.foodMapper = foodMapper;
        actionMap = Map.of(
                "query", this::handleQueryRequest,
                "change", this::handleChangeFoodRequest,
                "add", this::handleAddRequest,
                //"meal", this::handleMealInformation
                "choose", this::handleChooseRequest
        );
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("------------进入ainode节点");
        //提取用户反馈信息
        String feedback = extractFeedback(state);
        //大模型推测1、用户意图,2 提取关键信息
        String jsonString = callChatClient(feedback);
        JSONObject jsonObject = JSON.parseObject(jsonString);
        Map<String, Object> feedbackMap = jsonObject.getInnerMap();


        Map<String, Object> result=new HashMap<>();
        String action = (String) feedbackMap.get("action");
        if (actionMap.containsKey(action)) {
            result = actionMap.get(action).apply(feedbackMap);
        }
        if (result.size()>0) {
            return result;
        }
        return handleMealInformation(feedbackMap);
    }

    /**
     * 提取反馈信息
     */
    private String extractFeedback(OverAllState state) {
        String feedback = (String) state.data().get("feed_back");
        if (state.humanFeedback() != null) {
            feedback = state.humanFeedback().data().get("feed_back").toString();
        }
        return feedback;
    }

    /**
     * 调用AI模型获取解析结果
     */
    private String callChatClient(String feedback) {
        String prompt = """
                你是一个关键信息提取助手。
                从 content 文本中提取以下信息，并严格按指定 JSON 格式返回（不要输出多余文字，不要解释）：
                   
                1. "choose"：
                   - 如果文本包含与"选择"含义相同或相近的词（如"选"、"挑"等），返回 "Y"；
                   - 如果没有找到，返回 "N"。
                
                2. "foods"：
                   - 在 choose = "Y" 时，提取与食物相关的词（如"苹果"、"米饭"、"牛奶"等），多个词用英文逗号分隔；
                   - 如果没有找到，返回 ""。
                
                3. "add"：
                   - 如果文本中包含"添加"、"可以"、"加"等表示同意的词，返回 "Y"；
                   - 如果包含"不加"、"不要"、"去掉"等表示否定的词，返回 "N"；
                   - 如果都没有出现，返回 ""。
                
                4. "changeFood"：
                   - 如果有更换食材的意图，返回 "Y"，否则返回 "N"。
                   - 同时提取：
                     • "source"：被替换的原食物A；
                     • "target"：替换成的新食物B；
                   - 如果未检测到更换意图，source 和 target 返回空字符串 ""。
                
                5. "query" 与 "queryTarget"：
                   - 想吃苹果，如果识别想，吃，查询等查询意图，"query" 返回 "Y"，否则返回 ""；
                   - "queryTarget"：提取需要查询的具体对象或内容（如"苹果"），如我想吃苹果，查询食材库，则提取苹果。
                
                6. "mealKind" 与 "mealDate"：
                   - "mealKind"：提取餐类（如"早餐"、"午餐"、"晚餐"），没有则返回 ""；
                   - "mealDate"：提取日期或时间（如"今天"、"明天"），没有则返回 ""。
                7."action":根据上述，query，add，choose，changeFood选出为Y的。例如add为Y，则该值为Y
                
                **必须严格返回以下 JSON 格式：**
                {
                  "action":"query|change|add|choose|none"
                  "choose": "<Y/N>",
                  "foods": "<食物相关词，英文逗号分隔或空字符串>",
                  "add": "<Y/N或空字符串>",
                  "changeFood": "<Y/N>",
                  "source": "<原食物或空字符串>",
                  "target": "<新食物或空字符串>",
                  "query": "<Y/N或空字符串>",
                  "queryTarget": "<查询对象或空字符串>",
                  "mealKind": "<餐类或空字符串>",
                  "mealDate": "<日期或空字符串>"
                }
                
                content: "{{content}}"
                """ + feedback;

        return chatClient.prompt(prompt).call().content().toString();
    }


    /**
     * 处理查询请求
     */
    private Map<String, Object> handleQueryRequest(Map<String, Object> feedbackMap) {
        String queryTarget = (String) feedbackMap.get(QUERY_TARGET);
        List<String> foodItems = foodMapper.findByFoodnameLike("%" + queryTarget + "%");

        if (foodItems.size()==0) {
            CacheManager.getCache().put("addFood", queryTarget);
            return Map.of(RESULT, "食材库中没有" + queryTarget + ",是否将其加到食材库");
        } else if(foodItems.size()==1){
            String cFoods = (String) CacheManager.getCache().getIfPresent("foodList");
            //cFoods=cFoods+","+queryTarget;
           // String ncFoods = cFoods.replace(source, newTarget);
            CacheManager.getCache().put("foodList", queryTarget);
            return Map.of();
        }else {
            return Map.of(RESULT, "食材库中存在" + foodItems.toString() + ",请选择：");
        }
    }


    /**
     * 处理更换食材请求
     */
    private Map<String, Object> handleChangeFoodRequest(Map<String, Object> feedbackMap) {
        String source = (String) feedbackMap.get(SOURCE);
        String target = (String) feedbackMap.get(TARGET);

        Map<String, Object> replaceFood = (Map<String, Object>) CacheManager.getCache().getIfPresent("replaceFood");
        replaceFood.replace(SOURCE, source);
        //

        List<String> foodItems = foodMapper.findByFoodnameLike("%" + target + "%");
        Map<String, Object> result = new HashMap<>();
        String nextNode = "";

        if (foodItems.size() == 1) {
            // 存在目标食材，直接替换
            List<String> foodsList = (List<String>) CacheManager.getCache().getIfPresent("foodList");
            List<String> foodsListNew = foodsList.parallelStream()
                    .map(s -> s.equals(source) ? target : s)
                    .collect(Collectors.toList());
            CacheManager.getCache().put("foodList", foodsListNew);
            return Map.of(); // 继续执行下一个节点
        } else if (foodItems.size() > 1) {
            // 存在多个候选食材，需要用户选择
            result.put(AI_RETURN, "食材库中发现" + foodItems + "请选择");
            nextNode = "human_feedback";
            return Map.of(RESULT, result);
        } else {
            // 目标食材不存在，需要用户确认是否添加
            result.put(AI_RETURN, target + "不存在，是否添加到食材库");
            CacheManager.getCache().put("addFood", target);
            replaceFood.put(TARGET, target);
            return Map.of(RESULT, result);
        }
    }

    /**
     * 判断是否为添加请求
     */
    private boolean isAddRequest(Map<String, Object> feedbackMap) {
        return "Y".equals(feedbackMap.get(ADD));
    }

    /**
     * 处理添加请求
     */
    private Map<String, Object> handleAddRequest(Map<String, Object> feedbackMap) {
        String addFood = (String) CacheManager.getCache().getIfPresent("addFood");
        foodMapper.insertFood(new FoodIteam(addFood, "http://localhost:8080"));
        feedbackMap.replace(CHOOSE, "Y");
        Map<String, Object> replaceFood = (Map<String, Object>) CacheManager.getCache().getIfPresent("replaceFood");
        replaceFood.replace(TARGET, addFood);
        return Map.of();
    }

    /**
     * 判断是否为选择请求
     */
    private boolean isChooseRequest(Map<String, Object> feedbackMap) {
        return "Y".equals(feedbackMap.get(CHOOSE));
    }

    /**
     * 处理选择请求
     */
    private Map<String,Object> handleChooseRequest(Map<String, Object> feedbackMap) {
        Map<String, Object> replaceFood = (Map<String, Object>) CacheManager.getCache().getIfPresent("replaceFood");
        String source = (String) replaceFood.get(SOURCE);
        String target = (String) replaceFood.get(TARGET);
        String newTarget = (String) feedbackMap.get(FOODS);
        newTarget=isNullOrEmpty(newTarget)?target:newTarget;
        if (!isNullOrEmpty(newTarget)) {
            String cFoods = (String) CacheManager.getCache().getIfPresent("foodList");
            if (isNullOrEmpty(cFoods)){
                CacheManager.getCache().put("foodList", newTarget);
            }else {
                if (isNullOrEmpty(source)){
                    CacheManager.getCache().put("foodList", cFoods+=","+newTarget);
                }else {
                    String ncFoods = cFoods.replace(source, newTarget);
                    CacheManager.getCache().put("foodList", ncFoods);
                }

            }
        }
        return Map.of();
    }

    /**
     * 处理餐食信息
     */
    private Map<String, Object> handleMealInformation(Map<String, Object> feedbackMap) {
        StringBuilder aiReturn = new StringBuilder();
        Map<String, Object> cacheInformation = (Map<String, Object>) CacheManager.getCache().getIfPresent("mealplanInfomation");

        // 处理餐类信息
        String cacheMealKind = (String) cacheInformation.get(MEAL_KIND);
        String fbMealKind = (String) feedbackMap.get(MEAL_KIND);

        if (!isNullOrEmpty(fbMealKind)) {
            cacheInformation.put(MEAL_KIND, fbMealKind);
        } else if (isNullOrEmpty(cacheMealKind)) {
            aiReturn.append("请补充是早餐，晚饭，午饭？");
        }

        // 处理日期信息
        String fbMealDate = (String) feedbackMap.get(MEAL_DATE);
        String cacheMealDate = (String) cacheInformation.get("mealDate");

        if (!isNullOrEmpty(fbMealDate)) {
            cacheInformation.put("mealData", fbMealDate);
            CacheManager.getCache().put("mealplanInfomation", cacheInformation);
        } else if (isNullOrEmpty(cacheMealDate)) {
            aiReturn.append("请补充哪天吃？");
        }

        // 检查所有信息是否完整，生成食谱
        String cFoods = (String) CacheManager.getCache().getIfPresent("foodList");
        String cacheMealDateN = (String) cacheInformation.get("mealData");
        String cacheMealKindN = (String) cacheInformation.get(MEAL_KIND);

        if (cFoods != null && !isNullOrEmpty(cacheMealDateN) && !isNullOrEmpty(cacheMealKindN)) {
            Map<String, Object> mealplanMap = new HashMap<>();
            mealplanMap.put("mealkind", cacheMealKindN);
            mealplanMap.put("mealdate", cacheMealDateN);
            mealplanMap.put("foods", cFoods);
            return Map.of(RESULT, mealplanMap);
        }

        return Map.of(RESULT, aiReturn.toString());
    }

    /**
     * 判断字符串是否为空
     */
    public boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
