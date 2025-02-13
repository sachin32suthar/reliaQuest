package com.reliaquest.api.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class EmployeeServiceLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeServiceLoggingAspect.class);

    @Before("execution(* com.reliaquest.api.service.impl.EmployeeServiceImpl.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        Object[] args = joinPoint.getArgs();

        logger.info("Method {} started with arguments: {}", methodName, Arrays.toString(args));
    }

    @AfterReturning("execution(* com.reliaquest.api.service.impl.EmployeeServiceImpl.*(..))")
    public void logAfter(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();

        logger.info("Method {} completed successfully.", methodName);
    }

    @AfterThrowing(pointcut = "execution(* com.reliaquest.api.service.impl.EmployeeServiceImpl.*(..))", throwing = "ex")
    public void logException(JoinPoint joinPoint, Exception ex) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();

        logger.error("Method {} threw an exception: {}", methodName, ex.getMessage());
    }
}
