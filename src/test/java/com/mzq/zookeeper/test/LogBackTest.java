package com.mzq.zookeeper.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class LogBackTest {

    @Test
    public void test1() {
        /*
         * 当使用LoggerFactory.getLogger(LogBackTest.class)获取logger时，实际上会创建一个logger树
         * |-- root（logback.xml配置里有的） [配置的debug]
         *   |-- com [debug，由于没有配置该logger，因此该logger的level与它的parent相同]
         *     |-- com.mzq（logback.xml配置里有的） [配置的warn]
         *       |-- com.mzq.zookeeper（logback.xml配置里有的） [配置的info]
         *         ｜-- com.mzq.zookeeper.test [info，由于没有配置该logger，因此该logger的level与它的parent相同]
         *           |-- com.mzq.zookeeper.test.LogBackTest [info，由于没有配置该logger，因此该logger的level与它的parent相同]
         *
         * 而LoggerFactory.getLogger(LogBackTest.class);返回的logger则是logger树的最末端，也就是com.mzq.zookeeper.test.LogBackTest。
         *
         * logback的日志输出模式是事件驱动模式，当我们使用一个logger进行日志输出时，会判断当前使用logger要产生的日志事件的level和该logger在logback.xml里配置的level是否匹配（即前者大于等于后者则算匹配）。
         * 如果匹配，则会创建一个日志事件，这个日志事件从logger树的底端向顶端冒泡，每个logger在收到事件时都会使用对应的appender进行输出，然后根据
         * 当前logger的additivity判断是否继续向上冒泡，使用父logger的appender进行输出。这类似于js的事件模型。
         *
         * 这种事件驱动的日志输出模型的好处是：虽然我们logback.xml的配置文件中，没有配置com.mzq.zookeeper.test.LogBackTest这个logger，但是我们配置了
         * com.mzq.zookeeper这个logger，当冒泡经过这个logger时，这个logger就会进行输出。因此所有以com.mzq.zookeeper为父logger的子logger都不需要配置，
         * 直接冒泡到com.mzq.zookeeper这个logger进行输出即可。
         *
         * 注意：
         * 如果一个logger没有配置level，那么它的level和它的父logger的level相同。我们通过上图可以得到，我们通过LoggerFactory.getLogger(LogBackTest.class)获得的logger（即com.mzq.zookeeper.test.LogBackTest）的level是info。
         */
        Logger logger1 = LoggerFactory.getLogger(LogBackTest.class);
        /*
            1.当代码里写logger.info时，代表此处期望输出info级别的日志。但该日志能否输出取决于当前logger在logback.xml里配置的level。
            2.当使用logger.info进行日志输出时，Logger会判断info事件等级是否超过了当前logger配置的level，当匹配时，logger会产生一个日志事件，然后让Appender来处理这个
            日志事件，当appender处理完毕后，如果当前logger的additivity属性为true时，则获取当前logger的父logger，让父logger的appender也处理这个日志事件（注意：父logger处理
            日志事件时，不会再判断日志等级了，而是直接让appender来处理日志事件）。直至logger树上所有的logger都处理完该日志事件后（前提是logger树上的所有logger的additivity属性都是true）
            ，整个logger.info方法也就执行完毕了。
            3.logger树上任意一个logger的additivity属性为false时，当日志事件冒泡到该logger时，logger的appender处理完该事件后，就结束了日志事件的冒泡，logger.info方法则处理结束。
            4.当使用logger.info进行日志输出时，如果info事件等级没有超过该logger配置的level（例如配置的是warn），那么则不会产生事件日志，logger.info方法执行结束。
         */
        logger1.info("hasdfasdfasdfsdfsdfsdfsdfsdfhaxcvretertetyiyuiyuiuyi", new RuntimeException("hello"));

        logger1.warn("hohohohoxcvsdf");
        logger1.error("error test:{}", "user error", new RuntimeException("hello error"));

        Logger logger2 = LoggerFactory.getLogger(ApplicationContext.class);
        logger2.warn("puqwieorupweurpowerpuepruwperu");

        Logger logger3 = LoggerFactory.getLogger("mzq.test.hello.world");
        logger3.error("heiheiheihei");

        System.out.println("test");
    }
}
