import argparse
import time
import random
from collections import deque

from api import SnakeFieldAPI
from data_structures import Direction, get_directions_as_list

# CONFIGURABLE STRATEGY PARAMETERS
LENGTH_THRESHOLD = 15  # Activates space preservation loops at this size

def get_target_coord(current_head, direction, grid_size):
    """Calculates target coordinates handling map wrap-around rules correctly."""
    x, y = current_head
    width, height = grid_size
    if direction == "NORTH": y = (y - 1) % height
    elif direction == "SOUTH": y = (y + 1) % height
    elif direction == "EAST":  x = (x + 1) % width
    elif direction == "WEST":  x = (x - 1) % width
    return (x, y)

def get_opposite_direction(direction):
    """Returns the reverse direction to prevent self-collision via 180 turns."""
    mapping = {"NORTH": "SOUTH", "SOUTH": "NORTH", "EAST": "WEST", "WEST": "EAST"}
    return mapping.get(direction, "WEST")

def find_bfs_move(my_head, dangerous_cells, grid_size, target_apples):
    """Finds the first directional step towards the closest item target using BFS."""
    width, height = grid_size
    directions = ["NORTH", "SOUTH", "EAST", "WEST"]
    
    queue = deque([(my_head, [])])
    visited = {my_head}

    while queue:
        curr_pos, path = queue.popleft()
        
        if curr_pos in target_apples:
            if path:
                return path[0]

        for move in directions:
            next_pos = get_target_coord(curr_pos, move, grid_size)
            if next_pos not in dangerous_cells and next_pos not in visited:
                visited.add(next_pos)
                queue.append((next_pos, path + [move]))
                
    return None

