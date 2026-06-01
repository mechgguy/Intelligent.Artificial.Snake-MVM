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

    /*
     * NEU:
     * Wenn wir mit Sword/Star einmal eine Snake geschnitten haben,
     * wird das true.
     * Danach soll der Bot vorsichtig sein und nicht blind weiter in Snakes fahren.
     */
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
                Thread.sleep(500);

                GameField field = api.getField();
                Snake mySnake = field.snakesPerTeamName().get(teamName);

                if (mySnake != null && mySnake.body() != null && !mySnake.body().isEmpty()) {
                    updateLengthMode(mySnake);

                    if (attackPreparedForNextTick) {
                        attackItemTicks = ATTACK_ITEM_TICKS;
                        attackPreparedForNextTick = false;
                        alreadyCutWithAttackItem = false;
                        System.out.println("ANGRIFFS-ITEM IST JETZT AKTIV!");
                    }

                    System.out.println("Length: " + mySnake.body().size());
                    System.out.println("StartLength: " + startLength);
                    System.out.println("TargetLength: " + (startLength + MAX_EXTRA_LENGTH));
                    System.out.println("WantsBadApple: " + wantsBadApple);
                    System.out.println("AttackItemTicks: " + attackItemTicks);
                    System.out.println("AttackPreparedForNextTick: " + attackPreparedForNextTick);
                    System.out.println("AlreadyCutWithAttackItem: " + alreadyCutWithAttackItem);
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

                System.out.println("Richtung: " + nextDirection);
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

        /*
         * Wenn Sword/Star im Inventory ist UND direkt vorne eine Snake steht:
         * vorbereiten und geradeaus weiterfahren.
         */
        if (!hasAttackPower && !attackPreparedForNextTick) {
            Direction straightAttack = prepareAttackItemAndGoStraightIfEnemyInFront(
                    mySnake,
                    field,
                    myHead,
                    trueDir,
                    ownBody,
                    enemyBodies,
                    badApples
            );

            if (straightAttack != null) {
                return straightAttack;
            }
        }

        /*
         * NEU:
         * Wenn wir schon einmal geschnitten haben,
         * dann nicht mehr blind weiter in Snakes reinfahren.
         * Dann vorsichtig fahren.
         */
        if (hasAttackPower && alreadyCutWithAttackItem) {
            return decideCarefulAfterCutDirection(
                    field,
                    mySnake,
                    myHead,
                    trueDir,
                    ownBody,
                    enemyBodies,
                    badApples
            );
        }

        /*
         * Angriff nur, solange noch nicht geschnitten wurde.
         */
        if (hasAttackPower) {
            return decideAttackDirection(
                    field,
                    myHead,
                    trueDir,
                    ownBody,
                    enemyBodies,
                    badApples
            );
        }

        if (wantsBadApple) {
            Direction badAppleDirection = decideBadAppleDirection(
                    field,
                    mySnake,
                    myHead,
                    trueDir,
                    badApples
            );

            if (badAppleDirection != null) {
                System.out.println("LÄNGE ZU HOCH -> BAD APPLE SUCHEN: " + badAppleDirection);
                return badAppleDirection;
            }
        }

        return decideNormalDirection(
                field,
                mySnake,
                myHead,
                trueDir,
                badApples
        );
    }

    private Direction prepareAttackItemAndGoStraightIfEnemyInFront(
            Snake mySnake,
            GameField field,
            Position myHead,
            Direction trueDir,
            Set<Position> ownBody,
            Set<Position> enemyBodies,
            Set<Position> badApples
    ) {
        if (attackItemTicks > 0 || attackPreparedForNextTick) {
            return null;
        }

        if (mySnake.inventory() == null || mySnake.inventory().isEmpty()) {
            return null;
        }

        Position front = wrap(move(myHead, trueDir), field.size());

        if (!ownBody.contains(front)) {
            System.out.println("Vorne eigener Körper -> kein Angriff");
            return null;
        }

        if (badApples.contains(front)) {
            System.out.println("Vorne Bad Apple -> kein Angriff");
            return null;
        }

        if (!enemyBodies.contains(front)) {
            return null;
        }

        String sword = findItemNameInInventory(mySnake, true);

        if (sword != null) {
            if (activateItemByName(sword)) {
                attackPreparedForNextTick = true;
                System.out.println("SWORD VORBEREITET UND GERADEAUS IN SNAKE: " + trueDir);
                return trueDir;
            }
        }

        String star = findItemNameInInventory(mySnake, false);

        if (star != null) {
            if (activateItemByName(star)) {
                attackPreparedForNextTick = true;
                System.out.println("STAR VORBEREITET UND GERADEAUS IN SNAKE: " + trueDir);
                return trueDir;
            }
        }

        return null;
    }

    /*
     * ANGRIFFS-MODUS:
     * Vor dem ersten Schnitt darf er in eine Snake reinfahren.
     */
    private Direction decideAttackDirection(
            GameField field,
            Position myHead,
            Direction trueDir,
            Set<Position> ownBody,
            Set<Position> enemyBodies,
            Set<Position> badApples
    ) {
        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : Direction.values()) {

            if (isOpposite(trueDir, dir)) {
                continue;
            }

            Position next = wrap(move(myHead, dir), field.size());


            if (badApples.contains(next)) {
                continue;
            }

            if (enemyBodies.contains(next) || (ownBody.contains(next))) {
                System.out.println("ATTACK ITEM SCHNEIDET SNAKE: " + dir);

                /*
                 * NEU:
                 * Wir merken uns: Es wurde geschnitten.
                 * Danach wird der Bot vorsichtig.
                 */
                alreadyCutWithAttackItem = true;

                return dir;
            }

            int score = 0;

            score += scoreTowardsEnemy(next, enemyBodies, field.size());

            if (dir == trueDir) {
                score += 5;
            }

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }

            System.out.println(
                    "ATTACK Dir: " + dir
                            + " | Score: " + score
                            + " | EnemyDirect: " + enemyBodies.contains(next)
            );
        }

        if (bestDirection != null) {
            System.out.println("ATTACK ITEM JAGT GEGNER: " + bestDirection);
            return bestDirection;
        }

        return trueDir;
    }

    /*
     * NEU:
     * Nach dem ersten Schnitt:
     * Nicht mehr in Gegnerkörper fahren.
     * Gegnerkörper werden wieder als blockiert behandelt.
     */
    private Direction decideCarefulAfterCutDirection(
            GameField field,
            Snake mySnake,
            Position myHead,
            Direction trueDir,
            Set<Position> ownBody,
            Set<Position> enemyBodies,
            Set<Position> badApples
    ) {
        int mySize = mySnake.body().size();

        Set<Position> blocked = new HashSet<>();
        blocked.addAll(ownBody);
        blocked.addAll(enemyBodies);

        Set<Position> lethalHeadZone = new HashSet<>();

        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) {
                continue;
            }

            Snake enemy = e.getValue();

            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) {
                continue;
            }

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

            if (isOpposite(trueDir, dir)) {
                continue;
            }

            Position next = wrap(move(myHead, dir), field.size());

            /*
             * Nach Schnitt:
             * Nicht mehr in eigene Snake, andere Snake oder Bad Apple.
             */
            if (blocked.contains(next)) {
                continue;
            }

            if (badApples.contains(next)) {
                continue;
            }

            if (fallback == null) {
                fallback = dir;
            }

            /*
             * Wenn vorne wieder eine Snake kommen könnte:
             * lieber vermeiden.
             */
            if (lethalHeadZone.contains(next)) {
                continue;
            }

            int score = 0;

            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(badApples);
            floodBlocked.addAll(lethalHeadZone);

            int space = countFreeFields(next, floodBlocked, field.size());

            score += space * 100;

            if (space < mySize) {
                score -= 3000;
            }

            if (dir == trueDir) {
                score += 10;
            }

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }

            System.out.println(
                    "NACH-SCHNITT Dir: " + dir
                            + " | Score: " + score
                            + " | Space: " + space
            );
        }

        if (bestDirection != null) {
            System.out.println("NACH SCHNITT VORSICHTIG: " + bestDirection);
            return bestDirection;
        }

        if (fallback != null) {
            return fallback;
        }

        return trueDir;
    }

    private Direction decideBadAppleDirection(
            GameField field,
            Snake mySnake,
            Position myHead,
            Direction trueDir,
            Set<Position> badApples
    ) {
        if (badApples == null || badApples.isEmpty()) {
            return null;
        }

        int mySize = mySnake.body().size();
        Set<Position> blocked = getBlockedPositions(field);
        Set<Position> lethalHeadZone = new HashSet<>();

        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) {
                continue;
            }

            Snake enemy = e.getValue();

            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) {
                continue;
            }

            Position enemyHead = enemy.body().get(0);

            for (Direction d : Direction.values()) {
                Position p = wrap(move(enemyHead, d), field.size());

                if (enemy.body().size() >= mySize) {
                    lethalHeadZone.add(p);
                }
            }
        }

        Position targetBadApple = findNearestBadApplePosition(myHead, badApples, field.size());

        if (targetBadApple == null) {
            return null;
        }

        Direction bestDirection = null;
        int bestScore = Integer.MIN_VALUE;
        Direction emergencyBadAppleNow = null;

        for (Direction dir : Direction.values()) {

            if (isOpposite(trueDir, dir)) {
                continue;
            }

            Position next = wrap(move(myHead, dir), field.size());

            if (blocked.contains(next)) {
                continue;
            }

            boolean lethalRisk = lethalHeadZone.contains(next);

            if (badApples.contains(next)) {
                if (!lethalRisk) {
                    System.out.println("BAD APPLE DIREKT ESSEN: " + dir);
                    return dir;
                }

                emergencyBadAppleNow = dir;
                continue;
            }

            if (lethalRisk) {
                continue;
            }

            int score = 0;

            int distBad = distance(next, targetBadApple, field.size());

            score += 8000 - distBad * 1000;

            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(lethalHeadZone);

            int space = countFreeFields(next, floodBlocked, field.size());

            score += space * 10;

            if (space < 2) {
                score -= 1000;
            }

            if (dir == trueDir) {
                score += 5;
            }

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }

            System.out.println(
                    "BAD-APPLE-MODUS Dir: " + dir
                            + " | Score: " + score
                            + " | DistBad: " + distBad
                            + " | Space: " + space
            );
        }

        if (bestDirection != null) {
            return bestDirection;
        }

        if (emergencyBadAppleNow != null) {
            return emergencyBadAppleNow;
        }

        return null;
    }

    private Direction decideNormalDirection(
            GameField field,
            Snake mySnake,
            Position myHead,
            Direction trueDir,
            Set<Position> badApples
    ) {
        int mySize = mySnake.body().size();

        Set<Position> blocked = getBlockedPositions(field);

        Set<Position> lethalHeadZone = new HashSet<>();
        Set<Position> weakHeadZone = new HashSet<>();

        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) {
                continue;
            }

            Snake enemy = e.getValue();

            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) {
                continue;
            }

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

        Item targetApple = null;

        if (!wantsBadApple) {
            targetApple = findNearestGoodApple(myHead, field.items(), field.size());
        }

        Direction bestSafe = null;
        int bestSafeScore = Integer.MIN_VALUE;

        Direction fallbackBadApple = null;
        Direction fallbackLethal = null;
        Direction fallbackAny = null;

        for (Direction dir : Direction.values()) {

            if (isOpposite(trueDir, dir)) {
                continue;
            }

            Position next = wrap(move(myHead, dir), field.size());

            if (blocked.contains(next)) {
                continue;
            }

            if (fallbackAny == null) {
                fallbackAny = dir;
            }

            if (lethalHeadZone.contains(next)) {
                if (fallbackLethal == null) {
                    fallbackLethal = dir;
                }
                continue;
            }

            if (badApples.contains(next)) {
                if (fallbackBadApple == null) {
                    fallbackBadApple = dir;
                }
                continue;
            }

            int score = 0;

            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(lethalHeadZone);
            floodBlocked.addAll(badApples);

            int space = countFreeFields(next, floodBlocked, field.size());

            score += space * 100;

            if (space < mySize) {
                score -= 5000;
            }

            if (targetAttackItem != null) {
                int distItem = distance(next, targetAttackItem.position(), field.size());

                if (isSwordItem(targetAttackItem)) {
                    score += 2500 - distItem * 250;

                    if (distItem == 0) {
                        score += 12000;
                    } else if (distItem == 1 && space > mySize + 2) {
                        score += 6000;
                    } else if (distItem == 2 && space > mySize + 4) {
                        score += 3000;
                    }
                } else {
                    score += 1200 - distItem * 150;

                    if (distItem == 0) {
                        score += 7000;
                    } else if (distItem == 1 && space > mySize + 2) {
                        score += 3500;
                    } else if (distItem == 2 && space > mySize + 4) {
                        score += 1800;
                    }
                }
            }

            if (targetApple != null) {
                int distApple = distance(next, targetApple.position(), field.size());

                score += 100 - distApple * 10;

                if (distApple == 0) {
                    score += 800;
                } else if (distApple == 1 && space > mySize + 3) {
                    score += 300;
                }
            }

            if (weakHeadZone.contains(next)) {
                score -= 100;
            }

            if (dir == trueDir) {
                score += 10;
            }

            if (score > bestSafeScore) {
                bestSafeScore = score;
                bestSafe = dir;
            }

            System.out.println(
                    "NORMAL Dir: " + dir
                            + " | Score: " + score
                            + " | Space: " + space
                            + " | Length: " + mySize
            );
        }

        if (bestSafe != null) {
            return bestSafe;
        }

        if (fallbackBadApple != null) {
            return fallbackBadApple;
        }

        if (fallbackLethal != null) {
            return fallbackLethal;
        }

        if (fallbackAny != null) {
            return fallbackAny;
        }

        return trueDir;
    }

    private int scoreTowardsEnemy(Position next, Set<Position> enemyBodies, Size size) {
        int bestDistance = Integer.MAX_VALUE;

        for (Position enemyPart : enemyBodies) {
            int dist = distance(next, enemyPart, size);

            if (dist < bestDistance) {
                bestDistance = dist;
            }
        }

        if (bestDistance == Integer.MAX_VALUE) {
            return 0;
        }

        if (bestDistance == 0) {
            return 1_000_000;
        }

        if (bestDistance == 1) {
            return 500_000;
        }

        if (bestDistance == 2) {
            return 200_000;
        }

        if (bestDistance == 3) {
            return 80_000;
        }

        if (bestDistance == 4) {
            return 30_000;
        }

        return 10_000 - bestDistance * 500;
    }

    private boolean activateItemByName(String item) {
        try {
            api.activateItem(item);
            System.out.println("ANGRIFFS-ITEM VORBEREITET: " + item);
            return true;
        } catch (Exception e) {
            System.out.println("Item konnte nicht vorbereitet werden: " + item);
            return false;
        }
    }

    private String findItemNameInInventory(Snake mySnake, boolean swordOnly) {
        if (mySnake.inventory() == null) {
            return null;
        }

        for (String item : mySnake.inventory()) {
            if (item == null) {
                continue;
            }

            if (swordOnly && isSwordName(item)) {
                return item;
            }

            if (!swordOnly && isStarName(item)) {
                return item;
            }
        }

        return null;
    }

    private boolean isAttackItemName(String name) {
        return isSwordName(name) || isStarName(name);
    }

    private boolean isSwordName(String name) {
        if (name == null) {
            return false;
        }

        return name.toLowerCase().contains("sword")
                || name.toLowerCase().contains("Sword");
    }

    private boolean isSwordItem(Item item) {
        return item != null && item.type() != null && isSwordName(item.type());
    }

    private boolean isStarName(String name) {
        if (name == null) {
            return false;
        }

        String t = name.toLowerCase();

        return t.contains("star")
                || t.contains("Star")
                || t.contains("stern")
                || t.contains("invincible");
    }

    private Set<Position> getOwnBodyPositions(Snake mySnake) {
        Set<Position> own = new HashSet<>();

        if (mySnake.body() != null) {
            own.addAll(mySnake.body());
        }

        return own;
    }

    private Set<Position> getEnemyBodyPositions(GameField field) {
        Set<Position> enemy = new HashSet<>();

        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) {
                continue;
            }

            Snake snake = e.getValue();

            if (snake.body() != null) {
                enemy.addAll(snake.body());
            }
        }

        return enemy;
    }

    private Set<Position> getBlockedPositions(GameField field) {
        Set<Position> blocked = new HashSet<>();

        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            Snake snake = e.getValue();

            if (snake.body() == null || snake.body().isEmpty()) {
                continue;
            }

            List<Position> body = snake.body();
            boolean isMe = e.getKey().equals(teamName);

            int limit = isMe ? body.size() - 1 : body.size();

            for (int i = 0; i < limit; i++) {
                blocked.add(body.get(i));
            }
        }

        return blocked;
    }

    private Set<Position> getBadApplePositions(List<Item> items) {
        Set<Position> bad = new HashSet<>();

        if (items == null) {
            return bad;
        }

        for (Item item : items) {
            if (item != null
                    && item.type() != null
                    && item.type().toLowerCase().contains("bad")) {
                bad.add(item.position());
            }
        }

        return bad;
    }

    private Position findNearestBadApplePosition(Position head, Set<Position> badApples, Size size) {
        Position nearest = null;
        int best = Integer.MAX_VALUE;

        for (Position badApple : badApples) {
            int d = distance(head, badApple, size);

            if (d < best) {
                best = d;
                nearest = badApple;
            }
        }

        return nearest;
    }

    private Item findNearestGoodApple(Position head, List<Item> items, Size size) {
        if (items == null) {
            return null;
        }

        Item nearest = null;
        int best = Integer.MAX_VALUE;

        for (Item item : items) {
            if (item == null || item.type() == null) {
                continue;
            }

            String t = item.type().toLowerCase();

            if (t.contains("bad")) {
                continue;
            }

            if (isAttackItemName(t)) {
                continue;
            }

            if (t.contains("apple") || t.contains("sword") || t.contains("star")|| t.contains("Star") ) {
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

        if (sword != null) {
            return sword;
        }

        return findNearestSpecificAttackItem(head, items, size, true);
    }

    private Item findNearestSpecificAttackItem(Position head, List<Item> items, Size size, boolean swordOnly) {
        if (items == null) {
            return null;
        }

        Item nearest = null;
        int best = Integer.MAX_VALUE;

        for (Item item : items) {
            if (item == null || item.type() == null) {
                continue;
            }

            if (swordOnly && !isSwordName(item.type())) {
                continue;
            }

            if (!swordOnly && !isStarName(item.type())) {
                continue;
            }

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
        if (snake.body() == null || snake.body().size() < 2) {
            return Direction.EAST;
        }

        Position head = snake.body().get(0);
        Position neck = snake.body().get(1);

        int dx = head.x() - neck.x();
        int dy = head.y() - neck.y();

        if (dx > fieldSize.width() / 2) {
            dx -= fieldSize.width();
        }

        if (dx < -fieldSize.width() / 2) {
            dx += fieldSize.width();
        }

        if (dy > fieldSize.height() / 2) {
            dy -= fieldSize.height();
        }

        if (dy < -fieldSize.height() / 2) {
            dy += fieldSize.height();
        }

        if (dx == 1) {
            return Direction.EAST;
        }

        if (dx == -1) {
            return Direction.WEST;
        }

        if (dy == 1) {
            return Direction.SOUTH;
        }

        if (dy == -1) {
            return Direction.NORTH;
        }

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

        if (x < 0) {
            x = s.width() - 1;
        } else if (x >= s.width()) {
            x = 0;
        }

        if (y < 0) {
            y = s.height() - 1;
        } else if (y >= s.height()) {
            y = 0;
        }

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

        return Math.min(dx, s.width() - dx)
                + Math.min(dy, s.height() - dy);
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