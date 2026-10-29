package com.smartdroneinspection.inspections.spi;

import java.util.UUID;

/** Inspection-module boundary for manager-dispatched maintenance reinspection tasks. */
public interface ReinspectionInspectionAccess {

  UUID createAssignedInspection(ReinspectionInspectionAssignment assignment);
}
