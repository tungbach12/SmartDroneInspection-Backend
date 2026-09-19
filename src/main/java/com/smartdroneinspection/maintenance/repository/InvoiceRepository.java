package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.Invoice;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {}
