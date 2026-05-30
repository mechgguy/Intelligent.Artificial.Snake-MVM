import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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

        Set<Position> snakeBlockedPositions = getBlockedPositions(field);
        Set<Position> badApplePositions = getBadApplePositions(field.items());

        // Für Flood Fill zählen Bad Apples als blockiert,
        // damit der Bot sie nicht freiwillig als guten Weg betrachtet.
        Set<Position> blockedForFloodFill = new HashSet<>(snakeBlockedPositions);
        blockedForFloodFill.addAll(badApplePositions);

        List<Direction> possibleDirections = new ArrayList<>();
        possibleDirections.add(Direction.NORTH);
        possibleDirections.add(Direction.EAST);
        possibleDirections.add(Direction.SOUTH);
        possibleDirections.add(Direction.WEST);

        Direction bestNormalDirection = null;
        int bestNormalScore = Integer.MIN_VALUE;

        Direction bestWallDirection = null;
        int bestWallScore = Integer.MIN_VALUE;

        Direction emergencyBadAppleDirection = null;
        int emergencyBadAppleScore = Integer.MIN_VALUE;

        for (Direction direction : possibleDirections) {

            if (isOpposite(currentDirection, direction)) {
                continue;
            }

            Position rawNextPosition = moveWithoutWall(myHead, direction);
            boolean goesThroughWall = !isInsideField(rawNextPosition, field.size());

            Position nextPosition = wrapPosition(rawNextPosition, field.size());

            // In Snake oder Leiche NIEMALS reinfahren.
            if (snakeBlockedPositions.contains(nextPosition)) {
                continue;
            }

            boolean isBadAppleField = badApplePositions.contains(nextPosition);

            int score = 0;

            int freeSpace = countReachableFreeFields(nextPosition, blockedForFloodFill, field.size());
            score += freeSpace * 8;

            if (freeSpace < mySnake.body().size() + 3) {
                score -= 500;
            }

            Item nearestGoodItem = findNearestGoodItem(myHead, field.items());

            if (nearestGoodItem != null) {
                int distanceAfterMove = distanceWithWall(nextPosition, nearestGoodItem.position(), field.size());
                score -= distanceAfterMove * 10;
            }

            int enemyDanger = enemyDangerScore(nextPosition, field.snakesPerTeamName(), field.size(), mySnake.body().size());
            score -= enemyDanger * 60;

            int badAppleDanger = badAppleDangerScore(nextPosition, badApplePositions, field.size());
            score -= badAppleDanger * 80;

            if (direction == currentDirection) {
                score += 5;
            }

            /*
             * WICHTIG:
             * Bad Apple wird NICHT normal genommen.
             * Es wird nur gespeichert als Notfallrichtung.
             */
            if (isBadAppleField) {
                score -= 10000;

                if (score > emergencyBadAppleScore) {
                    emergencyBadAppleScore = score;
                    emergencyBadAppleDirection = direction;
                }

                continue;
            }

            if (!goesThroughWall) {
                if (score > bestNormalScore) {
                    bestNormalScore = score;
                    bestNormalDirection = direction;
                }
            } else {
                score -= 40;

                if (score > bestWallScore) {
                    bestWallScore = score;
                    bestWallDirection = direction;
                }
            }

            System.out.println(
                    "Richtung: " + direction
                            + " | Ziel: " + nextPosition
                            + " | Platz: " + freeSpace
                            + " | Score: " + score
                            + " | Wand: " + goesThroughWall
                            + " | BadApple: " + isBadAppleField
            );
        }

        // 1. Beste normale sichere Richtung
        if (bestNormalDirection != null) {
            return bestNormalDirection;
        }

        // 2. Wenn normal nichts geht, dann durch Wand
        if (bestWallDirection != null) {
            return bestWallDirection;
        }

        // 3. ABSOLUTER NOTFALL:
        // Bad Apple nur nehmen, wenn sonst keine sichere Richtung existiert.
        if (emergencyBadAppleDirection != null) {
            System.out.println("NOTFALL: Bad Apple wird genommen!");
            return emergencyBadAppleDirection;
        }

        // 4. Letzter Notfall
        return currentDirection;
    }

    private Set<Position> getBlockedPositions(GameField field) {
        Set<Position> blocked = new HashSet<>();

        for (Snake snake : field.snakesPerTeamName().values()) {
            if (snake.body() == null) {
                continue;
            }

            // Alle Schlangen blockieren, auch tote Schlangen/Leichen
            blocked.addAll(snake.body());
        }

        return blocked;
    }

    private Set<Position> getBadApplePositions(List<Item> items) {
        Set<Position> badApples = new HashSet<>();

        if (items == null) {
            return badApples;
        }

        for (Item item : items) {
            if (isBadApple(item)) {
                badApples.add(item.position());
            }
        }

        return badApples;
    }

    private boolean isBadApple(Item item) {
        if (item == null || item.type() == null) {
            return false;
        }

        String type = item.type().toLowerCase();

        return type.contains("bad");
    }

    private Item findNearestGoodItem(Position myHead, List<Item> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        Item nearest = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Item item : items) {
            if (isBadApple(item)) {
                continue;
            }

            int itemDistance = distance(myHead, item.position());

            if (itemDistance < bestDistance) {
                bestDistance = itemDistance;
                nearest = item;
            }
        }

        return nearest;
    }

    private int badAppleDangerScore(Position nextPosition, Set<Position> badApplePositions, Size size) {
        int danger = 0;

        for (Position badApple : badApplePositions) {
            int distanceToBadApple = distanceWithWall(nextPosition, badApple, size);

            if (distanceToBadApple == 0) {
                danger += 1000;
            } else if (distanceToBadApple == 1) {
                danger += 5;
            }
        }

        return danger;
    }

    private int enemyDangerScore(Position nextPosition, Map<String, Snake> snakes, Size size, int myLength) {
        int danger = 0;

        for (Map.Entry<String, Snake> entry : snakes.entrySet()) {

            if (entry.getKey().equals(teamName)) {
                continue;
            }

            Snake enemySnake = entry.getValue();

            if (!enemySnake.alive()) {
                continue;
            }

            if (enemySnake.body() == null || enemySnake.body().isEmpty()) {
                continue;
            }

            Position enemyHead = enemySnake.body().get(0);
            int distanceToEnemyHead = distanceWithWall(nextPosition, enemyHead, size);

            int enemyLength = enemySnake.body().size();

            if (distanceToEnemyHead == 0) {
                danger += 1000;
            } else if (distanceToEnemyHead == 1) {
                if (enemyLength >= myLength) {
                    danger += 30;
                } else {
                    danger += 10;
                }
            } else if (distanceToEnemyHead == 2) {
                danger += 5;
            }
        }

        return danger;
    }

    private int countReachableFreeFields(Position start, Set<Position> blockedPositions, Size size) {
        Set<Position> visited = new HashSet<>();
        Queue<Position> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Position current = queue.poll();

            for (Direction direction : Direction.values()) {
                Position rawNext = moveWithoutWall(current, direction);
                Position next = wrapPosition(rawNext, size);

                if (visited.contains(next)) {
                    continue;
                }

                if (blockedPositions.contains(next)) {
                    continue;
                }

                visited.add(next);
                queue.add(next);
            }
        }

        return visited.size();
    }

    private Position moveWithoutWall(Position position, Direction direction) {
        return switch (direction) {
            case NORTH -> new Position(position.x(), position.y() - 1);
            case SOUTH -> new Position(position.x(), position.y() + 1);
            case EAST -> new Position(position.x() + 1, position.y());
            case WEST -> new Position(position.x() - 1, position.y());
        };
    }

    private Position wrapPosition(Position position, Size size) {
        int x = position.x();
        int y = position.y();

        if (x < 0) {
            x = size.width() - 1;
        } else if (x >= size.width()) {
            x = 0;
        }

        if (y < 0) {
            y = size.height() - 1;
        } else if (y >= size.height()) {
            y = 0;
        }

        return new Position(x, y);
    }

    private boolean isInsideField(Position position, Size size) {
        return position.x() >= 0
                && position.y() >= 0
                && position.x() < size.width()
                && position.y() < size.height();
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

    private int distanceWithWall(Position a, Position b, Size size) {
        int normalX = Math.abs(a.x() - b.x());
        int normalY = Math.abs(a.y() - b.y());

        int wrappedX = size.width() - normalX;
        int wrappedY = size.height() - normalY;

        int bestX = Math.min(normalX, wrappedX);
        int bestY = Math.min(normalY, wrappedY);

        return bestX + bestY;
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