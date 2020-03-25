package com.mzq.zookeeper.launcher.web.controller;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.mzq.zookeeper.launcher.dao.repository.redis.StudentRepository;
import com.mzq.zookeeper.launcher.domain.Student;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/hello")
public class HelloController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private MessageSource messageSource;

    @GetMapping("/world/{contents}")
    @ResponseBody
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String world(@PathVariable String contents) {
        return contents;
    }

    @GetMapping("/world/page/{contents}")
    public String page(@PathVariable String contents, Model model) {
        model.addAttribute("contents", contents).addAttribute("test", new Date()).addAttribute("hello", "world");
        model.addAttribute("names", Lists.newArrayList("张三", "李四", "王五"));
        model.addAttribute("user", "zhangsan");
        return "helloWorld";
    }

    @GetMapping("/takeLeader")
    public String takeLeader(Model model) {
        String leaderStudentId = redisTemplate.opsForValue().get("leaderStudent");
        Optional<Student> studentOptional = StringUtils.isNotBlank(leaderStudentId)
                ? studentRepository.findById(leaderStudentId) : Optional.empty();

        model.addAttribute("leaderStudent", studentOptional);
        return "takeLeader";
    }

    @GetMapping("/leaderStudent")
    @ResponseBody
    public Student leaderStudent() {
        String leaderStudentId = redisTemplate.opsForValue().get("leaderStudent");
        return StringUtils.isNotBlank(leaderStudentId) ? studentRepository.findById(leaderStudentId).orElse(null) : null;
    }

    @PostMapping("/requestBody")
    @ResponseBody
    public String requestBody(@Valid @RequestBody Student student, BindingResult studentErrors, @RequestHeader String accept, Locale locale) {
        String contents;
        if (studentErrors.hasErrors()) {
            contents = studentErrors.getFieldErrors().stream()
                    .map(error -> messageSource.getMessage(error, locale)).collect(Collectors.joining(","));
        } else {
            contents = student.getName();
        }

        return contents;
    }

    @GetMapping("/upload/index")
    public String uploadIndex() {
        return "upload";
    }

    @PostMapping("/upload")
    @ResponseBody
    public Student[] upload(String name, @RequestParam MultipartFile studentFile, @RequestPart("leaderStudentFile") Student student) throws IOException {
        byte[] bytes = studentFile.getBytes();
        Student student1 = JSON.parseObject(bytes, Student.class);
        return new Student[]{student, student1};
    }

    @GetMapping("/download")
    @ResponseBody
    public Resource download(String location, HttpServletResponse servletResponse) {
        String filename = org.springframework.util.StringUtils.getFilename(location);
        servletResponse.addHeader("content-disposition", "attachment;filename=" + filename);

        return new ClassPathResource(location);
    }

    @PostMapping("/validate")
    public String validate(@Valid Student student, BindingResult studentBindingResult) {
        return "validate";
    }
}
