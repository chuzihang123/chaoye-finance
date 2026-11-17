-- 朝烨财务品牌更名。执行前备份数据库，可重复执行。
-- 只更新包含旧品牌字样的名称、显示文案和凭证单位抬头。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE jbx_book
SET name = REGEXP_REPLACE(REGEXP_REPLACE(name, 'jinbooks财务(记账系统|管理)', '朝烨财务', 1, 0, 'i'), 'jinbooks', '朝烨财务', 1, 0, 'i'),
    company_name = REGEXP_REPLACE(REGEXP_REPLACE(company_name, 'jinbooks财务(记账系统|管理)', '朝烨财务', 1, 0, 'i'), 'jinbooks', '朝烨财务', 1, 0, 'i')
WHERE name REGEXP 'jinbooks' OR company_name REGEXP 'jinbooks';

UPDATE jbx_institutions
SET inst_name = REGEXP_REPLACE(inst_name, 'jinbooks', '朝烨财务', 1, 0, 'i'),
    full_name = REGEXP_REPLACE(full_name, 'jinbooks', '朝烨财务', 1, 0, 'i'),
    front_title = REGEXP_REPLACE(front_title, 'jinbooks', '朝烨财务', 1, 0, 'i'),
    console_title = REGEXP_REPLACE(console_title, 'jinbooks', '朝烨财务', 1, 0, 'i')
WHERE inst_name REGEXP 'jinbooks' OR full_name REGEXP 'jinbooks'
   OR front_title REGEXP 'jinbooks' OR console_title REGEXP 'jinbooks';

UPDATE jbx_organizations
SET org_name = REGEXP_REPLACE(org_name, 'jinbooks', '朝烨财务', 1, 0, 'i'),
    full_name = REGEXP_REPLACE(REGEXP_REPLACE(full_name, 'jinbooks财务(记账系统|管理)', '朝烨财务', 1, 0, 'i'), 'jinbooks', '朝烨财务', 1, 0, 'i'),
    name_path = REGEXP_REPLACE(name_path, 'jinbooks', '朝烨财务', 1, 0, 'i')
WHERE org_name REGEXP 'jinbooks' OR full_name REGEXP 'jinbooks' OR name_path REGEXP 'jinbooks';

UPDATE jbx_resources
SET parent_name = REGEXP_REPLACE(parent_name, 'jinbooks', '朝烨财务', 1, 0, 'i')
WHERE parent_name REGEXP 'jinbooks';

UPDATE jbx_voucher
SET company_name = REGEXP_REPLACE(REGEXP_REPLACE(company_name, 'jinbooks财务(记账系统|管理)', '朝烨财务', 1, 0, 'i'), 'jinbooks', '朝烨财务', 1, 0, 'i')
WHERE company_name REGEXP 'jinbooks';

UPDATE jbx_history_system_logs
SET target_name = REGEXP_REPLACE(target_name, 'jinbooks', '朝烨财务', 1, 0, 'i'),
    message = REGEXP_REPLACE(message, 'jinbooks', '朝烨财务', 1, 0, 'i')
WHERE target_name REGEXP 'jinbooks' OR message REGEXP 'jinbooks';

COMMIT;
