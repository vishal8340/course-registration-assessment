# AI Prompt and Use Record

The prompts listed below were given by the human user to the AI assistant. The response column summarizes what the AI returned, and the disposition column records the human review and resulting changes. This is a concise prompt/use record rather than a verbatim transcript. No credentials, secrets, or sensitive data were included.

## Prompts and outcomes

| Human prompt given to AI | AI response summary | Human disposition and files/decisions informed | Verification |
| --- | --- | --- | --- |
| Initial request supplied the desired project tree for a course-registration assessment. | Scaffolded a Java/Maven domain model and documentation; initial implementation had simple enrollment/drop semantics. | Accepted as a starting point, then materially revised after the actual implementation and assessment requirements were clarified. Informed the source files and initial docs. | Initial `mvn test` completed successfully; later review identified stale assumptions and insufficient tests. |
| “could you check again all code and files” | Reviewed the then-current implementation and reported that it used eligibility plus a waitlist and FIFO promotion, while tests/docs were stale or incomplete. | Findings accepted. They prompted replacing stale documentation and expanding tests. | Re-ran `mvn test`; it passed the then-existing four tests. |
| “based on code could you please also update other files like design.md, AI-prompt-logs and limitations and readme files” | Updated the four documentation files to describe the implementation visible at that time. | Accepted as a doc update; the later acceptance criteria review led to further corrections and more precise statements. | Read back the updated files and searched for outdated statements. |
| User supplied acceptance criteria for capacity, waitlist uniqueness, FIFO promotion, ineligible learners, invalid/repeated drops, focused/concurrency tests, design/setup/limitations deliverables, and an AI-use record. | Compared criteria with the code. The key gaps were tests for promotion, invalid/repeated drops, and concurrency; the map was also retained by reference. | Accepted the criteria. Added tests, changed `CourseOffering` to snapshot and validate its learner map, updated the compiler target to use `release`, and expanded the design, README, limitations, and this record. | `mvn clean test` passed all 9 tests with 0 failures/errors. The initial clean build showed Java source/target module-path warnings; switched the Maven compiler configuration to `maven.compiler.release=17` and reran the clean test. |

## Assumptions and independently reviewed decisions

- At enrollment time, an ineligible learner is rejected and never newly waitlisted.
- At promotion time, an ineligible learner is skipped but retained in place; the first currently eligible learner after it is promoted. If that learner later becomes eligible, a subsequent successful drop can promote them.
- Eligibility remains a boolean in this bounded example; no configurable policy engine was added.
- The AI-generated initial documentation did not match the evolved implementation and was corrected rather than relied upon. The map snapshot validation and acceptance-focused tests were reviewed against the existing lock-based state transitions.

## Verification evidence

- Test command: `mvn clean test`.
- Result: 9 tests run, 0 failures, 0 errors, 0 skipped; Maven reported `BUILD SUCCESS`.
- Focused coverage includes enrollment with capacity, prerequisite rejection, waitlisting and duplicate requests, FIFO promotion, skip-and-retain eligibility behavior, invalid and repeated drops, map snapshot validation, and concurrent enrollment capacity.
