package com.mzq.zookeeper.test;

import com.mzq.zookeeper.launcher.MyApp;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Objects;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = MyApp.class)
public class ZookeeperExcercise {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperExcercise.class);

    @Autowired
    private CuratorFramework curatorFramework;

    @Autowired
    private TaskExecutor taskExecutor;

    @Test
    public void excerciseCurator() throws Exception {
        curatorFramework.create().creatingParentContainersIfNeeded().withMode(CreateMode.EPHEMERAL).inBackground(((client, event) -> {
            logger.info("节点{}发生了事件，事件类型：{}", event.getPath(), event.getType());
        }), taskExecutor).forPath("/hello", "world".getBytes());

        String taskPath = curatorFramework.create().withMode(CreateMode.PERSISTENT).forPath("/task", "mission list".getBytes());
        Stat stat = new Stat();
        byte[] taskPathContents = curatorFramework.getData().storingStatIn(stat).usingWatcher((Watcher) watchedEvent -> logger.info("{}发生了事件{}", watchedEvent.getPath(), watchedEvent.getType())).forPath(taskPath);
        String taskPathStr = new String(taskPathContents);

        String task1Path = curatorFramework.create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(ZKPaths.makePath(taskPath, "task-"));
        Stat stat1 = curatorFramework.setData().withVersion(stat.getVersion()).forPath(taskPath, (taskPathStr + task1Path).getBytes());
        curatorFramework.delete().deletingChildrenIfNeeded().withVersion(stat1.getVersion()).forPath(taskPath);

        Stat stat2 = curatorFramework.checkExists().forPath(taskPath);
        assert Objects.isNull(stat2);
    }

    @Test
    public void excerciseInterProcessMutex() throws Exception {
        InterProcessMutex mutex = new InterProcessMutex(curatorFramework, "/hello/mutex");
        mutex.acquire();



        mutex.release();
    }
}
