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

       // if (!ownBody.contains(front)) {
         //   System.out.println("Vorne eigener Körper -> kein Angriff");
           // return null;
        //}

        if (badApples.contains(front)) { //vermeiden von bad apples
            return null;
        }

        boolean isEnemy = enemyBodies.contains(front);
        boolean isOwnBody = ownBody.contains(front);

        if (!isEnemy && !isOwnBody) {
            return null;
        }

        // Safety: if the cell ahead is an enemy HEAD and we have no weapon ready,
        // driving straight in would be a head-on collision (mutual death).
        // Only proceed if we actually have a usable weapon to activate.
        boolean frontIsEnemyHead = false;
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (enemy.alive() && enemy.body() != null && !enemy.body().isEmpty()) {
                if (enemy.body().get(0).equals(front)) {
                    frontIsEnemyHead = true;
                    break;
                }
            }
        }

        String sword = findItemNameInInventory(mySnake, true);
        String star  = findItemNameInInventory(mySnake, false);

        // If the front cell is literally the enemy head and we have no weapon at all: abort
        if (frontIsEnemyHead && sword == null && star == null) {
            System.out.println("⚠️ HEAD-ON ABORT: Enemy head directly in front, no weapon!");
            return null;
        }

        if (sword != null) {
            if (activateItemByName(sword)) {
                attackPreparedForNextTick = true;
                System.out.println("SWORD VORBEREITET UND GERADEAUS IN SNAKE: " + trueDir);
                return trueDir;
            }
        }

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
        Direction emergencyOwnBodyCut = null;
        Direction emergencyBadApple = null;

        // Collect actual enemy heads (get(0) = head)
        Set<Position> enemyHeads = new HashSet<>();
        // Also track which heads belong to equal/larger snakes (ramming them = mutual death even with sword)
        Set<Position> lethalEnemyHeads = new HashSet<>();
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();

            if (enemy.alive() && enemy.body() != null && !enemy.body().isEmpty()) {
                Position eHead = enemy.body().get(0);
                enemyHeads.add(eHead);
                // Ramming heads causes mutual death regardless of weapon — avoid!
                lethalEnemyHeads.add(eHead);
            }
        }
        for (Direction dir : Direction.values()) {
            if (isOpposite(trueDir, dir)) {
                continue;
            }
            Position next = wrap(move(myHead, dir), field.size());

            // 1. ÄNDERUNG: Bad Apple für den Notfall merken, statt ihn komplett zu überspringen!
            if (badApples.contains(next)) {
                if (emergencyBadApple == null) {
                    emergencyBadApple = dir;
                }
                continue;
            }
            // PRIO 0: Never ram directly into an enemy head — sword cuts BODY not head-to-head
            if (lethalEnemyHeads.contains(next)) {
                System.out.println("⚠️ SKIP HEAD-ON: Avoiding direct head collision at " + next);
                if (emergencyOwnBodyCut == null) emergencyOwnBodyCut = dir; // last resort only
                continue;
            }
            // PRIO 1: Enemy BODY (not head) — safe to cut with sword
            if (enemyBodies.contains(next) && !enemyHeads.contains(next)) {
                System.out.println("⚔️ ATTACK ITEM SCHNEIDET KÖRPER: " + dir);
                alreadyCutWithAttackItem = true;
                return dir;
            }
            else if (ownBody.contains(next)) {
                if (emergencyOwnBodyCut == null) {
                    emergencyOwnBodyCut = dir;
                }
                continue;
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

        // 1. Gibt es einen freien Weg zum Gegner?
        if (bestDirection != null) {
            System.out.println("ATTACK ITEM JAGT GEGNER: " + bestDirection);
            return bestDirection;
        }

        // 2. ÄNDERUNG: Bevor wir uns selbst zerschneiden, fressen wir lieber den Bad Apple!
        if (emergencyBadApple != null) {
            System.out.println("NOTFALL: ESSE BAD APPLE: " + emergencyBadApple);
            return emergencyBadApple;
        }

        // 3. Wenn absolut gar nichts anderes mehr geht: Eigenen Körper opfern.
        if (emergencyOwnBodyCut != null) {
            System.out.println("NOTFALL: ATTACK ITEM SCHNEIDET EIGENEN KÖRPER: " + emergencyOwnBodyCut);
            alreadyCutWithAttackItem = true;
            return emergencyOwnBodyCut;
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

        // 2-step danger zones for post-cut careful movement
        Set<Position> lethalHeadZone  = new HashSet<>();
        Set<Position> approachZone    = new HashSet<>();
        buildDangerZones(field, myHead, mySize, lethalHeadZone, approachZone);

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
             * Avoid both immediate lethal zone AND 2-step approach zone after a cut.
             */
            if (lethalHeadZone.contains(next) || approachZone.contains(next)) {
                continue;
            }

            int score = 0;

            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(badApples);
            floodBlocked.addAll(lethalHeadZone);
            floodBlocked.addAll(approachZone);

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
        Set<Position> approachZone   = new HashSet<>();
        buildDangerZones(field, myHead, mySize, lethalHeadZone, approachZone);

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

            boolean lethalRisk = lethalHeadZone.contains(next) || approachZone.contains(next);

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
            floodBlocked.addAll(approachZone);

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

        // 2-step head-on danger zones (see buildDangerZones for explanation)
        Set<Position> lethalHeadZone  = new HashSet<>(); // 1-step: immediate death
        Set<Position> approachZone    = new HashSet<>(); // 2-step: converging head-on
        Set<Position> weakHeadZone    = new HashSet<>(); // 1-step: weaker enemy (low risk)
        buildDangerZones(field, myHead, mySize, lethalHeadZone, approachZone);

        // weakHeadZone: 1-step reachable by enemies SMALLER than us (non-lethal, slight penalty)
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;
            if (enemy.body().size() < mySize) {
                Position enemyHead = enemy.body().get(0);
                for (Direction d : Direction.values()) {
                    weakHeadZone.add(wrap(move(enemyHead, d), field.size()));
                }
            }
        }

        // --- Locate targets in priority order ---
        // PRIO 1: Sword (greedy, always pursue)
        Item targetSword = findNearestSpecificAttackItem(myHead, field.items(), field.size(), true);
        // PRIO 1b: Star (greedy, if no sword)
        Item targetStar  = findNearestSpecificAttackItem(myHead, field.items(), field.size(), false);
        // The best attack item is sword first, then star
        Item targetAttackItem = targetSword != null ? targetSword : targetStar;

        // PRIO 2: Boost
        Item targetBoost = findNearestBoost(myHead, field.items(), field.size());

        // PRIO 3: Good apple (only if under length cap)
        Item targetApple = (!wantsBadApple)
                ? findNearestGoodApple(myHead, field.items(), field.size())
                : null;

        // --- Score each candidate direction ---
        Direction bestSafe = null;
        int bestSafeScore = Integer.MIN_VALUE;

        Direction fallbackBadApple = null;
        Direction fallbackLethal   = null;
        Direction fallbackAny      = null;

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

            // Hard block: 1-step lethal zone (immediate head-on risk)
            if (lethalHeadZone.contains(next)) {
                if (fallbackLethal == null) {
                    fallbackLethal = dir;
                }
                continue;
            }

            // Bad apple: allow only as last resort (saved as fallback, not scored normally)
            if (badApples.contains(next)) {
                if (fallbackBadApple == null) {
                    fallbackBadApple = dir;
                }
                continue;
            }

            // --- Flood-fill for space safety ---
            Set<Position> floodBlocked = new HashSet<>();
            floodBlocked.addAll(blocked);
            floodBlocked.addAll(lethalHeadZone);
            floodBlocked.addAll(approachZone); // treat 2-step zones as blocked for flood fill
            floodBlocked.addAll(badApples);

            int space = countFreeFields(next, floodBlocked, field.size());

            int score = space * 100;

            if (space < mySize) {
                score -= 5000;
            }

            // =========================================================
            // PRIORITY SCORING — descending order, non-overlapping bands
            // =========================================================

            // PRIO 1: Sword (highest) — score band: +500_000 base
            if (targetSword != null) {
                int d = distance(next, targetSword.position(), field.size());
                score += 500_000 - d * 20_000;
                if (d == 0) score += 1_000_000; // stepping on it
            }

            // PRIO 1b: Star — score band: +200_000 base (only if no sword)
            else if (targetStar != null) {
                int d = distance(next, targetStar.position(), field.size());
                score += 200_000 - d * 8_000;
                if (d == 0) score += 400_000;
            }

            // PRIO 2: Boost — score band: +80_000 base
            if (targetBoost != null) {
                int d = distance(next, targetBoost.position(), field.size());
                score += 80_000 - d * 3_000;
                if (d == 0) score += 150_000;
            }

            // PRIO 3: Good apple — score band: +5_000 base (well below boost/sword)
            if (targetApple != null) {
                int d = distance(next, targetApple.position(), field.size());
                score += 5_000 - d * 200;
                if (d == 0) score += 8_000;
            }

            // PRIO 4: nothing special — just survive (space score above handles this)

            // slight penalty for heading into weak-enemy head zone
            if (weakHeadZone.contains(next)) {
                score -= 100;
            }

            // 2-step approach zone: strong penalty — likely head-on next tick
            if (approachZone.contains(next)) {
                score -= 50_000;
            }

            // Contested target penalty: if an enemy head is also approaching the same
            // item target as us AND we have no sword active, reduce item score to avoid
            // a head-on collision at the pickup point.
            if (targetAttackItem != null) {
                Position tPos = targetAttackItem.position();
                int myDistToTarget = distance(myHead, tPos, field.size());
                for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
                    if (e.getKey().equals(teamName)) continue;
                    Snake enemy = e.getValue();
                    if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;
                    Position eHead = enemy.body().get(0);
                    int enemyDistToTarget = distance(eHead, tPos, field.size());
                    // If enemy is within 1 tick of us reaching the same target, it's contested
                    if (Math.abs(myDistToTarget - enemyDistToTarget) <= 1 && myDistToTarget <= 3) {
                        // If we'd step onto or adjacent to the target, and enemy is also closing in
                        int distNextToTarget = distance(next, tPos, field.size());
                        if (distNextToTarget <= 1 && attackItemTicks == 0) {
                            score -= 80_000; // heavy penalty: contested pickup = likely head-on
                            System.out.println("⚠️ CONTESTED TARGET at " + tPos + " — backing off");
                        }
                        break;
                    }
                }
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

        // --- Choose direction ---
        Direction chosen = null;

        if (bestSafe != null) {
            chosen = bestSafe;
        } else if (wantsBadApple && fallbackBadApple != null) {
            // PRIO 5: bad apple — only when we deliberately want to shrink
            System.out.println("PRIO 5: Bad apple to shrink: " + fallbackBadApple);
            chosen = fallbackBadApple;
        } else if (fallbackLethal != null) {
            // PRIO 6: lethal head zone — risky but alive
            chosen = fallbackLethal;
        } else if (fallbackAny != null) {
            // PRIO 7: any move (collision is the very last resort, handled by caller)
            chosen = fallbackAny;
        } else {
            chosen = trueDir; // PRIO 8: collide — no other option
        }

        // --- BOOST ACTIVATION (after direction chosen) ---
        // Rule A: enemy within 2 tiles AND we hold at least 1 sword → activate boost
        // Rule B: closed space, gap within 2 tiles ahead → activate boost to punch through
        tryActivateBoost(mySnake, myHead, chosen, field, blocked, mySize);

        return chosen;
    }

    /**
     * Activates a boost from inventory when the conditions are met:
     *   A) An enemy is within 2 Manhattan tiles AND we have a sword in inventory.
     *   B) We are in a tight space (free fields < mySize * 2) AND the gap in the
     *      chosen direction clears within 2 steps.
     */
    private void tryActivateBoost(
            Snake mySnake, Position myHead, Direction chosenDir,
            GameField field, Set<Position> blocked, int mySize
    ) {
        String boost = findBoostNameInInventory(mySnake);
        if (boost == null) return;

        // Rule A: enemy nearby + sword held
        boolean enemyClose = false;
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;
            Position eHead = enemy.body().get(0);
            if (distance(myHead, eHead, field.size()) <= 2) {
                enemyClose = true;
                break;
            }
        }

        if (enemyClose && hasSwordInInventory(mySnake)) {
            if (activateItemByName(boost)) {
                System.out.println("⚡ BOOST: Enemy ≤2 tiles + sword in hand → speed burst!");
            }
            return;
        }

        // Rule B: tight space, but gap opens within 2 steps in chosen direction
        int totalFree = countFreeFields(myHead, blocked, field.size());
        boolean inClosedSpace = totalFree < mySize * 2;

        if (inClosedSpace && chosenDir != null) {
            Position step1 = wrap(move(myHead, chosenDir), field.size());
            Position step2 = wrap(move(step1, chosenDir), field.size());

            // "gap within 2" = step1 or step2 is free and opens up space
            boolean gapAhead = !blocked.contains(step1) || !blocked.contains(step2);
            int spaceAhead = !blocked.contains(step2)
                    ? countFreeFields(step2, blocked, field.size())
                    : 0;

            if (gapAhead && spaceAhead >= mySize) {
                if (activateItemByName(boost)) {
                    System.out.println("⚡ BOOST: Tight space, gap ≤2 steps → punching through!");
                }
            }
        }
    }

    /**
     * Builds two sets for head-collision avoidance:
     *
     * lethalZone   — positions reachable in 1 move by a lethal enemy head.
     *                Entering this is an immediate death risk.
     *
     * approachZone — positions reachable in 2 moves by a lethal enemy that is
     *                moving TOWARD us. Entering here risks a head-on next tick.
     *
     * Equal-size snakes are treated as lethal (head-on = mutual death).
     */
    private void buildDangerZones(
            GameField field, Position myHead, int mySize,
            Set<Position> lethalZone, Set<Position> approachZone
    ) {
        for (Map.Entry<String, Snake> e : field.snakesPerTeamName().entrySet()) {
            if (e.getKey().equals(teamName)) continue;
            Snake enemy = e.getValue();
            if (!enemy.alive() || enemy.body() == null || enemy.body().isEmpty()) continue;

            boolean enemyIsLethal = enemy.body().size() >= mySize;
            Position enemyHead = enemy.body().get(0);
            Direction enemyDir = getTrueDirection(enemy, field.size());

            // 1-step: every square the enemy can step onto next tick
            for (Direction d : Direction.values()) {
                if (isOpposite(enemyDir, d)) continue;
                Position p1 = wrap(move(enemyHead, d), field.size());
                if (enemyIsLethal) lethalZone.add(p1);

                // 2-step: squares reachable in one more move, only if closing on us
                for (Direction d2 : Direction.values()) {
                    if (isOpposite(d, d2)) continue;
                    Position p2 = wrap(move(p1, d2), field.size());
                    if (enemyIsLethal) {
                        int distNow   = distance(enemyHead, myHead, field.size());
                        int distAfter = distance(p2, myHead, field.size());
                        if (distAfter < distNow) {
                            approachZone.add(p2);
                        }
                    }
                }
            }
        }
        approachZone.removeAll(lethalZone); // avoid double-flagging
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

    /** Returns the name of a boost/speed item in inventory, or null. */
    private String findBoostNameInInventory(Snake mySnake) {
        if (mySnake.inventory() == null) return null;
        for (String item : mySnake.inventory()) {
            if (item != null && isBoostName(item)) return item;
        }
        return null;
    }

    /** Returns true if inventory contains at least one sword (not star). */
    private boolean hasSwordInInventory(Snake mySnake) {
        if (mySnake.inventory() == null) return false;
        for (String item : mySnake.inventory()) {
            if (item != null && isSwordName(item)) return true;
        }
        return false;
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

    private boolean isBoostName(String name) {
        if (name == null) return false;
        String t = name.toLowerCase();
        return t.contains("speed") || t.contains("boost") || t.contains("turbo");
    }

    private boolean isBoostItem(Item item) {
        return item != null && item.type() != null && isBoostName(item.type());
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

    /**
     * Returns the nearest speed-boost item on the field, or null if none.
     */
    private Item findNearestBoost(Position head, List<Item> items, Size size) {
        if (items == null) return null;
        Item nearest = null;
        int best = Integer.MAX_VALUE;
        for (Item item : items) {
            if (item == null || item.type() == null) continue;
            String t = item.type().toLowerCase();
            if (t.contains("speed") || t.contains("boost") || t.contains("turbo")) {
                int d = distance(head, item.position(), size);
                if (d < best) { best = d; nearest = item; }
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

            // Skip bad apples, attack items, and boost items — those have their own finders
            if (t.contains("bad")) continue;
            if (isAttackItemName(t)) continue;
            if (t.contains("speed") || t.contains("boost") || t.contains("turbo")) continue;

            // Only genuine apples
            if (t.contains("apple")) {
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
        // Priority 1: sword
        Item sword = findNearestSpecificAttackItem(head, items, size, true);
        if (sword != null) {
            return sword;
        }
        // Priority 2: star/invincibility (was broken — used swordOnly=true by mistake)
        return findNearestSpecificAttackItem(head, items, size, false);
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