package com.catalog;

import com.catalog.dto.ProductRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateRetrieveUpdateFilterAndDeleteProduct() throws Exception {
        ProductRequest createReq = new ProductRequest(
                "NIKE-PEG-41",
                "Air Zoom Pegasus 41",
                "Responsive daily road running shoe",
                "running-shoes",
                new BigDecimal("139.99"),
                120
        );

        // 1. Create
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.sku").value("NIKE-PEG-41"))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String productId = createdJson.get("id").asText();

        // 2. Retrieve by ID
        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Air Zoom Pegasus 41"));

        // 3. List & Filter
        mockMvc.perform(get("/api/v1/products")
                        .param("category", "running-shoes")
                        .param("minPrice", "100")
                        .param("maxPrice", "150")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value("NIKE-PEG-41"));

        // 4. Update
        ProductRequest updateReq = new ProductRequest(
                "NIKE-PEG-41",
                "Air Zoom Pegasus 41 - Updated",
                "Responsive daily road running shoe",
                "running-shoes",
                new BigDecimal("129.99"),
                95
        );

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(129.99));

        // 5. Soft Delete & Verify 404
        mockMvc.perform(delete("/api/v1/products/{id}", productId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isNotFound());
    }
}
