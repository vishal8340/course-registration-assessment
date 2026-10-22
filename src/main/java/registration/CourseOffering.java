package registration;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages enrollment, dropping, and FIFO waitlist promotion for a single course offering.
 *
 * Eligibility policy (see DESIGN.md for full rationale):
 *  - At ENROLL time: an ineligible learner is rejected outright (never waitlisted).
 *  - At PROMOTE time: an ineligible learner at the front of the waitlist is SKIPPED
 *    but NOT removed; they keep their FIFO position for a future seat opening.
 */
public class CourseOffering {

    private final String offeringId;
    private final int capacity;
    private final Map<String, Learner> learners; // immutable snapshot, keyed by id

    private final LinkedHashSet<String> enrolled = new LinkedHashSet<>();
    private final LinkedList<String> waitlist = new LinkedList<>(); // head = earliest arrival

    private final ReentrantLock lock = new ReentrantLock();

    public CourseOffering(String offeringId, int capacity, Map<String, Learner> learners) {
        Objects.requireNonNull(offeringId, "offeringId");
        if (offeringId.isBlank()) {
            throw new IllegalArgumentException("offeringId must not be blank");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        Objects.requireNonNull(learners, "learners");
        Map<String, Learner> learnerSnapshot = new HashMap<>(learners);
        learnerSnapshot.forEach((id, learner) -> {
            if (!id.equals(learner.getId())) {
                throw new IllegalArgumentException("Learner map key must match learner ID: " + id);
            }
        });

        this.offeringId = offeringId;
        this.capacity = capacity;
        this.learners = Map.copyOf(learnerSnapshot);
    }

    public String getOfferingId() { return offeringId; }
    public int getCapacity() { return capacity; }

    /**
     * Attempts to enroll a learner. If the offering is full and the learner is eligible,
     * they are placed on the waitlist instead. Duplicate requests are idempotent-safe:
     * they return a status describing the existing state rather than creating duplicates.
     */
    public EnrollmentResult enroll(String learnerId) {
        lock.lock();
        try {
            Learner learner = learners.get(learnerId);
            if (learner == null) {
                return EnrollmentResult.of(EnrollmentStatus.INVALID_LEARNER, learnerId,
                        "No such learner: " + learnerId);
            }

            if (enrolled.contains(learnerId)) {
                return EnrollmentResult.of(EnrollmentStatus.ALREADY_ENROLLED, learnerId,
                        "Learner is already enrolled.");
            }

            if (waitlist.contains(learnerId)) {
                return EnrollmentResult.of(EnrollmentStatus.ALREADY_WAITLISTED, learnerId,
                        "Learner is already on the waitlist.");
            }

            if (!learner.isPrerequisiteMet()) {
                return EnrollmentResult.of(EnrollmentStatus.INELIGIBLE_PREREQUISITE, learnerId,
                        "Learner does not meet the prerequisite; not enrolled or waitlisted.");
            }

            if (enrolled.size() < capacity) {
                enrolled.add(learnerId);
                return EnrollmentResult.of(EnrollmentStatus.ENROLLED, learnerId,
                        "Enrolled successfully.");
            }

            waitlist.addLast(learnerId);
            return EnrollmentResult.of(EnrollmentStatus.WAITLISTED, learnerId,
                    "Course is full; learner added to waitlist.");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops an enrolled learner, freeing a seat. If a seat opens, scans the waitlist
     * head-to-tail for the earliest ELIGIBLE learner and promotes them, skipping over
     * (but retaining) any ineligible learners encountered along the way.
     */
    public DropResult drop(String learnerId) {
        lock.lock();
        try {
            Learner learner = learners.get(learnerId);
            if (learner == null) {
                return DropResult.of(DropStatus.INVALID_LEARNER, learnerId, Optional.empty(),
                        "No such learner: " + learnerId);
            }

            if (!enrolled.remove(learnerId)) {
                // Not enrolled - covers both "never enrolled" and "duplicate drop" cases.
                return DropResult.of(DropStatus.NOT_ENROLLED, learnerId, Optional.empty(),
                        "Learner is not currently enrolled; nothing to drop.");
            }

            Optional<String> promoted = promoteFromWaitlist();
            if (promoted.isPresent()) {
                return DropResult.of(DropStatus.DROPPED_WITH_PROMOTION, learnerId, promoted,
                        "Dropped. Promoted waitlisted learner: " + promoted.get());
            }
            return DropResult.of(DropStatus.DROPPED_NO_PROMOTION, learnerId, Optional.empty(),
                    "Dropped. No eligible waitlisted learner to promote.");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Scans the waitlist from the head. Skips ineligible learners in place (retains them).
     * Promotes and removes the first eligible learner found, if any, and if a seat is free.
     */
    private Optional<String> promoteFromWaitlist() {
        if (enrolled.size() >= capacity) {
            return Optional.empty(); // no seat actually free (defensive; shouldn't happen via drop())
        }

        var iterator = waitlist.listIterator();
        while (iterator.hasNext()) {
            String candidateId = iterator.next();
            Learner candidate = learners.get(candidateId);

            // Defensive: if learner record vanished, treat as ineligible/skip.
            if (candidate != null && candidate.isPrerequisiteMet()) {
                iterator.remove();
                enrolled.add(candidateId);
                return Optional.of(candidateId);
            }
            // else: ineligible — skip, stays in waitlist at same relative position.
        }
        return Optional.empty();
    }

    /** Read-only snapshot of currently enrolled learner IDs, in enrollment order. */
    public Set<String> currentEnrolled() {
        lock.lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(enrolled));
        } finally {
            lock.unlock();
        }
    }

    /** Read-only snapshot of the waitlist in FIFO order (head = earliest). */
    public List<String> currentWaitlist() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new LinkedList<>(waitlist));
        } finally {
            lock.unlock();
        }
    }

    public boolean isFull() {
        lock.lock();
        try {
            return enrolled.size() >= capacity;
        } finally {
            lock.unlock();
        }
    }

    public int availableSeats() {
        lock.lock();
        try {
            return capacity - enrolled.size();
        } finally {
            lock.unlock();
        }
    }
}