# Course Registration Assessment

A Java 17 domain model for managing one course offering, learner eligibility, enrollment capacity, and a FIFO waitlist. The project uses Maven and JUnit 5.

## Requirements

- JDK 17 or later
- Maven

## Build and test

Run the test suite from the project root with `mvn clean test` (or `mvn test` for an incremental run).

## Behavior

- Create a `CourseOffering` with an offering ID, positive capacity, and a map of known learners keyed by learner ID.
- Each `Learner` has an ID, name, and prerequisite eligibility flag. Eligibility can later be changed with `setPrerequisiteMet`.
- `enroll(learnerId)` returns `INVALID_LEARNER` for unknown IDs and `INELIGIBLE_PREREQUISITE` for known ineligible learners. Eligible learners are enrolled if a seat is available, or added to the waitlist if full.
- Repeated enrollment requests return `ALREADY_ENROLLED` or `ALREADY_WAITLISTED` rather than creating duplicates.
- `drop(learnerId)` removes an enrolled learner and attempts to promote the earliest eligible waitlisted learner. Ineligible waitlisted learners are skipped but retained in their original relative order.
- `currentEnrolled()` and `currentWaitlist()` provide read-only snapshots. `availableSeats()` and `isFull()` report current capacity state.
- Enrollment and drop results carry a status, learner ID, and explanatory message. A drop result also identifies a promoted learner, if any.
- Concurrent enrollment and drop state changes are serialized by the offering's lock so capacity and queue membership remain consistent.

See [DESIGN.md](DESIGN.md) for policy and synchronization details and [LIMITATIONS.md](LIMITATIONS.md) for known constraints.
