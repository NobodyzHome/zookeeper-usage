package com.mzq.zookeeper.test;

import com.alibaba.fastjson.JSON;
import com.mzq.zookeeper.launcher.MyApp;
import com.mzq.zookeeper.launcher.dao.repository.redis.StudentRepository;
import com.mzq.zookeeper.launcher.domain.Student;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.RetryOneTime;
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
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = MyApp.class)
public class ZookeeperExcercise {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperExcercise.class);

    @Autowired(required = false)
    private CuratorFramework curatorFramework;

    @Autowired
    private TaskExecutor taskExecutor;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;

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
    public void excerciseInterProcessMutex_upsert() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:12181", Long.valueOf(TimeUnit.MINUTES.toMillis(30)).intValue()
                , 3000000, new RetryOneTime(1000))) {
            client.start();

            InterProcessMutex mutex = new InterProcessMutex(client, "/hello/mutex");
            System.out.println("准备获取锁");
            mutex.acquire();
            System.out.println("获取到锁，目前所有等待获取锁的成员：" + mutex.getParticipantNodes());

            Thread.sleep(TimeUnit.SECONDS.toMillis(120));
            String id = "zhangsan1";
            Student student;
            if (studentRepository.existsById(id)) {
                student = studentRepository.findById(id).get();
                student.setAge(student.getAge() + 10);
                logger.info("已有指定id的数据，需要更新数据。id={}", id);
            } else {
                student = new Student();
                student.setAge(20);
                student.setName("张三");
                student.setId(id);
                logger.info("没有指定id的数据，需要插入数据。id={}", id);
            }
            studentRepository.save(student);

            mutex.release();
            System.out.println("释放锁，目前所有等待获取锁的成员：" + mutex.getParticipantNodes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void excerciseDistributedBarrier_saveData() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:12181", new RetryOneTime(1000))) {
            client.start();

            DistributedBarrier distributedBarrier = new DistributedBarrier(client, "/hello/barrier");
            distributedBarrier.setBarrier();

            Student student = new Student();
            student.setName("李四");
            student.setAge(30);
            studentRepository.save(student);
            stringRedisTemplate.opsForValue().set("studentId", student.getId());

            distributedBarrier.removeBarrier();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void excerciseDistributedBarrier_updateData() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:12181", new RetryOneTime(1000))) {
            client.start();

            DistributedBarrier distributedBarrier = new DistributedBarrier(client, "/hello/barrier");
            distributedBarrier.waitOnBarrier();

            String studentId = stringRedisTemplate.boundValueOps("studentId").get();
            Optional<Student> studentOptional = studentRepository.findById(studentId);
            if (studentOptional.isPresent()) {
                Student student = studentOptional.get();
                client.create().creatingParentContainersIfNeeded().withMode(CreateMode.PERSISTENT).forPath(ZKPaths.makePath("/students", studentId), student.getName().getBytes());
            }
        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    @Test
    public void excerciseDistributedDoubleBarrier_saveData() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:12181", new RetryOneTime(1000))) {
            client.start();

            for (int i = 1; i <= 10; i++) {
                Student student = new Student();
                student.setAge(i + 5);
                student.setName("test_" + i);
                client.create().creatingParentContainersIfNeeded().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath(ZKPaths.makePath("/student-list", "student-"), JSON.toJSONString(student).getBytes());
            }

            DistributedDoubleBarrier distributedDoubleBarrier = new DistributedDoubleBarrier(client, "/hello/doubleBarrier", 2);
            distributedDoubleBarrier.enter();

            distributedDoubleBarrier.leave();
            Set<String> studentIds = stringRedisTemplate.opsForSet().members("studentIds");
            for (String studentId : studentIds) {
                Student student = studentRepository.findById(studentId).get();
                System.out.println(JSON.toJSONString(student));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void excerciseDistributedDoubleBarrier_saveMongo() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:12181", new RetryOneTime(1000))) {
            client.start();

            DistributedDoubleBarrier distributedDoubleBarrier = new DistributedDoubleBarrier(client, "/hello/doubleBarrier", 2);
            distributedDoubleBarrier.enter();

            List<String> studentPathList = client.getChildren().forPath("/student-list");
            BoundSetOperations<String, String> studentSetOps = stringRedisTemplate.boundSetOps("studentIds");
            for (String studentNode : studentPathList) {
                String studentJson = new String(client.getData().forPath(ZKPaths.makePath("/student-list", studentNode)));
                Student student = JSON.parseObject(studentJson, Student.class);
                studentRepository.save(student);
                studentSetOps.add(student.getId());

                client.delete().inBackground((client1, event) -> {
                    logger.info("节点{}被删除了，类型：{}", event.getPath(), event.getType());
                    assert Objects.isNull(client1.checkExists().forPath(event.getPath()));
                }, taskExecutor).forPath(ZKPaths.makePath("/student-list", studentNode));
            }

            distributedDoubleBarrier.leave();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 总体来说，master选举的使用方式是多个zk客户端竞争获取Master，获取到Master的程序往一个公共的地方写数据，没有获取到Master的程序等待Master的释放。还有一个客户端从公共的地方获取数据，即代表获取的是当前master写入的数据，
     * 当master切换后，其他获取master的程序也往公共的地方写数据。这样，客户端只需要从公共的地方写数据，就能获取到最新的master写入的数据，而不用关心master是谁。因为客户端其实只关心在公共的地方写入的数据，而不关心master是谁。
     * 这样，就可以实现在客户端无感知的情况下切换master。
     */
    @Test
    public void excerciseLeaderLatch_client1() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:12181", new RetryOneTime(1000))) {
            client.start();

            LeaderLatch leaderLatch = new LeaderLatch(client, "/hello/leader");
            leaderLatch.addListener(new LeaderLatchListener() {
                @Override
                public void isLeader() {
                    Student student = new Student();
                    student.setAge(21);
                    student.setName("张三");

                    studentRepository.save(student);
                    stringRedisTemplate.opsForValue().set("leaderStudent", student.getId());
                }

                @Override
                public void notLeader() {
                    System.out.println("没有获取到leader");
                }
            });

            leaderLatch.start();
            leaderLatch.await();

            // 在这里放置一个死循环，代表着只要程序不异常中断或关闭，当前LeaderLatch都不会放弃Master
            while (true) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void excerciseLeaderLatch_client2() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:12181", new RetryOneTime(1000))) {
            client.start();

            LeaderLatch leaderLatch = new LeaderLatch(client, "/hello/leader");
            leaderLatch.addListener(new LeaderLatchListener() {
                @Override
                public void isLeader() {
                    Student student = new Student();
                    student.setAge(30);
                    student.setName("大叔");

                    studentRepository.save(student);
                    stringRedisTemplate.opsForValue().set("leaderStudent", student.getId());
                }

                @Override
                public void notLeader() {
                    System.out.println("没有获取到leader");
                }
            });

            leaderLatch.start();
            while (true) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void excerciseLeaderLatch_client3() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:12181", new RetryOneTime(1000))) {
            client.start();

            // 也可以不通过设置listener来设置获取到master的执行操作，只要能走到await方法后，就代表获取到master了
            LeaderLatch leaderLatch = new LeaderLatch(client, "/hello/leader");
            leaderLatch.start();
            leaderLatch.await();

            Student student = new Student();
            student.setAge(35);
            student.setName("又一村");
            studentRepository.save(student);
            stringRedisTemplate.opsForValue().set("leaderStudent", student.getId());

            while (true) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void excerciseTakeLeader() {
        threadPoolTaskScheduler.scheduleWithFixedDelay(() -> {
            String leaderStudentId = stringRedisTemplate.opsForValue().get("leaderStudent");
            if (StringUtils.isNotBlank(leaderStudentId)) {
                studentRepository.findById(leaderStudentId).ifPresent(student -> logger.info("当前获取到master的客户端注册的student是：{}", JSON.toJSONString(student)));
            } else {
                logger.info("当前还没有Master注册student");
            }
        }, 5000);

        // 如果没有参与者了，那么就需要把redis里的记录的master的student的id删除，也就是告诉客户端当前没有master了
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:12181", new RetryOneTime(1000));
             PathChildrenCache pathChildrenCache = new PathChildrenCache(client, "/hello/leader", false)) {
            client.start();

            pathChildrenCache.getListenable().addListener(((client1, event) -> {
                if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)) {
                    if (client1.getChildren().forPath("/hello/leader").isEmpty()) {
                        stringRedisTemplate.delete("leaderStudent");
                    }
                }
            }));
            pathChildrenCache.start();
            while (true) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}