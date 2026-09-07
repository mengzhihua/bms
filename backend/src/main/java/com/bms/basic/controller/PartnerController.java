package com.bms.basic.controller;

import com.bms.common.BaseCrudController;
import com.bms.basic.entity.Partner;
import com.bms.basic.mapper.PartnerMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/partner")
public class PartnerController extends BaseCrudController<Partner, PartnerMapper> {
    public PartnerController() {
        super(Partner.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name", "contact"};
    }
}
