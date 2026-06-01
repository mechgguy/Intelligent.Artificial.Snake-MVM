import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

class Client {

    private final HttpSnakeFieldAPI api;
    private final String teamName;

    private static final int ATTACK_ITEM_TICKS = 4;
    private int attackItemTicks = 0;
    private boolean attackPreparedForNextTick = false;

    public Client(String serverUrl, String teamName, String gameName, String password) {
        this.api = new HttpSnakeFieldAPI(serverUrl, teamName, gameName, password);
        this.teamName = teamName;
    }

    public void run() {
        try {
            api.setDirection(Direction.EAST);

            while (true) {
                Thread.sleep(500);

                GameField field = api.getField();
                Snake mySnake = field.snakesPerTeamName().get(teamName);

                if (mySnake != null && mySnake.body() != null && !mySnake.body().isEmpty()) {
                    if (attackPreparedForNextTick) {
                        attackItemTicks = ATTACK_ITEM_TICKS;
                        attackPreparedForNextTick = false;
                        System.out.println("COMBAT ACTIVE: Sword swung!");
                    }
                    System.out.println("Royal Fight Frame | Current Length: " + mySnake.body().size());
                }

                Direction nextDirection = decideDirection(field);
                api.setDirection(nextDirection);

                if (attackItemTicks > 0) {
                    attackItemTicks--;
                }
                System.out.println("Next Trajectory: " + nextDirection);
                System.out.println("--------------------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Direction decideDirection(GameField field) {
        Snake mySnake = field.snakesPerTeamName().get(teamName);
        if (mySnake == null || mySnake.body() == null || mySnake.body().isEmpty()) {
            return Direction.EAST;
        }

        Position myHead = mySnake.body().get(0);
        Direction trueDir = getTrueDirection(mySnake, field.size());

        boolean hasAttackPower = attackItemTicks > 0;

        Set<Position> ownBody = getOwnBodyPositions(mySnake);
        Set<Position> enemyBodies = getEnemyBodyPositions(field);

        // Auto-manage speed boost allocations according to tactical needs
        manageTacticalBoosts(mySnake, field, myHead, enemyBodies);

        if (!hasAttackPower && !attackPreparedForNextTick) {
            Direction straightAttack = prepareAttackItemAndGoStraightIfEnemyInFront(
                    mySnake, field, myHead, trueDir, ownBody, enemyBodies
            );
            if (straightAttack != null) return straightAttack;
        }

        if (hasAttackPower) {
            return decideAttackDirection(field, myHead, trueDir, ownBody, enemyBodies);
        }

        return decideNormalDirection(field, mySnake, myHead, trueDir);
    }

    private void manageTacticalBoosts(Snake mySnake, GameField field, Position myHead, Set<Position> enemyBodies) {
        if (mySnake.inventory() == null || mySnake.inventory().isEmpty()) return;

        String boostItemName = null;
        int swordCount = 0;

        for (String item : mySnake.inventory()) {
            if (item == null) continue;
            if (isSpeedBoostName(item)) boostItemName = item;
            else if (isSwordName(item)) swordCount++;
        }

        if (boostItemName == null) return;

        boolean triggerBoost = false;

        // Requirement 1: Enemy within 2 units proximity AND we possess a Sword in inventory
        if (swordCount >= 1) {
            for (Position p : enemyBodies) {
                if (distance(myHead, p, field.size()) <= 2) {
                    triggerBoost = true;
                    System.out.println("[BOOST] Combat burst deployed against target!");
                    break;
                }
            }
        }

        // Requirement 2: Trapped in closed room and navigating toward a safe gap within 2 units
        if (!triggerBoost) {
            Set<Position> blocked = getBlockedPositions(field);
            int availableSpace = countFreeFields(myHead, blocked, field.size());
            if (availableSpace < mySnake.body().size() * 1.5) {
                for (Direction d : Direction.values()) {
                    Position step1 = wrap(move(myHead, d), field.size());
                    if (!blocked.contains(step1)) {
                        for (Direction d2 : Direction.values()) {
                            Position step2 = wrap(move(step1, d2), field.size());
                            if (!blocked.contains(step2)) {
                                triggerBoost = true;
                                System.out.println("[BOOST] Escaping tight boundary cluster.");
                                break;
                            }
                        }
                    }
                    if (triggerBoost) break;
                }
            }
        }

        if (triggerBoost) {
            activateItemByName(boostItemName);
        }
    }

    private Direction decideNormalDirection(
            GameField field,
            Snake mySnake,
            Position myHead,
            Direction trueDir
    ) {
        int mySize = mySnake.body().size();
        Set<Position> blocked = getBlockedPositions(field);

        Set<Position> lethalHeadZone = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;

            Position enemyHead = enemy.body().get(0);
            for (Direction d : Direction.values()) {
                Position p = wrap(move(enemyHead, d), field.size());
                if (enemy.body().size() >= mySize) {
                    lethalHeadZone.add(p);
                }
            }
        }

        Item targetSword = findNearestSpecificAttackItem(myHead, field.items(), field.size());
        Item targetBoost = findNearestSpeedBoost(myHead, field.items(), field.size());

        // Clean 5-Tier Combat Matrix Array (0 = Highest Priority, 4 = Fallback Collision)
        Direction[] priorityDirections = new Direction[5];
        int[] priorityScores = new int[5];
        for (int i = 0; i < 5; i++) priorityScores[i] = Integer.MIN_VALUE;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;

            Position next = wrap(move(myHead, dir), field.size());

            // --- PRIORITY 5: HARD PHYSICAL OBSTACLE COLLISION ---
            if (blocked.contains(next)) {
                if (priorityDirections[4] == null) priorityDirections[4] = dir;
                continue;
            }

            Set<Position> floodBlocked = new HashSet<>(blocked);
            floodBlocked.addAll(lethalHeadZone);
            int spaceAfterMove = countFreeFields(next, floodBlocked, field.size());
            
            int baseScore = spaceAfterMove * 10;
            if (dir == trueDir) baseScore += 5;

            Item itemOnTile = null;
            for (Item item : field.items()) {
                if (item != null && item.position().x() == next.x() && item.position().y() == next.y()) {
                    String type = item.type().toLowerCase();
                    if (isSwordName(type) || isSpeedBoostName(type)) {
                        itemOnTile = item;
                    }
                    break;
                }
            }

            // --- PRIORITY 1: SWORD GREEDY TRACKING ---
            int swordDist = Integer.MAX_VALUE;
            if (targetSword != null) swordDist = distance(next, targetSword.position(), field.size());
            
            if (itemOnTile != null && isSwordItem(itemOnTile)) {
                int score = baseScore + 200000;
                if (score > priorityScores[0]) { priorityScores[0] = score; priorityDirections[0] = dir; }
            } else if (swordDist != Integer.MAX_VALUE) {
                int score = baseScore + (100000 - swordDist * 200);
                if (score > priorityScores[0]) { priorityScores[0] = score; priorityDirections[0] = dir; }
            }

            // --- PRIORITY 2: SPEED BOOST TRACKING ---
            int boostDist = Integer.MAX_VALUE;
            if (targetBoost != null) boostDist = distance(next, targetBoost.position(), field.size());

            if (itemOnTile != null && isSpeedBoostName(itemOnTile.type())) {
                int score = baseScore + 200000;
                if (score > priorityScores[1]) { priorityScores[1] = score; priorityDirections[1] = dir; }
            } else if (boostDist != Integer.MAX_VALUE) {
                int score = baseScore + (100000 - boostDist * 200);
                if (score > priorityScores[1]) { priorityScores[1] = score; priorityDirections[1] = dir; }
            }

            // --- PRIORITY 3: STRUCTURAL EMPTY TRAVERSAL ---
            if (itemOnTile == null && !lethalHeadZone.contains(next)) {
                if (baseScore > priorityScores[2]) { priorityScores[2] = baseScore; priorityDirections[2] = dir; }
            }

            // --- PRIORITY 4: HEAD-ON ENGAGEMENT COMBAT SWAP ---
            if (lethalHeadZone.contains(next)) {
                int score = baseScore + scoreTowardsEnemy(next, getEnemyBodyPositions(field), field.size());
                if (score > priorityScores[3]) { priorityScores[3] = score; priorityDirections[3] = dir; }
            }
        }

        // Uncompromising cascading cascade loop evaluation
        for (int i = 0; i < 5; i++) {
            if (priorityDirections[i] != null) {
                return priorityDirections[i];
            }
        }

        return priorityDirections[4] != null ? priorityDirections[4] : trueDir;
    }

    private int scoreTowardsEnemy(Position next, Set<Position> enemyBodies, Size size) {
        int bestDistance = Integer.MAX_VALUE;
        for (Position enemyPart : enemyBodies) {
            int dist = distance(next, enemyPart, size);
            if (dist < bestDistance) bestDistance = dist;
        }
        if (bestDistance == Integer.MAX_VALUE) return 0;

        if (bestDistance == 0) return 1000000;
        if (bestDistance == 1) return 500000;
        if (bestDistance == 2) return 200000;
        
        return 10000 - bestDistance * 500;
    }

    private Direction prepareAttackItemAndGoStraightIfEnemyInFront(
            Snake mySnake, GameField field, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> enemyBodies
    ) {
        if (attackItemTicks > 0 || attackPreparedForNextTick) return null;
        if (mySnake.inventory() == null || mySnake.inventory().isEmpty()) return null;

        Position front = wrap(move(myHead, trueDir), field.size());
        if (ownBody.contains(front) || !enemyBodies.contains(front)) return null;

        String sword = findItemNameInInventory(mySnake);
        if (sword != null && activateItemByName(sword)) {
            attackPreparedForNextTick = true;
            return trueDir;
        }

        return null;
    }

    private Direction decideAttackDirection(
            GameField field, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> enemyBodies
    ) {
        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;

            Position next = wrap(move(myHead, dir), field.size());

            if (enemyBodies.contains(next) || ownBody.contains(next)) {
                return dir;
            }

            int score = scoreTowardsEnemy(next, enemyBodies, field.size());
            if (dir == trueDir) score += 5;

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }
        }
        return bestDirection != null ? bestDirection : trueDir;
    }

    private boolean activateItemByName(String item) {
        try {
            api.activateItem(item);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String findItemNameInInventory(Snake mySnake) {
        if (mySnake.inventory() == null) return null;
        for (String item : mySnake.inventory()) {
            if (item == null) continue;
            if (isSwordName(item)) return item;
        }
        return null;
    }

    private boolean isSwordName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.contains("sword") || n.contains("schwert") || n.contains("star") || n.contains("stern") || n.contains("invincible");
    }

    private boolean isSwordItem(Item item) {
        return item != null && item.type() != null && isSwordName(item.type());
    }

    private boolean isSpeedBoostName(String name) {
        if (name == null) return false;
        String t = name.toLowerCase();
        return t.contains("boost") || t.contains("speed") || t.contains("turbo");
    }

    private Set<Position> getOwnBodyPositions(Snake mySnake) {
        Set<Position> own = new HashSet<>();
        if (mySnake.body() != null) own.addAll(mySnake.body());
        return own;
    }

    private Set<Position> getEnemyBodyPositions(GameField field) {
        Set<Position> enemy = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake snake = e.getValue();
            if (snake.body() != null) enemy.addAll(snake.body());
        }
        return enemy;
    }

    private Set<Position> getBlockedPositions(GameField field) {
        Set<Position> blocked = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            Snake snake = e.getValue();
            if (snake.body() == null || snake.body().isEmpty()) continue;

            List<Position> body = snake.body();
            boolean isMe = e.getKey().equals(teamName);
            
            // Stationary dead agents act as unpassable masonry
            int limit = (isMe && snake.alive()) ? body.size() - 1 : body.size();

            for (int i = 0; i < limit; i++) {
                blocked.add(body.get(i));
            }
        }
        return blocked;
    }

    private Item findNearestSpeedBoost(Position head, List<Item> items, Size size) {
        if (items == null) return null;
        Item nearest = null;
        int best = Integer.MAX_VALUE;

        for (Item item : items) {
            if (item == null || item.type() == null) continue;
            if (isSpeedBoostName(item.type())) {
                int d = distance(head, item.position(), size);
                if (d < best) {
                    best = d;
                    nearest = item;
                }
            }
        }
        return nearest;
    }

    private Item findNearestSpecificAttackItem(Position head, List<Item> items, Size size) {
        if (items == null) return null;
        Item nearest = null;
        int best = Integer.MAX_VALUE;

        for (Item item : items) {
            if (item == null || item.type() == null) continue;
            if (!isSwordName(item.type())) continue;

            int d = distance(head, item.position(), size);
            if (d < best) {
                best = d;
                nearest = item;
            }
        }
        return nearest;
    }

    private int countFreeFields(Position start, Set<Position> blocked, Size size) {
        Set<Position> visited = new HashSet<>();
        Queue<Position> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Position cur = queue.poll();

            for (Direction d : Direction.values()) {
                Position next = wrap(move(cur, d), size);
                if (!visited.contains(next) && !blocked.contains(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return visited.size();
    }

    private Direction getTrueDirection(Snake snake, Size fieldSize) {
        if (snake.body() == null || snake.body().size() < 2) return Direction.EAST;

        Position head = snake.body().get(0);
        Position neck = snake.body().get(1);

        int dx = head.x() - neck.x();
        int dy = head.y() - neck.y();

        if (dx > fieldSize.width() / 2) dx -= fieldSize.width();
        if (dx < -fieldSize.width() / 2) dx += fieldSize.width();
        if (dy > fieldSize.height() / 2) dy -= fieldSize.height();
        if (dy < -fieldSize.height() / 2) dy += fieldSize.height();

        if (dx == 1) return Direction.EAST;
        if (dx == -1) return Direction.WEST;
        if (dy == 1) return Direction.SOUTH;
        if (dy == -1) return Direction.NORTH;

        return Direction.EAST;
    }

    private Position move(Position p, Direction d) {
        return switch (d) {
            case NORTH -> new Position(p.x(), p.y() - 1);
            case SOUTH -> new Position(p.x(), p.y() + 1);
            case EAST -> new Position(p.x() + 1, p.y());
            case WEST -> new Position(p.x() - 1, p.y());
        };
    }

    private Position wrap(Position p, Size s) {
        int x = p.x();
        int y = p.y();

        if (x < 0) x = s.width() - 1;
        else if (x >= s.width()) x = 0;

        if (y < 0) y = s.height() - 1;
        else if (y >= s.height()) y = 0;

        return new Position(x, y);
    }

    private boolean isOpposite(Direction a, Direction b) {
        return (a == Direction.NORTH && b == Direction.SOUTH)
                || (a == Direction.SOUTH && b == Direction.NORTH)
                || (a == Direction.EAST && b == Direction.WEST)
                || (a == Direction.WEST && b == Direction.EAST);
    }

    private int distance(Position a, Position b, Size s) {
        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());

        return Math.min(dx, s.width() - dx) + Math.min(dy, s.height() - dy);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Client <team_name> <game_name> [password] [server_url]");
            return;
        }

        String teamName = args[0];
        String gameName = args[1];
        String password = args.length > 2 ? args[2] : "test";
        String serverUrl = args.length > 3 ? args[3] : "http://localhost:3030";

        new Client(serverUrl, teamName, gameName, password).run();
    }
}