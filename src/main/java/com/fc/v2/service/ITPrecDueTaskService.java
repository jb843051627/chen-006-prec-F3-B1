package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TPrecDueTask;
import com.fc.v2.model.custom.PrecDueRunResult;

/**
 * 证照效期满期提醒条目 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITPrecDueTaskService {

    /** 按主键回查条目 */
    TPrecDueTask selectTPrecDueTaskById(Long id);

    /**
     * 挑条目那把尺：该时刻到点(满期日-提前天数 <= 当日)、未了结(0待开口/1已开口/2开不出去)、
     * 当日尚未出过声的条目。与落出声痕迹那条路认同一个"当日"，不许一头当日一头前一日。
     */
    List<TPrecDueTask> listDue(Date at);

    /** 执行一次，返回**成功条数**；单条失败跳过继续 */
    int runOnce(Date at);

    /**
     * 重载：执行一次，整轮结果账落进 sink 并返回（在不在窗口、出声几条、
     * 转值班员几条、卡住哪几条各挂什么事由）。周期执行以服务返回的结果为准。
     */
    PrecDueRunResult runOnce(Date at, PrecDueRunResult sink);
}
