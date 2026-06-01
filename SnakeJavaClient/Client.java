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

    private static final int MAX_EXTRA_LENGTH = 5;
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
                // Configured for 1 tick per second map speed
                Thread.sleep(1000);

                GameField field = api.getField();
                if (field == null) continue;

                Snake mySnake = field.snakesPerTeamName().get(teamName);

                if (mySnake != null && mySnake.body() != null && !mySnake.body().isEmpty()) {
                    updateLengthMode(mySnake);

                    if (attackPreparedForNextTick) {
                        attackItemTicks = ATTACK_ITEM_TICKS;
                        attackPreparedForNextTick = false;
                        alreadyCutWithAttackItem = false;
                        System.out.println("⚔️ ATTACK POWERUP ACTIVATED!");
                    }

                    System.out.println("Length: " + mySnake.body().size());
                    System.out.println("Inventory: " + mySnake.inventory());
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
        boolean hasAttackPower = attackItemTicks > 0;

        Set<Position> ownBody = getOwnBodyPositions(mySnake);
        Set<Position> enemyBodies = getEnemyBodyPositions(field);
        Set<Position> badApples = getBadApplePositions(field.items());
        Set<Position> blocked = getBlockedPositions(field);

        // Active Emergency Shield & Escape Activations
        executeAutomaticInventoryManagement(mySnake, myHead, field, blocked);

        if (!hasAttackPower && !attackPreparedForNextTick) {
            Direction straightAttack = prepareAttackItemAndGoStraightIfEnemyInFront(
                    mySnake, field, myHead, trueDir, ownBody, enemyBodies, badApples
            );
            if (straightAttack != null) {
                return straightAttack;
            }
        }

        if (hasAttackPower && alreadyCutWithAttackItem) {
            return decideCarefulAfterCutDirection(field, mySnake, myHead, trueDir, ownBody, enemyBodies, badApples);
        }

        if (hasAttackPower) {
            return decideAttackDirection(field, myHead, trueDir, ownBody, enemyBodies, badApples);
        }

        return decideNormalDirection(field, mySnake, myHead, trueDir, badApples);
    }

    private void executeAutomaticInventoryManagement(Snake mySnake, Position myHead, GameField field, Set<Position> blocked) {
        if (mySnake.inventory() == null || mySnake.inventory().isEmpty()) return;

        int openSpace = countFreeFields(myHead, blocked, field.size());
        
        // Much more aggressive InstantStack burning if local freedom drops below size safety threshold
        if (openSpace < mySnake.body().size() * 1.5 || openSpace < 15) {
            String stackItem = findItemByKeyword(mySnake, "stack");
            if (stackItem == null) stackItem = findItemByKeyword(mySnake, "instant");
            
            if (stackItem != null && activateItemByName(stackItem)) {
                System.out.println("💥 EMERGENCY: InstantStack deployed to break free from potential enclosure!");
                return;
            }
        }

        String boostItem = findItemByKeyword(mySnake, "speed");
        if (boostItem == null) boostItem = findItemByKeyword(mySnake, "boost");
        
        if (boostItem != null) {
            for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
                if (e.getKey().equals(teamName)) continue;
                Snake enemy = e.getValue();
                if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;

                Position enemyHead = enemy.body().get(0);
                int dist = distance(myHead, enemyHead, field.size());
                if (dist > 0 && dist <= 4) {
                    if (activateItemByName(boostItem)) {
                        System.out.println("🚀 VELOCITY ENGAGED: SpeedBoost burnt to out-maneuver open-ended zones.");
                        break;
                    }
                }
            }
        }
    }

    private String findItemByKeyword(Snake mySnake, String keyword) {
        for (String item : mySnake.inventory()) {
            if (item != null && item.toLowerCase().contains(keyword.toLowerCase())) {
                return item;
            }
        }
        return null;
    }

    private Direction prepareAttackItemAndGoStraightIfEnemyInFront(
            Snake mySnake, GameField field, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> enemyBodies, Set<Position> badApples
    ) {
        Position front = wrap(move(myHead, trueDir), field.size());
        if (badApples.contains(front)) return null;

        boolean isEnemy = enemyBodies.contains(front);
        boolean isOwnBody = ownBody.contains(front);

        if (!isEnemy && !isOwnBody) return null;

        String sword = findItemNameInInventory(mySnake, true);
        if (sword != null && activateItemByName(sword)) {
            attackPreparedForNextTick = true;
            return trueDir;
        }

        String star = findItemNameInInventory(mySnake, false);
        if (star != null && activateItemByName(star)) {
            attackPreparedForNextTick = true;
            return trueDir;
        }

        return null;
    }

    private Direction decideAttackDirection(
            GameField field, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> enemyBodies, Set<Position> badApples
    ) {
        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;
        Direction emergencyOwnBodyCut = null;
        Direction emergencyBadApple = null;

        Set<Position> enemyHeads = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (enemy.alive() && enemy.body() != null && !enemy.body().isEmpty()) {
                enemyHeads.add(enemy.body().get(0));
            }
        }

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;
            Position next = wrap(move(myHead, dir), field.size());

            if (badApples.contains(next)) {
                if (emergencyBadApple == null) emergencyBadApple = dir;
                continue;
            }

            if (enemyHeads.contains(next)) {
                alreadyCutWithAttackItem = true;
                return dir;
            } else if (enemyBodies.contains(next)) {
                alreadyCutWithAttackItem = true;
                return dir;
            } else if (ownBody.contains(next)) {
                if (emergencyOwnBodyCut == null) emergencyOwnBodyCut = dir;
                continue;
            }

            int score = scoreTowardsEnemy(next, enemyBodies, field.size());
            if (dir == trueDir) score += 5;

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }
        }

        if (bestDirection != null) return bestDirection;
        if (emergencyBadApple != null) return emergencyBadApple;
        if (emergencyOwnBodyCut != null) {
            alreadyCutWithAttackItem = true;
            return emergencyOwnBodyCut;
        }

        return trueDir;
    }

    private Direction decideCarefulAfterCutDirection(
            GameField field, Snake mySnake, Position myHead, Direction trueDir,
            Set<Position> ownBody, Set<Position> enemyBodies, Set<Position> badApples
    ) {
        int mySize = mySnake.body().size();
        Set<Position> blocked = new HashSet<>();
        blocked.addAll(ownBody);
        blocked.addAll(enemyBodies);

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

        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;
        Direction fallback = null;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;
            Position next = wrap(move(myHead, dir), field.size());

            if (blocked.contains(next) || badApples.contains(next)) continue;
            if (fallback == null) fallback = dir;
            if (lethalHeadZone.contains(next)) continue;

            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(badApples);
            floodBlocked.addAll(lethalHeadZone);

            int space = countFreeFields(next, floodBlocked, field.size());
            int score = space * 150;

            // Heavily penalize tight pockets
            if (space < mySize * 1.5) score -= 15000;
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

        Set<Position> lethalHeadZone = new HashSet<>();
        Set<Position> weakHeadZone = new HashSet<>();

        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;

            Position enemyHead = enemy.body().get(0);
            for (Direction d : Direction.values()) {
                Position p = wrap(move(enemyHead, d), field.size());
                if (enemy.body().size() >= mySize) {
                    lethalHeadZone.add(p);
                } else {
                    weakHeadZone.add(p);
                }
            }
        }

        Item targetAttackItem = findNearestSwordFirstThenStar(myHead, field.items(), field.size());
        Item targetApple = (!wantsBadApple) ? findNearestGoodApple(myHead, field.items(), field.size()) : null;

        Direction bestSafe = null;
        int bestSafeScore = Integer.MIN_VALUE;

        Direction fallbackBadApple = null;
        Direction fallbackLethal = null;
        Direction fallbackAny = null;

        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) continue;
            Position next = wrap(move(myHead, dir), field.size());

            if (blocked.contains(next)) continue;
            if (fallbackAny == null) fallbackAny = dir;
            if (lethalHeadZone.contains(next)) {
                if (fallbackLethal == null) fallbackLethal = dir;
                continue;
            }

            if (badApples.contains(next)) {
                if (fallbackBadApple == null) fallbackBadApple = dir;
                continue;
            }

            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(lethalHeadZone);
            floodBlocked.addAll(badApples);

            // Compute exact free fields
            int space = countFreeFields(next, floodBlocked, field.size());
            
            // NEW: Calculate regional congestion index (how many blocked objects in a 5x5 window)
            int congestion = calculateRegionalCongestion(next, floodBlocked, field.size());

            // Strategy Base Score Configuration
            int score = space * 200;
            score -= congestion * 350; // Heavy penalty for high-density cluster pockets!

            // Enclosure avoidance check
            if (space < mySize * 1.5) {
                score -= 40000; // Ultra high penalty to reject boxed in domains completely
            }

            if (targetAttackItem != null) {
                int distItem = distance(next, targetAttackItem.position(), field.size());
                if (isSwordItem(targetAttackItem)) {
                    score += 2500 - distItem * 250;
                    if (distItem == 0) score += 12000;
                } else {
                    score += 1200 - distItem * 150;
                    if (distItem == 0) score += 7000;
                }
            }

            if (targetApple != null) {
                int distApple = distance(next, targetApple.position(), field.size());
                score += 150 - distApple * 15;
                if (distApple == 0) score += 1000;
            }

            if (weakHeadZone.contains(next)) score += 300;
            if (dir == trueDir) score += 25; // Slight forward momentum preference

            if (score > bestSafeScore) {
                bestSafeScore = score;
                bestSafe = dir;
            }
        }

        if (bestSafe != null) return bestSafe;
        if (fallbackBadApple != null) return fallbackBadApple;
        if (fallbackLethal != null) return fallbackLethal;
        
        return (fallbackAny != null) ? fallbackAny : trueDir;
    }

    private int calculateRegionalCongestion(Position target, Set<Position> blocked, Size size) {
        int count = 0;
        // Scan a 5x5 perimeter centered over the destination position
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                Position checkPos = wrap(new Position(target.x() + dx, target.y() + dy), size);
                if (blocked.contains(checkPos)) {
                    count++;
                }
            }
        }
        return count;
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
        return 10_000 - bestDistance * 500;
    }

    private boolean activateItemByName(String item) {
        try {
            api.activateItem(item);
            return true;
        } catch (Exception e) {
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

    private boolean isAttackItemName(String name) {
        return isSwordName(name) || isStarName(name);
    }

    private boolean isSwordName(String name) {
        return name != null && name.toLowerCase().contains("sword");
    }

    private boolean isSwordItem(Item item) {
        return item != null && item.type() != null && isSwordName(item.type());
    }

    private boolean isStarName(String name) {
        if (name == null) return false;
        String t = name.toLowerCase();
        return t.contains("star") || t.contains("stern") || t.contains("invincible");
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
            int limit = e.getKey().equals(teamName) ? body.size() - 1 : body.size();

            for (int i = 0; i < limit; i++) {
                blocked.add(body.get(i));
            }
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

    private Item findNearestGoodApple(Position head, List<Item> items, Size size) {
        if (items == null) return null;
        Item nearest = null;
        int best = Integer.MAX_VALUE;

        for (Item item : items) {
            if (item == null || item.type() == null) continue;
            String t = item.type().toLowerCase();
            if (t.contains("bad") || isAttackItemName(t)) continue;

            if (t.contains("apple") || t.contains("boost") || t.contains("stack") || t.contains("speed")) {
                int d = distance(head, item.position(), size);
                if (d < best) {
                    best = d;
                    nearest = item;
                }
            }
        }
        return nearest;
    }

    private Item findNearestSwordFirstThenStar(Position head, List<Item> items, Size size) {
        Item sword = findNearestSpecificAttackItem(head, items, size, true);
        if (sword != null) return sword;
        return findNearestSpecificAttackItem(head, items, size, false);
    }

    private Item findNearestSpecificAttackItem(Position head, List<Item> items, Size size, boolean swordOnly) {
        if (items == null) return null;
        Item nearest = null;
        int best = Integer.MAX_VALUE;

        for (Item item : items) {
            if (item == null || item.type() == null) continue;
            if (swordOnly && !isSwordName(item.type())) continue;
            if (!swordOnly && !isStarName(item.type())) continue;

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

        // Max lookahead cutoff to measure true paths rather than huge dead-ends
        int searchLimit = 250; 
        int counted = 0;

        while (!queue.isEmpty() && counted < searchLimit) {
            Position cur = queue.poll();
            counted++;
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