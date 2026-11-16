-- 修正标准1及其账套中仍沿用原始默认值的收入取数规则。
-- 执行前备份数据库。本补丁可重复执行，保留已改为其他取数规则的配置。
START TRANSACTION;

UPDATE jbx_standard_statement_rules
SET rule = 'PROFIT_AND_LOSS_AMOUNT', symbol = '-'
WHERE standard_id = '1' AND type = 'income' AND item_code = '1'
  AND subject_code IN ('6001', '6051') AND rule = 'DEBIT_AMOUNT' AND symbol = '+';

UPDATE jbx_statement_rules AS r
JOIN jbx_book AS b ON b.id = r.book_id
SET r.rule = 'PROFIT_AND_LOSS_AMOUNT', r.symbol = '-'
WHERE b.standard_id = '1' AND r.type = 'income' AND r.item_code = '1'
  AND r.subject_code IN ('6001', '6051') AND r.rule = 'DEBIT_AMOUNT' AND r.symbol = '+';

COMMIT;
