package com.mzq.zookeeper.launcher.service;

import com.mzq.zookeeper.launcher.dao.repository.redis.StudentRepository;
import com.mzq.zookeeper.launcher.domain.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Service
@Validated
@CacheConfig(cacheNames = {"myCache1", "produceCache"})
public class HelloService {

    @Autowired
    public StudentRepository studentRepository;

    @Cacheable(cacheNames = "myCache2", key = "methodName+'-'+#beforeAge")
    public List<Student> findByAgeBefore(int beforeAge) {
        return studentRepository.findByAgeBefore(beforeAge);
    }

    @NotEmpty
    @Size(max = 10)
    @Cacheable
    public List<Student> findByAgeBeforeValidated(@Min(10) @Max(30) int beforeAge) {
        return findByAgeBefore(beforeAge);
    }
}
