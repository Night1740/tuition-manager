# Later: attendance and fees

Attendance and fees stay out of Phase 1. The schema, deterministic ids, and assignment dates already exist so a later phase can add them without changing identity rules.

## Attendance roll

- The session date defaults to the same injectable local calendar used for admission, student archive, batch archive, and assignment start/end.
- The roll is the students who have an open assignment on that date.
- `endedOn` is exclusive. A student removed today is not on today's roll.
- A student who was allowed over capacity and still has an open assignment is on the roll.
- The attendance id is UUIDv5 of `studentId|batchId|epochDay` in the attendance namespace `7d931b7f-60fa-4e03-927c-feeaf2e1248c`. That name format must not change once rows exist.
- Still open: which statuses exist (present, absent, late, or a missing row), and whether the teacher can mark a date other than today.

## Fees

- The fee-obligation id is UUIDv5 of `studentId|feePlanId|periodKey` in the namespace `71dcac16-7822-4dfe-8790-a6bdfa64299d`.
- `periodKey` is `YYYY-MM` from `feePeriodKey`. That format must not change once rows exist.
- Payments stay append-only: a receipt is a positive amount, a reversal is a negative amount that points at the receipt. There is no update or delete on the payment table.

## Still deferred

A photo picker is not part of Phase 1. `photo_uri` remains an optional stored string.
