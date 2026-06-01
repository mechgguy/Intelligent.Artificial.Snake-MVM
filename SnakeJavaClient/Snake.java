import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Snake {
    // ... existing fields
    private List<String> inventory;
    private List<String> activeEffects;
    
    // Add getters for these fields
    public boolean hasSword() {
        return inventory != null && inventory.contains("Sword");
    }
}

record Snake(List<Position> body, boolean alive, List<String> inventory,
             @JsonProperty("active_effects") List<ActiveEffectInfo> activeEffects, int[] color) {
}
