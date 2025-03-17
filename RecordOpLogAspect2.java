package com.youyi.runner.aspect;

import com.youyi.common.annotation.RecordOpLog;
import com.youyi.domain.audit.model.OperationLogExtraData;
import com.youyi.runner.util.OperationLogHelper;
import org.aspectj.lang.Aspect;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.annotation.Order;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
@Aspect
public class RecordOpLogAspect2  {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordOpLogAspect2.class);
    private static final ExpressionParser expressionParser = new SpelExpressionParser();
    private static ThreadPoolExecutor asyncRecordOpLogExecutor;
    private final OperationLogHelper operationLogHelper;


    @Autowired
    public RecordOpLogAspect(OperationLogHelper operationLogHelper) {
        this.operationLogHelper = operationLogHelper;
    }

    @Override
    public void onApplicationEvent(@Nonnull ApplicationReadyEvent event) {
        checkConfig(RECORD_OP_LOG_THREAD_POOL_CONFIG);
        initAsyncExecutor();
    }

    @Override
    public int getOrder() {
        return AspectOrdered.RECORD_OP_LOG.getOrder();
    }

    @Pointcut("@annotation(com.youyi.common.annotation.RecordOpLog)")
    public void pointCut() {}

    @AfterReturning(value = "pointCut()", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        processOperationLog(joinPoint, result, null);
    }

    @AfterThrowing(value = "pointCut()", throwing = "exception")
    public void afterThrowing(JoinPoint joinPoint, Throwable exception) {
        processOperationLog(joinPoint, null, exception);
    }

    private void processOperationLog(JoinPoint joinPoint, Object result, Throwable exception) {
        if (asyncRecordOpLogExecutor == null) {
            LOGGER.error("Async executor for recording operation logs is not initialized.");
            return;
        }
        try {
            final OperationLogDO operationLogDO = preRecordOpLog(joinPoint);
            asyncRecordOpLogExecutor.execute(() -> {
                buildOperationLogDO(joinPoint, operationLogDO, result, exception);
                doRecordOpLog(operationLogDO);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to submit async task to record operation log: {}", e.getMessage(), e);
        }
    }

    private OperationLogDO preRecordOpLog(JoinPoint jp) {
        MethodSignature methodSignature = (MethodSignature) jp.getSignature();
        RecordOpLog recordOpLog = methodSignature.getMethod().getAnnotation(RecordOpLog.class);
        OperationLogDO operationLogDO = new OperationLogDO();
        operationLogDO.setOperatorName(SYSTEM_OPERATOR_NAME);
        return operationLogDO;
    }

    private void buildOperationLogDO(JoinPoint jp, OperationLogDO operationLogDO, Object result, Throwable exception) {
        MethodSignature methodSignature = (MethodSignature) jp.getSignature();
        Method method = methodSignature.getMethod();
        RecordOpLog recordOpLog = method.getAnnotation(RecordOpLog.class);
        String className = jp.getTarget().getClass().getName();
        String[] parameterNames = methodSignature.getParameterNames();
        Class<?>[] parameterTypes = methodSignature.getParameterTypes();
        Object[] parameterValues = jp.getArgs();

        List<String> paramValues = filterFields(parameterNames, parameterValues, recordOpLog.fields(), recordOpLog.desensitize());
        List<String> paramTypes = Arrays.stream(parameterTypes).map(Class::getSimpleName).collect(Collectors.toList());

        OperationLogExtraData extraData = OperationLogExtraData.builder()
                .method(method.getName())
                .className(className)
                .argType(String.join(",", paramTypes))
                .argName(String.join(",", parameterNames))
                .argValue(String.join(",", paramValues))
                .build();

        if (exception != null) {
            // 记录异常信息
        } else if (result != null) {
            // 记录返回值
        }
        operationLogDO.setExtraData(extraData);
    }

    private List<String> filterFields(String[] parameterNames, Object[] parameterValues, String[] fields, boolean desensitize) {
        if (fields.length == 0) {
            if (!desensitize) {
                return Arrays.stream(parameterValues).map(Object::toString).collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
        Map<String, Object> fieldMap = new HashMap<>();
        for (String field : fields) {
            Object fieldValue = extractFieldValueWithSpEL(parameterNames, parameterValues, field);
            if (fieldValue != null) {
                fieldMap.put(field, fieldValue);
            }
        }
        return Collections.singletonList(GsonUtil.toJson(fieldMap));
    }

    private Object extractFieldValueWithSpEL(String[] parameterNames, Object[] parameterValues, String spelExpression) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], parameterValues[i]);
        }
        return expressionParser.parseExpression(spelExpression).getValue(context);
    }
}