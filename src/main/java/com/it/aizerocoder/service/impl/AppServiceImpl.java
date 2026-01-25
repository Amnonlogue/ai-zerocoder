package com.it.aizerocoder.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.it.aizerocoder.model.entity.App;
import com.it.aizerocoder.mapper.AppMapper;
import com.it.aizerocoder.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/Amnonlogue">wanghf</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}
