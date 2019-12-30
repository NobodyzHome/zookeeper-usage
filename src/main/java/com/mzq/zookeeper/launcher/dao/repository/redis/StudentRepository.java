package com.mzq.zookeeper.launcher.dao.repository.redis;

import com.mzq.zookeeper.launcher.domain.Student;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StudentRepository extends MongoRepository<Student, String> {

    List<Student> findByAgeBefore(int age);
}
