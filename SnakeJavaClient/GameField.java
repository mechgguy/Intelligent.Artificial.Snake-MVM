import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

record GameField(Size size, Map<String, Snake> snakesPerTeamName, List<Item> items) {
    GameField(Size size, @JsonProperty("snake") Map<String, Snake> snakesPerTeamName, List<Item> items) {
        this.size = size;
        this.snakesPerTeamName = snakesPerTeamName;
        this.items = items;
    }

    public Size size() {
        return this.size;
    }

    @JsonProperty("snake")
    public Map<String, Snake> snakesPerTeamName() {
        return this.snakesPerTeamName;
    }

    public List<Item> items() {
        return this.items;
    }
}
