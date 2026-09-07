package com.bms.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bms.billing.entity.Fee;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FeeMapper extends BaseMapper<Fee> {

    @Select("<script>SELECT COUNT(*) AS cnt, COALESCE(SUM(amount),0) AS amount, COALESCE(SUM(tax_amount),0) AS \"taxAmount\", COALESCE(SUM(total_amount),0) AS \"totalAmount\" "
            + "FROM bms_fee WHERE status &lt;&gt; 'CANCELLED'"
            + "<if test='direction != null and direction != \"\"'> AND direction = #{direction}</if>"
            + "<if test='partnerCode != null and partnerCode != \"\"'> AND partner_code = #{partnerCode}</if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status}</if>"
            + "<if test='from != null'> AND biz_date &gt;= #{from}</if>"
            + "<if test='to != null'> AND biz_date &lt;= #{to}</if></script>")
    Map<String, Object> summary(@Param("direction") String direction, @Param("partnerCode") String partnerCode,
                                @Param("status") String status, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT direction, partner_code AS \"partnerCode\", charge_item_code AS \"chargeItemCode\", COUNT(*) AS cnt, "
            + "COALESCE(SUM(amount),0) AS amount, COALESCE(SUM(tax_amount),0) AS \"taxAmount\", COALESCE(SUM(total_amount),0) AS \"totalAmount\" "
            + "FROM bms_fee WHERE status <> 'CANCELLED' AND biz_date >= #{from} AND biz_date <= #{to} "
            + "GROUP BY direction, partner_code, charge_item_code ORDER BY direction, partner_code, charge_item_code")
    List<Map<String, Object>> groupByPartnerItem(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT direction, charge_item_code AS \"chargeItemCode\", COALESCE(SUM(total_amount),0) AS \"totalAmount\", COUNT(*) AS cnt "
            + "FROM bms_fee WHERE status <> 'CANCELLED' AND biz_date >= #{from} AND biz_date <= #{to} "
            + "GROUP BY direction, charge_item_code ORDER BY direction, 3 DESC")
    List<Map<String, Object>> groupByItem(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT biz_date AS \"bizDate\", "
            + "COALESCE(SUM(CASE WHEN direction = 'AR' THEN total_amount ELSE 0 END),0) AS ar, "
            + "COALESCE(SUM(CASE WHEN direction = 'AP' THEN total_amount ELSE 0 END),0) AS ap "
            + "FROM bms_fee WHERE status <> 'CANCELLED' AND biz_date >= #{from} AND biz_date <= #{to} GROUP BY biz_date ORDER BY biz_date")
    List<Map<String, Object>> dailyTrend(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT partner_code AS \"partnerCode\", direction, COALESCE(SUM(total_amount),0) AS \"totalAmount\", COUNT(*) AS cnt "
            + "FROM bms_fee WHERE status <> 'CANCELLED' AND biz_date >= #{from} AND biz_date <= #{to} "
            + "GROUP BY partner_code, direction ORDER BY 3 DESC")
    List<Map<String, Object>> groupByPartner(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT COALESCE(SUM(total_amount),0) FROM bms_fee WHERE direction = #{direction} AND status = #{status} AND biz_date >= #{from} AND biz_date <= #{to}")
    java.math.BigDecimal sumByStatus(@Param("direction") String direction, @Param("status") String status, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
