package com.mzq.zookeeper.test;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HelloWorld {

    @Test
    public void test1() throws IOException {
        try (ZooKeeper zookeeper = new ZooKeeper("localhost:2181,localhost:2182,localhost:2183", 100000, System.out::println)) {
            Stat helloTestStat = zookeeper.exists("/helloTest", true);
            if (Objects.isNull(helloTestStat)) {
                zookeeper.create("/helloTest", "hoho".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                zookeeper.delete("/helloTest", helloTestStat.getVersion());
            }

            // 如果create方法中传入callback，那么就是调用的异步方法，当create创建完成后，会调用callback
            zookeeper.create("/hello", "heihei".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL, (rc, path, ctx, name) -> {
                // 传入的path属性值就是调create方法传入的第一个参数，在这里就是"/hello"；name属性值一般情况下和path属性值是相同的，唯一不同的是创建sequence节点，那么此时name属性值是真正创建的节点，在这里可以是"/hello00000000012"这种。
                System.out.println(String.format("rc=%d,path=%s,ctx=%s,name=%s", rc, path, ctx, name));
            }, new Date());

             /*
                 在zookeeper的api中，对于节点的操作（例如create、exists），凡是需要传入Callback对象的方法基本上是异步的方法，不传callback的是同步的方法。
                 当调用同步的方法时，只有调用zookeeper服务器获取到结果后，才会返回给调用方，并且把返回结果也返回给调用方。
                 当调用异步的方法时，zookeeper的实现应该是分出一个线程来执行zookeeper服务器的操作，对于api方法的调用方来说，会很快的回到调用方，而且调用方此时还不知道zookeeper的服务器的响应数据。
                 等zookeeper服务器响应后，会在新的线程里执行传入的Callback对象，并且把服务器的响应结果传给Callback对象。
             */
            Stat yoyoStat = zookeeper.exists("/yoyoheihei", true);
            zookeeper.create("/yoyoheihei", "hoho".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            System.out.println(yoyoStat);

            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try (ZooKeeper zooKeeper = new ZooKeeper("localhost:2181", 100000, null)) {
            List<String> children = zooKeeper.getChildren("/", false);
            List<String> nodeList = children.stream().filter(node -> !node.equals("zookeeper")).collect(Collectors.toList());
            for (String node : nodeList) {
                String path = "/" + node;
                Stat nodeStat = zooKeeper.exists(path, System.out::println);
                List<String> children1 = zooKeeper.getChildren(path, false);
                if (Objects.isNull(children1) || children1.isEmpty()) {
                    zooKeeper.delete(path, nodeStat.getVersion());
                }
            }
        } catch (InterruptedException | IOException | KeeperException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {
        try (ZooKeeper zooKeeper = new ZooKeeper("localhost:2181,localhost:2182,localhost:2183", 1000000, null)) {
            Stat taskStat = zooKeeper.exists("/task", System.out::println);
            if (Objects.nonNull(taskStat)) {
                // 注意：此处在/task节点设置了监听器，当在/task节点下创建或删除节点时，会产生事件并触发/task节点上的事件监听器，同时还会删除/task节点的事件监听器。
                // 因此当第一次进行/task节点下的节点创建时，会触发事件监听器，后面再创建节点，由于没有再给/task注册事件监听器，就不会触发事件监听器了。
                List<String> children = zooKeeper.getChildren("/task", event -> {
                    System.out.println(event);
                });
                for (int i = 1; i <= 10; i++) {
                    zooKeeper.create("/task/task-", String.format("task-%d", i).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL,
                            (rc, path, ctx, name) -> {
                                System.out.println(String.format("rc=%d，path=%s，ctx=%s，name=%s", rc, path, ctx, name));
                            }, new Date());
                }

            }

            Thread.sleep(TimeUnit.MINUTES.toMillis(5));
        } catch (InterruptedException | IOException | KeeperException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        try (ZooKeeper zooKeeper = new ZooKeeper("localhost:2181,localhost:2182,localhost:2183", 1000000, System.out::println)) {
            zooKeeper.create("/user", "user list".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            for (int i = 1; i <= 10; i++) {
                zooKeeper.getChildren("/user", event -> {
                    System.out.println("data change event:" + event);
                });
                String pathCreated = zooKeeper.create("/user/user-", ("user" + i).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
                Stat pathStat = zooKeeper.exists(pathCreated, false);
                System.out.println(String.format("%s的拥有人：%s", pathCreated, pathStat.getEphemeralOwner()));
            }
        } catch (InterruptedException | IOException | KeeperException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test5() {
        try (ZooKeeper zooKeeper = new ZooKeeper("localhost:2181,localhost:2182,localhost:2183", 1000000, System.out::println)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            // 当前程序注册了向zookeeper注册了一个该客户端对/user目录的子节点变化事件监听器，其他zookeeper客户端对/user节点进行删除或添加子节点操作时，当前程序便会收到节点变动事件。
            zooKeeper.getChildren("/user", event -> {
                System.out.println(String.format("event:%s,path:%s", event.getType(), event.getPath()));
                countDownLatch.countDown();
            });
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (InterruptedException | IOException | KeeperException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test6() {
        try (ZooKeeper zooKeeper = new ZooKeeper("localhost:2181", 1000000, null)) {
            Stat userStat = zooKeeper.exists("/user", false);
            // 故意让version值不对，执行时则会报异常：org.apache.zookeeper.KeeperException$BadVersionException: KeeperErrorCode = BadVersion for /user
            Stat stat = zooKeeper.setData("/user", "hello".getBytes(), userStat.getVersion() + 10);
            System.out.println(stat);
        } catch (InterruptedException | IOException | KeeperException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test7() {
        try (ZooKeeper zooKeeper = new ZooKeeper("localhost:2181", 100000, null)) {
            zooKeeper.addAuthInfo("digest", "user:pass".getBytes());
            zooKeeper.addAuthInfo("digest", "five:six".getBytes());
            zooKeeper.create("/info", "info-contents".getBytes(), ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
        } catch (InterruptedException | IOException | KeeperException e) {
            e.printStackTrace();
        }

        // 新开启一个会话
        try (ZooKeeper zooKeeper = new ZooKeeper("localhost:2181", 100000, null)) {
            try {
                // 新开启的会话中由于还没有注册账号，因此此时没有权限访问info节点，所以此时执行set操作会报错：org.apache.zookeeper.KeeperException$NoAuthException: KeeperErrorCode = NoAuth for /info
                zooKeeper.setData("/info", "info list".getBytes(), 0);
            } catch (Exception e) {
                System.out.println("设置当前节点数据失败，因为当前会话没有对/info节点的访问权限");
            }
            // 为当前会话注册账号
            zooKeeper.addAuthInfo("digest", "five:six".getBytes());
            // 由于注册了/info节点需要的访问账号，因此此时就可以访问/info节点了，同时/info节点允许任何操作，所以set操作可以被执行
            zooKeeper.setData("/info", "info list".getBytes(), 0);
        } catch (InterruptedException | IOException | KeeperException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test8() {
        try (ZooKeeper zooKeeper = new ZooKeeper("localhost:2181", 100000, null)) {
            zooKeeper.create("/log", "log list".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            zooKeeper.setACL("/log", Collections.singletonList(new ACL(ZooDefs.Perms.READ, new Id("digest", "three:nCYNKztt6KI9VqbpYEKbVmKu7xI="))), 0);
            try {
                zooKeeper.getACL("/log", null);
            } catch (KeeperException | InterruptedException e) {
                System.out.println("获取/log节点的acl失败，因为当前会话不能访问/log节点");
            }
            zooKeeper.addAuthInfo("digest", "three:four".getBytes());
            List<ACL> aclList = zooKeeper.getACL("/log", null);
            byte[] logData = zooKeeper.getData("/log", null, null);
            String logStr = new String(logData);
            System.out.println(logStr);

            try {
                zooKeeper.setData("/log", "hello".getBytes(), 0);
            } catch (KeeperException | InterruptedException e) {
                System.out.println("设置log节点错误，因为该节点不允许set操作");
            }
        } catch (InterruptedException | IOException | KeeperException e) {
            e.printStackTrace();
        }
    }
}
