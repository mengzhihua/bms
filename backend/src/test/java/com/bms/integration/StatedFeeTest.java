package com.bms.integration;

import com.bms.integration.controller.OpenApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatedFeeTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void freightDeltaUsesStatedAmountAndWmsWarehouse() throws Exception {
        assertEquals("WH-SH", OpenApiController.warehouse("WH01"));
        String body = "[{\"extRef\":\"WB-1:FD1\",\"bizType\":\"TRANSPORT\",\"customerCode\":\"CUST-001\","
                + "\"supplierCode\":\"SF\",\"warehouseCode\":\"WH01\",\"statedAmount\":-40.00,"
                + "\"direction\":\"AP\",\"chargeItemCode\":\"FREIGHT\",\"remark\":\"SF->SELF01\"}]";
        mockMvc.perform(post("/api/open/tms/docs")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].billStatus").value("BILLED"))
                .andExpect(jsonPath("$.data[0].apAmount").value(-40.0));
        mockMvc.perform(post("/api/open/tms/docs")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].apAmount").value(-40.0));
    }
}
