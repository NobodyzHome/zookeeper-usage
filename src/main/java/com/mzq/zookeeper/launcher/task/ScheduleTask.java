package com.mzq.zookeeper.launcher.task;

import com.alibaba.fastjson.JSON;
import com.mzq.zookeeper.launcher.dao.repository.redis.StudentRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

//@Component
public class ScheduleTask {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleTask.class);

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedDelay = 5000)
    public void takeLeader() {
        ValueOperations<String, String> redisOps = stringRedisTemplate.opsForValue();
        String leaderStudentId = redisOps.get("leaderStudent");
        if (StringUtils.isNotBlank(leaderStudentId)) {
            studentRepository.findById(leaderStudentId).ifPresent(student -> logger.info("当前获取到master的客户端注册的student是：{}", JSON.toJSONString(student)));
        }
    }
}
