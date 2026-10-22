# Limitations

- Enrollment state and the waitlist are held in memory and are lost when the process exits; there is no persistence or database integration.
- The model represents prerequisite eligibility only as a boolean; it does not model individual prerequisite courses, terms, schedules, time conflicts, instructors, or authentication.
- `CourseOffering` takes a structural snapshot of the learner map and validates that keys match learner IDs. The learner objects themselves remain mutable so prerequisite eligibility can change.
- `Learner` requires non-null ID and name but does not reject blank strings or normalize whitespace; IDs are looked up as supplied.
- Waitlist promotion occurs after a successful drop. Changing a waitlisted learner's eligibility does not trigger promotion until a later drop opens a seat.
- There is no application entry point, command-line interface, HTTP API, or user interface; this repository contains the domain model and tests only.
- The status names and edge-case policies are implementation choices; confirm they match the assessment rubric.
- The in-memory lock protects one `CourseOffering` instance only; it does not coordinate state across processes or persist concurrent changes.
