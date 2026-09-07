package com.bms.basic.controller;

import com.bms.common.BaseCrudController;
import com.bms.basic.entity.ChargeItem;
import com.bms.basic.mapper.ChargeItemMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/charge-item")
public class ChargeItemController extends BaseCrudController<ChargeItem, ChargeItemMapper> {
    public ChargeItemController() {
        super(ChargeItem.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name"};
    }
}
