package com.mzq.zookeeper.test;

import com.alibaba.fastjson.JSON;
import com.mongodb.client.MongoDatabase;
import com.mzq.zookeeper.launcher.MyApp;
import com.mzq.zookeeper.launcher.dao.repository.redis.StudentRepository;
import com.mzq.zookeeper.launcher.domain.Student;
import org.apache.commons.lang3.RandomUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.apache.curator.framework.recipes.atomic.PromotedToLock;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.support.collections.DefaultRedisList;
import org.springframework.data.redis.support.collections.DefaultRedisSet;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = MyApp.class)
public class HelloWorldCuratorRecipes {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final Logger logger = LoggerFactory.getLogger(HelloWorldCuratorRecipes.class);

    @Test
    public void testNodeCache() {
        /*
            NodeCache用于监控指定节点的创建、修改操作，当该节点被创建或修改后，会执行注册的NodeCacheListener（注意：也就是说，NodeCache可以对当前zk服务器中不存在的节点进行监控，当监控的节点被创造时，执行NodeCacheListener）
            与普通的为节点添加watcher不同，使用NodeCache会一直对节点进行监听，而不是像watcher那样被触发后就被删除了。
            所谓cache，代表着NodeCache在启动后（执行start方法），就会在本地存一份该节点的快照，当zk服务器中该节点的数据发生变化后，NodeCache就会发现本地快照和zk服务器上的不一致，就会执行NodeCacheListener。

            注意：NodeCacheListener的一个不方便的地方是NodeCacheListener方法没有任何入参，想获取节点数据只能在实现中依靠内部类的特性来获取方法外部类引用的NodeCache对象。
         */
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:2182", new RetryOneTime(1000));
             NodeCache nodeCache = new NodeCache(client, "/user-list")) {
            client.start();
            nodeCache.start();

            nodeCache.getListenable().addListener(() -> System.out.println(String.format("节点%s发生了变化，新数据：%s，version：%d"
                    , nodeCache.getPath(), new String(nodeCache.getCurrentData().getData()), nodeCache.getCurrentData().getStat().getVersion())));
            // 创建和修改指定节点会触发NodeCacheListener执行
            client.create().withMode(CreateMode.PERSISTENT).forPath("/user-list", "user info".getBytes());
            client.setData().forPath("/user-list", "hoho".getBytes());

            // NodeCache指定的节点下创建和删除子节点不会触发NodeCacheListener执行
            client.create().withMode(CreateMode.EPHEMERAL).forPath("/user-list/lisi", "hoho".getBytes());
            client.delete().forPath("/user-list/lisi");

            // NodeCache指定的节点被删除，也不会触发NodeCacheListener执行
            // 总结：NodeCache只有指定节点被创建或删除时才会被触发
            client.delete().forPath("/user-list");

            Thread.sleep(3000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPathChildrenCache() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:2182", new RetryOneTime(1000));
             PathChildrenCache pathChildrenCache = new PathChildrenCache(client, "/users", true)) {
            client.start();
            // PathChildrenCache有一个特殊的地方是，当PathChildrenCache启动后，如果PathChildrenCache中给定的节点不存在，会自动创建该节点
            pathChildrenCache.start();

            pathChildrenCache.getListenable().addListener((cli, event) -> {
                PathChildrenCacheEvent.Type eventType = event.getType();
                System.out.println(String.format("节点%s发生了%s事件", event.getData().getPath(), eventType));
            });

            pathChildrenCache.getListenable().addListener((cli, event) -> System.out.println("节点发生了：" + event.getType() + " 事件"));

            /*
                PathChildrenCache监控的是指定节点的子节点，孙子节点的增、删、改不会触发PathChildrenCacheListener执行
             */
            // /users节点自身的修改不会触发PathChildrenCacheListener执行
            client.setData().forPath("/users", "user list".getBytes());

            // /users节点的子节点的增加、修改会触发PathChildrenCacheListener执行
            String childPath = client.create().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath("/users/user-");
            client.setData().forPath(childPath, "hoho".getBytes());

            // /users节点的孙子节点的增、删、改不会触发PathChildrenCacheListener执行
            String grandsonPath = client.create().forPath(ZKPaths.makePath(childPath, "/status"));
            client.setData().forPath(grandsonPath, "ok".getBytes());
            client.delete().forPath(grandsonPath);

            // /users节点的子节点的删除会触发PathChildrenCacheListener执行
            client.delete().forPath(childPath);

            // /users节点自身被删除不会触发PathChildrenCacheListener执行
            client.delete().forPath("/users");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTreeCache() {
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        /*
            TreeCache是针对指定节点下的全部树结构进行监控，也就是既监控指定节点的增、删、改操作，也监控指定节点下的所有后代节点的增、删、改操作。
         */
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:2182", new RetryOneTime(1000));
             TreeCache treeCache = TreeCache.newBuilder(client, "/users").setExecutor(executorService).build()) {
            client.start();
            treeCache.start();

            // 添加第一个监听器
            treeCache.getListenable().addListener((cli, event) -> {
                String action = null;
                switch (event.getType()) {
                    case NODE_ADDED: {
                        action = "创建";
                        break;
                    }
                    case NODE_REMOVED: {
                        action = "删除";
                        break;
                    }
                    case NODE_UPDATED: {
                        action = "修改";
                        break;
                    }
                }
                if (Objects.nonNull(event.getData())) {
                    System.out.println(String.format("节点%s被%s了，最新数据：%s", event.getData().getPath(), action, new String(event.getData().getData())));
                } else {
                    System.out.println(event.getType());
                }
            });
            // 添加第二个监听器
            treeCache.getListenable().addListener((cli, event) -> System.out.println("发生了：" + event.getType() + " 事件"), Executors.newSingleThreadExecutor());

            // /users节点自身的变动以及/users节点的所有后代节点的变动都会触发TreeCacheListener。所以以下操作都会触发TreeCacheListener。
            // 创建和修改当前节点
            String pathCreated = client.create().withMode(CreateMode.PERSISTENT).forPath("/users", "user-list".getBytes());
            client.setData().forPath(pathCreated, "hoho".getBytes());

            // 创建和修改子节点
            String childPath = client.create().forPath(ZKPaths.makePath(pathCreated, "zhangsan"), "张三".getBytes());
            client.setData().forPath(childPath, "zhang san".getBytes());

            // 创建、修改、删除孙子节点
            String grandsonPath = client.create().forPath(ZKPaths.makePath(childPath, "status"), "ok".getBytes());
            client.setData().forPath(grandsonPath, "error".getBytes());
            client.delete().forPath(grandsonPath);

            // 删除子节点和当前节点
            client.delete().forPath(childPath);
            client.delete().forPath(pathCreated);
            Thread.sleep(3000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        executorService.shutdown();
    }

    @Test
    public void testLeaderSelector() {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try (CuratorFramework client = CuratorFrameworkFactory.builder().connectString("localhost:2182,localhost:2183,localhost:2184").retryPolicy(new RetryOneTime(1000))
                .defaultData("default node data".getBytes()).runSafeService(executorService).build()) {
            client.start();

            LeaderSelector leaderSelector = new LeaderSelector(client, "/master", new LeaderSelectorListenerAdapter() {
                @Override
                public void takeLeadership(CuratorFramework client) {
                    System.out.println(String.format("线程%s获取到了master", Thread.currentThread()));
                }
            });

            // 如果设置了autoRequeue，那么当前客户端获取了/master并执行完takeLeadership方法后，当前客户端删除完/master下的对应节点后，会重新创建一个新的节点，用于再一次有机会获取master。
            // 说白了就是让当前客户端获取master并执行完相应处理后，继续再有机会获取master
            // leaderSelector.autoRequeue();

            // 当LeaderSelector启动会，就会新起一个线程并尝试创建/master节点（由leaderPath变量控制），然后所有参与竞争的客户端都会在/master下创建临时顺序节点。当一个客户端获取/master后，该客户端会执行takeLeadership方法。
            // 当客户端执行完takeLeadership方法后，默认会在/master下删除对应的子节点，代表当前客户端获取/master并执行完成后，就不再参与获取/master了。
            leaderSelector.start();

            Thread.sleep(TimeUnit.MINUTES.toMillis(10));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 可以开启多个junit测试，也就是开启了多个JVM，就发现lock.acquire后的代码，在同一时间只能有一个JVM执行，实现了跨JVM之间的同步
     */
    @Test
    public void testInterProcessMutex() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:2182", new RetryOneTime(1000))) {
            InterProcessMutex lock = new InterProcessMutex(client, "/hello/locks");
            client.start();

            lock.acquire();

            System.out.println(String.format("线程%s获取到了分布式锁", Thread.currentThread().getName()));
            Collection<String> participantNodes = lock.getParticipantNodes();
            System.out.println(String.join(",", participantNodes));

            lock.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testDistributedAtomicInteger() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:2182", new RetryOneTime(1000))) {
            client.start();
            // DistributedAtomicInteger在进行add等操作时，会先进行一次乐观操作，如果乐观操作不成功，并且设置了PromotedToLock，那么会创建InterProcessMutex进行一次悲观操作。
            // 因此如果需要DistributedAtomicInteger进行悲观操作，则必须创建PromotedToLock对象
            DistributedAtomicInteger atomicInteger = new DistributedAtomicInteger(client, "/hello/atomicInteger"
                    , new RetryOneTime(1000), PromotedToLock.builder().lockPath("/hello/atomicInteger/locks").build());
            boolean initialize = atomicInteger.initialize(30);
            AtomicValue<Integer> atomicValue1 = atomicInteger.add(10);
            AtomicValue<Integer> atomicValue2 = atomicInteger.decrement();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * DistributedBarrier主要用来让两个JVM执行有一个先后顺序，即一个JVM是否能执行取决于另一个JVM的处理是否完毕。
     * 具体场景：
     * 假如有一个JVM的处理是在库里增加一条数据，而另一个JVM的处理是取出增加的数据，并对其进行更新。这样这两个JVM的执行就应该有一个先后顺序了，
     * JVM_A执行完了以后，JVM_B才能执行。所以JVM_A在执行业务逻辑之前，先使用DistributedBarrier设置一个屏障点，然后在业务逻辑执行完毕后，再删除这个屏障点。
     * 而JVM_B在执行业务逻辑之前，先等待屏障点，当没有屏障点时，说明JVM_A已经处理完毕了，此时JVM_B就可以处理业务逻辑了。
     * <p>
     * JVM_B在等待屏障点时：
     * 1.如果有屏障点，说明此时JVM_A的业务逻辑还没有处理完，那么此时JVM_B不应该执行，直到JVM_A的业务处理完毕
     * 2.如果没有屏障点，说明此时JVM_A的业务已经处理完了，JVM_B可以执行业务逻辑了
     * <p>
     * 但其实这里有一个隐含的保证，就是要保证JVM_A肯定会在JVM_B之前先执行，如果JVM_B先执行了，此时JVM_A还没有执行执行，就没有设置屏障点，
     * 那么JVM_B执行时，发现没有屏障点，就直接执行了，实际上JVM_B此时还不应该执行。这个使用DistributedBarrier就不能保证了，可以转而使用
     * DistributedDoubleBarrier来进行保证。
     * <p>
     * 总结：
     * DistributedBarrier适用于JVM1肯定会在JVM2之前执行，并且需要JVM2在JVM1执行完成以后再执行的场景。
     */
    @Test
    public void testDistributedBarrier_saveData() {
        /*
            DistributedBarrier的思路是设置一个公共屏障点，其他客户端在进行是否要因为公共屏障点而阻塞的检查时，判断公共屏障点是否存在，如果公共屏障点存在则阻塞，如果公共屏障点不存在则不会被阻塞。
            目的就是当一个JVM设置了屏障点后，其他JVM因为发现已经有了屏障点而阻塞，待一个JVM认为可以继续执行了以后，把屏障点删除，其他JVM则由于屏障点被删除了而解除阻塞。

            实现方式：
            1.一个JVM在zk中设置公共屏障点节点，DistributedBarrier.setBarrier()
            2.其他JVM在执行waitOnBarrier时，判断节点是否存在（此时会注册watcher，用于唤醒线程），如果不存在则无需阻塞；如果存在，则使用Object.wait进入阻塞
            3.当节点被修改、删除时，阻塞中的JVM会收到该节点的watcher事件，然后另开启一个线程，这个线程会notify被阻塞的线程
            4.从wait方法中解除阻塞，恢复运行的线程，再次判断公共屏障点节点是否存在，继续走到第2点
         */
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:9181", new RetryNTimes(3, 1000))) {
            client.start();

            DistributedBarrier distributedBarrier = new DistributedBarrier(client, "/hello/barrier");
            distributedBarrier.setBarrier();

            Queue<String> myQueue = new DefaultRedisList<>("my-queue", redisTemplate);
            for (int i = 1; i <= 10; i++) {
                String e = "test" + i;
                myQueue.add(e);
                logger.info("添加了元素：{}", e);
            }

            distributedBarrier.removeBarrier();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testDistributedBarrier_updateData() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:9181", new RetryOneTime(1000))) {
            client.start();
            DistributedBarrier distributedBarrier = new DistributedBarrier(client, "/hello/barrier");
            // 走到这步时，当前线程会判断是否有公共屏障点节点，如果没有则不会被阻塞，如果有的话该线程则会被阻塞，直到其他JVM把公共屏障点节点删除，当前JVM会收到节点删除的watcher，在该watcher的处理中会唤醒被阻塞的节点
            distributedBarrier.waitOnBarrier();

            BoundListOperations<String, String> listOps = redisTemplate.boundListOps("my-queue");
            List<String> list = new DefaultRedisList<>("my-queue-updated", redisTemplate);
            String pop;
            while (Objects.nonNull(pop = listOps.leftPop())) {
                String updated = pop.concat("_updated");
                list.add(updated);
                logger.info("{}被更新为：{}", pop, updated);
            }
            System.out.println(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * DistributedDoubleBarrier是一个双向的公共屏障点，即进入和离开公共屏障点时，都需要等待指定数量客户端到达或离开公共屏障点，线程才可以继续执行，否则会被阻塞。
     * DistributedDoubleBarrier的一个经典场景就是现在有多个JVM，一个JVM用于生产数据，其他JVM用于更新数据。那么其他JVM需要等生产数据的JVM执行完毕才可以执行。如果其他JVM
     * 先于生产数据的JVM执行，那么在走到公共屏障点时会被阻塞。只有生产数据的JVM生产完数据进入公共屏障点，并且其他JVM也做好处理数据的准备时（即进入了公共屏障点），所有JVM
     * 才能继续执行。少了哪一个都会被阻塞。
     * <p>
     * 当然这种方式也有一定的弊端，就是JVM_A生产数据，JVM_B和JVM_C接收数据，当JVM_A和JVM_B进入屏障点时，如果JVM_C因为某种原因没有进入屏障点，那么JVM_B就会被卡住，然而实际上
     * JVM_B和JVM_C没有关系，完全可以在JVM_A生产完数据后执行，没必要等JVM_C。
     * 所以，公共屏障点的数量设置的越小越好，防止因为某一个JVM没有到达屏障点，而让其他JVM一直阻塞下去。
     * <p>
     * 因此可以看到，在使用DistributedDoubleBarrier时，往往不同的JVM进入或离开公共屏障点的时机不一样，而DistributedDoubleBarrier让这些JVM能够互相等待，直到所有JVM都做好处理的
     * 准备后再一起执行。
     */
    @Test
    public void testDistributedDoubleBarrier_saveData() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:9181", new RetryOneTime(1000))) {
            client.start();

            Set<String> set = new DefaultRedisSet<>("my-set", redisTemplate);
            for (int i = 1; i <= 10; i++) {
                set.add("test-set-" + i);
            }

            /*
                DistributedDoubleBarrier.enter方法注意事项：
                1.当执行enter方法后，会在barrierPath下创建一个节点，代表着当前客户端存在于该barrier下
                2.当参与该barrier的客户端数量小于指定数量，则当前线程会阻塞
                3.当参与该barrier的数量大于或等于指定数量时，则当前线程不会被阻塞，并且所有因为参与该barrier而阻塞的线程都会被解除阻塞
                4.参与该barrier的数量大于或等于指定数量时，会在barrierPath指定的路径下创建一个/ready节点，代表当前barrier已满足
                5.当存在/ready节点时，所有再使用这个节点的DistributedDoubleBarrier执行enter方法都不会被阻塞。因此，使用enter方法后，应该使用leave方法进行退出
            */
            DistributedDoubleBarrier distributedDoubleBarrier = new DistributedDoubleBarrier(client, "/hello/doubleBarrier", 3);
            /*
             * 当程序执行到这里时，可以看到数据生产已经生成完毕，这时再进入公共屏障点，代表数据生产已经完毕，其余数据处理JVM进入到屏障点后，就可以继续执行了。
             */
            distributedDoubleBarrier.enter();

            /*
                DistributedDoubleBarrier.leave方法注意事项：
                1.leave会把enter时在barrier下创建的参与者节点删除
                2.如果barrier下还有其他参与节点，那么当前线程阻塞
                3.如果barrier下没有参与节点了，那么删除barrier下的ready节点，并且当前线程能继续执行，同时所有因为参与该barrier而阻塞的线程都会被解除阻塞

                此处由于程序设置了所有处理JVM在处理数据后才会离开公共屏障点，所以如果能走完leave方法，说明处理JVM都已经处理完数据了，就可以放心的获取处理的数据了。
            */
            distributedDoubleBarrier.leave();

            List<Student> studentList = studentRepository.findByAgeBefore(20);
            Set<String> updatedSet = redisTemplate.opsForSet().members("my-set-update");
            logger.info("studentList:{}", JSON.toJSONString(studentList));
            logger.info("updatedSet:{}", JSON.toJSONString(updatedSet));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void DistributedDoubleBarrier_updateData() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:9181", new RetryOneTime(1000))) {
            client.start();

            DistributedDoubleBarrier distributedDoubleBarrier = new DistributedDoubleBarrier(client, "/hello/doubleBarrier", 3);
            // 由于通过程序设置了生产JVM生产完数据后才会进入公共屏障点，因此如果能执行完enter方法，那么生产JVM必然生产完数据了，后面的程序就可以获取生产的数据并处理了。
            distributedDoubleBarrier.enter();

            Set<String> set = new DefaultRedisSet<>("my-set", redisTemplate);
            Set<String> updateSet = new DefaultRedisSet<>("my-set-update", redisTemplate);
            set.stream().map(e -> e.concat("_updated")).forEach(updateSet::add);

            /*
             * 当处理JVM离开屏障点时，说明数据处理已经完毕。此时如果想继续执行下去，需要数据生产JVM和其他处理JVM都准备离开公共屏障点
             */
            distributedDoubleBarrier.leave();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void DistributedDoubleBarrier_transferData() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:9181", new RetryOneTime(1000))) {
            client.start();

            DistributedDoubleBarrier distributedDoubleBarrier = new DistributedDoubleBarrier(client, "/hello/doubleBarrier", 3);
            distributedDoubleBarrier.enter();

            BoundSetOperations<String, String> setOps = redisTemplate.boundSetOps("my-set");
            Set<String> members = setOps.members();
            for (String member : members) {
                Student student = new Student();
                student.setAge(RandomUtils.nextInt(1, 50));
                student.setName(member);
                studentRepository.save(student);
            }

            distributedDoubleBarrier.leave();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
