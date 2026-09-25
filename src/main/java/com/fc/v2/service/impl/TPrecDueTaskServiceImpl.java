package com.fc.v2.service.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TPrecDueTaskMapper;
import com.fc.v2.model.auto.TPrecDueTask;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.model.custom.PrecDueRunResult;
import com.fc.v2.service.ITPrecDueTaskService;
import com.fc.v2.service.ITSysUserService;

/**
 * 证照效期满期提醒条目 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * <p>规矩：
 * <br>1. 出声只在办公日白天开（周一至周五 09:00-17:00，半开区间：09:00 整点开，17:00 整点起算已下班）；
 *       钟点没到或过了下班点，一律不动，搁到下一个白天。
 * <br>2. 挑条目与落出声痕迹认同一把尺子：一轮只取一个"当日"(runDay)，两边都按它算，
 *       不许一头按当日、一头按前一日。
 * <br>3. 满期按日历日算：开口日 = 满期日 - 提前天数（自然日）。老数据只写了日期、落库即
 *       当日 00:00:00，与新录数据走同一段代码、同一把日历日尺子，钟点不挪开口日。
 * <br>4. 出过声的台账留痕（遍数+时刻+情形）；同一条证同一天不重复出声；没准信的隔一轮
 *       再催；催满三遍仍没回音的转值班员上门，系统不再出声。
 * <br>5. 卡住的（经手人空缺、经手人已调离）单拎出来挂事由，不拖住整轮，剩下的照扫不误。
 * <br>6. 准信不另开登记口：条目被业务侧了结（改情形/删标记/挪满期）即自然出列。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TPrecDueTaskServiceImpl implements ITPrecDueTaskService {

    private static final Logger log = LoggerFactory.getLogger(TPrecDueTaskServiceImpl.class);

    /** 出声窗口：上午九点整开 */
    private static final int WIN_FROM = 9;
    /** 出声窗口：下午五点整起算已下班（半开区间不含） */
    private static final int WIN_TO = 17;
    /** 催满几遍仍无回音即转值班员上门 */
    private static final int MAX_REMIND = 3;

    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_STUCK = 2;
    private static final int STATUS_ESCALATED = 3;

    @javax.annotation.Resource
    private TPrecDueTaskMapper precDueTaskMapper;

    @javax.annotation.Resource
    private ITSysUserService sysUserService;

    @Override
    public TPrecDueTask selectTPrecDueTaskById(Long id) {
        return this.precDueTaskMapper.selectById(id);
    }

    /**
     * 日历日统一走**墙钟字符串**（yyyy-MM-dd）：JDBC 的 serverTimezone 与本机时区不对称，
     * 直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String day(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd").format(d);
    }

    /** 日历日加减自然日（yyyy-MM-dd 字符串进，同格式出） */
    private static String addDays(String dayStr, int n) {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(dayStr));
            c.add(Calendar.DAY_OF_YEAR, n);
            return day(c.getTime());
        } catch (ParseException e) {
            throw new IllegalStateException("日历日格式不正: " + dayStr, e);
        }
    }

    /** 单条提前开口天数：空/负一律按 0（满期当日开口） */
    private static int advanceDays(TPrecDueTask r) {
        if (r.getAmount() == null || r.getAmount().signum() < 0) {
            return 0;
        }
        return r.getAmount().intValue();
    }

    /** 是否落在出声窗口：办公日（周一至周五）且钟点在 [09:00, 17:00) */
    private static boolean inWindow(Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
            return false;
        }
        int hour = c.get(Calendar.HOUR_OF_DAY);
        return hour >= WIN_FROM && hour < WIN_TO;
    }

    @Override
    public List<TPrecDueTask> listDue(Date at) {
        List<TPrecDueTask> due = new ArrayList<TPrecDueTask>();
        if (at == null) {
            return due;
        }
        String runDay = day(at);
        List<TPrecDueTask> open = this.precDueTaskMapper.selectList(new QueryWrapper<TPrecDueTask>()
                .eq("del_flag", 0)
                .in("status", STATUS_WAIT, STATUS_DONE, STATUS_STUCK)
                .orderByAsc("id"));
        for (TPrecDueTask r : open) {
            if (r.getDueAt() == null) {
                continue;
            }
            // 开口日 = 满期日 - 提前天数；当日 >= 开口日 即该出声（含已过期）
            String openDay = addDays(day(r.getDueAt()), -advanceDays(r));
            if (runDay.compareTo(openDay) < 0) {
                continue;
            }
            // 同一条证同一天不许接二连三：当日已出过声的不再挑
            if (r.getLastRemindAt() != null && day(r.getLastRemindAt()).equals(runDay)) {
                continue;
            }
            due.add(r);
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        return runOnce(at, new PrecDueRunResult()).getReminded();
    }

    @Override
    public PrecDueRunResult runOnce(Date at, PrecDueRunResult sink) {
        if (sink == null) {
            sink = new PrecDueRunResult();
        }
        // 窗口外一律不动：不挑、不扫、不碰台账，搁到下一个白天
        if (at == null || !inWindow(at)) {
            sink.setInWindow(false);
            return sink;
        }
        sink.setInWindow(true);
        for (TPrecDueTask r : listDue(at)) {
            try {
                handleOne(r, at, sink);
            } catch (Exception e) {
                // 单条失败跳过继续，别叫它把整轮拖住
                log.error("证照满期提醒单条处理失败 itemNo={}", r.getItemNo(), e);
                sink.addStuck(displayNo(r), "处理异常:" + e.getMessage());
            }
        }
        return sink;
    }

    /** 处理一条：先看出不出得去，再看该不该转上门，最后出声落痕 */
    private void handleOne(TPrecDueTask r, Date at, PrecDueRunResult sink) {
        String stuckReason = stuckReason(r);
        if (stuckReason != null) {
            markStuck(r, at, stuckReason);
            sink.addStuck(displayNo(r), stuckReason);
            return;
        }
        int count = r.getRemindCount() == null ? 0 : r.getRemindCount();
        if (count >= MAX_REMIND) {
            escalate(r, at);
            sink.addEscalated();
            return;
        }
        remind(r, at, count);
        sink.addReminded();
    }

    /** 卡住事由：经手那栏空着，或接话的人调去了别处（查无此账号）；出得去返回 null */
    private String stuckReason(TPrecDueTask r) {
        String handler = r.getHandler();
        if (handler == null || handler.trim().isEmpty()) {
            return "经手人空缺，待派活";
        }
        if (!handlerAlive(handler.trim())) {
            return "经手人[" + handler.trim() + "]已调离（查无此账号）";
        }
        return null;
    }

    /** 经手人是否还在册（查无此人=已调离）。抽成保护方法，便于单测顶替用户账。 */
    protected boolean handlerAlive(String handler) {
        List<TSysUser> users = this.sysUserService.selectTSysUserList(
                new QueryWrapper<TSysUser>().eq("username", handler));
        return users != null && !users.isEmpty();
    }

    /** 出声落痕：遍数+1、时刻落当日、情形转已开口 */
    private void remind(TPrecDueTask r, Date at, int count) {
        r.setRemindCount(count + 1);
        r.setLastRemindAt(at);
        r.setStatus(STATUS_DONE);
        r.setRemark(day(at) + " 已出声（第" + (count + 1) + "遍）");
        this.precDueTaskMapper.updateById(r);
    }

    /** 卡住挂事由：情形转开不出去，事由上台账 */
    private void markStuck(TPrecDueTask r, Date at, String reason) {
        r.setStatus(STATUS_STUCK);
        r.setRemark(day(at) + " 卡住：" + reason);
        this.precDueTaskMapper.updateById(r);
    }

    /** 三催无回音：情形转已转值班员上门，系统不再对着它出声 */
    private void escalate(TPrecDueTask r, Date at) {
        r.setStatus(STATUS_ESCALATED);
        r.setRemark(day(at) + " 三催无回音，转值班员上门");
        this.precDueTaskMapper.updateById(r);
    }

    private static String displayNo(TPrecDueTask r) {
        return r.getItemNo() == null ? "(无证号)" : r.getItemNo();
    }
}
