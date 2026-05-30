import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Client {
    private final HttpSnakeFieldAPI api;
    private final String teamName;

    public Client(String serverUrl, String teamName, String gameName, String password) {
        this.api = new HttpSnakeFieldAPI(serverUrl, teamName, gameName, password);
        this.teamName = teamName;
    }

    public void run() {
        try {
            Direction currentDirection = Direction.EAST;

            api.setDirection(currentDirection);

            while (true) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                GameField field = api.getField();

                Direction nextDirection = decideDirection(field, currentDirection);

                currentDirection = nextDirection;

                api.setDirection(currentDirection);

                System.out.println("Neue Richtung: " + currentDirection);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Direction decideDirection(GameField field, Direction currentDirection) {
        Snake mySnake = field.snakesPerTeamName().get(teamName);

        if (mySnake == null || mySnake.body() == null || mySnake.body().isEmpty()) {
            return currentDirection;
        }

        Position myHead = mySnake.body().get(0);

        Set<Position> blockedPositions = getBlockedPositions(field);

        List<Direction> possibleDirections = new ArrayList<>();
        possibleDirections.add(Direction.NORTH);
        possibleDirections.add(Direction.EAST);
        possibleDirections.add(Direction.SOUTH);
        possibleDirections.add(Direction.WEST);

        Direction bestDirection = currentDirection;
        int bestScore = Integer.MIN_VALUE;

        for (Direction direction : possibleDirections) {

            if (isOpposite(currentDirection, direction)) {
                continue;
            }

            Position nextPosition = move(myHead, direction);

            // Nicht in lebende oder tote Schlangen fahren
            if (blockedPositions.contains(nextPosition)) {
                continue;
            }

            int score = 0;

            // Zum nächsten Apfel / Item laufen
            Item nearestItem = findNearestItem(myHead, field.items());

            if (nearestItem != null) {
                int distanceAfterMove = distance(nextPosition, nearestItem.position());
                score -= distanceAfterMove * 10;
            }

            // Gegnerkopf meiden
            int enemyDanger = enemyDangerScore(nextPosition, field.snakesPerTeamName());
            score -= enemyDanger * 50;

            // Kleiner Bonus fürs Geradeausfahren
            if (direction == currentDirection) {
                score += 5;
            }

            if (score > bestScore) {
                bestScore = score;
                bestDirection = direction;
            }
        }

        return bestDirection;
    }

    private Set<Position> getBlockedPositions(GameField field) {
        Set<Position> blocked = new HashSet<>();

        for (Snake snake : field.snakesPerTeamName().values()) {
            if (snake.body() == null) {
                continue;
            }

            // Auch tote Schlangen/Leichen werden blockiert
            blocked.addAll(snake.body());
        }

        return blocked;
    }

    private Item findNearestItem(Position myHead, List<Item> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        Item nearest = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Item item : items) {
            int itemDistance = distance(myHead, item.position());

            if (itemDistance < bestDistance) {
                bestDistance = itemDistance;
                nearest = item;
            }
        }

        return nearest;
    }

    private int enemyDangerScore(Position nextPosition, Map<String, Snake> snakes) {
        int danger = 0;

        for (Map.Entry<String, Snake> entry : snakes.entrySet()) {

            if (entry.getKey().equals(teamName)) {
                continue;
            }

            Snake enemySnake = entry.getValue();

            // Tote Snake bewegt sich nicht mehr.
            // Ihr Körper ist trotzdem durch getBlockedPositions blockiert.
            if (!enemySnake.alive()) {
                continue;
            }

            if (enemySnake.body() == null || enemySnake.body().isEmpty()) {
                continue;
            }

            Position enemyHead = enemySnake.body().get(0);
            int distanceToEnemyHead = distance(nextPosition, enemyHead);

            if (distanceToEnemyHead == 0) {
                danger += 100;
            } else if (distanceToEnemyHead == 1) {
                danger += 10;
            } else if (distanceToEnemyHead == 2) {
                danger += 3;
            }
        }

        return danger;
    }

    private Position move(Position position, Direction direction) {
        return switch (direction) {
            case NORTH -> new Position(position.x(), position.y() - 1);
            case SOUTH -> new Position(position.x(), position.y() + 1);
            case EAST -> new Position(position.x() + 1, position.y());
            case WEST -> new Position(position.x() - 1, position.y());
        };
    }

    private boolean isOpposite(Direction currentDirection, Direction newDirection) {
        return (currentDirection == Direction.NORTH && newDirection == Direction.SOUTH)
                || (currentDirection == Direction.SOUTH && newDirection == Direction.NORTH)
                || (currentDirection == Direction.EAST && newDirection == Direction.WEST)
                || (currentDirection == Direction.WEST && newDirection == Direction.EAST);
    }

    private int distance(Position a, Position b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Client <team_name> <game_name> [password] [server_url]");
            System.out.println("Example: java Client team1 default secret http://localhost:3030");
            return;
        }

        String teamName = args[0];
        String gameName = args[1];
        String password = args.length > 2 ? args[2] : "test";
        String serverUrl = args.length > 3 ? args[3] : "http://localhost:3030";

        Client client = new Client(serverUrl, teamName, gameName, password);
        client.run();
    }
}