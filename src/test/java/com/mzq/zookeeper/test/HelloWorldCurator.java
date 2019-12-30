package com.mzq.zookeeper.test;

import com.google.common.collect.Lists;
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.retry.RetryOneTime;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class HelloWorldCurator {

    @Test
    public void test1() {
        // 如果只想控制创建CuratorFramework时的基本信息，就使用CuratorFrameworkFactory.newClient直接创建即可。这种方式虽然简便，但不能控制创建时的细节
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:2182", new RetryNTimes(3, 1000))) {
            client.start();
            String pathCreated = client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath("/user/user-", "user1".getBytes());
            System.out.println(pathCreated);

            Stat dataStat = new Stat();
            client.getData().storingStatIn(dataStat).usingWatcher((Watcher) System.out::println).forPath(pathCreated);
            client.delete().deletingChildrenIfNeeded().forPath("/user");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        // 如果想更多的控制创建的CuratorFramework，可以使用CuratorFrameworkFactory.builder()调出Builder来，然后给这个builder赋值各种需要的参数
        // 如果创建CuratorFramework时，使用namespace方法赋值了，那么使用这个CuratorFramework时都是基于这个节点操作的。注意：namespace赋值不能以/开头
        try (CuratorFramework client = CuratorFrameworkFactory.builder().connectString("localhost:2182,localhost:2183").defaultData("this is a default data".getBytes())
                .authorization(Lists.newArrayList(new AuthInfo("digest", "hello:world".getBytes()), new AuthInfo("digest", "user:pass".getBytes())))
                .retryPolicy(new RetryOneTime(1000)).sessionTimeoutMs((int) TimeUnit.SECONDS.toMillis(5)).namespace("user").build()) {
            client.start();

            // 如果forPath方法没有给出节点的值的话，则使用创建CuratorFrameworkFactory时给出的defaultData
            String pathCreated = client.create().withMode(CreateMode.PERSISTENT_SEQUENTIAL).withACL(ZooDefs.Ids.CREATOR_ALL_ACL).forPath("/us-");
            Stat stat = new Stat();
            byte[] pathData = client.getData().storingStatIn(stat).usingWatcher((Watcher) event -> {
                System.out.println(event);
            }).forPath(pathCreated);
            String contents = new String(pathData);
            System.out.println(contents);

            Stat stat1 = new Stat();
            List<ACL> acls = client.getACL().storingStatIn(stat1).forPath(pathCreated);
            System.out.println(acls);

            Stat stat2 = client.checkExists().forPath(pathCreated);
            Stat stat3 = client.setData().withVersion(stat2.getVersion()).forPath(pathCreated, "haha".getBytes());

            client.getChildren().usingWatcher((CuratorWatcher) event -> {
                System.out.println(event);
            }).forPath(pathCreated);

            String statusPath = client.create().withMode(CreateMode.EPHEMERAL).forPath(pathCreated + "/status", "ok".getBytes());
            Stat stat4 = client.setData().forPath(statusPath, "error".getBytes());

            client.checkExists().usingWatcher((CuratorWatcher) event -> System.out.println(event)).forPath(statusPath);
            List<String> children = client.getChildren().storingStatIn(stat4).forPath("/");
            System.out.println(children);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {
        try (CuratorFramework client = CuratorFrameworkFactory.builder().authorization("digest", "hello:world".getBytes()).retryPolicy(new RetryNTimes(2, 1000))
                .connectString("localhost:2182").defaultData("user default".getBytes()).build()) {
            client.start();
            String pathCreated = client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).withACL(ZooDefs.Ids.CREATOR_ALL_ACL, true).forPath("/coco/uouo/lala", "big boy".getBytes());
            Stat pathStat = client.checkExists().usingWatcher((CuratorWatcher) event -> System.out.println(String.format("发生了事件。节点：%s，事件类型：%s", event.getPath(), event.getType()))).forPath(pathCreated);
            Stat stat = client.setData().withVersion(pathStat.getVersion()).forPath(pathCreated, "hoho".getBytes());
            List<ACL> acls = client.getACL().forPath(pathCreated);
            System.out.println(acls);
            Stat stat1 = client.setACL().withVersion(stat.getAversion()).withACL(Collections.singletonList(new ACL(ZooDefs.Perms.ADMIN, new Id("digest", "user:pass")))).forPath(pathCreated);
            try {
                List<ACL> acls1 = client.getACL().forPath(pathCreated);
            } catch (Exception e) {
                System.out.println("此时会报错，因为没有权限访问该节点");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        try (CuratorFramework client = CuratorFrameworkFactory.builder().connectString("localhost:2184").defaultData("default node data".getBytes())
                .retryPolicy(new RetryOneTime(1000)).authorization("digest", "hello:world".getBytes()).namespace("user").build()) {
            client.start();
            for (int i = 1; i <= 10; i++) {
                // 返回的是被创建的节点的路径，不包括父级路径
                String userCreated = client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT_SEQUENTIAL).withACL(ZooDefs.Ids.CREATOR_ALL_ACL, true).forPath("/new-us-", ("new user " + i).getBytes());
                client.create().withMode(CreateMode.EPHEMERAL).forPath(userCreated + "/status", "ok".getBytes());
            }
            // 该方法只会列出当前节点的子节点，不会列出当前节点的孙子节点，并且返回的内容不是完整的节点路径，而是子节点的名称
            List<String> children = client.getChildren().forPath("/");
            System.out.println(children);

            for (String node : children) {
                Stat stat = new Stat();
                byte[] nodeData = client.getData().storingStatIn(stat).usingWatcher((Watcher) event -> System.out.println("节点数据被改变了。节点：" + event.getPath() + "，事件类型：" + event.getType())).forPath("/" + node);
                String nodeContents = new String(nodeData);

                client.setData().withVersion(stat.getVersion()).forPath("/" + node, (node + " new data").getBytes());
                byte[] nodeDataNew = client.getData().forPath("/" + node);
                String nodeContentsNew = new String(nodeDataNew);
                System.out.println(String.format("节点：%s，老数据：%s，新数据：%s", node, nodeContents, nodeContentsNew));
            }

            Stat stat = client.checkExists().forPath("/");
            // 如果设置了deletingChildrenIfNeeded，那么该节点和该节点下的所有子节点（包括孙子节点等等）都会被删除，直到指定节点因为没有子节点了而可以被删除
            client.delete().deletingChildrenIfNeeded().withVersion(stat.getVersion()).forPath("/");

            Stat stat1 = client.checkExists().forPath("/");
            assert Objects.isNull(stat1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test5() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:2182", new RetryOneTime(1000))) {
            client.start();
            // inBackground是走异步来执行set命令，并在set命令执行完成后，调用传入的callback。在callback中event会告诉事件的类型（例如是删除节点、增加子节点等）和执行结果
            Stat stat = client.setData().inBackground((client1, event) -> System.out.println(event)).withUnhandledErrorListener((msg, throwable) -> System.out.println(msg)).forPath("/task/hsdf", "yoyo".getBytes());
            System.out.println(stat);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
