package com.bms.basic.controller;

import com.bms.common.BaseCrudController;
import com.bms.basic.entity.Warehouse;
import com.bms.basic.mapper.WarehouseMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/warehouse")
public class WarehouseController extends BaseCrudController<Warehouse, WarehouseMapper> {
    public WarehouseController() {
        super(Warehouse.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name"};
    }
}
