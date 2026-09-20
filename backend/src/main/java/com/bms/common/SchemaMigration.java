package com.bms.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 对已有数据库执行 schema.sql 中 CREATE TABLE IF NOT EXISTS 无法覆盖的结构变更（H2 / MySQL 通用）。
 * 每项迁移先通过 JDBC 元数据判断是否已应用，因此可重复启动。
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaMigration implements ApplicationRunner {

    private static final String DOC_TABLE = "bms_biz_doc";
    private static final String OLD_INDEX = "idx_biz_doc_ref";
    private static final String UNIQUE_INDEX = "uk_biz_doc_ref";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        ensureUniqueDocRef();
    }

    /** 业务单据 (source, ext_ref) 由普通索引升级为唯一索引，供并发推送幂等使用 */
    private void ensureUniqueDocRef() throws SQLException {
        Map<String, Set<String>> uniqueIndexes = new HashMap<>();
        boolean hasOld = false;
        boolean mysql;
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            mysql = md.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
            for (String table : new String[]{DOC_TABLE, DOC_TABLE.toUpperCase(Locale.ROOT)}) {
                try (ResultSet rs = md.getIndexInfo(null, null, table, false, false)) {
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name == null) {
                            continue;
                        }
                        if (name.equalsIgnoreCase(OLD_INDEX)) {
                            hasOld = true;
                        } else if (!rs.getBoolean("NON_UNIQUE")) {
                            uniqueIndexes.computeIfAbsent(name, k -> new HashSet<>())
                                    .add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                        }
                    }
                }
                if (!uniqueIndexes.isEmpty() || hasOld) {
                    break;
                }
            }
        }
        Set<String> want = new HashSet<>(Arrays.asList("source", "ext_ref"));
        if (uniqueIndexes.containsValue(want)) {
            return;
        }
        List<Map<String, Object>> dups = jdbc.queryForList(
                "SELECT source, ext_ref, COUNT(*) AS cnt FROM bms_biz_doc WHERE ext_ref IS NOT NULL "
                        + "GROUP BY source, ext_ref HAVING COUNT(*) > 1");
        if (!dups.isEmpty()) {
            log.error("bms_biz_doc 存在 {} 组重复的 (source, ext_ref)，无法创建唯一索引 {}，请人工合并后重启: {}",
                    dups.size(), UNIQUE_INDEX, dups);
            return;
        }
        if (hasOld) {
            jdbc.execute(mysql ? "DROP INDEX " + OLD_INDEX + " ON " + DOC_TABLE : "DROP INDEX " + OLD_INDEX);
        }
        jdbc.execute("CREATE UNIQUE INDEX " + UNIQUE_INDEX + " ON " + DOC_TABLE + " (source, ext_ref)");
        log.info("已为 {} 创建唯一索引 {} (source, ext_ref)", DOC_TABLE, UNIQUE_INDEX);
    }
}
