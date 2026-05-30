import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class HttpSnakeFieldAPI {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final OkHttpClient client = new OkHttpClient();
    private static final JsonMapper jsonMapper = new JsonMapper();
    private final String gameUrl;
    private final String directionUrl;
    private final String activateItemUrl;
    private final String authHeader;

    public HttpSnakeFieldAPI(String baseUrl, String teamName, String gameName, String password) {
        this.gameUrl = "%s/games/%s/state".formatted(baseUrl, gameName);
        this.directionUrl = "%s/games/%s/snake/direction".formatted(baseUrl, gameName);
        this.activateItemUrl = "%s/games/%s/snake/activate".formatted(baseUrl, gameName);
        this.authHeader = createBasicAuthHeader(teamName, password);
    }

    private String createBasicAuthHeader(String teamName, String password) {
        String credentials = teamName + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    public GameField getField() throws IOException {
        Request request = (new Request.Builder()).url(this.gameUrl).addHeader("Authorization", this.authHeader).get().build();
        Response response = client.newCall(request).execute();
        String json = response.body().string();
        GameField gameField = this.parseGameField(json);
        response.close();

        return gameField;
    }

    public void setDirection(Direction direction) throws IOException {
        String json = jsonMapper.writeValueAsString(new DirectionRequest(direction));
        Request request = (new Request.Builder()).url(this.directionUrl).addHeader("Authorization", this.authHeader).post(RequestBody.create(json, JSON)).build();
        Response response = client.newCall(request).execute();

        if (response != null) {
            response.close();
        }
    }

    public void activateItem(String item) throws IOException {
        String json = jsonMapper.writeValueAsString(new ActivateItemRequest(item));
        Request request = (new Request.Builder()).url(this.activateItemUrl).addHeader("Authorization", this.authHeader).post(RequestBody.create(json, JSON)).build();
        Response response = client.newCall(request).execute();

        if (response != null) {
            response.close();
        }
    }

    private GameField parseGameField(String json) {
        try {
            return (GameField)jsonMapper.readValue(json, GameField.class);
        } catch (JsonProcessingException var) {
            throw new IllegalArgumentException("Failed to parse game field JSON", var);
        }
    }
}
