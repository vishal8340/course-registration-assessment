package registration;

public record EnrollmentResult(EnrollmentStatus status, String learnerId, String message) {
    public static EnrollmentResult of(EnrollmentStatus status, String learnerId, String message) {
        return new EnrollmentResult(status, learnerId, message);
    }
}