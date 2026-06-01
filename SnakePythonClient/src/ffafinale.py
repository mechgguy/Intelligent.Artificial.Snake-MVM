import time
import math
import sys
import requests

class Position:
    def __init__(self, x, y):
        self._x = x
        self._y = y
    def x(self): return self._x
    def y(self): return self._y
    def __eq__(self, other): return isinstance(other, Position) and self._x == other._x and self._y == other._y
    def __hash__(self): return hash((self._x, self._y))
    def __repr__(self): return f"({self._x},{self._y})"

class Size:
    def __init__(self, width, height):
        self._width = width
        self._height = height
    def width(self): return self._width
    def height(self): return self._height

class Item:
    def __init__(self, item_type, position):
        self._type = item_type
        self._position = position
    def type(self): return self._type
    def position(self): return self._position

class Snake:
    def __init__(self, body, inventory, active_effects, alive):
        self._body = body
        self._inventory = inventory if inventory else []
        self._active_effects = active_effects if active_effects else []
        self._alive = alive
    def body(self): return self._body
    def inventory(self): return self._inventory
    def active_effects(self): return self._active_effects
    def alive(self): return self._alive

class GameField:
    def __init__(self, size, snakes_per_team, items):
        self._size = size
        self._snakes_per_team = snakes_per_team
        self._items = items
    def size(self): return self._size
    def snakesPerTeamName(self): return self._snakes_per_team
    def items(self): return self._items

class HttpSnakeFieldAPI:
    def __init__(self, server_url, team_name, game_name, password):
        self.server_url = server_url
        self.team_name = team_name
        self.game_name = game_name
        self.password = password
        
    def getField(self):
        try:
            res = requests.get(f"{self.server_url}/games/{self.game_name}/field", timeout=0.4).json()
            size = Size(res['size']['width'], res['size']['height'])
            snakes = {}
            for t_name, s_data in res['snakesPerTeamName'].items():
                body = [Position(p['x'], p['y']) for p in s_data['body']]
                snakes[t_name] = Snake(body, s_data.get('inventory', []), s_data.get('activeEffects', []), s_data.get('alive', True))
            items = [Item(i['type'], Position(i['position']['x'], i['position']['y'])) for i in res.get('items', [])]
            return GameField(size, snakes, items)
        except Exception as e:
            print(f"API Error fetching field: {e}")
            return None

    def setDirection(self, direction):
        try:
            requests.post(f"{self.server_url}/games/{self.game_name}/direction", 
                          json={"teamName": self.team_name, "password": self.password, "direction": direction}, timeout=0.4)
        except Exception as e:
            print(f"API Error setting direction: {e}")

    def activateItem(self, item_name):
        res = requests.post(f"{self.server_url}/games/{self.game_name}/activate", 
                            json={"teamName": self.team_name, "password": self.password, "itemName": item_name}, timeout=0.4)
        if res.status_code != 200:
            raise Exception(f"Failed to activate item: {res.text}")


