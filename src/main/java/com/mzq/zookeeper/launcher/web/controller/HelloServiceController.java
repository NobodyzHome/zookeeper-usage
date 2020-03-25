package com.mzq.zookeeper.launcher.web.controller;

import com.mzq.zookeeper.launcher.domain.Student;
import com.mzq.zookeeper.launcher.service.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/hello/service")
public class HelloServiceController {

    @Autowired
    private HelloService helloService;

    @GetMapping("/student/before/{age}")
    public List<Student> studentsBeforeAge(@PathVariable int age, Boolean needValidate) {
        return Optional.ofNullable(needValidate).filter(Boolean.TRUE::equals)
                .map(x -> helloService.findByAgeBeforeValidated(age)).orElse(helloService.findByAgeBefore(age));
    }
}
