package com.mzq.zookeeper.launcher;

import com.mzq.zookeeper.launcher.domain.Student;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@EnableConfigurationProperties
@SpringBootApplication
@EnableScheduling
@EnableCaching
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
