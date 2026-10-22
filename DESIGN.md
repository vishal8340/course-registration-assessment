# Design

## Domain model

- `Learner` stores an ID, name, and mutable prerequisite eligibility flag. The flag is `volatile` so updates are visible across threads.
- `CourseOffering` is initialized with an offering ID, positive capacity, and a lookup map of known learners keyed by ID. It stores enrolled IDs in insertion order and waitlisted IDs in FIFO order.
- `EnrollmentStatus` and `DropStatus` enumerate operation outcomes. `EnrollmentResult` includes the status, learner ID, and message. `DropResult` additionally includes an optional promoted learner ID.

## Boundary and API

`CourseOffering` is the service boundary for this slice: callers supply a known learner ID to `enroll` or `drop`, and receive a result object rather than modifying enrollment state directly. Read methods expose immutable snapshots and capacity counts. No persistence, transport/API server, catalog, or other registration product concerns are included.

The constructor snapshots the supplied learner map and requires each map key to match the corresponding learner ID. This prevents later structural changes to the caller's map from silently changing which learners the offering recognizes. Learner eligibility remains mutable on the learner object.

## Invariants

- `0 <= enrolled.size() <= capacity` at all times.
- A learner ID appears at most once across the enrolled set and waitlist.
- A learner newly enrolled or waitlisted must be known and prerequisite-eligible at the time of the enrollment request.
- Promotion only fills a seat freed by a successful drop, and moves exactly one eligible waitlisted learner to enrolled.
- Invalid IDs and unsuccessful drops do not change enrolled or waitlist state.

## Enrollment policy

`enroll` processes a request in this order: unknown learner, already enrolled, already waitlisted, prerequisite eligibility, available seat, then waitlist. Thus duplicate requests report the learner's current enrolled/waitlisted state even if their eligibility has changed since the original request. Ineligible learners are not newly enrolled or waitlisted.

## Drop and promotion policy

Dropping an unknown ID returns `INVALID_LEARNER`; a known learner who is not enrolled returns `NOT_ENROLLED`. On a successful drop, the offering scans the waitlist from the head and promotes the first currently eligible learner. Ineligible entries are skipped without being removed, so their position relative to other remaining entries is retained. Drop status distinguishes whether a promotion occurred.

## Concurrency and snapshots

`CourseOffering` guards enrollment state operations with a `ReentrantLock`. Its enrolled and waitlist accessors return immutable copies, so callers cannot mutate internal collections. `Learner.prerequisiteMet` is volatile; a promotion uses the eligibility value observed while it scans the queue. State is in-memory only and is not shared across application instances.
