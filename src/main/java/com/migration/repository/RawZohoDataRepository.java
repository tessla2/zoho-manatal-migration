package com.migration.repository;

import com.migration.entity.RawZohoData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RawZohoDataRepository extends JpaRepository<RawZohoData, Long> {

    List<RawZohoData> findByModule(String module);

    List<RawZohoData> findByProcessed(Boolean processed);

    List<RawZohoData> findByModuleAndProcessed(String module, Boolean processed);
}
