package com.mzq.zookeeper.launcher.web.config;

import com.alibaba.fastjson.support.spring.FastJsonJsonView;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;

/**
 * spring boot的优势在于按照spring boot的配置需求给出配置，spring自己就可以帮你完成bean组装。但这种配置有时也不太能满足个性化的需求，
 * 毕竟只能配置部分内容。
 * 以：spring.mvc.static-path-pattern属性举例，我们只能配置一个静态资源映射，但是如果我想配置其他静态资源映射，就可以实现WebMvcConfigurer接口，
 * 给出需要的配置。
 *
 * @author maziqiang
 */
@Component
public class MvcCustomerConfig implements WebMvcConfigurer {

    /**
     * 增加新的静态资源的映射
     *
     * @param registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/*").addResourceLocations("classpath:static/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
        localeChangeInterceptor.setParamName("ln");
        localeChangeInterceptor.setHttpMethods("GET", "POST");

        registry.addInterceptor(localeChangeInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/view/*").setViewName("myView");
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver cookieLocaleResolver = new CookieLocaleResolver();
        cookieLocaleResolver.setCookieName("locale");
        cookieLocaleResolver.setCookieMaxAge(120);
        cookieLocaleResolver.setDefaultLocale(Locale.US);

        return cookieLocaleResolver;
    }

    @Bean(name = "myView")
    public FastJsonJsonView fastJsonJsonView() {
        FastJsonJsonView fastJsonJsonView = new FastJsonJsonView();
        fastJsonJsonView.setExposePathVariables(true);
        fastJsonJsonView.setExposeContextBeansAsAttributes(true);

        return fastJsonJsonView;
    }
}
