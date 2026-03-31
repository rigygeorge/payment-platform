package com.paymentplatform.payment.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that logs performance of all service methods.
 *
 * Why AOP?
 * - Cross-cutting concern — performance logging has nothing to do
 *   with business logic. AOP keeps it completely separate.
 * - Zero changes to PaymentService, FraudDetectionService etc.
 *   They don't even know they're being timed.
 * - Add/remove logging by toggling one class — no business logic touched.
 *
 * @Around intercepts method call, measures time, logs if > threshold.
 */
@Slf4j
@Aspect
@Component
public class PerformanceLoggingAspect {

    private static final long WARNING_THRESHOLD_MS = 200;
    private static final long CRITICAL_THRESHOLD_MS = 1000;

    /**
     * Intercept all methods in service package.
     * Pointcut: any method, any return type, in any class under .service package
     */
    @Around("execution(* com.paymentplatform.payment.service.*.*(..))")
    public Object logPerformance(ProceedingJoinPoint joinPoint) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className  = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        long startTime = System.currentTimeMillis();

        try {
            // Proceed with actual method execution
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;

            // Log based on how slow the method was
            logResult(className, methodName, executionTime, null);

            return result;

        } catch (Exception ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            logResult(className, methodName, executionTime, ex);
            throw ex; // re-throw — don't swallow exceptions
        }
    }

    private void logResult(String className, String methodName,
                           long executionTime, Exception ex) {
        String label = className + "." + methodName;

        if (ex != null) {
            log.error("PERF [{}] FAILED in {}ms — error: {}",
                    label, executionTime, ex.getMessage());

        } else if (executionTime > CRITICAL_THRESHOLD_MS) {
            log.error("PERF [{}] CRITICAL — {}ms (threshold: {}ms)",
                    label, executionTime, CRITICAL_THRESHOLD_MS);

        } else if (executionTime > WARNING_THRESHOLD_MS) {
            log.warn("PERF [{}] SLOW — {}ms (threshold: {}ms)",
                    label, executionTime, WARNING_THRESHOLD_MS);

        } else {
            log.debug("PERF [{}] OK — {}ms", label, executionTime);
        }
    }
}