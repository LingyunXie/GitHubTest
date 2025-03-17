package com.youyi.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RecordOpLog {
    /**
     * 操作类型
     */
    OperationType opType() default OperationType.DEFAULT;

    /**
     * 是否是系统操作，记录 operatorId 和 operatorName
     */
    boolean system() default false;

    /**
     * 是否脱敏
     */
    boolean desensitize() default false;

    /**
     * 指定记录的参数字段
     */
    String[] fields() default {};
}