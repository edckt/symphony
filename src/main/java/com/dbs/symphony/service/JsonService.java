package com.dbs.symphony.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import org.springframework.stereotype.Service;

@Service
public class JsonService {
    private final ObjectMapper mapper;

    public JsonService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String toJson(UserQuotaSpecDto spec) {
        try {
            return mapper.writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize spec", e);
        }
    }

    public UserQuotaSpecDto fromJson(String json) {
        try {
            return mapper.readValue(json, UserQuotaSpecDto.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize spec", e);
        }
    }
}