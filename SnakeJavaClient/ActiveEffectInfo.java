import com.fasterxml.jackson.annotation.JsonProperty;

record ActiveEffectInfo(String effect, @JsonProperty("remaining_ticks") int remainingTicks) {
}
