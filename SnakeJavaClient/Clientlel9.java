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
    private boolean alreadyCutWithAttackItem = false;

    private static final int MAX_EXTRA_LENGTH = 999;
    private int startLength = -1;
    private boolean wantsBadApple = false;

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
                    updateLengthMode(mySnake);

                    if (attackPreparedForNextTick) {
                        attackItemTicks = ATTACK_ITEM_TICKS;
                        attackPreparedForNextTick = false;
                        alreadyCutWithAttackItem = false;
                        System.out.println("💥 ATTACK EFFECT IS NOW ACTIVE!");
                    }

                    System.out.println("Length: " + mySnake.body().size());
                    System.out.println("Inventory: " + mySnake.inventory());
                    System.out.println("Active Effects: " + mySnake.activeEffects());
                }

                Direction nextDirection = decideDirection(field);
                api.setDirection(nextDirection);

                if (attackItemTicks > 0) {
                    attackItemTicks--;
                    if (attackItemTicks == 0) {
                        alreadyCutWithAttackItem = false;
                    }
                }

                System.out.println("Heading: " + nextDirection);
                System.out.println("--------------------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateLengthMode(Snake mySnake) {
        int currentLength = mySnake.body().size();
        if (startLength == -1) {
            startLength = currentLength;
        }
        int maxAllowedLength = startLength + MAX_EXTRA_LENGTH;
        wantsBadApple = currentLength >= maxAllowedLength;
    }

    private Direction decideDirection(GameField field) {
        Snake mySnake = field.snakesPerTeamName().get(teamName);
        if (mySnake == null || mySnake.body() == null || mySnake.body().isEmpty()) {
            return Direction.EAST;
        }

        Position myHead = mySnake.body().get(0);
        Direction trueDir = getTrueDirection(mySnake, field.size());

        Set<Position> enemyBodies = getEnemyBodyPositions(field);
        Set<Position> badApples = getBadApplePositions(field.items());
        Set<Position> blocked = getBlockedPositions(field);

        // =========================================================================
        // 🔥 MANDATORY RULE 1: FORCE SWORD USE IF ENEMY IS AT DISTANCE 1
        // =========================================================================
        if (attackItemTicks == 0 && !attackPreparedForNextTick) {
            boolean enemyDirectlyAdjacent = false;
            for (Position enemyPart : enemyBodies) {
                if (distance(myHead, enemyPart, field.size()) == 1) {
                    enemyDirectlyAdjacent = true;
                    break;
                }
            }

            if (enemyDirectlyAdjacent) {
                String weaponToForce = findItemNameInInventory(mySnake, true); 
                if (weaponToForce == null) {
                    weaponToForce = findItemNameInInventory(mySnake, false); // Fallback to Star
                }

                if (weaponToForce != null) {
                    if (activateItemByName(weaponToForce)) {
                        attackPreparedForNextTick = true;
                        System.out.println("🚨 MANDATORY RULE: Forced activation of " + weaponToForce + " due to proximity (distance 1)!");
                    }
                }
            }
        }

        // =========================================================================
        // 🔥 MANDATORY RULE 2: FORCE BOOST TO RUSH ENEMY IF DISTANCE IS 4 AND OWN SWORD
        // =========================================================================
        String instantSword = findItemNameInInventory(mySnake, true); 
        if (instantSword != null) {
            boolean enemyAtDistanceFour = false;
            
            for (Map.Entry<String, Snake> entry : field.snakesPerTeamName().entrySet()) {
                if (entry.getKey().equals(teamName)) continue;
                Snake enemy = entry.getValue();
                if (enemy != null && enemy.alive() && enemy.body() != null && !enemy.body().isEmpty()) {
                    for (Position enemyPart : enemy.body()) {
                        if (distance(myHead, enemyPart, field.size()) == 4) {
                            enemyAtDistanceFour = true;
                            break;
                        }
                    }
                }
                if (enemyAtDistanceFour) break;
            }

            if (enemyAtDistanceFour) {
                String boostToForce = findBoostNameInInventory(mySnake.inventory());
                if (boostToForce != null) {
                    if (activateItemByName(boostToForce)) {
                        System.out.println("⚡ MANDATORY RUSH: Injected Boost! Enemy spotted at distance 4 while armed with Sword.");
                    }
                }
            }
        }

        // =========================================================================
        // 🔥 MANDATORY RULE 3: FORCE STACK IF SNAKE LENGTH EXCEEDS THRESHOLD 18
        // =========================================================================
        if (mySnake.body().size() > 18) {
            String thresholdStack = findInstantStackInInventory(mySnake);
            if (thresholdStack != null) {
                if (activateItemByName(thresholdStack)) {
                    System.out.println("💥 MANDATORY THRESHOLD STACK: Size is " + mySnake.body().size() + " (> 18)! Deploying Instant Stack.");
                }
            }
        }
        // =========================================================================

        // Run general proactive triggers for remaining items (Defensive Boosts / Space Collapse Stacks)
        executeProactiveInventoryTriggers(mySnake, myHead, field, blocked, enemyBodies);

        boolean hasAttackPower = attackItemTicks > 0;

        if (hasAttackPower && alreadyCutWithAttackItem) {
            return decideCarefulAfterCutDirection(field, mySnake, myHead, trueDir, getOwnBodyPositions(mySnake), enemyBodies, badApples);
        }

        if (hasAttackPower) {
            return decideAttackDirection(field, myHead, trueDir, getOwnBodyPositions(mySnake), enemyBodies, badApples);
        }

        return decideNormalDirection(field, mySnake, myHead, trueDir, badApples);
    }

    private void executeProactiveInventoryTriggers(
            Snake mySnake, Position myHead, GameField field, 
            Set<Position> blocked, Set<Position> enemyBodies
    ) {
        if (mySnake.inventory() == null || mySnake.inventory().isEmpty()) return;

        List<String> inventory = new LinkedList<>(mySnake.inventory());
        Size size = field.size();

        // 1. BACKUP/DEFENSIVE BOOST TRIGGER: Accelerate to escape tight traps
        String boostToken = findBoostNameInInventory(inventory);
        if (boostToken != null) {
            int availableSpace = countFreeFields(myHead, blocked, size);
            boolean defensiveEscape = (availableSpace < 12);

            if (defensiveEscape && activateItemByName(boostToken)) {
                System.out.println("⚡ PROACTIVE TRIGGER: Velocity Boost injected! (Defensive Escape: true)");
                inventory.remove(boostToken);
            }
        }

        // 2. BACKUP/PROACTIVE INSTANT STACK TRIGGER: Compress when a direct crash is imminent
        String stackToken = findInstantStackInInventory(mySnake);
        if (stackToken != null) {
            Direction trueDir = getTrueDirection(mySnake, size);
            Position nextTile = wrap(move(myHead, trueDir), size);
            
            boolean collisionImminent = blocked.contains(nextTile);
            int availableSpace = countFreeFields(myHead, blocked, size);
            
            if ((collisionImminent || availableSpace < mySnake.body().size()) && activateItemByName(stackToken)) {
                System.out.println("💥 PROACTIVE TRIGGER: Safe space collapsed or collision near! Deploying Instant Stack.");
                inventory.remove(stackToken);
            }
        }
    }

    private String findBoostNameInInventory(List<String> inventory) {
        if (inventory == null) return null;
        for (String item : inventory) {
            if (item != null && isSpeedBoostName(item)) return item;
        }
        return null;
    }

    private Direction decideAttackDirection(
            GameField field, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> enemyBodies, Set<Position> badApples
    ) {
        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;
            Position next = wrap(move(myHead, dir), field.size());

            if (ownBody.contains(next) || badApples.contains(next)) continue;

            if (enemyBodies.contains(next)) {
                System.out.println("ATTACK ITEM SCHNEIDET SNAKE: " + dir);
                alreadyCutWithAttackItem = true;
                return dir;
            }

            int score = scoreTowardsEnemy(next, enemyBodies, field.size());
            if (dir == trueDir) score += 5;

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }
        }
        return (bestDirection != null) ? bestDirection : trueDir;
    }

    private Direction decideCarefulAfterCutDirection(
            GameField field, Snake mySnake, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> enemyBodies, Set<Position> badApples
    ) {
        int mySize = mySnake.body().size();
        Set<Position> blocked = new HashSet<>(ownBody);
        blocked.addAll(enemyBodies);
        Set<Position> headDangerZone = getEnemyHeadDangerZone(field, mySnake);

        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;
        Direction fallback = null;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;
            Position next = wrap(move(myHead, dir), field.size());

            if (blocked.contains(next) || badApples.contains(next)) continue;
            if (fallback == null) fallback = dir;
            if (headDangerZone.contains(next)) continue;

            Set<Position> floodBlocked = new HashSet<>(blocked);
            floodBlocked.addAll(badApples);
            floodBlocked.addAll(headDangerZone);

            int space = countFreeFields(next, floodBlocked, field.size());
            int score = space * 100;
            if (space < mySize) score -= 3000;
            if (dir == trueDir) score += 10;

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }
        }
        return (bestDirection != null) ? bestDirection : (fallback != null) ? fallback : trueDir;
    }

    private Direction decideNormalDirection(
            GameField field, Snake mySnake, Position myHead, Direction trueDir, Set<Position> badApples
    ) {
        int mySize = mySnake.body().size();
        Set<Position> blocked = getBlockedPositions(field);
        Set<Position> headDangerZone = getEnemyHeadDangerZone(field, mySnake);

        Item targetUsefulItem = findNearestUsefulItem(myHead, field.items(), field.size());

        Direction bestSafe = null;
        int bestSafeScore = Integer.MIN_VALUE;
        Direction fallbackBadApple = null;
        Direction fallbackAny = null;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;
            Position next = wrap(move(myHead, dir), field.size());

            if (blocked.contains(next)) continue;
            if (fallbackAny == null) fallbackAny = dir;
            if (headDangerZone.contains(next)) continue;

            if (badApples.contains(next)) {
                if (fallbackBadApple == null) fallbackBadApple = dir;
                continue;
            }

            Set<Position> floodBlocked = new HashSet<>(blocked);
            floodBlocked.addAll(headDangerZone);
            floodBlocked.addAll(badApples);

            int space = countFreeFields(next, floodBlocked, field.size());
            int score = space * 100;
            if (space < mySize) score -= 5000;

            if (targetUsefulItem != null) {
                int distItem = distance(next, targetUsefulItem.position(), field.size());

                if (isSwordItem(targetUsefulItem)) {
                    score += 15000 - distItem * 700;
                } else if (isInstantStackName(targetUsefulItem.type())) {
                    score += 12000 - distItem * 600;
                } else if (isSpeedBoostName(targetUsefulItem.type())) {
                    score += 9000 - distItem * 400;
                } else {
                    score += 5000 - distItem * 300;
                }
            }

            if (dir == trueDir) score += 10;

            if (score > bestSafeScore) {
                bestSafeScore = score;
                bestSafe = dir;
            }
        }

        if (bestSafe != null) return bestSafe;
        if (fallbackBadApple != null) return fallbackBadApple;
        return (fallbackAny != null) ? fallbackAny : trueDir;
    }

    private Set<Position> getEnemyHeadDangerZone(GameField field, Snake mySnake) {
        Set<Position> danger = new HashSet<>();
        int mySize = mySnake.body().size();

        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (enemy == null || !enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;

            Position enemyHead = enemy.body().get(0);
            danger.add(enemyHead);

            for (Direction d : Direction.values()) {
                danger.add(wrap(move(enemyHead, d), field.size()));
            }

            if (enemy.body().size() >= mySize) {
                for (Direction d1 : Direction.values()) {
                    Position oneStep = wrap(move(enemyHead, d1), field.size());
                    for (Direction d2 : Direction.values()) {
                        danger.add(wrap(move(oneStep, d2), field.size()));
                    }
                }
            }
        }
        return danger;
    }

    private Item findNearestUsefulItem(Position head, List<Item> items, Size size) {
        if (items == null) return null;
        Item bestItem = null; int bestScore = Integer.MIN_VALUE;

        for (Item item : items) {
            if (item == null || item.type() == null || !isUsefulItem(item)) continue;
            int dist = distance(head, item.position(), size);
            int score = 0;

            if (isSwordName(item.type())) score += 10000;
            else if (isInstantStackName(item.type())) score += 8000;
            else if (isSpeedBoostName(item.type())) score += 5000;
            else if (isStarName(item.type())) score += 4000;

            score -= dist * 500;
            if (score > bestScore) { bestScore = score; bestItem = item; }
        }
        return bestItem;
    }

    private boolean isUsefulItem(Item item) {
        if (item == null || item.type() == null) return false;
        String t = item.type().toLowerCase();
        return isSwordName(t) || isInstantStackName(t) || isSpeedBoostName(t) || isStarName(t);
    }

    private int scoreTowardsEnemy(Position next, Set<Position> enemyBodies, Size size) {
        int bestDistance = Integer.MAX_VALUE;
        for (Position enemyPart : enemyBodies) {
            int dist = distance(next, enemyPart, size);
            if (dist < bestDistance) bestDistance = dist;
        }
        if (bestDistance == Integer.MAX_VALUE) return 0;
        if (bestDistance == 0) return 1_000_000;
        if (bestDistance == 1) return 500_000;
        if (bestDistance == 2) return 200_000;
        if (bestDistance == 3) return 80_000;
        if (bestDistance == 4) return 30_000;
        return 10_000 - bestDistance * 500;
    }

    private boolean activateItemByName(String item) {
        try {
            api.activateItem(item);
            System.out.println("ITEM AKTIVIERT: " + item);
            return true;
        } catch (Exception e) {
            System.out.println("Item Fehler: " + item);
            return false;
        }
    }

    private String findItemNameInInventory(Snake mySnake, boolean swordOnly) {
        if (mySnake.inventory() == null) return null;
        for (String item : mySnake.inventory()) {
            if (item == null) continue;
            if (swordOnly && isSwordName(item)) return item;
            if (!swordOnly && isStarName(item)) return item;
        }
        return null;
    }

    private String findInstantStackInInventory(Snake mySnake) {
        if (mySnake.inventory() == null) return null;
        for (String item : mySnake.inventory()) {
            if (item != null && isInstantStackName(item)) return item;
        }
        return null;
    }

    private boolean isInstantStackName(String name) {
        if (name == null) return false;
        String t = name.toLowerCase();
        return t.contains("instant") || t.contains("stack");
    }

    private boolean isSpeedBoostName(String name) {
        if (name == null) return false;
        String t = name.toLowerCase();
        return t.contains("speed") || t.contains("boost");
    }

    private boolean isSwordName(String name) {
        return name != null && (name.toLowerCase().contains("sword") || name.toLowerCase().contains("schwert"));
    }

    private boolean isSwordItem(Item item) {
        return item != null && item.type() != null && isSwordName(item.type());
    }

    private boolean isStarName(String name) {
        if (name == null) return false;
        String t = name.toLowerCase();
        return t.contains("star") || t.contains("stern") || t.contains("power") || t.contains("invincible");
    }

    private Set<Position> getOwnBodyPositions(Snake mySnake) {
        return (mySnake.body() != null) ? new HashSet<>(mySnake.body()) : new HashSet<>();
    }

    private Set<Position> getEnemyBodyPositions(GameField field) {
        Set<Position> enemy = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake snake = e.getValue();
            if (snake != null && snake.body() != null) enemy.addAll(snake.body());
        }
        return enemy;
    }

    private Set<Position> getBlockedPositions(GameField field) {
        Set<Position> blocked = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            Snake snake = e.getValue();
            if (snake == null || snake.body() == null || snake.body().isEmpty()) continue;
            List<Position> body = snake.body();
            int limit = e.getKey().equals(teamName) ? body.size() - 1 : body.size();
            for (int i = 0; i < limit; i++) blocked.add(body.get(i));
        }
        return blocked;
    }

    private Set<Position> getBadApplePositions(List<Item> items) {
        Set<Position> bad = new HashSet<>();
        if (items == null) return bad;
        for (Item item : items) {
            if (item != null && item.type() != null && item.type().toLowerCase().contains("bad")) {
                bad.add(item.position());
            }
        }
        return bad;
    }

    private int countFreeFields(Position start, Set<Position> blocked, Size size) {
        Set<Position> visited = new HashSet<>();
        Queue<Position> queue = new LinkedList<>();
        queue.add(start); visited.add(start);

        while (!queue.isEmpty()) {
            Position cur = queue.poll();
            for (Direction d : Direction.values()) {
                Position next = wrap(move(cur, d), size);
                if (!visited.contains(next) && !blocked.contains(next)) {
                    visited.add(next); queue.add(next);
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
        int x = p.x(); int y = p.y();
        if (x < 0) x = s.width() - 1; else if (x >= s.width()) x = 0;
        if (y < 0) y = s.height() - 1; else if (y >= s.height()) y = 0;
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