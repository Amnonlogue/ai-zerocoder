package com.it.aizerocoder.controller;

import com.it.aizerocoder.common.BaseResponse;
import com.it.aizerocoder.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> health(){
        return ResultUtils.success("ok");
    }
}
