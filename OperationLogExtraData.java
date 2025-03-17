package com.youyi.domain.audit.model;

public class OperationLogExtraData {
    private String method;
    private String className;
    private String argType;
    private String argName;
    private String errorMessage;
    private String returnValue;

    public static Builder builder() {
        return new OperationLogExtraData.Builder();
    }

    public static class Builder {
        private OperationLogExtraData extraData = new OperationLogExtraData();

        public OperationLogExtraData.Builder method(String method) {
            extraData.method = method;
            return this;
        }

        public OperationLogExtraData.Builder className(String className) {
            extraData.className = className;
            return this;
        }

        public OperationLogExtraData.Builder argType(String argType) {
            extraData.argType = argType;
            return this;
        }

        public OperationLogExtraData.Builder argName(String argName) {
            extraData.argName = argName;
            return this;
        }

        public OperationLogExtraData.Builder argValue(String argValue) {
            extraData.argValue = argValue;
            return this;
        }

        public OperationLogExtraData build() {
            return extraData;
        }
    }
}
