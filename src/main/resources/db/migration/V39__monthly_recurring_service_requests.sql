ALTER TABLE service_requests
    DROP CONSTRAINT ck_service_requests_recurrence,
    ADD CONSTRAINT ck_service_requests_recurrence CHECK (
        (request_type = 'ONE_TIME'
            AND recurrence_unit IS NULL
            AND recurrence_interval IS NULL
            AND occurrence_count IS NULL
            AND recurrence_end_date IS NULL)
        OR
        (request_type = 'RECURRING'
            AND recurrence_unit IN ('DAY', 'WEEK', 'MONTH')
            AND ((recurrence_unit = 'MONTH' AND recurrence_interval BETWEEN 1 AND 120)
                OR (recurrence_unit IN ('DAY', 'WEEK') AND recurrence_interval BETWEEN 1 AND 52))
            AND (
                (recurrence_unit = 'MONTH'
                    AND ((occurrence_count BETWEEN 2 AND 100 AND recurrence_end_date IS NULL)
                        OR (occurrence_count IS NULL AND recurrence_end_date > requested_from)))
                OR
                (recurrence_unit IN ('DAY', 'WEEK')
                    AND ((occurrence_count BETWEEN 1 AND 100 AND recurrence_end_date IS NULL)
                        OR (occurrence_count IS NULL AND recurrence_end_date >= requested_from)))
            ))
    );