def count_reachable_space(start_pos, dangerous_cells, grid_size, max_depth=40):
    """
    Optimized Flood Fill lookahead. 
    Reduced max_depth from 120 to 40 to prevent computation time outs at large sizes.
    """
    width, height = grid_size
    directions = ["NORTH", "SOUTH", "EAST", "WEST"]
    
    queue = deque([start_pos])
    visited = set(dangerous_cells)  # Pre-seed visited with obstacles for faster lookup
    if start_pos in visited:
        return 0
    visited.add(start_pos)
    
    score = 0

    while queue and score < max_depth:
        curr = queue.popleft()
        score += 1
        for move in directions:
            next_pos = get_target_coord(curr, move, grid_size)
            if next_pos not in visited:
                visited.add(next_pos)
                queue.append(next_pos)
                
    return score   

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Hackathon Multi-Agent Strategy Snake Bot")
    parser.add_argument("team_name", help="Name of the team/snake")
    parser.add_argument("game_name", help="Name of the game to join")
    parser.add_argument("--password", default="test", help="Password for server")
    parser.add_argument("--base_url", default="http://localhost:3030", help="Base URL")
    args = parser.parse_args()

    team_name = args.team_name
    base_url = args.base_url
    game_name = args.game_name
    password = args.password

    currentDirection: Direction = "EAST"
    api = SnakeFieldAPI(base_url, team_name, game_name, password)

    api.set_direction(currentDirection)

    print(f"Connecting dynamic bot '{team_name}' to lobby '{game_name}'...")

    while True:
        start_tick_time = time.time()
        try:
            # 1. Fetch current tick game state map
            field = api.get_field()
            
            # --- ROBUST TEAM IDENTIFICATION FALLBACK ---
            my_snake = field.snakes.get(team_name)
            my_found_key = team_name
            
            if not my_snake:
                snake_keys = list(field.snakes.keys())
                if snake_keys:
                    assigned_key = snake_keys[0]
                    for s_name in snake_keys:
                        if team_name in s_name or "mvm" in s_name.lower():
                            assigned_key = s_name
                            break
                    my_snake = field.snakes[assigned_key]
                    my_found_key = assigned_key
            
            if not my_snake or not my_snake.alive:
                print("Snake waiting for round countdown / spawn context...")
                time.sleep(1.0)
                continue

            my_head = my_snake.body[0]
            current_length = len(my_snake.body)
            grid_size = field.size
            directions_list = ["NORTH", "SOUTH", "EAST", "WEST"]

            # 2. OBJECT MATRIX MAPPING
            dangerous_cells = set()
            
            # Rule A: Treat ALL snake bodies (alive and dead greyed-out segments) as solid walls
            for s_name, s_info in field.snakes.items():
                for block in s_info.body:
                    dangerous_cells.add(block)

            # Rule B: Bad apples shrink us. If length == 1, eating one causes an instant crash!
            if current_length == 1:
                for bad_apple in field.bad_apples:
                    dangerous_cells.add(bad_apple)

            # Rule C: HEAD-ON COLLISION DEFENSE
            # Block positions that an enemy head can reach on the exact same frame
            for s_name, s_info in field.snakes.items():
                if s_name != my_found_key and s_info.alive:
                    enemy_head = s_info.body[0]
                    for enemy_move in directions_list:
                        predicted_enemy_cell = get_target_coord(enemy_head, enemy_move, grid_size)
                        dangerous_cells.add(predicted_enemy_cell)

            # 3. Filter safe immediate moves (and exclude the immediate backward 180 direction)
            all_moves = get_directions_as_list()
            forbidden_reverse = get_opposite_direction(currentDirection)
            
            safe_moves = []
            for move in all_moves:
                if move == forbidden_reverse:
                    continue
                next_pos = get_target_coord(my_head, move, grid_size)
                if next_pos not in dangerous_cells:
                    safe_moves.append(move)

            # --- PANIC LAYER ---
            # If enemy head predictions box us in entirely, drop predictions.
            # Physical boundaries (raw bodies & lethal items) are strictly preserved!
            if not safe_moves:
                dangerous_cells.clear()
                for s_name, s_info in field.snakes.items():
                    for block in s_info.body:
                        dangerous_cells.add(block)
                if current_length == 1:
                    for bad_apple in field.bad_apples:
                        dangerous_cells.add(bad_apple)
                
                safe_moves = []
                for move in all_moves:
                    if move == forbidden_reverse:
                        continue
                    next_pos = get_target_coord(my_head, move, grid_size)
                    if next_pos not in dangerous_cells:
                        safe_moves.append(move)

            # 4. Strategy Engine Selection
            chosen_move = None

            # INITIAL SPAWN DISPERSAL
            if current_length <= 3 and safe_moves:
                best_spawn_move = None
                max_distance_from_enemies = -1
                
                for move in safe_moves:
                    hypothetical_spawn_step = get_target_coord(my_head, move, grid_size)
                    total_enemy_distance = 0
                    
                    for s_name, s_info in field.snakes.items():
                        if s_name != my_found_key and s_info.alive:
                            enemy_head = s_info.body[0]
                            dx = min(abs(hypothetical_spawn_step[0] - enemy_head[0]), grid_size[0] - abs(hypothetical_spawn_step[0] - enemy_head[0]))
                            dy = min(abs(hypothetical_spawn_step[1] - enemy_head[1]), grid_size[1] - abs(hypothetical_spawn_step[1] - enemy_head[1]))
                            total_enemy_distance += (dx + dy)
                    
                    if total_enemy_distance > max_distance_from_enemies:
                        max_distance_from_enemies = total_enemy_distance
                        best_spawn_move = move
                
                if best_spawn_move:
                    chosen_move = best_spawn_move

            # HUNTER MODE ENGINE vs SURVIVAL MODE ENGINE
            if not chosen_move:
                if current_length < LENGTH_THRESHOLD:
                    good_apples = set(field.apples)
                    bad_apples = set(field.bad_apples) if current_length > 1 else set()
                    
                    if safe_moves:
                        if good_apples:
                            chosen_move = find_bfs_move(my_head, dangerous_cells, grid_size, good_apples)
                        if not chosen_move and bad_apples:
                            chosen_move = find_bfs_move(my_head, dangerous_cells, grid_size, bad_apples)
                else:
                    print(f"[SURVIVAL MODE] Fast space loop optimization at size {current_length}.")

            # 5. Flood Fill Space Fallback Evaluation
            if not chosen_move and safe_moves:
                best_space_score = -1
                best_moves = []
                
                for move in safe_moves:
                    hypothetical_step = get_target_coord(my_head, move, grid_size)
                    space_score = count_reachable_space(hypothetical_step, dangerous_cells, grid_size)
                    
                    if space_score > best_space_score:
                        best_space_score = space_score
                        best_moves = [move]
                    elif space_score == best_space_score:
                        best_moves.append(move)
                
                if best_moves:
                    chosen_move = random.choice(best_moves)

            # 6. Final Direction Push
            if chosen_move:
                currentDirection = chosen_move
            elif safe_moves:
                currentDirection = safe_moves[0]
            else:
                print("[CRITICAL PANIC] No safe options left. Maintaining trajectory.")

            api.set_direction(currentDirection)
            
            # Dynamic sleep to adapt perfectly to server time budget
            elapsed = time.time() - start_tick_time
            sleep_time = max(0.05, 0.4 - elapsed)
            time.sleep(sleep_time)

        except Exception as e:
            print(f"Synchronizing turn cycle... (Status Details: {e})")
            time.sleep(0.5)