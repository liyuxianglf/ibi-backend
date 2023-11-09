package com.yx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.model.entity.Chart;
import com.yx.service.ChartService;
import com.yx.mapper.ChartMapper;
import org.springframework.stereotype.Service;

/**
* @author lige
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2023-10-25 20:06:48
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

}




