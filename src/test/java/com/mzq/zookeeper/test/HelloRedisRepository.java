package com.mzq.zookeeper.test;

import com.mzq.zookeeper.launcher.MyApp;
import com.mzq.zookeeper.launcher.dao.repository.redis.StudentRepository;
import com.mzq.zookeeper.launcher.domain.Student;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = MyApp.class)
public class HelloRedisRepository {

    @Autowired
    private StudentRepository studentRepository;

    @Test
    public void testSave() {
        Student student = new Student();
        student.setId("hello");
        student.setAge(15);
        student.setName("test");
        Student save = studentRepository.save(student);
        System.out.println(save);
    }
}
