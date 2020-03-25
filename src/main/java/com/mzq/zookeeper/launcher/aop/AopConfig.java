package com.mzq.zookeeper.launcher.aop;

import com.alibaba.fastjson.JSON;
import com.mzq.zookeeper.launcher.web.controller.HelloController;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class AopConfig {

    @Pointcut(value = "target(com.mzq.zookeeper.launcher.web.controller.HelloController) && target(target)")
    public void pointcut1(HelloController target) {
    }

    @Before(value = "pointcut1(target)", argNames = "joinPoint,target")
    public void logBefore(JoinPoint joinPoint, HelloController target) {
        Object[] args = joinPoint.getArgs();
        Signature signature = joinPoint.getSignature();
        Logger logger = LoggerFactory.getLogger(target.getClass());
        logger.info("即将执行{}.{}方法，参数：{}", signature.getDeclaringTypeName(), signature.getName(), JSON.toJSONString(args));
    }

    @AfterReturning(value = "pointcut1(target)", returning = "result", argNames = "joinPoint,target,result")
    public void logAfter(JoinPoint joinPoint, HelloController target, Object result) {
        Logger logger = LoggerFactory.getLogger(target.getClass());
        Signature signature = joinPoint.getSignature();
        logger.info("方法{}执行完毕，参数：{}", signature.toLongString(), JSON.toJSONString(joinPoint.getArgs()));
    }
}
