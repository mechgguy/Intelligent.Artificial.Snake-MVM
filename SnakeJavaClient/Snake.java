import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record Snake(List<Position> body, boolean alive, List<String> inventory,
             @JsonProperty("active_effects") List<ActiveEffectInfo> activeEffects, int[] color) {
}
