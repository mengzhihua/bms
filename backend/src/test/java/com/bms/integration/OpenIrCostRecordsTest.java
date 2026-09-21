package com.bms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenIrCostRecordsTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void irStuckOrderFeesAppearInCostRecords() throws Exception {
        String from = LocalDate.now().minusDays(1).toString();
        String to = LocalDate.now().toString();
        String body = mockMvc.perform(get("/api/open/cost/records")
                        .param("from", from)
                        .param("to", to)
                        .header("X-Api-Key", "test-open-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        boolean found = false;
        boolean freightCarrier = false;
        for (JsonNode row : objectMapper.readTree(body).get("data")) {
            if ("IR-SO-STUCK".equals(row.path("orderNo").asText())
                    && "WH-SH".equals(row.path("warehouseCode").asText())) {
                found = true;
            }
            if ("IR-SO-STUCK".equals(row.path("orderNo").asText())
                    && ("TRANSPORT".equals(row.path("costType").asText())
                    || "FREIGHT".equals(row.path("costType").asText()))) {
                freightCarrier = "SF".equals(row.path("carrierCode").asText());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(found, "应包含 IR-SO-STUCK 费用");
        org.junit.jupiter.api.Assertions.assertTrue(freightCarrier, "运费应带回承运商 SF");
    }

    @Test
    void irSnapshotsWrapCostRecords() throws Exception {
        String body = mockMvc.perform(get("/api/open/ir/snapshots")
                        .header("X-Api-Key", "test-open-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.system").value("BMS"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        boolean costRow = false;
        boolean snapshot = false;
        for (JsonNode row : data.get("costs")) {
            if ("IR-SO-STUCK".equals(row.path("orderNo").asText())
                    && "WH-SH".equals(row.path("warehouseCode").asText())) {
                costRow = true;
            }
        }
        for (JsonNode row : data.get("snapshots")) {
            if ("COST".equals(row.path("dataType").asText())
                    && "FEE-IR-AR-001".equals(row.path("bizKey").asText())) {
                snapshot = true;
                org.junit.jupiter.api.Assertions.assertEquals("NEW", row.path("status").asText());
                org.junit.jupiter.api.Assertions.assertEquals("IR-SO-STUCK", row.path("orderNo").asText());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(costRow, "Open IR costs 应含 IR-SO-STUCK");
        org.junit.jupiter.api.Assertions.assertTrue(snapshot, "Open IR snapshots 应含 COST/FEE-IR-AR-001");
    }
}
