package com.palmer.billingstatementgenerator.models;

import java.math.BigDecimal;

public class TenantSettings {

    private BigDecimal salesTaxRate;

    public TenantSettings setSalesTaxRate(BigDecimal salesTaxRate) {
        this.salesTaxRate = salesTaxRate;
        return this;
    }

    public BigDecimal getSalesTaxRate() {
        return salesTaxRate;
    }
}
