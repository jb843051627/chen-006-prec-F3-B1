-- prec 易制毒化学品购销运输许可管控 -- schema (chen-006)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_006

CREATE TABLE IF NOT EXISTS t_prec_audit_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '许可报批单号(局里统一格式 YPG-BP-日-号)',
  chem_name varchar(128) DEFAULT NULL COMMENT '品种(盐酸/丙酮等)',
  qty decimal(12,2) DEFAULT NULL COMMENT '申报数量',
  unit varchar(16) DEFAULT '吨' COMMENT '数量单位',
  purpose varchar(500) DEFAULT NULL COMMENT '用途',
  store_site_no varchar(64) DEFAULT NULL COMMENT '存放库房核定号',
  round_no int DEFAULT '1' COMMENT '报批轮次(打回重报一轮+1,旧押不续)',
  node_no int DEFAULT '0' COMMENT '当前所在道 0派出所 1大队 2市局 3已出证',
  sign_mode int DEFAULT '0' COMMENT '同层核签方式 0任一人 1名单点齐(随当前道写)',
  need_count int DEFAULT '1' COMMENT '本道应画押人数(随当前道写)',
  sign_count int DEFAULT '0' COMMENT '本道已画押人数(系统积,不由人填)',
  sign_total int DEFAULT '0' COMMENT '本轮累计有效画押(系统积,与流水账核对)',
  status int DEFAULT '0' COMMENT '报批情形 0在核 1已出证 2已道否',
  permit_no varchar(64) DEFAULT NULL COMMENT '许可证编号(出证时锁)',
  permit_due datetime DEFAULT NULL COMMENT '凭证期限(出证时锁)',
  issue_time datetime DEFAULT NULL COMMENT '出证时刻',
  veto_node int DEFAULT NULL COMMENT '道否卡在第几道',
  veto_reason varchar(500) DEFAULT NULL COMMENT '道否事由',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_audit_bill_no (bill_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购买许可逐级核签单';

-- 画押流水账：每一笔落押/道否一行，是积数唯一的“账”；屏上积数必须与此表 count 对得上。
-- 唯一键兜底并发：同一轮同一道同一席位同人只许一笔，双击/并挤不翻倍；不同席位各算各的，不吞一枚。
CREATE TABLE IF NOT EXISTS t_prec_audit_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '核签单主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '报批单号(冗余便于捋账)',
  round_no int NOT NULL COMMENT '落在哪一轮(重报后旧轮行一律勾销)',
  node_no int NOT NULL COMMENT '道次 0派出所 1大队 2市局',
  slot_no int NOT NULL DEFAULT '0' COMMENT '点名席位 0不点名单人 1用途合规 2量数',
  slot_name varchar(64) DEFAULT NULL COMMENT '席位名',
  signer varchar(64) NOT NULL COMMENT '画押人账号(取登录会话,不许前端填)',
  signer_name varchar(64) DEFAULT NULL COMMENT '画押人姓名',
  action int NOT NULL COMMENT '动作 1落押 2道否',
  opinion varchar(500) DEFAULT NULL COMMENT '签注/否由',
  sign_time datetime NOT NULL COMMENT '画押时刻',
  valid int DEFAULT '1' COMMENT '是否有效 1有效 0已勾销(重报勾旧押)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sign_once (bill_id, round_no, node_no, slot_no, signer, action),
  KEY idx_sign_bill_round (bill_id, round_no, valid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购买许可核签画押流水账';

-- 末道点名册：市局两人各占一席，换人停用旧行、另起新行。
CREATE TABLE IF NOT EXISTS t_prec_audit_signer (
  id bigint NOT NULL COMMENT '主键',
  node_no int NOT NULL COMMENT '道次(末道=2)',
  slot_no int NOT NULL COMMENT '点名席位 1用途合规 2量数',
  slot_name varchar(64) DEFAULT NULL COMMENT '席位名',
  signer varchar(64) NOT NULL COMMENT '点名账号',
  signer_name varchar(64) DEFAULT NULL COMMENT '点名人姓名',
  status int DEFAULT '1' COMMENT '点名情形 1在册 0已停点',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_signer_seat (node_no, slot_no, signer),
  KEY idx_signer_node_slot (node_no, slot_no, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='市局核签点名册';

-- 发号序列：报批单号/许可证编号取号，连接级 LAST_INSERT_ID 取号，不依赖外部组件。
CREATE TABLE IF NOT EXISTS t_prec_seq (
  seq_key varchar(64) NOT NULL COMMENT '序列键(按日)',
  seq_val bigint NOT NULL DEFAULT '0' COMMENT '当前序号',
  PRIMARY KEY (seq_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='易毒政务发号序列';

-- 末道点名册种子（演示用 admin/fuce 两账号，上线按局里实际点名账号替换）。
INSERT IGNORE INTO t_prec_audit_signer (id, node_no, slot_no, slot_name, signer, signer_name, status, del_flag, create_by, create_time)
VALUES (9001, 2, 1, '用途合规核签', 'admin', '管理员', 1, 0, 'seed', NOW()),
       (9002, 2, 2, '量数核签',     'fuce',  '付册',   1, 0, 'seed', NOW());

CREATE TABLE IF NOT EXISTS t_prec_ctrl_line (
  id bigint NOT NULL COMMENT '主键(即线号,同日落两条线上顺位并列时取线号大者)',
  rule_code varchar(64) DEFAULT NULL COMMENT '分档线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '分档线名目',
  chem_name varchar(128) NOT NULL COMMENT '管哪个品种(浓硫酸/稀盐酸/丙酮等,一线只管一个品种)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '免管浓度上界(%),空=这一道不设上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '三类浓度上界(%),空=这一道不设上限(老文件只录了两道界的照认)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '二类浓度上界(%),空=这一道不设上限',
  eff_start datetime DEFAULT NULL COMMENT '起算日(当日即作数)',
  eff_end datetime DEFAULT NULL COMMENT '交棒日(当日仍作数;与新线起算日双闭重叠,空=至今有效)',
  priority int DEFAULT NULL COMMENT '同日多条线都够得着时的顺位号(数值越大越先)',
  status int DEFAULT NULL COMMENT '线的情形 0在用 1已退位(退位只留档,新报的单不许再搬)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ctrl_chem_day (chem_name, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品浓度分档管控线';

-- 钉进系统的分档线种子：一线一档，回算只认报备当日作数的线，不看今天在用哪版。
-- 交棒日双闭：旧线管到当日、新线当日起算，重叠当日先比顺位号(priority 大者)，顺位相同比线号(id 大者)。
-- 压线从严：浓度正好等于某道上界，按更严一档收（10.00% 正压三类上界即按二类管）。
-- 稀盐酸旧线是早年老文件翻录，只录了两道界，缺的 th3 视作不设上限，线照认。
INSERT IGNORE INTO t_prec_ctrl_line
(id, rule_code, rule_name, chem_name, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time, remark)
VALUES
 (920000000000000101, 'H2SO4-2024', '2024版浓硫酸浓度分档线', '浓硫酸', 10.00, 70.00, NULL, '2024-01-01 00:00:00', '2025-10-01 00:00:00', 10, 1, 0, 'seed', NOW(), '旧版：不足70.00%为三类，70.00压线归二类；2025-10-01交棒当日仍作数，当日与新版重叠按顺位认新版'),
 (920000000000000102, 'H2SO4-2025', '2025版浓硫酸浓度分档线', '浓硫酸', 10.00, 50.00, NULL, '2025-10-01 00:00:00', NULL,                    20, 0, 0, 'seed', NOW(), '新版：三类上界挪到50.00%；前年秋后按旧线够格三类的货不许拿这条重判'),
 (920000000000000201, 'HCL-OLD',    '稀盐酸浓度分档线(老文件翻录)', '稀盐酸', 10.00, 20.00, NULL, '2023-01-01 00:00:00', '2025-01-01 00:00:00',  5, 1, 0, 'seed', NOW(), '老文件只录两道界，缺的二类上界视作不设上限，线照样认'),
 (920000000000000202, 'HCL-2025',   '2025版稀盐酸浓度分档线', '稀盐酸',  5.00, 15.00, NULL, '2025-01-01 00:00:00', NULL,                    15, 0, 0, 'seed', NOW(), '新版：免管/三道上界分别挪到5.00%/15.00%'),
 (920000000000000301, 'ACETONE-2024', '2024版丙酮浓度分档线', '丙酮', 10.00, 60.00, NULL, '2024-01-01 00:00:00', '2026-01-01 00:00:00',  8, 1, 0, 'seed', NOW(), '旧版：不足60.00%为三类，60.00压线归二类；2026-01-01交棒退位'),
 (920000000000000302, 'ACETONE-2026', '2026版丙酮浓度分档线', '丙酮',  5.00, 50.00, NULL, '2026-01-01 00:00:00', NULL,                    18, 0, 0, 'seed', NOW(), '新版：免管/三类上界分别挪到5.00%/50.00%');

CREATE TABLE IF NOT EXISTS t_prec_due_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '凭证编号',
  due_at datetime DEFAULT NULL COMMENT '证照满期时刻(准到钟点;老数据只写了日期的落库即当日00:00:00,与新数据同一把日历日尺子)',
  amount decimal(12,2) DEFAULT NULL COMMENT '单条提前开口自然日数',
  handler varchar(64) DEFAULT NULL COMMENT '眼下经手人账号(空=未派活,卡住挂事由)',
  remind_count int NOT NULL DEFAULT '0' COMMENT '已出声遍数(系统积,到3仍无回音转值班员)',
  last_remind_at datetime DEFAULT NULL COMMENT '最近一次出声时刻(同日不重复出声的尺子)',
  status int DEFAULT NULL COMMENT '条目情形 0待开口 1已开口 2开不出去(卡住挂事由) 3已转值班员上门(系统不再出声)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='证照效期满期提醒条目';

CREATE TABLE IF NOT EXISTS t_prec_firm_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '企业建档单号',
  site_id int DEFAULT NULL COMMENT '挂靠库房核定底册',
  site_no varchar(64) DEFAULT NULL COMMENT '库房核定号',
  qty decimal(12,2) DEFAULT NULL COMMENT '年拟购总量(吨)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '其中二类产品(吨)',
  grade_level int DEFAULT NULL COMMENT '规模档',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已定档',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='从业单位建档单';

CREATE TABLE IF NOT EXISTS t_prec_report_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '报送册号',
  row_no int DEFAULT NULL COMMENT '原册内行次',
  item_code varchar(64) DEFAULT NULL COMMENT '单位备案号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行交易数量(公斤)',
  status int DEFAULT NULL COMMENT '行进展 0待核 1已入账 2挂驳回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='季度报送册核收明细';

CREATE TABLE IF NOT EXISTS t_prec_revoke_card (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '注销办理卡编号',
  stage int DEFAULT NULL COMMENT '当前格口 0..5',
  status int DEFAULT NULL COMMENT '卡的落定 0在办 1已封档 2已归卷',
  content varchar(255) DEFAULT NULL COMMENT '办理记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪格动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='歇业注销办理卡';

CREATE TABLE IF NOT EXISTS t_prec_site_qual (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '库房核定号',
  site_name varchar(128) DEFAULT NULL COMMENT '库房核定底册名称',
  site_type varchar(32) DEFAULT NULL COMMENT '主存品类',
  road_name varchar(128) DEFAULT NULL COMMENT '辖属(市—县—大队)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '本站单月存量一档上限(吨)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限(吨)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限(吨)',
  status int DEFAULT NULL COMMENT '底册情形 0在用 1已退位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库房核定底册';

-- 初始档案数据：两条种子底册，KF00 在用、KF01 已退位。
-- 三档单月存量上限取 20/80/200 吨。
INSERT IGNORE INTO t_prec_site_qual (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'KF00', '临江市化工园区北库核定底册', '酸类', '临江市—临溪县—城西大队', 20.00, 80.00, 200.00, 0, 0, 'seed', NOW()),
       (1, 'KF01', '河东旧库核定底册（已退位）', '酮类', '临江市—临溪县—河东大队', 20.00, 80.00, 200.00, 1, 0, 'seed', NOW());


-- ============================================================================
-- 菜单与授权：易毒管控 / 购买许可核签
-- 列序：id,name,descripion,url,is_blank,pid,perms,type,icon,order_num,visible,
--       create_by,create_time,update_by,update_time,remark
-- ============================================================================
INSERT IGNORE INTO t_sys_permission
(id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES
 (910000000000000001, '易毒管控', NULL, '', 0, 0, '', 0, 'layui-icon layui-icon-auz', 8, 0, 'seed', NOW()),
 (910000000000000010, '购买许可核签', '核签台账与受理纸', '/precAudit/view', 0, 910000000000000001, 'precAudit:view', 1, 'layui-icon layui-icon-form', 1, 0, 'seed', NOW()),
 (910000000000000011, '核签集合', '核签台账数据', '/precAudit/list', 0, 910000000000000010, 'precAudit:list', 2, '', NULL, 0, 'seed', NOW()),
 (910000000000000012, '厂里递单', '厂里递交购买许可报批单', '/precAudit/add', 0, 910000000000000010, 'precAudit:add', 2, 'layui-icon layui-icon-add-1', NULL, 0, 'seed', NOW()),
 (910000000000000013, '画押', '当前道画押（末道按点名席位）', '/precAudit/sign', 0, 910000000000000010, 'precAudit:sign', 2, 'layui-icon layui-icon-ok', NULL, 0, 'seed', NOW()),
 (910000000000000014, '道否', '本道把话说死，单据折住', '/precAudit/veto', 0, 910000000000000010, 'precAudit:veto', 2, 'layui-icon layui-icon-close', NULL, 0, 'seed', NOW()),
 (910000000000000015, '打回重报', '旧押勾销，回预审重新起积', '/precAudit/resubmit', 0, 910000000000000010, 'precAudit:resubmit', 2, 'layui-icon layui-icon-refresh-3', NULL, 0, 'seed', NOW()),
 (910000000000000016, '预审前改单', '预审落押前修改单据内容', '/precAudit/edit', 0, 910000000000000010, 'precAudit:edit', 2, 'layui-icon layui-icon-edit', NULL, 0, 'seed', NOW()),
 (910000000000000017, '许可出证', '三道齐后锁品种/量数/期限', '/precAudit/issue', 0, 910000000000000010, 'precAudit:issue', 2, 'layui-icon layui-icon-release', NULL, 0, 'seed', NOW());

-- 授给管理员角色(488243256161730560)。上线按岗位再拆：派出所/大队/市局/厂方。
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id, create_by, create_time)
SELECT 911000000000000000 + n, 488243256161730560, 910000000000000000 + n, 'seed', NOW()
FROM (SELECT 1 n UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13
      UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17) t;

-- ============================================================================
-- 周期执行：证照满期提醒唯一入口。调度器触发走业务服务层(precDueTaskJob.run)，
-- 办公日白天(09:00-17:00)每小时扫一轮；同日不重复出声由条目上的出声时刻兜底。
-- 状态 0正常；禁止并发(上一轮没跑完不并挤)。
-- ============================================================================
INSERT IGNORE INTO t_sys_quartz_job
(id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES
 (910000000000000100, '证照满期提醒周期执行', 'PREC', 'precDueTaskJob.run()', '0 20 9-16 ? * MON-FRI', '2', '1', 0, 'seed', NOW(), '到点自己跑的编排：办公日白天整点过20分扫一轮');
