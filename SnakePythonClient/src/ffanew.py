import argparse
import time
import random
from collections import deque

from api import SnakeFieldAPI
from data_structures import Direction, get_directions_as_list

LENGTH_THRESHOLD = 12

def get_target_coord(current_head, direction, grid_size):
    x, y = current_head
    width, height = grid_size
    if direction == "NORTH": y = (y - 1) % height
    elif direction == "SOUTH": y = (y + 1) % height
    elif direction == "EAST":  x = (x + 1) % width
    elif direction == "WEST":  x = (x - 1) % width
    return (x, y)

def get_opposite_direction(direction):
    mapping = {"NORTH": "SOUTH", "SOUTH": "NORTH", "EAST": "WEST", "WEST": "EAST"}
    return mapping.get(direction, "WEST")

def find_local_bfs_move(my_head, dangerous_cells, grid_size, target_apples, max_dist=15):
    width, height = grid_size
    directions = ["NORTH", "SOUTH", "EAST", "WEST"]
    
    queue = deque([(my_head, [], 0)])
    visited = {my_head}

    while queue:
        curr_pos, path, dist = queue.popleft()
        if dist > max_dist: continue

        if curr_pos in target_apples:
            if path: return path[0]

        for move in directions:
            next_pos = get_target_coord(curr_pos, move, grid_size)
            if next_pos not in dangerous_cells and next_pos not in visited:
                visited.add(next_pos)
                queue.append((next_pos, path + [move], dist + 1))
    return None

def count_reachable_space(start_pos, dangerous_cells, grid_size, max_depth=50):
    directions = ["NORTH", "SOUTH", "EAST", "WEST"]
    queue = deque([start_pos])
    visited = set(dangerous_cells)
    if start_pos in visited: return 0
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
    parser = argparse.ArgumentParser(description="Advanced Tactical FFA Snake")
    parser.add_argument("team_name")
    parser.add_argument("game_name")
    parser.add_argument("--password", default="test")
    parser.add_argument("--base_url", default="http://localhost:3030")
    args = parser.parse_args()

    currentDirection: Direction = "EAST"
    api = SnakeFieldAPI(args.base_url, args.team_name, args.game_name, args.password)

    api.set_direction(currentDirection)

    print(f"Launching Tactical Trapper Bot '{args.team_name}'...")

    while True:
        start_tick_time = time.time()
        try:
            field = api.get_field()
            
            my_snake = field.snakes.get(args.team_name)
            my_found_key = args.team_name
            if not my_snake:
                snake_keys = list(field.snakes.keys())
                if snake_keys:
                    my_found_key = snake_keys[0]
                    my_snake = field.snakes[my_found_key]
            
            if not my_snake or not my_snake.alive:
                time.sleep(1.0)
                continue

            my_head = my_snake.body[0]
            current_length = len(my_snake.body)
            grid_size = field.size
            directions_list = ["NORTH", "SOUTH", "EAST", "WEST"]

            # --- ADVANCED FEATURE EXTRACTION: TAIL MAPPING ---
            permanent_obstacles = set()
            tail_escape_tiles = set()

            for s_name, s_info in field.snakes.items():
                # Extract the last segment of every snake body
                if len(s_info.body) > 1:
                    tail_tile = s_info.body[-1]
                    tail_escape_tiles.add(tail_tile)
                
                for block in s_info.body:
                    permanent_obstacles.add(block)

            # Tail-Gating Optimization: Remove tail tips from obstacles because they vacate next tick
            permanent_obstacles = permanent_obstacles - tail_escape_tiles
            dangerous_cells = set(permanent_obstacles)

            if current_length == 1:
                for bad_apple in field.bad_apples:
                    dangerous_cells.add(bad_apple)

            # --- DIRECTIONAL THREAT ASSESSMENT ---
            # Instead of placing a bubble around every head blindly, look at their orientation
            for s_name, s_info in field.snakes.items():
                if s_name != my_found_key and s_info.alive:
                    enemy_head = s_info.body[0]
                    
                    # Determine general heading of enemy from segment 0 to segment 1
                    enemy_dir_forbidden = None
                    if len(s_info.body) > 1:
                        enemy_neck = s_info.body[1]
                        # Track if they can't 180 flip
                        if enemy_neck[0] == enemy_head[0]:
                            enemy_dir_forbidden = "NORTH" if enemy_neck[1] < enemy_head[1] else "SOUTH"
                        else:
                            enemy_dir_forbidden = "WEST" if enemy_neck[0] < enemy_head[0] else "EAST"

                    for enemy_move in directions_list:
                        if enemy_move == enemy_dir_forbidden:
                            continue  # They can't physically pull a 180 into this tile
                        
                        predicted_cell = get_target_coord(enemy_head, enemy_move, grid_size)
                        dangerous_cells.add(predicted_cell)

            # Calculate safe options
            all_moves = get_directions_as_list()
            forbidden_reverse = get_opposite_direction(currentDirection)
            
            safe_moves = []
            for move in all_moves:
                if move == forbidden_reverse: continue
                next_pos = get_target_coord(my_head, move, grid_size)
                if next_pos not in dangerous_cells:
                    safe_moves.append(move)

            # Panic fallback: Restore physical bodies if bubbles completely trap us
            if not safe_moves:
                dangerous_cells = set(permanent_obstacles)
                if current_length == 1:
                    for bad_apple in field.bad_apples: dangerous_cells.add(bad_apple)
                
                safe_moves = [m for m in all_moves if m != forbidden_reverse and get_target_coord(my_head, m, grid_size) not in dangerous_cells]

            # Strategy Selection
            chosen_move = None
            good_apples = set(field.apples)
            bad_apples = set(field.bad_apples) if current_length > 1 else set()
            
            if safe_moves:
                # Local hunt optimization to stay in our clean pocket zone
                chosen_move = find_local_bfs_move(my_head, dangerous_cells, grid_size, good_apples, max_dist=10)
                if not chosen_move and bad_apples:
                    chosen_move = find_local_bfs_move(my_head, dangerous_cells, grid_size, bad_apples, max_dist=10)

            # Space Loop Fallback Evaluation
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
                
                if best_moves: chosen_move = random.choice(best_moves)

            if chosen_move: currentDirection = chosen_move
            elif safe_moves: currentDirection = safe_moves[0]

            api.set_direction(currentDirection)
            
            elapsed = time.time() - start_tick_time
            time.sleep(max(0.05, 0.4 - elapsed))

        except Exception as e:
            print(f"Cycle frame sync hold... ({e})")
            time.sleep(0.5)