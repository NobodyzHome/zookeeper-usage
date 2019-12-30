package com.mzq.zookeeper.launcher;

import com.mzq.zookeeper.launcher.domain.Student;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnableConfigurationProperties
@SpringBootApplication
public class MyApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = new SpringApplicationBuilder(MyApp.class).bannerMode(Banner.Mode.OFF).run(args);
        Student bean = applicationContext.getBean(Student.class);
        StringRedisTemplate redisTemplate = applicationContext.getBean(StringRedisTemplate.class);
        redisTemplate.opsForValue().set("hello", "world");
        redisTemplate.boundListOps("list").rightPush("zhangsan");
        System.out.println(bean);
    }
}
