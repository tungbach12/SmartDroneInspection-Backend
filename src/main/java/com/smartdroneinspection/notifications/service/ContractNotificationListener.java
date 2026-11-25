package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.contracts.events.CustomerReportAcceptedEvent;
import com.smartdroneinspection.contracts.events.InspectorPayoutSettledEvent;
import com.smartdroneinspection.contracts.events.InspectorReceivableRecoveryEvent;
import com.smartdroneinspection.contracts.events.OccurrenceRefundEvent;
import com.smartdroneinspection.contracts.events.ServiceQuoteAcceptedEvent;
import com.smartdroneinspection.contracts.events.ServiceQuoteSavedEvent;
import com.smartdroneinspection.contracts.events.ServiceRequestManagerChangedEvent;
import com.smartdroneinspection.contracts.events.SignedContractUploadedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records in-app notifications for commercial contract, quote, payout, and refund lifecycle events.
 */
@Component
public class ContractNotificationListener {

  private final ContractNotificationService contractNotificationService;

  public ContractNotificationListener(ContractNotificationService contractNotificationService) {
    this.contractNotificationService = contractNotificationService;
  }

  @ApplicationModuleListener
  public void onManagerChanged(ServiceRequestManagerChangedEvent event) {
    contractNotificationService.recordManagerChanged(event);
  }

  @ApplicationModuleListener
  public void onQuoteSaved(ServiceQuoteSavedEvent event) {
    contractNotificationService.recordQuoteSaved(event);
  }

  @ApplicationModuleListener
  public void onQuoteAccepted(ServiceQuoteAcceptedEvent event) {
    contractNotificationService.recordQuoteAccepted(event);
  }

  @ApplicationModuleListener
  public void onSignedContractUploaded(SignedContractUploadedEvent event) {
    contractNotificationService.recordSignedContractUploaded(event);
  }

  @ApplicationModuleListener
  public void onCustomerReportAccepted(CustomerReportAcceptedEvent event) {
    contractNotificationService.recordCustomerReportAccepted(event);
  }

  @ApplicationModuleListener
  public void onInspectorPayoutSettled(InspectorPayoutSettledEvent event) {
    contractNotificationService.recordInspectorPayoutSettled(event);
  }

  @ApplicationModuleListener
  public void onOccurrenceRefund(OccurrenceRefundEvent event) {
    contractNotificationService.recordOccurrenceRefund(event);
  }

  @ApplicationModuleListener
  public void onReceivableRecovery(InspectorReceivableRecoveryEvent event) {
    contractNotificationService.recordInspectorReceivableRecovery(event);
  }
}
