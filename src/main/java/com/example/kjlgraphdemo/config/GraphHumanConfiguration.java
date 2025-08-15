package com.example.kjlgraphdemo.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.kjlgraphdemo.mapper.FoodMapper;
import com.example.kjlgraphdemo.node.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;


@Configuration
public class GraphHumanConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GraphHumanConfiguration.class);
    //ChatMemory chatMemory = new InMemoryChatMemory();

    @Autowired
    private FoodMapper foodMapper;
    @Bean
    public StateGraph humanGraph(ChatClient.Builder chatClientBuilder) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            //keyStrategyHashMap.put("query", new ReplaceStrategy());
            // 人类反馈替换策略
            keyStrategyHashMap.put("feed_back", new ReplaceStrategy());     // 用户反馈
            keyStrategyHashMap.put("result", new ReplaceStrategy());

            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                // 1. ai_node
                .addNode("ai_node", node_async(new AiNode(chatClientBuilder, foodMapper)))
                // 2. 用户反馈节点(用于截断流程)
                .addNode("human_feedback", node_async(new HumanNode(chatClientBuilder)))
                // 流程起点
                .addEdge(StateGraph.START, "ai_node")
                // 查询完成后进入人工选择
                .addEdge("human_feedback", "ai_node")
                // 查询完成后进入人工选择
                .addEdge("ai_node", "human_feedback");

        // 打印 PlantUML 拓扑图
        GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML);
        logger.info("\n=== human graph ===");
        logger.info(representation.content());
        logger.info("===================\n");

        return stateGraph;
    }
}
