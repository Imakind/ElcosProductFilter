package com.example.productfilter.dto;

public record ProductFilterDTO(
        Integer brandId,
        Integer groupId,
        Integer subGroupId,
        String param1,
        String param2,
        String param3,
        String param4,
        String param5,
        String keyword
) {}
