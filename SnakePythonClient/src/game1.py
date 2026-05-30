import argparse
import time
import random
from collections import deque

from api import SnakeFieldAPI
from data_structures import Direction, get_directions_as_list

# CONFIGURABLE STRATEGY PARAMETERS
LENGTH_THRESHOLD = 15  # Switches to defensive survival mode at this size

def get_target_coord(current_head, direction, grid_size):
    """Calculates target coordinates handling map wrap-around rules correctly."""
    x, y = current_head
    width, height = grid_size
    if direction == "NORTH": y = (y - 1) % height
    elif direction == "SOUTH": y = (y + 1) % height
    elif direction == "EAST":  x = (x + 1) % width
    elif direction == "WEST":  x = (x - 1) % width
    return (x, y)

def find_bfs_move(my_head, dangerous_cells, grid_size, apples):
    """
    Finds the exact first directional step towards the closest real apple using BFS.
    Returns None if all paths to apples are blocked.
    """
    width, height = grid_size
    directions = ["NORTH", "SOUTH", "EAST", "WEST"]
    
    # queue tracks: (current_position, list_of_moves_taken_to_get_here)
    queue = deque([(my_head, [])])
    visited = {my_head}

    while queue:
        curr_pos, path = queue.popleft()
        
        # Found an actual apple! Return the initial step that started this path
        if curr_pos in apples:
            if path:
                return path[0]

        for move in directions:
            next_pos = get_target_coord(curr_pos, move, grid_size)
            
            if next_pos not in dangerous_cells and next_pos not in visited:
                visited.add(next_pos)
                queue.append((next_pos, path + [move]))
                
    return None

def count_reachable_space(start_pos, dangerous_cells, grid_size, max_depth=120):
    """Counts available free maneuver space from a position using Flood Fill."""
    width, height = grid_size
    directions = ["NORTH", "SOUTH", "EAST", "WEST"]
    
    queue = deque([start_pos])
    visited = {start_pos}
    score = 0

    while queue and score < max_depth:
        curr = queue.popleft()
        score += 1
        
        for move in directions:
            next_pos = get_target_coord(curr, move, grid_size)
            if next_pos not in dangerous_cells and next_pos not in visited:
                visited.add(next_pos)
                queue.append(next_pos)
                
    return score   

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Two-Policy Greedy/Safe Snake Bot")
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

    print(f"Connecting dynamic bot '{team_name}' to lobby '{game_name}'...")
    api.set_direction(currentDirection)

    while True:
        try:
            # 1. Fetch game state map
            field = api.get_field()
            
            my_snake = field.snakes.get(team_name)
            if not my_snake or not my_snake.alive:
                print("Snake waiting for next match spawn... polling server.")
                time.sleep(1.0)
                continue

            my_head = my_snake.body[0]
            current_length = len(my_snake.body)
            grid_size = field.size

            # 2. Map immediate dangerous coordinates (obstacle matrix)
            dangerous_cells = set()
            for s_name, s_info in field.snakes.items():
                if s_info.alive:
                    for block in s_info.body:
                        dangerous_cells.add(block)

            # 3. Establish adjacent safe movements
            all_moves = get_directions_as_list()
            safe_moves = []
            for move in all_moves:
                next_pos = get_target_coord(my_head, move, grid_size)
                if next_pos not in dangerous_cells:
                    safe_moves.append(move)

            # 4. Filter out parsed apples
            apples = set(field.apples)

            # 5. Dual-Policy Strategy Engine
            chosen_move = None

            if current_length < LENGTH_THRESHOLD:
                # POLICY 1: GREEDY HUNTER MODE
                if safe_moves and apples:
                    chosen_move = find_bfs_move(my_head, dangerous_cells, grid_size, apples)
                    if chosen_move:
                        print(f"[GREEDY] Length {current_length}/{LENGTH_THRESHOLD} -> Routing to closest apple via path: {chosen_move}")
            else:
                # POLICY 2: SAFE SURVIVAL MODE
                print(f"[SURVIVAL] Length {current_length} >= {LENGTH_THRESHOLD} -> Prioritizing space maximization.")

            # Fallback to Flood Fill Lookahead if BFS pathing fails or Survival mode is active
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

            # ... (Step 1-4 remaining the same) ...

            # # 5. DUAL-POLICY STRATEGY SELECTION
            # chosen_move = None

            # # SPECIAL TURN 1 POLICIES (Right at Spawn)
            # if current_length <= 3:  # Adjust based on starting length
            #     best_spawn_move = None
            #     max_distance_from_enemies = -1
                
            #     for move in safe_moves:
            #         hypothetical_spawn_step = get_target_coord(my_head, move, grid_size)
                    
            #         # Calculate total distance to all other starting bot heads
            #         total_enemy_distance = 0
            #         for s_name, s_info in field.snakes.items():
            #             if s_name != team_name and s_info.alive:
            #                 enemy_head = s_info.body[0]
            #                 # Calculate simple Manhattan distance factoring wrap-around
            #                 dx = min(abs(hypothetical_spawn_step[0] - enemy_head[0]), width - abs(hypothetical_spawn_step[0] - enemy_head[0]))
            #                 dy = min(abs(hypothetical_spawn_step[1] - enemy_head[1]), height - abs(hypothetical_spawn_step[1] - enemy_head[1]))
            #                 total_enemy_distance += (dx + dy)
                    
            #         # We want the direction that maximizes distance from opponents to get clean breathing room
            #         if total_enemy_distance > max_distance_from_enemies:
            #             max_distance_from_enemies = total_enemy_distance
            #             best_spawn_move = move
                
            #     if best_spawn_move:
            #         chosen_move = best_spawn_move
            #         print(f"[SPAWN CONTROL] Choosing {chosen_move} to steer away from opponent clusters.")

            # # Normal gameplay kicks in if not turn 1 or if spawn logic didn't find a move
            # if not chosen_move:
            #     if current_length < LENGTH_THRESHOLD:
            #         # POLICY 1: GREEDY HUNTER MODE
            #         if safe_moves and apples:
            #             chosen_move = find_bfs_move(my_head, dangerous_cells, grid_size, apples)

            # 6. Final Execution Step
            if chosen_move:
                currentDirection = chosen_move
            elif safe_moves:
                currentDirection = safe_moves[0]
            else:
                print("[PANIC] No safe coordinates found.")

            api.set_direction(currentDirection)
            
            # Sleep interval to match 1-tick-per-second server steps comfortably
            time.sleep(0.4)

        except Exception as e:
            print(f"Waiting for game round to start... (Status: {e})")
            time.sleep(1.0)