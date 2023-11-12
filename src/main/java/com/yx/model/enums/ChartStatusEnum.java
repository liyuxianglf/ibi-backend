package com.yx.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图表状态枚举
 *
 */
public enum ChartStatusEnum {

    WAIT("等待状态", "wait"),
    RUNNING("执行状态", "running"),
    SUCCESS("执行成功", "success"),
    FAILED("执行失败", "failed");

    private final String name;

    private final String value;

    ChartStatusEnum(String text, String value) {
        this.name = text;
        this.value = value;
    }

    /**
     * 获取值列表
     *
     * @return
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static ChartStatusEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (ChartStatusEnum anEnum : ChartStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }

    public String getName() {
        return name;
    }
}
