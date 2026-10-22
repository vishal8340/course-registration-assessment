package registration;

import java.util.Optional;

public record DropResult(DropStatus status, String learnerId,
                          Optional<String> promotedLearnerId, String message) {
    public static DropResult of(DropStatus status, String learnerId,
                                 Optional<String> promotedLearnerId, String message) {
        return new DropResult(status, learnerId, promotedLearnerId, message);
    }
}