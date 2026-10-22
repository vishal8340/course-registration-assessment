package registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourseOfferingTest {

    private Map<String, Learner> learners;

    @BeforeEach
    void setUp() {
        learners = new HashMap<>();
        learners.put("L1", new Learner("L1", "Alice", true));
        learners.put("L2", new Learner("L2", "Bob", true));
        learners.put("L3", new Learner("L3", "Carol", true));
        learners.put("L4", new Learner("L4", "Dave", false));   // ineligible
        learners.put("L5", new Learner("L5", "Eve", true));
    }

    @Test
    void enroll_succeedsWhenCapacityAvailableAndEligible() {
        CourseOffering offering = new CourseOffering("CS101", 2, learners);
        EnrollmentResult result = offering.enroll("L1");

        assertEquals(EnrollmentStatus.ENROLLED, result.status());
        assertTrue(offering.currentEnrolled().contains("L1"));
        assertEquals(1, offering.availableSeats());
    }

    @Test
    void enroll_rejectsIneligibleLearner() {
        CourseOffering offering = new CourseOffering("CS101", 2, learners);
        EnrollmentResult result = offering.enroll("L4"); // not eligible

        assertEquals(EnrollmentStatus.INELIGIBLE_PREREQUISITE, result.status());
        assertFalse(offering.currentEnrolled().contains("L4"));
        assertFalse(offering.currentWaitlist().contains("L4"));
    }

    @Test
    void enroll_whenFull_addsEligibleLearnerToWaitlistOnce() {
        CourseOffering offering = new CourseOffering("CS101", 1, learners);
        offering.enroll("L1"); // fills the single seat

        EnrollmentResult first = offering.enroll("L2");
        assertEquals(EnrollmentStatus.WAITLISTED, first.status());
        assertEquals(1, offering.currentWaitlist().size());

        EnrollmentResult duplicate = offering.enroll("L2");
        assertEquals(EnrollmentStatus.ALREADY_WAITLISTED, duplicate.status());
        assertEquals(List.of("L2"), offering.currentWaitlist());
    }

    @Test
    void enroll_duplicateEnrollRequest_doesNotDuplicate() {
        CourseOffering offering = new CourseOffering("CS101", 2, learners);
        offering.enroll("L1");
        EnrollmentResult duplicate = offering.enroll("L1");

        assertEquals(EnrollmentStatus.ALREADY_ENROLLED, duplicate.status());
        assertEquals(1, offering.currentEnrolled().size());
    }

    @Test
    void drop_promotesEarliestEligibleLearnerInFifoOrder() {
        CourseOffering offering = new CourseOffering("CS101", 1, learners);
        offering.enroll("L1");
        offering.enroll("L2");
        offering.enroll("L3");

        DropResult result = offering.drop("L1");

        assertEquals(DropStatus.DROPPED_WITH_PROMOTION, result.status());
        assertEquals("L2", result.promotedLearnerId().orElseThrow());
        assertEquals(java.util.Set.of("L2"), offering.currentEnrolled());
        assertEquals(List.of("L3"), offering.currentWaitlist());
        assertEquals(0, offering.availableSeats());
    }

    @Test
    void drop_skipsButRetainsIneligibleWaitlistedLearner() {
        CourseOffering offering = new CourseOffering("CS101", 1, learners);
        offering.enroll("L1");
        offering.enroll("L2");
        offering.enroll("L3");
        learners.get("L2").setPrerequisiteMet(false);

        DropResult firstDrop = offering.drop("L1");

        assertEquals(DropStatus.DROPPED_WITH_PROMOTION, firstDrop.status());
        assertEquals("L3", firstDrop.promotedLearnerId().orElseThrow());
        assertEquals(List.of("L2"), offering.currentWaitlist());
        assertFalse(offering.currentEnrolled().contains("L2"));

        learners.get("L2").setPrerequisiteMet(true);
        DropResult secondDrop = offering.drop("L3");

        assertEquals(DropStatus.DROPPED_WITH_PROMOTION, secondDrop.status());
        assertEquals("L2", secondDrop.promotedLearnerId().orElseThrow());
        assertEquals(java.util.Set.of("L2"), offering.currentEnrolled());
        assertTrue(offering.currentWaitlist().isEmpty());
    }

    @Test
    void invalidEnrollmentAndDropsDoNotChangeStateAndDuplicateDropIsReported() {
        CourseOffering offering = new CourseOffering("CS101", 1, learners);

        assertEquals(EnrollmentStatus.INVALID_LEARNER, offering.enroll("missing").status());
        assertEquals(DropStatus.INVALID_LEARNER, offering.drop("missing").status());
        assertTrue(offering.currentEnrolled().isEmpty());
        assertTrue(offering.currentWaitlist().isEmpty());
        assertEquals(1, offering.availableSeats());

        offering.enroll("L1");
        assertEquals(DropStatus.DROPPED_NO_PROMOTION, offering.drop("L1").status());
        assertEquals(DropStatus.NOT_ENROLLED, offering.drop("L1").status());
        assertTrue(offering.currentEnrolled().isEmpty());
        assertTrue(offering.currentWaitlist().isEmpty());
        assertEquals(1, offering.availableSeats());
    }

    @Test
    void constructorSnapshotsLearnersAndRejectsMismatchedKeys() {
        Map<String, Learner> source = new HashMap<>(learners);
        CourseOffering offering = new CourseOffering("CS101", 2, source);
        source.clear();

        assertEquals(EnrollmentStatus.ENROLLED, offering.enroll("L1").status());
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("CS101", 2, Map.of("wrong", learners.get("L1"))));
    }

    @Test
    void concurrentEnrollmentsNeverExceedCapacityOrDuplicateWaitlistEntries() throws Exception {
        int learnerCount = 24;
        int capacity = 5;
        Map<String, Learner> manyLearners = new HashMap<>();
        for (int i = 0; i < learnerCount; i++) {
            String id = "C" + i;
            manyLearners.put(id, new Learner(id, "Learner " + i, true));
        }
        CourseOffering offering = new CourseOffering("CS-CONCURRENT", capacity, manyLearners);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EnrollmentStatus>> results = new ArrayList<>();
        List<EnrollmentStatus> statuses = new ArrayList<>();

        try {
            for (String id : manyLearners.keySet()) {
                results.add(executor.submit(() -> {
                    start.await();
                    return offering.enroll(id).status();
                }));
            }
            start.countDown();
            for (Future<EnrollmentStatus> result : results) {
                statuses.add(result.get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(capacity, statuses.stream().filter(status -> status == EnrollmentStatus.ENROLLED).count());
        assertEquals(learnerCount - capacity,
            statuses.stream().filter(status -> status == EnrollmentStatus.WAITLISTED).count());
        assertEquals(capacity, offering.currentEnrolled().size());
        assertEquals(learnerCount - capacity, offering.currentWaitlist().size());
        assertEquals(learnerCount, offering.currentEnrolled().size() + offering.currentWaitlist().size());
        assertTrue(offering.isFull());
        assertEquals(0, offering.availableSeats());
    }
}