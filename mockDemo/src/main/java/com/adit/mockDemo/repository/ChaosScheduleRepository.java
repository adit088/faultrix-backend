package com.adit.mockDemo.repository;

import com.adit.mockDemo.entity.ChaosRuleEntity;
import com.adit.mockDemo.entity.ChaosSchedule;
import com.adit.mockDemo.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChaosScheduleRepository extends JpaRepository<ChaosSchedule, Long> {

    List<ChaosSchedule> findByChaosRuleAndEnabledTrue(ChaosRuleEntity rule);

    List<ChaosSchedule> findByOrganization(Organization org);

    List<ChaosSchedule> findByChaosRule(ChaosRuleEntity rule);

    void deleteByChaosRule(ChaosRuleEntity rule);
}