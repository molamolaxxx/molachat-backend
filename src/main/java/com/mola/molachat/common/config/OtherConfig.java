package com.mola.molachat.common.config;

import com.mola.molachat.common.utils.SegmentLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 其他配置
 * @date : 2021-05-27 21:29
 **/
@Configuration
public class OtherConfig {

    @Bean
    public SegmentLock segmentLock() {
        SegmentLock segmentLock = new SegmentLock(15, true);
        return segmentLock;
    }
}