class Client:
    ATTACK_ITEM_TICKS = 4

    def __init__(self, server_url, team_name, game_name, password):
        self.api = HttpSnakeFieldAPI(server_url, team_name, game_name, password)
        self.team_name = team_name
        self.attack_item_ticks = 0
        self.attack_prepared_for_next_tick = False
        self.already_cut_with_attack_item = False
        self.start_length = -1
        self.wants_bad_apple = False

    def run(self):
        self.api.setDirection("EAST")
        while True:
            try:
                time.sleep(0.5)
                field = self.api.getField()
                if not field: continue

                my_snake = field.snakesPerTeamName().get(self.team_name)
                if my_snake and my_snake.body():
                    self.update_length_mode(my_snake)
                    if self.attack_prepared_for_next_tick:
                        self.attack_item_ticks = self.ATTACK_ITEM_TICKS
                        self.attack_prepared_for_next_tick = False
                        self.already_cut_with_attack_item = False
                        print("💥 ATTACK EFFECT IS NOW ACTIVE!")
                    
                    print(f"Length: {len(my_snake.body())} | Inventory: {my_snake.inventory()}")

                next_direction = self.decide_direction(field)
                self.api.setDirection(next_direction)

                if self.attack_item_ticks > 0:
                    self.attack_item_ticks -= 1
                    if self.attack_item_ticks == 0:
                        self.already_cut_with_attack_item = False

                print(f"Heading: {next_direction}\n--------------------------")
            except Exception as e:
                print(f"Loop Exception: {e}")

    def update_length_mode(self, my_snake):
        current_length = len(my_snake.body())
        if self.start_length == -1:
            self.start_length = current_length
        self.wants_bad_apple = current_length >= (self.start_length + 999)

    def decide_direction(self, field):
        my_snake = field.snakesPerTeamName().get(self.team_name)
        if not my_snake or not my_snake.body():
            return "EAST"

        my_head = my_snake.body()[0]
        true_dir = self.get_true_direction(my_snake, field.size())

        enemy_bodies = self.get_enemy_body_positions(field)
        bad_apples = self.get_bad_apple_positions(field.items())
        blocked = self.get_blocked_positions(field)

        # =========================================================================
        # 🔥 MANDATORY RULE 1: FORCE SWORD/STAR USE IF ENEMY IS AT DISTANCE 1
        # =========================================================================
        if self.attack_item_ticks == 0 and not self.attack_prepared_for_next_tick:
            enemy_directly_adjacent = any(self.distance(my_head, p, field.size()) == 1 for p in enemy_bodies)
            
            if enemy_directly_adjacent:
                weapon_to_force = self.find_item_name_in_inventory(my_snake, sword_only=True)
                if not weapon_to_force:
                    weapon_to_force = self.find_item_name_in_inventory(my_snake, sword_only=False)
                
                if weapon_to_force and self.activate_item_by_name(weapon_to_force):
                    self.attack_prepared_for_next_tick = True
                    print(f"🚨 MANDATORY ATTACK: Forced activation of {weapon_to_force} due to proximity (distance 1)!")

        # =========================================================================
        # 🔥 MANDATORY RULE 2: FORCE BOOST TO RUSH ENEMY IF DISTANCE IS 4 AND OWN SWORD
        # =========================================================================
        instant_sword = self.find_item_name_in_inventory(my_snake, sword_only=True)
        if instant_sword:
            enemy_at_distance_four = False
            for t_name, enemy in field.snakesPerTeamName().items():
                if t_name == self.team_name or not enemy or not enemy.alive() or not enemy.body(): 
                    continue
                if any(self.distance(my_head, p, field.size()) == 4 for p in enemy.body()):
                    enemy_at_distance_four = True
                    break
            
            if enemy_at_distance_four:
                boost_to_force = self.find_boost_name_in_inventory(my_snake.inventory())
                if boost_to_force and self.activate_item_by_name(boost_to_force):
                    print("⚡ MANDATORY RUSH: Injected Boost! Enemy spotted at distance 4 while armed with Sword.")

        # =========================================================================
        # 🔥 MANDATORY RULE 3: FORCE STACK IF SNAKE LENGTH EXCEEDS THRESHOLD 18
        # =========================================================================
        if len(my_snake.body()) > 18:
            threshold_stack = self.find_instant_stack_in_inventory(my_snake)
            if threshold_stack and self.activate_item_by_name(threshold_stack):
                print(f"💥 MANDATORY THRESHOLD STACK: Size is {len(my_snake.body())} (> 18)! Deploying Instant Stack.")

        # =========================================================================

        self.execute_proactive_inventory_triggers(my_snake, my_head, field, blocked)

        has_attack_power = self.attack_item_ticks > 0
        own_body = self.get_own_body_positions(my_snake)

        if has_attack_power and self.already_cut_with_attack_item:
            return self.decide_careful_after_cut_direction(field, my_snake, my_head, true_dir, own_body, enemy_bodies, bad_apples)
        if has_attack_power:
            return self.decide_attack_direction(field, my_head, true_dir, own_body, enemy_bodies, bad_apples)

        return self.decide_normal_direction(field, my_snake, my_head, true_dir, bad_apples)

    def execute_proactive_inventory_triggers(self, my_snake, my_head, field, blocked):
        if not my_snake.inventory(): return
        inventory = list(my_snake.inventory())
        size = field.size()

        boost_token = self.find_boost_name_in_inventory(inventory)
        if boost_token:
            available_space = self.count_free_fields(my_head, blocked, size)
            if available_space < 12 and self.activate_item_by_name(boost_token):
                print("⚡ PROACTIVE TRIGGER: Velocity Boost injected! (Defensive Escape: True)")
                if boost_token in inventory: inventory.remove(boost_token)

        stack_token = self.find_instant_stack_in_inventory(my_snake)
        if stack_token:
            true_dir = self.get_true_direction(my_snake, size)
            next_tile = self.wrap(self.move(my_head, true_dir), size)
            collision_imminent = next_tile in blocked
            available_space = self.count_free_fields(my_head, blocked, size)
            
            if (collision_imminent or available_space < len(my_snake.body())) and self.activate_item_by_name(stack_token):
                print("💥 PROACTIVE TRIGGER: Safe space collapsed or collision near! Deploying Instant Stack.")

    def decide_attack_direction(self, field, my_head, true_dir, own_body, enemy_bodies, bad_apples):
        best_direction, best_score = None, -sys.maxsize
        for dir_str in ["NORTH", "SOUTH", "EAST", "WEST"]:
            if self.is_opposite(true_dir, dir_str): continue
            next_pos = self.wrap(self.move(my_head, dir_str), field.size())
            if next_pos in own_body or next_pos in bad_apples: continue

            if next_pos in enemy_bodies:
                print(f"ATTACK ITEM SCHNEIDET SNAKE: {dir_str}")
                self.already_cut_with_attack_item = True
                return dir_str

            score = self.score_towards_enemy(next_pos, enemy_bodies, field.size())
            if dir_str == true_dir: score += 5
            if score > best_score:
                best_score, best_direction = score, dir_str

        return best_direction if best_direction else true_dir

    def decide_careful_after_cut_direction(self, field, my_snake, my_head, true_dir, own_body, enemy_bodies, bad_apples):
        my_size = len(my_snake.body())
        blocked = own_body.union(enemy_bodies)
        head_danger = self.get_enemy_head_danger_zone(field, my_snake)
        best_direction, best_score, fallback = None, -sys.maxsize, None

        for dir_str in ["NORTH", "SOUTH", "EAST", "WEST"]:
            if self.is_opposite(true_dir, dir_str): continue
            next_pos = self.wrap(self.move(my_head, dir_str), field.size())
            if next_pos in blocked or next_pos in bad_apples: continue
            if not fallback: fallback = dir_str
            if next_pos in head_danger: continue

            flood_blocked = blocked.union(bad_apples).union(head_danger)
            space = self.count_free_fields(next_pos, flood_blocked, field.size())
            score = space * 100
            if space < my_size: score -= 3000
            if dir_str == true_dir: score += 10

            if score > best_score:
                best_score, best_direction = score, dir_str

        return best_direction if best_direction else (fallback if fallback else true_dir)

    def decide_normal_direction(self, field, my_snake, my_head, true_dir, bad_apples):
        my_size = len(my_snake.body())
        blocked = self.get_blocked_positions(field)
        head_danger = self.get_enemy_head_danger_zone(field, my_snake)
        target_item = self.find_nearest_useful_item(my_head, field.items(), field.size())

        best_safe, best_safe_score = None, -sys.maxsize
        fallback_bad_apple, fallback_any = None, None

        for dir_str in ["NORTH", "SOUTH", "EAST", "WEST"]:
            if self.is_opposite(true_dir, dir_str): continue
            next_pos = self.wrap(self.move(my_head, dir_str), field.size())

            if next_pos in blocked: continue
            if not fallback_any: fallback_any = dir_str
            if next_pos in head_danger: continue
            if next_pos in bad_apples:
                if not fallback_bad_apple: fallback_bad_apple = dir_str
                continue

            flood_blocked = blocked.union(head_danger).union(bad_apples)
            space = self.count_free_fields(next_pos, flood_blocked, field.size())
            score = space * 100
            if space < my_size: score -= 5000

            if target_item:
                dist_item = self.distance(next_pos, target_item.position(), field.size())
                if self.is_sword_name(target_item.type()): score += 15000 - dist_item * 700
                elif self.is_star_name(target_item.type()): score += 14000 - dist_item * 650
                elif self.is_instant_stack_name(target_item.type()): score += 12000 - dist_item * 600
                elif self.is_speed_boost_name(target_item.type()): score += 9000 - dist_item * 400
                else: score += 5000 - dist_item * 300

            if dir_str == true_dir: score += 10
            if score > best_safe_score:
                best_safe_score, best_safe = score, dir_str

        if best_safe: return best_safe
        if fallback_bad_apple: return fallback_bad_apple
        return fallback_any if fallback_any else true_dir

    def get_enemy_head_danger_zone(self, field, my_snake):
        danger = set()
        my_size = len(my_snake.body())
        for t_name, enemy in field.snakesPerTeamName().items():
            if t_name == self.team_name or not enemy or not enemy.alive() or not enemy.body(): continue
            enemy_head = enemy.body()[0]
            danger.add(enemy_head)
            for d in ["NORTH", "SOUTH", "EAST", "WEST"]:
                danger.add(self.wrap(self.move(enemy_head, d), field.size()))
            if len(enemy.body()) >= my_size:
                for d1 in ["NORTH", "SOUTH", "EAST", "WEST"]:
                    step_one = self.wrap(self.move(enemy_head, d1), field.size())
                    for d2 in ["NORTH", "SOUTH", "EAST", "WEST"]:
                        danger.add(self.wrap(self.move(step_one, d2), field.size()))
        return danger

    def find_nearest_useful_item(self, head, items, size):
        if not items: return None
        best_item, best_score = None, -sys.maxsize
        for item in items:
            if not item or not item.type() or not self.is_useful_item(item): continue
            dist = self.distance(head, item.position(), size)
            score = 0
            if self.is_sword_name(item.type()): score += 15000
            elif self.is_star_name(item.type()): score += 14000
            elif self.is_instant_stack_name(item.type()): score += 12000
            elif self.is_speed_boost_name(item.type()): score += 9000
            score -= dist * 500
            if score > best_score:
                best_score, best_item = score, item
        return best_item

    def is_useful_item(self, item):
        t = item.type().lower()
        return self.is_sword_name(t) or self.is_instant_stack_name(t) or self.is_speed_boost_name(t) or self.is_star_name(t) or "apple" in t

    def score_towards_enemy(self, next_pos, enemy_bodies, size):
        best_distance = sys.maxsize
        for part in enemy_bodies:
            dist = self.distance(next_pos, part, size)
            if dist < best_distance: best_distance = dist
        if best_distance == sys.maxsize: return 0
        if best_distance == 0: return 1000000
        if best_distance == 1: return 500000
        if best_distance == 2: return 200000
        if best_distance == 3: return 80000
        return max(0, 10000 - best_distance * 500)

    def activate_item_by_name(self, item_name):
        try:
            self.api.activateItem(item_name)
            print(f"ITEM AKTIVIERT: {item_name}")
            return True
        except Exception:
            print(f"Item Fehler: {item_name}")
            return False

    def find_item_name_in_inventory(self, my_snake, sword_only):
        if not my_snake.inventory(): return None
        for item in my_snake.inventory():
            if not item: continue
            if sword_only and self.is_sword_name(item): return item
            if not sword_only and self.is_star_name(item): return item
        return None

    def find_instant_stack_in_inventory(self, my_snake):
        if not my_snake.inventory(): return None
        for item in my_snake.inventory():
            if item and self.is_instant_stack_name(item): return item
        return None

    def find_boost_name_in_inventory(self, inventory):
        if not inventory: return None
        for item in inventory:
            if item and self.is_speed_boost_name(item): return item
        return None

    def is_instant_stack_name(self, name): return "instant" in name.lower() or "stack" in name.lower()
    def is_speed_boost_name(self, name): return "speed" in name.lower() or "boost" in name.lower()
    def is_sword_name(self, name): return "sword" in name.lower() or "schwert" in name.lower()
    def is_star_name(self, name): return any(k in name.lower() for k in ["star", "stern", "power", "invincible"])

    def get_own_body_positions(self, my_snake): return set(my_snake.body()) if my_snake.body() else set()

    def get_enemy_body_positions(self, field):
        enemy_positions = set()
        for t_name, snake in field.snakesPerTeamName().items():
            if t_name != self.team_name and snake and snake.body():
                enemy_positions.update(snake.body())
        return enemy_positions

    def get_blocked_positions(self, field):
        blocked = set()
        for t_name, snake in field.snakesPerTeamName().items():
            if not snake or not snake.body(): continue
            limit = len(snake.body()) - 1 if t_name == self.team_name else len(snake.body())
            for i in range(limit):
                blocked.add(snake.body()[i])
        return blocked

    def get_bad_apple_positions(self, items):
        return {item.position() for item in items if item and item.type() and "bad" in item.type().lower()}

    def count_free_fields(self, start, blocked, size):
        visited = {start}
        queue = [start]
        count = 0
        while queue:
            cur = queue.pop(0)
            count += 1
            for d in ["NORTH", "SOUTH", "EAST", "WEST"]:
                nxt = self.wrap(self.move(cur, d), size)
                if nxt not in visited and nxt not in blocked:
                    visited.add(nxt)
                    queue.append(nxt)
        return count

    def get_true_direction(self, snake, field_size):
        if not snake.body() or len(snake.body()) < 2: return "EAST"
        head, neck = snake.body()[0], snake.body()[1]
        dx, dy = head.x() - neck.x(), head.y() - neck.y()
        if dx > field_size.width() / 2: dx -= field_size.width()
        if dx < -field_size.width() / 2: dx += field_size.width()
        if dy > field_size.height() / 2: dy -= field_size.height()
        if dy < -field_size.height() / 2: dy += field_size.height()

        if dx == 1: return "EAST"
        if dx == -1: return "WEST"
        if dy == 1: return "SOUTH"
        if dy == -1: return "NORTH"
        return "EAST"

    def move(self, p, d):
        if d == "NORTH": return Position(p.x(), p.y() - 1)
        if d == "SOUTH": return Position(p.x(), p.y() + 1)
        if d == "EAST": return Position(p.x() + 1, p.y())
        if d == "WEST": return Position(p.x() - 1, p.y())

    def wrap(self, p, s):
        x, y = p.x(), p.y()
        if x < 0: x = s.width() - 1
        elif x >= s.width(): x = 0
        if y < 0: y = s.height() - 1
        elif y >= s.height(): y = 0
        return Position(x, y)

    def is_opposite(self, a, b):
        return (a, b) in [("NORTH", "SOUTH"), ("SOUTH", "NORTH"), ("EAST", "WEST"), ("WEST", "EAST")]

    def distance(self, a, b, s):
        dx = abs(a.x() - b.x())
        dy = abs(a.y() - b.y())
        return min(dx, s.width() - dx) + min(dy, s.height() - dy)

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python client.py <team_name> <game_name> [password] [server_url]")
        sys.exit(1)
    
    t_name = sys.argv[1]
    g_name = sys.argv[2]
    pwd = sys.argv[3] if len(sys.argv) > 3 else "test"
    url = sys.argv[4] if len(sys.argv) > 4 else "http://localhost:3030"

    Client(url, t_name, g_name, pwd).run()