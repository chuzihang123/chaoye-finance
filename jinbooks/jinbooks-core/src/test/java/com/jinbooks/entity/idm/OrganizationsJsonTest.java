package com.jinbooks.entity.idm;

import org.junit.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.Assert.assertEquals;

public class OrganizationsJsonTest {
    @Test
    public void acceptsLegacyNullLevel() {
        Organizations organization = JsonMapper.builder().build().readValue("{\"orgCode\":\"QA\",\"level\":null}", Organizations.class);
        assertEquals(0, organization.getLevel());
        assertEquals("QA", organization.getOrgCode());
    }
}
