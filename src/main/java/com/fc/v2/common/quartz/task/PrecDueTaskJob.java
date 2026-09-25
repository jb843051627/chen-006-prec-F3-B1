package com.fc.v2.common.quartz.task;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fc.v2.model.custom.PrecDueRunResult;
import com.fc.v2.service.ITPrecDueTaskService;

/**
 * 证照满期提醒的周期执行入口（唯一入口）。
 * 调度器（invoke_target: precDueTaskJob.run()）触发后走业务服务层；
 * 本轮结果以服务返回的结果账为准，这里只照账说话，不另起算法。
 * 前台不开新增/编辑口子，也不许直接改这个落点。
 *
 * @author fuce
 * @date 2026-09-24
 */
@Component("precDueTaskJob")
public class PrecDueTaskJob {

    private static final Logger log = LoggerFactory.getLogger(PrecDueTaskJob.class);

    @Autowired
    private ITPrecDueTaskService precDueTaskService;

    /** 周期执行：跑一轮，结果以业务服务层返回为准 */
    public void run() {
        PrecDueRunResult result = precDueTaskService.runOnce(new Date(), new PrecDueRunResult());
        log.info("证照满期提醒周期执行：{}", result.summary());
    }
}
