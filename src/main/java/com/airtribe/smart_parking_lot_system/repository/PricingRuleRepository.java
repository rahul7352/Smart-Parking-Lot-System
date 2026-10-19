package com.airtribe.smart_parking_lot_system.repository;

import com.airtribe.smart_parking_lot_system.model.entity.PricingRule;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, VehicleType> {}