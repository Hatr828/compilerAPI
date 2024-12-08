package com.compilerAPI.compilerAPI.API;

import lombok.Data;

import java.util.List;

@Data
public class FunctionRequest {
    private String function;
    private List<String> args;
}
